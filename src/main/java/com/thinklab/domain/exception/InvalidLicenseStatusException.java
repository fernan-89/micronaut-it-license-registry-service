package com.thinklab.domain.exception;

/** Domain Exception: an illegal License lifecycle transition, or the license changed while the write was applied. RFC 7807 mapping: HTTP 409 Conflict. */
public class InvalidLicenseStatusException extends BusinessException {

    private static final String ERROR_CODE = "ERR-LIC-00409";

    public InvalidLicenseStatusException(String message) {
        super(ERROR_CODE, message);
    }
}
