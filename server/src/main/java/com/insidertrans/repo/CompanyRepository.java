package com.insidertrans.repo;

import com.insidertrans.domain.Company;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByTickerIgnoreCase(String ticker);

    List<Company> findByCik(String cik);

    List<Company> findAllByOrderByTickerAsc();
}
