package api.service;

import api.model.ProcessedTrace;
import api.repository.ProcessedTraceRepository;
import org.springframework.stereotype.Service;

@Service
public class VisualizationService {
    private final ProcessedTraceRepository repo;

    public VisualizationService(ProcessedTraceRepository repo) {
        this.repo = repo;
    }

    public String getTraceJson(Long processedTraceId) {
        ProcessedTrace pt = repo.findById(processedTraceId)
                .orElseThrow(() -> new RuntimeException("Processed trace not found"));

        return pt.getProcessedJson();
    }
}