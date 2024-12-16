package api.service;

import api.model.TraceData;
import api.model.InstrumentedCode;
import api.repository.InstrumentedCodeRepository;
import api.repository.TraceDataRepository;
import org.springframework.stereotype.Service;
import com.github.javaparser.ast.CompilationUnit;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Service
public class TracingService {
    private final TraceDataRepository traceRepo;
    private final InstrumentedCodeRepository codeRepo;

    public TracingService(TraceDataRepository traceRepo, InstrumentedCodeRepository codeRepo) {
        this.traceRepo = traceRepo;
        this.codeRepo = codeRepo;
    }

    private void cleanOutputDirectories() {
        File outDir = new File("resources/out");
        if (outDir.exists()) {
            try {
                // If deleteRecursively throws a checked exception, wrap it
                org.springframework.util.FileSystemUtils.deleteRecursively(outDir.toPath());
            } catch (IOException e) {
                throw new RuntimeException("Failed to clean output directories", e);
            }
        }
        // Ensure directories are recreated after cleaning
        if (!outDir.mkdirs()) {
            throw new RuntimeException("Failed to create output directory after cleaning.");
        }
    }
    public Long runTrace(Long instrumentedCodeId) {
        // Clean directories before starting a new trace run

        InstrumentedCode ic = codeRepo.findById(instrumentedCodeId)
                .orElseThrow(() -> new RuntimeException("Instrumented code not found"));

        // In the code inside Util, adjust to resources/out/downloaded_instrumented
        File instrumentedDir = Util.unzipAndDecode(ic.getInstrumentedSourceZip());


        // Compile and run to produce trace
        List<CompilationUnit> cus = Util.loadCUs(instrumentedDir);

        try {
            prorunvis.CompileAndRun.run(cus,
                    instrumentedDir.getAbsolutePath(),
                    instrumentedDir.getAbsolutePath() + "/compiled");
        } catch (Exception e) {
            throw new RuntimeException("Trace failed", e);
        }

        // After run, Trace.tr is created inside instrumentedDir
        File traceFile = new File(instrumentedDir, "Trace.tr");
        String traceContent = Util.readFileAsString(traceFile);

        TraceData td = new TraceData();
        td.setInstrumentedCodeId(instrumentedCodeId);
        td.setTraceFileContent(traceContent);
        td = traceRepo.save(td);
        return td.getId();
    }
}