package com.dateroem.backend.dto;

import java.util.List;

// "Products in this campaign" from the phone and desktop apps: every
// product the campaign promotes, replacing what was linked before (an
// empty list removes them all).
public record CampaignProductsRequest(List<Integer> productIds) {}
