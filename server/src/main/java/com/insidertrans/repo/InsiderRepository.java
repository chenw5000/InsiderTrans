package com.insidertrans.repo;

import com.insidertrans.domain.Insider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InsiderRepository extends JpaRepository<Insider, Long> {

    Optional<Insider> findByCik(String cik);

    Optional<Insider> findByNameIgnoreCase(String name);

    List<Insider> findAllByOrderByNameAsc();
}
