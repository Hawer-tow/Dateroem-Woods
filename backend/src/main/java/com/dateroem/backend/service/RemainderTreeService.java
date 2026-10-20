package com.dateroem.backend.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.dto.RemainderTreeDto;

@Service
public class RemainderTreeService {

    private static final String SELECT_COLUMNS =
        "r.Remainder_Trees_ID, r.Previous_Trip_ID, r.Origin_Trees_ID, r.NO_Trees, r.Type_Trees, r.Total_Body_Feet, " +
        "(SELECT t.Trees_ID FROM TREES t WHERE t.Remainder_Trees_ID = r.Remainder_Trees_ID AND t.Is_Deleted = FALSE " +
        "LIMIT 1) AS Claimed_By_Trees_Id";
    private static final String FROM = "FROM REMAINDER_TREES r";

    private final JdbcTemplate jdbcTemplate;

    public RemainderTreeService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private RemainderTreeDto mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new RemainderTreeDto(
            rs.getInt("Remainder_Trees_ID"), (Integer) rs.getObject("Previous_Trip_ID"),
            (Integer) rs.getObject("Origin_Trees_ID"), (Integer) rs.getObject("NO_Trees"),
            rs.getString("Type_Trees"), rs.getBigDecimal("Total_Body_Feet"),
            (Integer) rs.getObject("Claimed_By_Trees_Id")
        );
    }

    // Every undeleted record, claimed or not - "if a remainder tree is
    // claimed then dont let it be claimed by another tree creation" is
    // enforced by callers excluding anything with claimedByTreesId set
    // (except the tree currently being edited, which already owns it) -
    // this list still shows everything so a picker can tell the two apart.
    // previousTripId scopes it to "the remainders recorded so far while
    // finalizing *this* trip" - "finalizing trees can be continuous" until
    // the trip itself is finalized, so this list is what's already been
    // recorded plus whatever gets added next.
    public List<RemainderTreeDto> list(Integer previousTripId) {
        if (previousTripId != null) {
            return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " " + FROM + " WHERE r.Previous_Trip_ID = ? AND r.Is_Deleted = FALSE " +
                "ORDER BY r.Remainder_Trees_ID DESC",
                this::mapRow, previousTripId
            );
        }
        return jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " " + FROM + " WHERE r.Is_Deleted = FALSE ORDER BY r.Remainder_Trees_ID DESC",
            this::mapRow
        );
    }

    public RemainderTreeDto get(int remainderTreesId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT " + SELECT_COLUMNS + " " + FROM + " WHERE r.Remainder_Trees_ID = ? AND r.Is_Deleted = FALSE",
                this::mapRow, remainderTreesId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Remainder trees record not found");
        }
    }

    public int create(RemainderTreeDto remainder) {
        jdbcTemplate.update(
            "INSERT INTO REMAINDER_TREES (Previous_Trip_ID, Origin_Trees_ID, NO_Trees, Type_Trees, Total_Body_Feet) " +
            "VALUES (?, ?, ?, ?, ?)",
            remainder.previousTripId(), remainder.originTreesId(), remainder.noTrees(), remainder.typeTrees(),
            remainder.totalBodyFeet()
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
    }

    public void update(int remainderTreesId, RemainderTreeDto remainder) {
        int updated = jdbcTemplate.update(
            "UPDATE REMAINDER_TREES SET Previous_Trip_ID = ?, Origin_Trees_ID = ?, NO_Trees = ?, Type_Trees = ?, " +
            "Total_Body_Feet = ? WHERE Remainder_Trees_ID = ? AND Is_Deleted = FALSE",
            remainder.previousTripId(), remainder.originTreesId(), remainder.noTrees(), remainder.typeTrees(),
            remainder.totalBodyFeet(), remainderTreesId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Remainder trees record not found");
        }
    }
}
