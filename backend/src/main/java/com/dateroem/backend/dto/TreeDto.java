package com.dateroem.backend.dto;

import java.time.LocalDateTime;

// A tree registration for one trip - "an area in products to select from
// trees already registered for a certain trip" (PRODUCTS.Trees_ID) reads
// these, scoped by tripId. remainderTreesId is optional: set it to carry
// forward unused trees left over from an earlier trip instead of this
// being a brand-new registration.
public record TreeDto(
    Integer treesId, String houseHold, String coordinates, Integer noTrees, String typeTrees, Boolean active,
    Integer remainderTreesId, Integer farmerId, Integer tripId,
    // "delete trees should also be created and add a bin just like the
    // product section does it" - same soft-delete/restore scheme as
    // products, surfaced the same way.
    Boolean isDeleted, Integer deletedBy, LocalDateTime deletedAt
) {}
