package com.thinklab.domain.exception;

/** Domain Exception: no licence with that id in the tenant (another tenant's answers the same). RFC 7807 mapping: HTTP 404 Not Found. */
public class LicenseNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "ERR-LIC-00404";

    public LicenseNotFoundException(String message) {
        super(ERROR_CODE, message);
    }
}
