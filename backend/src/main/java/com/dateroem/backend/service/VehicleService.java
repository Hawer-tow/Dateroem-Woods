package com.dateroem.backend.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.dto.VehicleDto;

// "connect the two so someone can select from the list of machines ... in
// transport expense one can also select the vehicle one used instead of
// using the vehicle id" - VEHICLE.Unique_Identifier is what TRANSPORT.
// Vehicle_Unique_ID actually references, so that's the value the mobile
// picker sends once a vehicle is selected from this list. "adding of a
// new machine or vehicle is only allowed in the desktop" - create() is
// for the desktop admin app only.
@Service
public class VehicleService {

    private static final String SELECT_COLUMNS = "Vehicle_ID, Unique_Identifier, Driver, Phone_Number, Description";

    private final JdbcTemplate jdbcTemplate;

    public VehicleService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private VehicleDto mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new VehicleDto(
            rs.getInt("Vehicle_ID"), rs.getString("Unique_Identifier"),
            rs.getString("Driver"), rs.getString("Phone_Number"), rs.getString("Description")
        );
    }

    public List<VehicleDto> list() {
        return jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM VEHICLE WHERE Is_Deleted = FALSE ORDER BY Unique_Identifier",
            this::mapRow
        );
    }

    public VehicleDto get(int vehicleId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT " + SELECT_COLUMNS + " FROM VEHICLE WHERE Vehicle_ID = ? AND Is_Deleted = FALSE",
                this::mapRow, vehicleId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found");
        }
    }

    public int create(VehicleDto vehicle) {
        if (vehicle.uniqueIdentifier() == null || vehicle.uniqueIdentifier().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unique identifier (e.g. number plate) is required");
        }
        try {
            jdbcTemplate.update(
                "INSERT INTO VEHICLE (Unique_Identifier, Driver, Phone_Number, Description) VALUES (?, ?, ?, ?)",
                vehicle.uniqueIdentifier().trim(), blankToNull(vehicle.driver()),
                blankToNull(vehicle.phoneNumber()), blankToNull(vehicle.description())
            );
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A vehicle with that identifier already exists");
        }
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
    }

    // "in places like vehicle for an already created object provide an edit
    // area" - TRANSPORT rows reference Unique_Identifier, so changing it is
    // refused (with a plain reason) once any expense uses it; the driver,
    // phone and description can always be edited.
    public VehicleDto update(int vehicleId, VehicleDto vehicle) {
        get(vehicleId);
        if (vehicle.uniqueIdentifier() == null || vehicle.uniqueIdentifier().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unique identifier (e.g. number plate) is required");
        }
        try {
            jdbcTemplate.update(
                "UPDATE VEHICLE SET Unique_Identifier = ?, Driver = ?, Phone_Number = ?, Description = ? " +
                "WHERE Vehicle_ID = ? AND Is_Deleted = FALSE",
                vehicle.uniqueIdentifier().trim(), blankToNull(vehicle.driver()),
                blankToNull(vehicle.phoneNumber()), blankToNull(vehicle.description()), vehicleId
            );
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A vehicle with that identifier already exists");
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "This number plate is already used by transport records, so it can't be renamed - the other details can still be edited"
            );
        }
        return get(vehicleId);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
