package com.insidertrans.web;

import com.insidertrans.repo.InsiderRepository;
import com.insidertrans.web.dto.InsiderResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/insiders")
public class InsiderController {

    private final InsiderRepository insiders;

    public InsiderController(InsiderRepository insiders) {
        this.insiders = insiders;
    }

    @GetMapping
    public List<InsiderResponse> list() {
        return insiders.findAllByOrderByNameAsc().stream()
                .map(insider -> new InsiderResponse(insider.getId(), insider.getName(), insider.getCik()))
                .toList();
    }
}
