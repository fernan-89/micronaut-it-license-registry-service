package com.thinklab.application.usecase;

import com.thinklab.domain.exception.LicenseAccessDeniedException;

/** The licence registry is for IT staff and for the tools they run (ADR-032): a REQUESTER is refused everywhere. */
final class LicenseAccess {

    static final String REQUESTER_ROLE = "REQUESTER";

    private LicenseAccess() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    static void requireStaff(String role, String operation) {
        if (REQUESTER_ROLE.equals(role)) {
            throw new LicenseAccessDeniedException("A requester cannot " + operation + ": licences are handled by IT staff.");
        }
    }
}
