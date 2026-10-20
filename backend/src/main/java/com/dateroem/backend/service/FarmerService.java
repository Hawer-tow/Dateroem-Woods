package com.dateroem.backend.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.dto.FarmerDto;
import com.dateroem.backend.util.JsonKeys;

// Farmers - who trees are registered against, and who FARMER_PAYMENTS pays.
@Service
public class FarmerService {

    private static final String SELECT_COLUMNS = "Farmer_ID, Name, Phone_NO, Bank_Details_Marked, Balance";

    private final JdbcTemplate jdbcTemplate;

    public FarmerService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private FarmerDto mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new FarmerDto(
            rs.getInt("Farmer_ID"), rs.getString("Name"), rs.getString("Phone_NO"),
            rs.getString("Bank_Details_Marked"), rs.getBigDecimal("Balance")
        );
    }

    public List<FarmerDto> list() {
        return jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM FARMER WHERE Is_Deleted = FALSE ORDER BY Name", this::mapRow
        );
    }

    public FarmerDto get(int farmerId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT " + SELECT_COLUMNS + " FROM FARMER WHERE Farmer_ID = ? AND Is_Deleted = FALSE",
                this::mapRow, farmerId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Farmer not found");
        }
    }

    public int create(FarmerDto farmer) {
        validate(farmer);
        jdbcTemplate.update(
            "INSERT INTO FARMER (Name, Phone_NO, Bank_Details_Marked, Balance) VALUES (?, ?, ?, ?)",
            farmer.name().trim(), blankToNull(farmer.phoneNo()), blankToNull(farmer.bankDetailsMarked()),
            farmer.balance() == null ? java.math.BigDecimal.ZERO : farmer.balance()
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
    }

    public void update(int farmerId, FarmerDto farmer) {
        validate(farmer);
        int updated = jdbcTemplate.update(
            "UPDATE FARMER SET Name = ?, Phone_NO = ?, Bank_Details_Marked = ?, Balance = ? " +
            "WHERE Farmer_ID = ? AND Is_Deleted = FALSE",
            farmer.name().trim(), blankToNull(farmer.phoneNo()), blankToNull(farmer.bankDetailsMarked()),
            farmer.balance() == null ? java.math.BigDecimal.ZERO : farmer.balance(), farmerId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Farmer not found");
        }
    }

    // "check a certain farmer and provide info that this farmer tops on
    // the list as one of the farmers whom you bought most trees from on
    // one instant or in one or more trips" - ranked by total trees
    // supplied across every trip (not scoped to just one), so a farmer
    // who's sold across several trips shows their full standing.
    public List<Map<String, Object>> topSuppliers() {
        return JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
            "SELECT f.Farmer_ID, f.Name, f.Phone_NO, " +
            "COALESCE(SUM(t.No_Trees), 0) AS Total_Trees_Supplied, " +
            "COUNT(DISTINCT t.Trip_ID) AS Trip_Count " +
            "FROM FARMER f " +
            "JOIN TREES t ON t.Farmer_ID = f.Farmer_ID AND t.Is_Deleted = FALSE " +
            "WHERE f.Is_Deleted = FALSE " +
            "GROUP BY f.Farmer_ID, f.Name, f.Phone_NO " +
            "ORDER BY Total_Trees_Supplied DESC"
        ));
    }

    private void validate(FarmerDto farmer) {
        if (farmer.name() == null || farmer.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
