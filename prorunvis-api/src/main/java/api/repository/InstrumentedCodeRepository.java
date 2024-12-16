package api.repository;

import api.model.InstrumentedCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstrumentedCodeRepository extends JpaRepository<InstrumentedCode, Long> {
}