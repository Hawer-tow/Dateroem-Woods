package com.dateroem.backend.dto;

import java.time.LocalDate;

// One notice as the desktop app's form sends it (see NoticeService).
// showUntil: the last day it shows on the website; published: false keeps
// it as a draft.
public record NoticeRequest(String title, String message, LocalDate showUntil, Boolean published) {}
