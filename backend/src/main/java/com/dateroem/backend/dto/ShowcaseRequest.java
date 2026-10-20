package com.dateroem.backend.dto;

// One Bulk Orders showcase picture: the media key the upload returned
// ("showcase/....jpg") and an optional short caption. On a caption edit only
// caption is read.
public record ShowcaseRequest(String image, String caption) {}
