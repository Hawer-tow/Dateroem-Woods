package com.dateroem.backend.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.EnumMap;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.dto.CreateExpenseRequest;
import com.dateroem.backend.model.ExpenseTarget;
import com.dateroem.backend.util.JsonKeys;

@Service
public class ExpenseService {

    // The 8 categories a normal user can create/delete a holder in from
    // the mobile expenses page - farmer payments are "also part of the
    // expenses ... a very key expense" (business owner correction), so
    // they're creatable here too, alongside FarmerPaymentController's
    // separate dedicated "send to a farmer" flow (both just write
    // FARMER_PAYMENTS rows). LOAN/CAPITAL live entirely on the admin-only
    // Loans & Finance page - read here via lineItems() for the totals/
    // drill-down view, but never creatable through this generic endpoint.
    private static final Set<ExpenseTarget> CREATABLE = Set.of(
        ExpenseTarget.TRANSPORT, ExpenseTarget.COMMUNICATIONS, ExpenseTarget.MACHINE_EXPENSES,
        ExpenseTarget.ACCOMODATION, ExpenseTarget.OTHER_EXPENSES, ExpenseTarget.PERMITS, ExpenseTarget.SALARIES,
        ExpenseTarget.FARMER_PAYMENTS
    );

    // "in most expenses there is that option type ... make it like a
    // selection part rather than typing" - these 5 categories are exactly
    // the ones with a free-text Type column today; it becomes a SOFT/HARD
    // payment-mode selector instead (OTHER_EXPENSES/PERMITS/FARMER_PAYMENTS
    // have no Type column at all, hence "most" not "all").
    private static final Set<ExpenseTarget> HAS_PAYMENT_MODE = Set.of(
        ExpenseTarget.TRANSPORT, ExpenseTarget.COMMUNICATIONS, ExpenseTarget.MACHINE_EXPENSES,
        ExpenseTarget.ACCOMODATION, ExpenseTarget.SALARIES
    );

    private static final Map<ExpenseTarget, String> CATEGORY_CODES = new EnumMap<>(ExpenseTarget.class);
    static {
        CATEGORY_CODES.put(ExpenseTarget.TRANSPORT, "TRN");
        CATEGORY_CODES.put(ExpenseTarget.COMMUNICATIONS, "COM");
        CATEGORY_CODES.put(ExpenseTarget.MACHINE_EXPENSES, "MEX");
        CATEGORY_CODES.put(ExpenseTarget.ACCOMODATION, "ACC");
        CATEGORY_CODES.put(ExpenseTarget.SALARIES, "SAL");
        CATEGORY_CODES.put(ExpenseTarget.OTHER_EXPENSES, "OTH");
        CATEGORY_CODES.put(ExpenseTarget.PERMITS, "PER");
        CATEGORY_CODES.put(ExpenseTarget.FARMER_PAYMENTS, "FRM");
    }

    // Every CREATABLE category except FARMER_PAYMENTS, which has no
    // Trip_ID column (see lineItems()'s TREES join) so it can't be
    // grouped by trip the same simple way.
    private static final List<ExpenseTarget> TRIP_SCOPED_EXPENSE_TARGETS = List.of(
        ExpenseTarget.TRANSPORT, ExpenseTarget.COMMUNICATIONS, ExpenseTarget.MACHINE_EXPENSES,
        ExpenseTarget.ACCOMODATION, ExpenseTarget.OTHER_EXPENSES, ExpenseTarget.PERMITS, ExpenseTarget.SALARIES
    );

    private final JdbcTemplate jdbcTemplate;

