package com.insidertrans.web;

import com.insidertrans.domain.TransactionCode;
import com.insidertrans.service.LedgerService;
import com.insidertrans.web.dto.TransactionRequest;
import com.insidertrans.web.dto.TransactionResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final LedgerService ledger;

    public TransactionController(LedgerService ledger) {
        this.ledger = ledger;
    }

    @GetMapping
    public List<TransactionResponse> list(
            @RequestParam(required = false) String ticker,
            @RequestParam(required = false) String insider,
            @RequestParam(required = false) TransactionCode code,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return ledger.search(ticker, insider, code, from, to);
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable Long id) {
        return ledger.get(id);
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody TransactionRequest request) {
        TransactionResponse created = ledger.record(request);
        return ResponseEntity.created(URI.create("/api/transactions/" + created.id())).body(created);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        ledger.delete(id);
    }
}
