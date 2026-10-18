package com.insidertrans.web;

import com.insidertrans.service.InsiderImpactService;
import com.insidertrans.web.dto.ImpactRequest;
import com.insidertrans.web.dto.ImpactResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/impact")
public class ImpactController {

    private final InsiderImpactService impact;

    public ImpactController(InsiderImpactService impact) {
        this.impact = impact;
    }

    /**
     * Job 1: score recent open-market insider P/S as of {@code asOfDate}. The caller does not need
     * to know whether a Form 4 was filed that day.
     */
    @PostMapping
    public ImpactResponse evaluate(@Valid @RequestBody ImpactRequest request) {
        return impact.evaluate(request);
    }
}