    public ExpenseService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // "for the expense it can scan for the expense provide information on
    // the most expensive expense and observe even from other trips ...
    // more cost were incurred in the transport section compared to the
    // other trip" - one row per (trip, category) combination with its
    // total, ranked so the biggest cost centers surface first and the
    // same category is easy to compare across different trips just by
    // scanning for it.
    public List<Map<String, Object>> expenseBreakdown() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ExpenseTarget target : TRIP_SCOPED_EXPENSE_TARGETS) {
            rows.addAll(JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
                "SELECT '" + target.categoryKey() + "' AS Category, e.Trip_ID, t.Product_Description AS Trip_Description, " +
                "SUM(e." + target.amountColumn() + ") AS Total_Amount, COUNT(*) AS Entry_Count " +
                "FROM " + target.tableName() + " e JOIN TRIP t ON t.Trip_ID = e.Trip_ID " +
                "WHERE e.Is_Deleted = FALSE AND e.Trip_ID IS NOT NULL " +
                "GROUP BY e.Trip_ID, t.Product_Description"
            )));
        }
        rows.sort((a, b) -> ((BigDecimal) b.get("totalAmount")).compareTo((BigDecimal) a.get("totalAmount")));
        return rows;
    }

    // One row per category via the pre-built fn_trip_*_total functions, so
    // the trip-level view stays cheap under load (business rules: "the
    // cached data should be the profit ... the rest ... can be exempted in
    // less data scenarios").
    public Map<String, BigDecimal> categoryTotals(int tripId) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        totals.put("loan", scalar("fn_trip_loan_total", tripId));
        totals.put("capital", scalar("fn_trip_capital_total", tripId));
        totals.put("farmerPayments", scalar("fn_trip_farmer_payment_total", tripId));
        totals.put("salaries", scalar("fn_trip_salary_total", tripId));
        totals.put("accomodation", scalar("fn_trip_accomodation_total", tripId));
        totals.put("transport", scalar("fn_trip_transport_total", tripId));
        totals.put("communications", scalar("fn_trip_communication_total", tripId));
        totals.put("machineExpenses", scalar("fn_trip_machine_expense_total", tripId));
        totals.put("otherExpenses", scalar("fn_trip_other_expense_total", tripId));
        totals.put("permits", scalar("fn_trip_permit_total", tripId));
        return totals;
    }

    // Individual line items for one category - for drilling into e.g.
    // salaries instead of just seeing the trip-level total. assigned:
    // "all" | "assigned" | "pending" - lets the mobile expenses screen
    // toggle between everything and just what's already been matched to a
    // real FINANCIAL_TRANSACTION (business rules: "2 sections ... toggle
    // to the all or assigned ones").
    public List<Map<String, Object>> lineItems(int tripId, String categoryKey, String assigned) {
        ExpenseTarget target = ExpenseTarget.fromCategoryKey(categoryKey);
        String assignedClause = assignedClause(assigned);

        if (target == ExpenseTarget.FARMER_PAYMENTS) {
            return JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
                "SELECT fp.* FROM FARMER_PAYMENTS fp " +
                "WHERE fp.Is_Deleted = FALSE AND " + assignedClause.replace("Financial_Transaction_ID", "fp.Financial_Transaction_ID") +
                " AND fp.Farmer_ID IN " +
                "(SELECT DISTINCT t.Farmer_ID FROM TREES t WHERE t.Trip_ID = ? AND t.Is_Deleted = FALSE)",
                tripId
            ));
        }
        return JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
            "SELECT * FROM " + target.tableName() + " WHERE Trip_ID = ? AND Is_Deleted = FALSE AND " + assignedClause,
            tripId
        ));
    }

    private String assignedClause(String assigned) {
        if (assigned == null || "all".equalsIgnoreCase(assigned)) {
            return "1=1";
        }
        if ("assigned".equalsIgnoreCase(assigned)) {
            return "Financial_Transaction_ID IS NOT NULL";
        }
        if ("pending".equalsIgnoreCase(assigned)) {
            return "Financial_Transaction_ID IS NULL";
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "assigned must be all, assigned or pending");
    }

    // Creates an unvalidated holder row - business rules: "creates a
    // holder since this is stored in the system but not yet validated and
    // then later it will be validated by admins" (via
    // TransactionService.assign()/candidates()). Financial_Transaction_ID
    // and Reference_ID are left null by design.
    @Transactional
    public Map<String, Object> create(String categoryKey, int tripId, CreateExpenseRequest request, int actorUserId) {
        ExpenseTarget target = ExpenseTarget.fromCategoryKey(categoryKey);
        if (!CREATABLE.contains(target)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can't create a holder directly in " + categoryKey);
        }

        // "we also need to connect major expense to a user not just who
        // created it ... knowing who got to spend it is major ... do this
        // for all expenses [except] farmer [payments, which has its own
        // Farmer_ID/Trees_ID] and the permits" - a separate *_For_User
        // column per table (Salary_For_User, Transport_For_User, ...),
        // distinct from the existing User_ID (who recorded the entry).
        switch (target) {
            case TRANSPORT -> jdbcTemplate.update(
                "INSERT INTO TRANSPORT (Amount, Type, Description, Created_At, User_ID, Transport_For_User, Vehicle_Unique_ID, Additional_ID, Trip_ID) " +
                "VALUES (?, ?, ?, NOW(), ?, ?, ?, ?, ?)",
                request.amount(), request.type(), request.description(), actorUserId, request.forUserId(),
                request.vehicleUniqueId(), request.additionalId(), tripId
            );
            case COMMUNICATIONS -> jdbcTemplate.update(
                "INSERT INTO COMMUNICATIONS (Amount, Description, `type`, Created_At, User_ID, Communications_For_User, Trip_ID) " +
                "VALUES (?, ?, ?, NOW(), ?, ?, ?)",
                request.amount(), request.description(), request.type(), actorUserId, request.forUserId(), tripId
            );
            case MACHINE_EXPENSES -> jdbcTemplate.update(
                "INSERT INTO MACHINE_EXPENSES (Type, Amount, Date_Of_Expense, Machine_ID, Trip_ID, User_ID, Machine_Expenses_For_User) " +
                "VALUES (?, ?, NOW(), ?, ?, ?, ?)",
                request.type(), request.amount(), request.machineId(), tripId, actorUserId, request.forUserId()
            );
            case ACCOMODATION -> jdbcTemplate.update(
                "INSERT INTO ACCOMODATION (Amount, Description, Type, Created_At, User_ID, Accomodation_For_User, Trip_ID) " +
                "VALUES (?, ?, ?, NOW(), ?, ?, ?)",
                request.amount(), request.description(), request.type(), actorUserId, request.forUserId(), tripId
            );
            case OTHER_EXPENSES -> jdbcTemplate.update(
                "INSERT INTO OTHER_EXPENSES (Amount, Description, Created_At, Trip_ID, User_ID, Other_Expenses_For_User) " +
                "VALUES (?, ?, NOW(), ?, ?, ?)",
                request.amount(), request.description(), tripId, actorUserId, request.forUserId()
            );
            case PERMITS -> jdbcTemplate.update(
                "INSERT INTO PERMITS (Serial_NO, Amount, Start_Date, End_Date, Created_At, Trip_ID, User_ID) " +
                "VALUES (?, ?, ?, ?, NOW(), ?, ?)",
                request.serialNo(), request.amount(), request.startDate(), request.endDate(), tripId, actorUserId
            );
            case SALARIES -> jdbcTemplate.update(
                "INSERT INTO SALARIES (Amount, Salary_Type, Type, Description, Created_At, Trip_ID, User_ID, Salary_For_User) " +
                "VALUES (?, ?, ?, ?, NOW(), ?, ?, ?)",
                request.amount(), request.type(), request.type(), request.description(), tripId, actorUserId, request.forUserId()
            );
            case FARMER_PAYMENTS -> {
                if (request.farmerId() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "farmerId is required for a farmer payment");
                }
                // "trees are connected to farmer, farmer to farmer
                // payment, and any tree created in this a certain trip
                // will be linked to the farmer then to the payment" - a
                // treesId belonging to a different farmer would silently
                // mislabel who a payment was really for.
                if (request.treesId() != null) {
                    Integer treeFarmerId = jdbcTemplate.queryForObject(
                        "SELECT Farmer_ID FROM TREES WHERE Trees_ID = ? AND Is_Deleted = FALSE", Integer.class, request.treesId()
                    );
                    if (treeFarmerId == null) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tree registration not found");
                    }
                    if (!treeFarmerId.equals(request.farmerId())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That tree registration doesn't belong to this farmer");
                    }
                }
                // No Trip_ID column on FARMER_PAYMENTS (see lineItems()'s
                // TREES join) - the trip context isn't persisted here, same
                // as the dedicated FarmerPaymentService.create() flow.
                jdbcTemplate.update(
                    "INSERT INTO FARMER_PAYMENTS (Amount, Method, Recorded_At, Description, refunded, Recorder_By, Farmer_ID, Trees_ID) " +
                    "VALUES (?, ?, NOW(), ?, FALSE, ?, ?, ?)",
                    request.amount(), request.method(), request.description(), actorUserId, request.farmerId(), request.treesId()
                );
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported category: " + categoryKey);
        }

        int id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
            target.tableName(), id, "Created " + categoryKey + " holder, amount=" + request.amount(), actorUserId
        );

        // "if its hard it will create a new financial transaction but this
        // have a reference number that is a company based e.g
        // Dateroemxxxxx" - left unassigned (Category/Target_ID null) same
        // as any other FINANCIAL_TRANSACTION, so it surfaces in the
        // existing admin assign queue rather than silently auto-closing
        // the loop - "the system admin sees 20 ksh ... and will assign
        // that also" describes an explicit human confirmation step, same
        // as a real M-Pesa-sourced one.
        if (HAS_PAYMENT_MODE.contains(target) && "HARD".equalsIgnoreCase(request.type())) {
            createHardCashTransaction(target, tripId, request.amount());
        }

        return JsonKeys.camelCaseKeys(jdbcTemplate.queryForMap(
            "SELECT * FROM " + target.tableName() + " WHERE " + target.idColumn() + " = ?", id
        ));
    }

    private void createHardCashTransaction(ExpenseTarget target, int tripId, BigDecimal amount) {
        String referenceId = generateHardCashReference(target, tripId);
        jdbcTemplate.update(
            "INSERT INTO FINANCIAL_TRANSACTION (Payment_Mode, Reference_ID, Amount, Created_At) VALUES ('HARD', ?, ?, NOW())",
            referenceId, amount
        );
    }

    // "Dateroemxxxxx the xxxxx is a serial number ... the tripnumber+
    // expensecategory shot form com for communication per for permits.
    // and another number that is now generated automatically" -
    // Dateroem-{tripId}{categoryCode}{sequence}, e.g. Dateroem-12COM003.
    // Sequence is a simple per-trip-per-category running count; a rare
    // duplicate under true simultaneous creation is a display-only quirk
    // (Reference_ID carries no uniqueness constraint anywhere it's read),
    // not worth a locking scheme for how infrequently that'd happen.
    private String generateHardCashReference(ExpenseTarget target, int tripId) {
        String categoryCode = CATEGORY_CODES.getOrDefault(target, "GEN");
        String prefix = "Dateroem-" + tripId + categoryCode;
        Integer existing = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM FINANCIAL_TRANSACTION WHERE Reference_ID LIKE ?",
            Integer.class, prefix + "%"
        );
        int sequence = (existing == null ? 0 : existing) + 1;
        return prefix + String.format("%03d", sequence);
    }

    // A normal user can only delete their own not-yet-assigned holder -
    // business rules: "can delete it if it was wrong and the system also
    // checks this not to assign to an already deleted ... expense" (i.e.
    // TransactionService.candidates() already filters Is_Deleted = FALSE,
    // this is the other half: you can't delete one that's already been
    // matched to a real payment).
    @Transactional
    public void delete(String categoryKey, int rowId, int actorUserId, boolean isAdmin) {
        ExpenseTarget target = ExpenseTarget.fromCategoryKey(categoryKey);
        if (!CREATABLE.contains(target)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can't delete a holder directly in " + categoryKey);
        }

        Map<String, Object> row;
        try {
            row = jdbcTemplate.queryForMap(
                "SELECT " + target.ownerColumn() + " AS ownerId, Financial_Transaction_ID FROM " + target.tableName() +
                " WHERE " + target.idColumn() + " = ? AND Is_Deleted = FALSE",
                rowId
            );
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }

        if (row.get("Financial_Transaction_ID") != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already assigned to a transaction - can't delete");
        }
        if (!isAdmin && !Integer.valueOf(actorUserId).equals(row.get("ownerId"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your entry to delete");
        }

        jdbcTemplate.update(
            "UPDATE " + target.tableName() + " SET Is_Deleted = TRUE, Deleted_By = ?, Deleted_At = NOW() " +
            "WHERE " + target.idColumn() + " = ?",
            actorUserId, rowId
        );
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
            target.tableName(), rowId, "Deleted holder", actorUserId
        );
    }

    private BigDecimal scalar(String functionName, int tripId) {
        return jdbcTemplate.queryForObject("SELECT " + functionName + "(?)", BigDecimal.class, tripId);
    }
}
