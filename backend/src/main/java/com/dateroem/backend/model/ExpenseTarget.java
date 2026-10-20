package com.dateroem.backend.model;

/**
 * The expense/finance categories a FINANCIAL_TRANSACTION row can be
 * manually assigned to - matches the categories in
 * fn_trip_total_transaction_cost (dateroem_functions_procedures.sql), plus
 * BULK_DELIVERY (a heavy order's soft-cash portion, paid via STK push -
 * "if the payment is to be done through a push ... the money is filled in
 * the bulk delivery table"). PRODUCT_PAYMENTS is still excluded - it gets
 * its Financial_Transaction_ID set at payment time (website checkout STK),
 * not through this manual admin-assignment queue. CAPITAL is excluded
 * entirely - business owner correction: it's money an admin fills in
 * directly before a trip begins, not something matched against an
 * incoming payment (see migration 007, reverting 006's column).
 *
 * amountColumn/createdAtColumn back the candidate-matching search in
 * TransactionService.candidates(): given an unassigned FINANCIAL_TRANSACTION
 * (amount + when it was recorded), every autoMatchable target table is
 * searched for still-unassigned rows with the same amount, ranked by how
 * close their own createdAtColumn is - see migration 005 for the tables
 * that didn't have a usable creation timestamp until now.
 *
 * ownerColumn backs ExpenseService.delete()'s "only your own not-yet-
 * assigned holder" check - defaults to User_ID, but FARMER_PAYMENTS uses
 * Recorder_By instead.
 *
 * autoMatchable=false (LOAN) means the row is still assignable via
 * assign(), just never suggested by candidates() - Loans & Finance is
 * admin-only and filled directly, not through the generic self-service
 * "create a holder, admin matches it later" queue.
 */
public enum ExpenseTarget {
    TRANSPORT("TRANSPORT", "Transport_ID", "Reference_ID", "Trip_ID", null, null, "transport"),
    COMMUNICATIONS("COMMUNICATIONS", "Communications_ID", "Reference_ID", "Trip_ID", null, null, "communications"),
    MACHINE_EXPENSES("MACHINE_EXPENSES", "Machine_Expenses_ID", "Reference_ID", "Trip_ID", null, null, "machine-expenses", "Amount", "Date_Of_Expense"),
    OTHER_EXPENSES("OTHER_EXPENSES", "Other_Expenses_ID", "Reference_ID", "Trip_ID", null, null, "other-expenses"),
    PERMITS("PERMITS", "Permit_ID", "Reference_ID", "Trip_ID", null, null, "permits"),
    ACCOMODATION("ACCOMODATION", "Accomodation_ID", "Reference_ID", "Trip_ID", null, null, "accomodation"),
    LOAN("LOAN", "Loan_ID", "Reference_ID", "Trip_ID", null, null, "loan", "Amount", "Created_At", "User_ID", false),
    SALARIES("SALARIES", "Salary_ID", "unique_Salary_Id", "Trip_ID", null, null, "salaries"),
    LOAN_REPAYMENTS("LOAN_REPAYMENTS", "Loan_Repayment_ID", "Reference_ID", null, "LOAN", "Loan_ID", "loan-repayments", "Amount_Paid", "Payment_Date"),
    FARMER_PAYMENTS("FARMER_PAYMENTS", "Farmer_Payments_ID", "Reference_ID", null, null, null, "farmer-payments", "Amount", "Recorded_At", "Recorder_By", true),
    BULK_DELIVERY("BULK_DELIVERY", "Bulk_Delivery_ID", "Reference_ID", "Trip_ID", null, null, "bulk-delivery", "Soft_Cash_Amount", "Created_At"),
    // "when one drops a product ... expecting a refund of 15, still cannot
    // see it" - dropOrder() (OrderService) already computes and stores the
    // net refund on ORDERS.Refunded_Amount; this lets Send Money pay that
    // figure out to the customer the same way every other category is
    // paid. autoMatchable=false like LOAN - a refund is money going OUT to
    // a customer, never a candidate match for an unrelated INCOMING
    // payment (candidates() is for incoming money only).
    ORDER_REFUND("ORDERS", "Order_ID", "Reference_ID", null, null, null, "order-refund", "Refunded_Amount", "Cancelled_At", "Cancelled_By", false);

    private final String tableName;
    private final String idColumn;
    private final String matchColumn;
    private final String tripIdColumn;
    private final String tripLookupTable;
    private final String tripLookupJoinColumn;
    private final String categoryKey;
    private final String amountColumn;
    private final String createdAtColumn;
    private final String ownerColumn;
    private final boolean autoMatchable;

    ExpenseTarget(String tableName, String idColumn, String matchColumn, String tripIdColumn,
                  String tripLookupTable, String tripLookupJoinColumn, String categoryKey) {
        this(tableName, idColumn, matchColumn, tripIdColumn, tripLookupTable, tripLookupJoinColumn,
            categoryKey, "Amount", "Created_At");
    }

    ExpenseTarget(String tableName, String idColumn, String matchColumn, String tripIdColumn,
                  String tripLookupTable, String tripLookupJoinColumn, String categoryKey,
                  String amountColumn, String createdAtColumn) {
        this(tableName, idColumn, matchColumn, tripIdColumn, tripLookupTable, tripLookupJoinColumn,
            categoryKey, amountColumn, createdAtColumn, "User_ID", true);
    }

    ExpenseTarget(String tableName, String idColumn, String matchColumn, String tripIdColumn,
                  String tripLookupTable, String tripLookupJoinColumn, String categoryKey,
                  String amountColumn, String createdAtColumn, String ownerColumn, boolean autoMatchable) {
        this.tableName = tableName;
        this.idColumn = idColumn;
        this.matchColumn = matchColumn;
        this.tripIdColumn = tripIdColumn;
        this.tripLookupTable = tripLookupTable;
        this.tripLookupJoinColumn = tripLookupJoinColumn;
        this.categoryKey = categoryKey;
        this.amountColumn = amountColumn;
        this.createdAtColumn = createdAtColumn;
        this.ownerColumn = ownerColumn;
        this.autoMatchable = autoMatchable;
    }

    public String tableName() {
        return tableName;
    }

    public String idColumn() {
        return idColumn;
    }

    public String matchColumn() {
        return matchColumn;
    }

    public String tripIdColumn() {
        return tripIdColumn;
    }

    public String tripLookupTable() {
        return tripLookupTable;
    }

    public String tripLookupJoinColumn() {
        return tripLookupJoinColumn;
    }

    public String categoryKey() {
        return categoryKey;
    }

    public String amountColumn() {
        return amountColumn;
    }

    public String createdAtColumn() {
        return createdAtColumn;
    }

    public String ownerColumn() {
        return ownerColumn;
    }

    public boolean autoMatchable() {
        return autoMatchable;
    }

    public static ExpenseTarget fromCategoryKey(String key) {
        for (ExpenseTarget target : values()) {
            if (target.categoryKey.equals(key)) {
                return target;
            }
        }
        throw new IllegalArgumentException("Unknown expense category: " + key);
    }
}
