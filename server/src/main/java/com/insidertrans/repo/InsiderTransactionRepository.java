package com.insidertrans.repo;

import com.insidertrans.domain.InsiderTransaction;
import com.insidertrans.domain.TransactionCode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InsiderTransactionRepository extends JpaRepository<InsiderTransaction, Long> {

    boolean existsByAccessionNumber(String accessionNumber);

    @Query("""
            select t from InsiderTransaction t
            join fetch t.company c
            join fetch t.insider i
            where lower(c.ticker) = :ticker
              and coalesce(t.filingDate, t.transactionDate) >= :fromDate
              and coalesce(t.filingDate, t.transactionDate) <= :asOfDate
            order by coalesce(t.filingDate, t.transactionDate) desc, t.id desc
            """)
    List<InsiderTransaction> findKnownAsOf(
            @Param("ticker") String ticker,
            @Param("fromDate") LocalDate fromDate,
            @Param("asOfDate") LocalDate asOfDate);

    @Query(
            value = """
                    select t from InsiderTransaction t
                    join fetch t.company c
                    join fetch t.insider i
                    where (:ticker = '' or lower(c.ticker) = :ticker)
                      and lower(i.name) like :insider escape '\\'
                      and (:codeFilter = false or t.code = :code)
                      and t.transactionDate >= :fromDate
                      and t.transactionDate <= :toDate
                    order by t.transactionDate desc, t.id desc
                    """,
            countQuery = """
                    select count(t) from InsiderTransaction t
                    join t.company c
                    join t.insider i
                    where (:ticker = '' or lower(c.ticker) = :ticker)
                      and lower(i.name) like :insider escape '\\'
                      and (:codeFilter = false or t.code = :code)
                      and t.transactionDate >= :fromDate
                      and t.transactionDate <= :toDate
                    """)
    Page<InsiderTransaction> search(
            @Param("ticker") String ticker,
            @Param("insider") String insider,
            @Param("codeFilter") boolean codeFilter,
            @Param("code") TransactionCode code,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable);
}
