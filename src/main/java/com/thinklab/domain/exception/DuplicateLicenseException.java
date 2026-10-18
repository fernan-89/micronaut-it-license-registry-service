package com.thinklab.domain.exception;

/** Domain Exception: a licence for a product already registered in the organisation. RFC 7807 mapping: HTTP 409 Conflict. */
public class DuplicateLicenseException extends BusinessException {

    private static final String ERROR_CODE = "ERR-LIC-00409";

    public DuplicateLicenseException(String message) {
        super(ERROR_CODE, message);
    }
}
