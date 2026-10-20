package com.dateroem.backend.dto;

// coordinates - "lat,long" text, same convention as TREES.Coordinates -
// lets the website compute "delivery areas near you" and link out to
// Google Maps for directions.
public record ShippingAreaDto(
    Integer deliveryAreaId,
    String city,
    String areaName,
    String description,
    String coordinates,
    Boolean isActive
) {}
