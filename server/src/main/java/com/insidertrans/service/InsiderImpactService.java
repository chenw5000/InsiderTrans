package com.insidertrans.service;

import com.insidertrans.domain.InsiderTransaction;
import com.insidertrans.domain.TransactionCode;
import com.insidertrans.repo.InsiderTransactionRepository;
import com.insidertrans.web.dto.ImpactRequest;
import com.insidertrans.web.dto.ImpactResponse;
import com.insidertrans.web.dto.ImpactTradeResponse;
import com.insidertrans.web.dto.OhlcBar;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class InsiderImpactService {

    private final InsiderTransactionRepository transactions;

    public InsiderImpactService(InsiderTransactionRepository transactions) {
        this.transactions = transactions;
    }

    public ImpactResponse evaluate(ImpactRequest request) {
        if (request == null || request.ticker() == null || request.ticker().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ticker is required");
        }
        if (request.asOfDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "asOfDate is required");
        }
        String ticker = ImpactCalculator.tickerKey(request.ticker());
        LocalDate asOf = request.asOfDate();
        List<OhlcBar> bars = ImpactCalculator.usableBars(request.bars(), asOf);
        if (bars.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "bars must include at least one session on or before asOfDate");
        }

        LocalDate historyFrom = asOf.minusDays(ImpactCalculator.HISTORY_DAYS);
        List<InsiderTransaction> rows = transactions.findKnownAsOf(ticker.toLowerCase(), historyFrom, asOf);

        BigDecimal medianDv = ImpactCalculator.medianDollarVolume(bars);
        String trend = ImpactCalculator.trend(bars);

        List<ImpactTradeResponse> trades = new ArrayList<>();
        List<Double> scored = new ArrayList<>();
        List<Double> ret5 = new ArrayList<>();
        List<Double> ret10 = new ArrayList<>();
        List<Double> ret20 = new ArrayList<>();

        for (InsiderTransaction row : rows) {
            LocalDate known = row.getFilingDate() != null ? row.getFilingDate() : row.getTransactionDate();
            BigDecimal cash = cashValue(row);
            double type = ImpactCalculator.typeFactor(row.getCode());
            double amount = ImpactCalculator.amountFactor(row.getCode(), cash);
            double time = ImpactCalculator.timeFactor(known, asOf);
            double impact = ImpactCalculator.impact(type, amount, time);
            boolean inScore = (row.getCode() == TransactionCode.P || row.getCode() == TransactionCode.S)
                    && amount > 0
                    && time > 0;
            if (inScore) {
                scored.add(impact);
            }
            Double r5 = ImpactCalculator.sessionReturn(bars, known, 5);
            Double r10 = ImpactCalculator.sessionReturn(bars, known, 10);
            Double r20 = ImpactCalculator.sessionReturn(bars, known, 20);
            if (inScore && r5 != null) {
                ret5.add(r5);
            }
            if (inScore && r10 != null) {
                ret10.add(r10);
            }
            if (inScore && r20 != null) {
                ret20.add(r20);
            }
            trades.add(new ImpactTradeResponse(
                    row.getId(),
                    row.getInsider().getName(),
                    row.getInsiderRole(),
                    row.getTransactionDate(),
                    row.getFilingDate(),
                    known,
                    row.getCode(),
                    row.getCode().getLabel(),
                    row.getAcquiredOrDisposed(),
                    row.getShares(),
                    row.getPricePerShare(),
                    cash,
                    row.getSecurityTitle(),
                    round4(type),
                    round4(amount),
                    round4(time),
                    round4(impact),
                    round4Nullable(ImpactCalculator.cashToMedianVolume(cash, medianDv)),
                    round4Nullable(r5),
                    round4Nullable(r10),
                    round4Nullable(r20),
                    inScore));
        }

        double flowScore = round4(ImpactCalculator.flowScore(scored));
        String caveat = caveat(bars.size(), scored.size(), rows.isEmpty());
        return new ImpactResponse(
                ticker,
                asOf,
                !rows.isEmpty(),
                ImpactCalculator.HISTORY_DAYS,
                ImpactCalculator.SCORE_DAYS,
                bars.size(),
                medianDv,
                trend,
                scored.size(),
                flowScore,
                ImpactCalculator.flowLabel(flowScore),
                round4Nullable(ImpactCalculator.mean(ret5)),
                round4Nullable(ImpactCalculator.mean(ret10)),
                round4Nullable(ImpactCalculator.mean(ret20)),
                caveat,
                List.copyOf(trades));
    }

    private static BigDecimal cashValue(InsiderTransaction row) {
        if (row.getShares() == null || row.getPricePerShare() == null) {
            return null;
        }
        if (row.getPricePerShare().signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return row.getShares().multiply(row.getPricePerShare());
    }

    private static String caveat(int bars, int scored, boolean noFilings) {
        if (noFilings) {
            return "No Form 4 filings for this ticker were known on asOfDate in the prior "
                    + ImpactCalculator.HISTORY_DAYS
                    + " days. The caller does not need a filing on the request date.";
        }
        StringBuilder text = new StringBuilder(
                "Job 1 snapshot only: open-market P/S cash flow with 30-day filing decay. "
                        + "Not a price forecast. Bars after asOfDate are ignored. "
                        + "+5/+10/+20 session returns are null until that many sessions exist after the filing "
                        + "and on or before asOfDate.");
        if (bars < 90) {
            text.append(" Fewer than 90 sessions were usable; 90–120 is preferred.");
        }
        if (scored == 0) {
            text.append(" No priced P/S prints fell inside the 30-day score window.");
        }
        return text.toString();
    }

    private static double round4(double value) {
        return Math.round(value * 10000d) / 10000d;
    }

    private static Double round4Nullable(Double value) {
        return value == null ? null : round4(value);
    }
}
