package com.dateroem.backend.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.dto.TreeDto;

// "an area in products to select from trees already registered for a
// certain trip" - PRODUCTS.Trees_ID points here, scoped per trip.
@Service
public class TreeService {

    private static final String SELECT_COLUMNS =
        "Trees_ID, House_Hold, Coordinates, No_Trees, Type_Trees, Active, Remainder_Trees_ID, Farmer_ID, Trip_ID, " +
        "Is_Deleted, Deleted_By, Deleted_At";

    private final JdbcTemplate jdbcTemplate;

    public TreeService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private TreeDto mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TreeDto(
            rs.getInt("Trees_ID"), rs.getString("House_Hold"), rs.getString("Coordinates"),
            (Integer) rs.getObject("No_Trees"), rs.getString("Type_Trees"), (Boolean) rs.getObject("Active"),
            (Integer) rs.getObject("Remainder_Trees_ID"), (Integer) rs.getObject("Farmer_ID"),
            (Integer) rs.getObject("Trip_ID"), (Boolean) rs.getObject("Is_Deleted"),
            (Integer) rs.getObject("Deleted_By"),
            rs.getTimestamp("Deleted_At") == null ? null : rs.getTimestamp("Deleted_At").toLocalDateTime()
        );
    }

    // "desktop...to see the trees all associated based on a specific
    // trip"; tripId null means every undeleted tree (used by the Products
    // form's picker, which is trip-scoped by the caller passing tripId).
    public List<TreeDto> list(Integer tripId) {
        if (tripId != null) {
            return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM TREES WHERE Trip_ID = ? AND Is_Deleted = FALSE ORDER BY Trees_ID DESC",
                this::mapRow, tripId
            );
        }
        return jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM TREES WHERE Is_Deleted = FALSE ORDER BY Trees_ID DESC", this::mapRow
        );
    }

    public TreeDto get(int treesId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT " + SELECT_COLUMNS + " FROM TREES WHERE Trees_ID = ? AND Is_Deleted = FALSE",
                this::mapRow, treesId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tree registration not found");
        }
    }

    public List<TreeDto> listDeleted() {
        return jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM TREES WHERE Is_Deleted = TRUE ORDER BY Trees_ID DESC", this::mapRow
        );
    }

    // Same soft-delete/restore scheme as products - marks the row so it
    // stops showing up, keeps it (and who/when) for the restore flow.
    public void delete(int treesId, int actorUserId) {
        int updated = jdbcTemplate.update(
            "UPDATE TREES SET Is_Deleted = TRUE, Deleted_By = ?, Deleted_At = NOW() " +
            "WHERE Trees_ID = ? AND Is_Deleted = FALSE",
            actorUserId, treesId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tree registration not found or already deleted");
        }
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) " +
            "VALUES (?, ?, ?, CURDATE(), ?)",
            "TREES", treesId, "Deleted tree registration", actorUserId
        );
    }

    public void restore(int treesId, int actorUserId) {
        int updated = jdbcTemplate.update(
            "UPDATE TREES SET Is_Deleted = FALSE, Deleted_By = NULL, Deleted_At = NULL " +
            "WHERE Trees_ID = ? AND Is_Deleted = TRUE",
            treesId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tree registration not found or not deleted");
        }
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) " +
            "VALUES (?, ?, ?, CURDATE(), ?)",
            "TREES", treesId, "Restored tree registration", actorUserId
        );
    }

    public int create(TreeDto tree) {
        validate(tree);
        try {
            jdbcTemplate.update(
                "INSERT INTO TREES (House_Hold, Coordinates, No_Trees, Type_Trees, Active, Remainder_Trees_ID, Farmer_ID, Trip_ID) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                tree.houseHold(), tree.coordinates(), tree.noTrees(), tree.typeTrees(),
                tree.active() == null || tree.active(), tree.remainderTreesId(), tree.farmerId(), tree.tripId()
            );
        } catch (DataIntegrityViolationException e) {
            throw remainderConflict(e);
        }
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
    }

    // Full-object update - including reassigning remainderTreesId, which
    // is how "pick other remainder tree to a tree item in another trip"
    // actually happens: point this TREES row's Remainder_Trees_ID at a
    // different REMAINDER_TREES record (or null to detach it).
    public void update(int treesId, TreeDto tree) {
        validate(tree);
        int updated;
        try {
            updated = jdbcTemplate.update(
                "UPDATE TREES SET House_Hold = ?, Coordinates = ?, No_Trees = ?, Type_Trees = ?, Active = ?, " +
                "Remainder_Trees_ID = ?, Farmer_ID = ?, Trip_ID = ? WHERE Trees_ID = ? AND Is_Deleted = FALSE",
                tree.houseHold(), tree.coordinates(), tree.noTrees(), tree.typeTrees(),
                tree.active() == null || tree.active(), tree.remainderTreesId(), tree.farmerId(), tree.tripId(), treesId
            );
        } catch (DataIntegrityViolationException e) {
            throw remainderConflict(e);
        }
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tree registration not found");
        }
    }

    // "if a remainder tree is claimed then dont let it be claimed by
    // another tree creation" - the UNIQUE constraint on
    // TREES.Remainder_Trees_ID is the hard guarantee; pickers are
    // expected to exclude already-claimed remainders (RemainderTreeDto's
    // claimedByTreesId) so this should only ever fire on a genuine race
    // between two people picking the same one at once. Any other
    // integrity violation (e.g. an invalid Farmer_ID/Trip_ID) gets its
    // real database message instead of this specific one.
    private ResponseStatusException remainderConflict(DataIntegrityViolationException e) {
        String cause = e.getMostSpecificCause().getMessage();
        if (cause != null && cause.contains("uq_trees_remainder_trees_id")) {
            return new ResponseStatusException(
                HttpStatus.CONFLICT, "That remainder has just been claimed by another tree registration", e
            );
        }
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, cause, e);
    }

    private void validate(TreeDto tree) {
        if (tree.tripId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A tree registration must belong to a trip");
        }
    }
}
