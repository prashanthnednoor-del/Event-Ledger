package com.eventledger.repository;

import com.eventledger.model.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, String> {

    // Returns events in chronological order — handles out-of-order arrival automatically
    List<Event> findByAccountIdOrderByEventTimestampAsc(String accountId);

    // Paginated variant — sort is enforced by the service via Pageable
    Page<Event> findByAccountId(String accountId, Pageable pageable);

    // net balance: sum of CREDITs minus sum of DEBITs
    @Query("SELECT COALESCE(SUM(CASE WHEN e.type = 'CREDIT' THEN e.amount ELSE -e.amount END), 0) " +
           "FROM Event e WHERE e.accountId = :accountId")
    BigDecimal computeBalance(@Param("accountId") String accountId);

    // used to get the currency for the balance response
    @Query("SELECT e.currency FROM Event e WHERE e.accountId = :accountId ORDER BY e.eventTimestamp ASC")
    List<String> findCurrenciesByAccountId(@Param("accountId") String accountId);
}
