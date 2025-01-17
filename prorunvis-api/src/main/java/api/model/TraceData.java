package api.model;

import jakarta.persistence.*;

@Entity
public class TraceData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long instrumentedCodeId;

    @Lob
    private String traceFileContent;

    public Long getId() {
        return id;
    }

    public Long getInstrumentedCodeId() {
        return instrumentedCodeId;
    }

    public String getTraceFileContent() {
        return traceFileContent;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setInstrumentedCodeId(Long instrumentedCodeId) {
        this.instrumentedCodeId = instrumentedCodeId;
    }

    public void setTraceFileContent(String traceFileContent) {
        this.traceFileContent = traceFileContent;
    }
}