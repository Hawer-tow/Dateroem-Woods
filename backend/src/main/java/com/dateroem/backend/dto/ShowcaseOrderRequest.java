package com.dateroem.backend.dto;

import java.util.List;

// The showcase pictures in their new slideshow order - every current
// picture's id, first to last.
public record ShowcaseOrderRequest(List<Integer> showcaseIds) {}
