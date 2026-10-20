package com.dateroem.backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CampaignDto(
    Integer campaignId,
    String name,
    String channel,
    LocalDate startAt,
    LocalDate endAt,
    String status,
    // Relative media key, resolved to a full URL server-side - same
    // scheme as PRODUCTS.Image. Either a video or an image, never both:
    // mediaType ("VIDEO"/"IMAGE") says which, so the client knows how to
    // render whatever is here without guessing from the file extension.
    String media,
    String mediaType,
    // "make sure the deleted by and at are active" - see ProductDto.
    Boolean isDeleted,
    Integer deletedBy,
    LocalDateTime deletedAt
) {}
