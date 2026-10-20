package com.dateroem.backend.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.dto.MachineDto;

// "in machine expense i have the option machine id optional i want to
// eliminate that ... machine should be selected from the machine table" -
// this is the registry the mobile app's picker reads (list-only there);
// "adding of a new machine or vehicle is only allowed in the desktop" -
// create() exists for the desktop admin app, the mobile app's ApiService
// never calls it.
@Service
public class MachineService {

    private static final String SELECT_COLUMNS = "Machine_ID, Name, Serial_NO, Description";

    private final JdbcTemplate jdbcTemplate;

    public MachineService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private MachineDto mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new MachineDto(
            rs.getInt("Machine_ID"), rs.getString("Name"), rs.getString("Serial_NO"), rs.getString("Description")
        );
    }

    public List<MachineDto> list() {
        return jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM MACHINE WHERE Is_Deleted = FALSE ORDER BY Name", this::mapRow
        );
    }

    public MachineDto get(int machineId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT " + SELECT_COLUMNS + " FROM MACHINE WHERE Machine_ID = ? AND Is_Deleted = FALSE",
                this::mapRow, machineId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Machine not found");
        }
    }

    public MachineDto update(int machineId, MachineDto machine) {
        get(machineId);
        if (machine.name() == null || machine.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }
        jdbcTemplate.update(
            "UPDATE MACHINE SET Name = ?, Serial_NO = ?, Description = ? WHERE Machine_ID = ? AND Is_Deleted = FALSE",
            machine.name().trim(),
            machine.serialNo() == null || machine.serialNo().isBlank() ? null : machine.serialNo().trim(),
            machine.description() == null || machine.description().isBlank() ? null : machine.description().trim(),
            machineId
        );
        return get(machineId);
    }

    public int create(MachineDto machine) {
        if (machine.name() == null || machine.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }
        jdbcTemplate.update(
            "INSERT INTO MACHINE (Name, Serial_NO, Description) VALUES (?, ?, ?)",
            machine.name().trim(), blankToNull(machine.serialNo()), blankToNull(machine.description())
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
