package com.insidertrans.repo;

import com.insidertrans.domain.InsiderTransaction;
import com.insidertrans.domain.TransactionCode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InsiderTransactionRepository extends JpaRepository<InsiderTransaction, Long> {

    @Query("""
            select t from InsiderTransaction t
            join fetch t.company c
            join fetch t.insider i
            where (:ticker is null or lower(c.ticker) = lower(:ticker))
              and (:insider is null or lower(i.name) like :insider escape '\\')
              and (:code is null or t.code = :code)
              and (:fromDate is null or t.transactionDate >= :fromDate)
              and (:toDate is null or t.transactionDate <= :toDate)
            order by t.transactionDate desc, t.id desc
            """)
    List<InsiderTransaction> search(
            @Param("ticker") String ticker,
            @Param("insider") String insider,
            @Param("code") TransactionCode code,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);
}
