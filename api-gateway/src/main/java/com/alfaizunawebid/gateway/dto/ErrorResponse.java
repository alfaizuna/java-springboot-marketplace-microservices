package com.alfaizunawebid.gateway.dto;

import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    private OffsetDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
}
