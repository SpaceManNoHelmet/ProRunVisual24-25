package api.model;

import jakarta.persistence.*;

@Entity
public class ProcessedTrace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long traceDataId;

    @Lob
    private String processedJson;

    public Long getId() {
        return id;
    }

    public Long getTraceDataId() {
        return traceDataId;
    }

    public String getProcessedJson() {
        return processedJson;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setTraceDataId(Long traceDataId) {
        this.traceDataId = traceDataId;
    }

    public void setProcessedJson(String processedJson) {
        this.processedJson = processedJson;
    }
}