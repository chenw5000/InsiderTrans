package com.insidertrans.service.processor;

import java.time.LocalDate;

/**
 * One Form 4 (or 4/A) entry from an EDGAR daily index file.
 */
public record Form4Filing(
        String cik,
        String companyName,
        String formType,
        LocalDate filedDate,
        String fileName,
        String documentUrl) {

    /**
     * Accession from the master-index file name, e.g. {@code 0001045810-24-000294}.
     */
    public String accessionNumber() {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String name = fileName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        String file = slash >= 0 ? name.substring(slash + 1) : name;
        if (file.toLowerCase(java.util.Locale.ROOT).endsWith(".txt")) {
            file = file.substring(0, file.length() - 4);
        }
        return file.isBlank() ? null : file;
    }
}
