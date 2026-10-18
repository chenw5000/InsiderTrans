package com.insidertrans.web;

import com.insidertrans.service.processor.Form4FetchService;
import com.insidertrans.web.dto.BackfillRequest;
import com.insidertrans.web.dto.FetcherConfigRequest;
import com.insidertrans.web.dto.FetcherStatusResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final Form4FetchService fetcher;

    public AdminController(Form4FetchService fetcher) {
        this.fetcher = fetcher;
    }

    @GetMapping("/fetcher")
    public FetcherStatusResponse status() {
        return fetcher.status();
    }

    @PutMapping("/fetcher")
    public FetcherStatusResponse update(@RequestBody FetcherConfigRequest request) {
        return fetcher.update(request == null ? new FetcherConfigRequest(null, null) : request);
    }

    @PostMapping("/backfill")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public FetcherStatusResponse backfill(@Valid @RequestBody BackfillRequest request) {
        return fetcher.startBackfill(request.startDate(), request.endDate());
    }
}
