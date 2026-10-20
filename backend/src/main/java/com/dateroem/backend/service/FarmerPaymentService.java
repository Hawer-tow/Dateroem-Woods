package com.dateroem.backend.service;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.dto.CreateFarmerPaymentRequest;
import com.dateroem.backend.util.JsonKeys;

@Service
public class FarmerPaymentService {

    private final JdbcTemplate jdbcTemplate;

    public FarmerPaymentService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // "sending money to a farmer" (business rules, mobile transactions
    // page) - records the payment itself. It isn't yet tied to a specific
    // FINANCIAL_TRANSACTION row; TransactionService.assign() links the two
    // once the Mpesa/offline reconciliation for it happens.
    @Transactional
    public int create(CreateFarmerPaymentRequest request, int recorderUserId) {
        // "trees are connected to farmer, farmer to farmer payment, and
        // any tree created in this a certain trip will be linked to the
        // farmer then to the payment" - a treesId that belongs to a
        // different farmer would silently mislabel who a payment was
        // really for, so it's checked here rather than trusted as-is.
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
        jdbcTemplate.update(
            "INSERT INTO FARMER_PAYMENTS (Amount, Method, Recorded_At, Description, refunded, Recorder_By, Farmer_ID, Trees_ID) " +
            "VALUES (?, ?, NOW(), ?, FALSE, ?, ?, ?)",
            request.amount(), request.method(), request.description(), recorderUserId, request.farmerId(), request.treesId()
        );
        int id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
            "FARMER_PAYMENTS", id,
            "Recorded payment to Farmer_ID=" + request.farmerId() +
                (request.treesId() != null ? ", Trees_ID=" + request.treesId() : ""),
            recorderUserId
        );
        return id;
    }

    public List<Map<String, Object>> listByFarmer(int farmerId) {
        return JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
            "SELECT * FROM FARMER_PAYMENTS WHERE Farmer_ID = ? AND Is_Deleted = FALSE", farmerId
        ));
    }
}
