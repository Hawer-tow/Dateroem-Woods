package com.dateroem.backend.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.dto.ShippingAreaDto;

@Service
public class ShippingService {

    private static final String SELECT_COLUMNS = "Delivery_Area_ID, City, Area_Name, Description, Coordinates, Is_Active";

    private final JdbcTemplate jdbcTemplate;

    public ShippingService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private ShippingAreaDto mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ShippingAreaDto(
            rs.getInt("Delivery_Area_ID"), rs.getString("City"), rs.getString("Area_Name"),
            rs.getString("Description"), rs.getString("Coordinates"), rs.getBoolean("Is_Active")
        );
    }

    // Customer-facing checkout list - active, undeleted areas only.
    public List<ShippingAreaDto> activeAreas() {
        return jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM DELIVERY_AREAS WHERE Is_Active = TRUE AND Is_Deleted = FALSE " +
            "ORDER BY City, Area_Name",
            this::mapRow
        );
    }

    // Staff management list (mobile app) - every undeleted area, including
    // ones currently switched off, so they can be found again and re-enabled.
    public List<ShippingAreaDto> allAreas() {
        return jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM DELIVERY_AREAS WHERE Is_Deleted = FALSE ORDER BY City, Area_Name",
            this::mapRow
        );
    }

    public ShippingAreaDto get(int areaId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT " + SELECT_COLUMNS + " FROM DELIVERY_AREAS WHERE Delivery_Area_ID = ? AND Is_Deleted = FALSE",
                this::mapRow, areaId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery area not found");
        }
    }

    public int createArea(ShippingAreaDto area) {
        validate(area);
        jdbcTemplate.update(
            "INSERT INTO DELIVERY_AREAS (City, Area_Name, Description, Coordinates, Is_Active) VALUES (?, ?, ?, ?, ?)",
            area.city().trim(), area.areaName().trim(), blankToNull(area.description()), blankToNull(area.coordinates()),
            area.isActive() == null || area.isActive()
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
    }

    // Full-object update - lets staff fix a typo, change which city an area
    // belongs to, or flip it active/inactive without deleting/recreating it.
    // The map pin and the active switch are kept when a request doesn't
    // carry them: the phone's area form has no pin, and every edit from it
    // emptied the pin (24 October 2026). An empty pin clears it on purpose.
    public void updateArea(int areaId, ShippingAreaDto area) {
        validate(area);
        int updated = jdbcTemplate.update(
            "UPDATE DELIVERY_AREAS SET City = ?, Area_Name = ?, Description = ?, " +
            "Coordinates = IF(? IS NULL, Coordinates, NULLIF(TRIM(?), '')), Is_Active = COALESCE(?, Is_Active) " +
            "WHERE Delivery_Area_ID = ? AND Is_Deleted = FALSE",
            area.city().trim(), area.areaName().trim(), blankToNull(area.description()),
            area.coordinates(), area.coordinates(), area.isActive(), areaId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery area not found");
        }
    }

    private void validate(ShippingAreaDto area) {
        if (area.city() == null || area.city().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "City is required");
        }
        if (area.areaName() == null || area.areaName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Area name is required");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
