package com.smartbus.schedule.dto;

import java.util.List;

public record ApiErrorResponse(
    int status,
    String code,
    String message,
    List<String> details,
    String path
) {
}
