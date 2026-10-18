package com.thinklab.domain.exception;

/** Domain Exception: a REQUESTER tried to use the licence registry (staff only, ADR-032). RFC 7807 mapping: HTTP 403 Forbidden. */
public class LicenseAccessDeniedException extends BusinessException {

    private static final String ERROR_CODE = "ERR-LIC-00403";

    public LicenseAccessDeniedException(String message) {
        super(ERROR_CODE, message);
    }
}
