package com.dateroem.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductDto(
    Integer productId,
    String name,
    String sku,
    String type,
    String description,
    // Read-only: the tidied, professionally arranged copy of a long
    // description that the website shows customers (DescriptionFormatter);
    // null while there isn't one. Ignored when sent back in a save.
    String descriptionDisplay,
    BigDecimal price,
    BigDecimal stock,
    // "pieces" (discrete count) or "board_feet" (volume) - which reading
    // of `stock` applies, since some timber is sold by count and some by
    // measured volume rather than a second quantity column.
    String unit,
    String attributes,
    // The size of ONE physical piece of this product (a single plank, a
    // single pole, ...) - independent of stock/order quantity. Drives the
    // delivery SLA: above 500 is "large" (3-month window, needs a vehicle
    // scheduled), otherwise "small" (3 weeks). Null until someone
    // measures it - see OrderService's classification.
    BigDecimal bodyFeetPerUnit,
    // Short admin/marketer-filled badge (e.g. "Most Ordered - Only 2
    // left") shown directly on the product card - separate from the
    // Campaigns/Marketing feature, which is trip-promotion, not per-product.
    String marketerNote,
    String image,
    Integer treesId,
    // "make sure the deleted by and at are active" - surfaced so the
    // admin desktop's "show deleted" view can display who/when, and so a
    // deleted row can be told apart from a live one (DataTable's
    // isDeleted-based red highlight) without a second request.
    Boolean isDeleted,
    Integer deletedBy,
    LocalDateTime deletedAt,
    // Retiring (migration 033) - separate from deleting. A retired product
    // is hidden from the customer shop and can't be ordered, but keeps its
    // stock and existing orders and can be brought back. Read-only here:
    // set through the retire/unretire endpoints, ignored on a save.
    Boolean isRetired,
    LocalDateTime retiredAt,
    Integer retiredBy
) {}
