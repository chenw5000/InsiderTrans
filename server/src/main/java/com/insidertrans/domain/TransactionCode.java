package com.insidertrans.domain;

public enum TransactionCode {
    P("Open-market purchase"),
    S("Open-market sale"),
    A("Grant or award"),
    M("Option exercise"),
    F("Tax withholding"),
    G("Gift"),
    D("Sale to the issuer");

    private final String label;

    TransactionCode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Usual Form 4 direction for this code. Gifts can go either way, so they return null.
     */
    public AcquisitionDisposition defaultDisposition() {
        return switch (this) {
            case P, A, M -> AcquisitionDisposition.A;
            case S, F, D -> AcquisitionDisposition.D;
            case G -> null;
        };
    }
}
