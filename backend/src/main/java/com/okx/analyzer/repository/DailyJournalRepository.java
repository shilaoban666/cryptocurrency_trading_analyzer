package com.okx.analyzer.repository;

import com.okx.analyzer.entity.DailyJournal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyJournalRepository extends JpaRepository<DailyJournal, Long> {
    Optional<DailyJournal> findByJournalDate(LocalDate journalDate);
    List<DailyJournal> findByJournalDateBetweenOrderByJournalDateAsc(LocalDate start, LocalDate end);
}
