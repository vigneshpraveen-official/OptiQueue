package com.optiqueue.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {
    public NotFoundException(String what, Object id) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", "%s %s not found".formatted(what, id));
    }
}
