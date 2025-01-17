package api.repository;

import api.model.TraceData;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TraceDataRepository extends JpaRepository<TraceData, Long> {
}