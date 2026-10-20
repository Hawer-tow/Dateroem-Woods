package com.dateroem.backend.dto;

import java.math.BigDecimal;

// A trip that ends with unused registered trees leaves one of these behind
// (Previous_Trip_ID says which trip) - later, registering a TREES item for
// a *different* trip can point its Remainder_Trees_ID at this record
// instead of being a fresh registration, carrying the leftover forward.
// originTreesId is which specific TREES row it actually came from -
// "linking a specific tree ... is better since one can use this to detect
// even the exact location of the remainder trees by using the coordinates
// of the originator tree", since the physical storage location and the
// origin's own coordinates can differ once several households' leftovers
// get consolidated. claimedByTreesId is which TREES row (if any) has
// *already* carried this forward - "if a remainder tree is claimed then
// dont let it be claimed by another tree creation": a UNIQUE constraint on
// TREES.Remainder_Trees_ID enforces this at the database level, this
// field is what lets a picker exclude it up front instead of only finding
// out from a failed save.
public record RemainderTreeDto(
    Integer remainderTreesId, Integer previousTripId, Integer originTreesId, Integer noTrees, String typeTrees,
    BigDecimal totalBodyFeet, Integer claimedByTreesId
) {}
