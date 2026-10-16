package com.insidertrans.domain;

public enum AcquisitionDisposition {
    A("Acquired"),
    D("Disposed");

    private final String label;

    AcquisitionDisposition(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
