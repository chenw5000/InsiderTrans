package com.insidertrans.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "insider_transactions")
public class InsiderTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "insider_id", nullable = false)
    private Insider insider;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "filing_date")
    private LocalDate filingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, length = 8)
    private TransactionCode code;

    @Enumerated(EnumType.STRING)
    @Column(name = "acquired_or_disposed", nullable = false, length = 8)
    private AcquisitionDisposition acquiredOrDisposed;

    @Column(name = "shares", nullable = false, precision = 18, scale = 4)
    private BigDecimal shares;

    @Column(name = "price_per_share", precision = 18, scale = 4)
    private BigDecimal pricePerShare;

    @Column(name = "shares_owned_following", precision = 18, scale = 4)
    private BigDecimal sharesOwnedFollowing;

    @Column(name = "security_title", nullable = false, length = 120)
    private String securityTitle;

    @Column(name = "insider_role", length = 120)
    private String insiderRole;

    @Column(name = "accession_number", length = 20)
    private String accessionNumber;

    @Column(name = "document_url", length = 400)
    private String documentUrl;

    @Column(name = "form_type", length = 8)
    private String formType;

    public InsiderTransaction() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public Insider getInsider() {
        return insider;
    }

    public void setInsider(Insider insider) {
        this.insider = insider;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public LocalDate getFilingDate() {
        return filingDate;
    }

    public void setFilingDate(LocalDate filingDate) {
        this.filingDate = filingDate;
    }

    public TransactionCode getCode() {
        return code;
    }

    public void setCode(TransactionCode code) {
        this.code = code;
    }

    public AcquisitionDisposition getAcquiredOrDisposed() {
        return acquiredOrDisposed;
    }

    public void setAcquiredOrDisposed(AcquisitionDisposition acquiredOrDisposed) {
        this.acquiredOrDisposed = acquiredOrDisposed;
    }

    public BigDecimal getShares() {
        return shares;
    }

    public void setShares(BigDecimal shares) {
        this.shares = shares;
    }

    public BigDecimal getPricePerShare() {
        return pricePerShare;
    }

    public void setPricePerShare(BigDecimal pricePerShare) {
        this.pricePerShare = pricePerShare;
    }

    public BigDecimal getSharesOwnedFollowing() {
        return sharesOwnedFollowing;
    }

    public void setSharesOwnedFollowing(BigDecimal sharesOwnedFollowing) {
        this.sharesOwnedFollowing = sharesOwnedFollowing;
    }

    public String getSecurityTitle() {
        return securityTitle;
    }

    public void setSecurityTitle(String securityTitle) {
        this.securityTitle = securityTitle;
    }

    public String getInsiderRole() {
        return insiderRole;
    }

    public void setInsiderRole(String insiderRole) {
        this.insiderRole = insiderRole;
    }

    public String getAccessionNumber() {
        return accessionNumber;
    }

    public void setAccessionNumber(String accessionNumber) {
        this.accessionNumber = accessionNumber;
    }

    public String getDocumentUrl() {
        return documentUrl;
    }

    public void setDocumentUrl(String documentUrl) {
        this.documentUrl = documentUrl;
    }

    public String getFormType() {
        return formType;
    }

    public void setFormType(String formType) {
        this.formType = formType;
    }
}
