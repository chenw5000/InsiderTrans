package com.insidertrans.service.processor;

import com.insidertrans.domain.AcquisitionDisposition;
import com.insidertrans.domain.TransactionCode;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Pulls {@code ownershipDocument} XML out of an EDGAR Form 4 submission package and maps
 * transaction rows we can store.
 */
public final class Form4OwnershipParser {

    private static final Logger log = LoggerFactory.getLogger(Form4OwnershipParser.class);
    private static final int TITLE_MAX = 120;
    private static final int NAME_MAX = 200;
    private static final int ROLE_MAX = 120;

    private Form4OwnershipParser() {
    }

    public static String extractOwnershipXml(String filingDocument) {
        if (filingDocument == null || filingDocument.isBlank()) {
            throw new IllegalStateException("Form 4 document is empty");
        }
        int start = indexOfIgnoreCase(filingDocument, "<ownershipDocument");
        int end = lastIndexOfIgnoreCase(filingDocument, "</ownershipDocument>");
        if (start < 0 || end < 0 || end < start) {
            throw new IllegalStateException("Form 4 document has no ownershipDocument XML");
        }
        return filingDocument.substring(start, end + "</ownershipDocument>".length());
    }

    public static ParsedOwnership parseFilingDocument(String filingDocument) {
        return parseOwnershipXml(extractOwnershipXml(filingDocument));
    }

    public static ParsedOwnership parseOwnershipXml(String xml) {
        Document document = parseXml(xml);
        Element root = document.getDocumentElement();

        String issuerCik = text(root, "issuerCik");
        String issuerName = text(root, "issuerName");
        String ticker = text(root, "issuerTradingSymbol");
        if (ticker != null) {
            ticker = ticker.toUpperCase(Locale.ROOT);
        }

        Element owner = firstChild(root, "reportingOwner");
        String insiderCik = owner == null ? null : text(owner, "rptOwnerCik");
        String insiderName = owner == null ? null : text(owner, "rptOwnerName");
        String insiderRole = owner == null ? null : roleFrom(owner);
        if (insiderName != null && insiderName.length() > NAME_MAX) {
            insiderName = insiderName.substring(0, NAME_MAX);
        }

        List<ParsedTransaction> transactions = new ArrayList<>();
        collectTransactions(root, "nonDerivativeTransaction", transactions);
        collectTransactions(root, "derivativeTransaction", transactions);

        return new ParsedOwnership(
                Form4Downloader.normalizeCik(issuerCik),
                blankToNull(issuerName),
                blankToNull(ticker),
                Form4Downloader.normalizeCik(insiderCik),
                blankToNull(insiderName),
                blankToNull(insiderRole),
                List.copyOf(transactions));
    }

    private static void collectTransactions(
            Element root, String tag, List<ParsedTransaction> transactions) {
        NodeList nodes = root.getElementsByTagName(tag);
        for (int i = 0; i < nodes.getLength(); i++) {
            ParsedTransaction row = parseTransaction((Element) nodes.item(i));
            if (row != null) {
                transactions.add(row);
            }
        }
    }

    private static ParsedTransaction parseTransaction(Element node) {
        String codeRaw = text(node, "transactionCode");
        TransactionCode code = transactionCode(codeRaw);
        if (code == null) {
            log.debug("Skipping Form 4 row with unsupported code {}", codeRaw);
            return null;
        }

        LocalDate date = parseDate(text(node, "transactionDate"));
        if (date == null) {
            log.debug("Skipping Form 4 row with no transaction date");
            return null;
        }

        BigDecimal shares = parseDecimal(text(node, "transactionShares"));
        if (shares == null || shares.signum() <= 0) {
            log.debug("Skipping Form 4 row with no share amount");
            return null;
        }

        AcquisitionDisposition ad = acquisition(text(node, "transactionAcquiredDisposedCode"));
        if (ad == null) {
            ad = code.defaultDisposition();
        }
        if (ad == null) {
            log.debug("Skipping Form 4 gift row with no acquired/disposed flag");
            return null;
        }

        String title = text(node, "securityTitle");
        if (title == null || title.isBlank()) {
            title = "Common Stock";
        }
        if (title.length() > TITLE_MAX) {
            title = title.substring(0, TITLE_MAX);
        }

        return new ParsedTransaction(
                date,
                code,
                ad,
                shares,
                parseDecimal(text(node, "transactionPricePerShare")),
                parseDecimal(text(node, "sharesOwnedFollowingTransaction")),
                title);
    }

    private static String roleFrom(Element owner) {
        String title = text(owner, "officerTitle");
        if (title != null && !title.isBlank()) {
            return clip(title.trim(), ROLE_MAX);
        }
        List<String> parts = new ArrayList<>();
        if (flag(owner, "isDirector")) {
            parts.add("Director");
        }
        if (flag(owner, "isOfficer")) {
            parts.add("Officer");
        }
        if (flag(owner, "isTenPercentOwner")) {
            parts.add("10% Owner");
        }
        String other = text(owner, "otherText");
        if (flag(owner, "isOther") && other != null && !other.isBlank()) {
            parts.add(other.trim());
        }
        if (parts.isEmpty()) {
            return null;
        }
        return clip(String.join(", ", parts), ROLE_MAX);
    }

    private static boolean flag(Element owner, String tag) {
        String value = text(owner, tag);
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized);
    }

    private static TransactionCode transactionCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return TransactionCode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static AcquisitionDisposition acquisition(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AcquisitionDisposition.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim().replace(",", ""));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Document parseXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse Form 4 ownership XML", ex);
        }
    }

    private static Element firstChild(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return null;
        }
        return (Element) nodes.item(0);
    }

    /**
     * Prefers a nested {@code <value>} when present, which is how Form 4 tags footnote-capable fields.
     */
    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return null;
        }
        Element element = (Element) nodes.item(0);
        NodeList values = element.getElementsByTagName("value");
        String raw = values.getLength() > 0
                ? values.item(0).getTextContent()
                : element.getTextContent();
        return blankToNull(raw);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static int indexOfIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }

    private static int lastIndexOfIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase(Locale.ROOT).lastIndexOf(needle.toLowerCase(Locale.ROOT));
    }

    public record ParsedOwnership(
            String issuerCik,
            String issuerName,
            String ticker,
            String insiderCik,
            String insiderName,
            String insiderRole,
            List<ParsedTransaction> transactions) {
        public ParsedOwnership {
            transactions = transactions == null ? List.of() : List.copyOf(transactions);
        }
    }

    public record ParsedTransaction(
            LocalDate transactionDate,
            TransactionCode code,
            AcquisitionDisposition acquiredOrDisposed,
            BigDecimal shares,
            BigDecimal pricePerShare,
            BigDecimal sharesOwnedFollowing,
            String securityTitle) {
    }
}
