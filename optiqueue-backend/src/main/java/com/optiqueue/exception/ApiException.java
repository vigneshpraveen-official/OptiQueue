package com.optiqueue.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Base class for errors that map directly to an HTTP status + stable error code. */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public ApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
}
