package api.repository;

import api.model.ProcessedTrace;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedTraceRepository extends JpaRepository<ProcessedTrace, Long> {
}