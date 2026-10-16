package com.insidertrans.web;

import com.insidertrans.repo.CompanyRepository;
import com.insidertrans.web.dto.CompanyResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyRepository companies;

    public CompanyController(CompanyRepository companies) {
        this.companies = companies;
    }

    @GetMapping
    public List<CompanyResponse> list() {
        return companies.findAllByOrderByTickerAsc().stream()
                .map(company -> new CompanyResponse(
                        company.getId(), company.getTicker(), company.getName(), company.getCik()))
                .toList();
    }
}
