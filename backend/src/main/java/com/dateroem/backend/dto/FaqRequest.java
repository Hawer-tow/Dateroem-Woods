package com.dateroem.backend.dto;

// One frequently asked question as the desktop app's form sends it (see
// FaqService). linkPath: an optional page on the website the question is
// about, e.g. "/shipping"; sortOrder: smaller numbers show first;
// published: false hides it from the website without deleting it.
public record FaqRequest(String topic, String question, String answer, String linkPath, Integer sortOrder, Boolean published) {}
