package api.service;

import api.model.TraceData;
import api.model.ProcessedTrace;
import api.repository.TraceDataRepository;
import api.repository.ProcessedTraceRepository;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.utils.ProjectRoot;
import com.google.gson.Gson;
import com.github.javaparser.ast.Node;
import org.springframework.stereotype.Service;
import prorunvis.trace.TraceNode;
import prorunvis.trace.process.TraceProcessor;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProcessingService {

    private final ProcessedTraceRepository procRepo;
    private final TraceDataRepository traceRepo;

    public ProcessingService(ProcessedTraceRepository procRepo, TraceDataRepository traceRepo) {
        this.procRepo = procRepo;
        this.traceRepo = traceRepo;
    }

    public Long processTrace(Long traceDataId) {
        TraceData td = traceRepo.findById(traceDataId)
                .orElseThrow(() -> new RuntimeException("Trace not found"));

        File tempTraceFile = Util.createTempTraceFile(td.getTraceFileContent());

        // Re-parse the instrumented code
        Path codeRoot = Paths.get("resources/in");
        ProjectRoot projectRoot = Util.parseProject(codeRoot.toFile());
        List<CompilationUnit> cus = Util.getCUs(projectRoot);

        // Build the map again
        Map<Integer, Node> map = new HashMap<>();
        for (CompilationUnit cu : cus) {
            prorunvis.preprocess.Preprocessor.run(cu);
            prorunvis.instrument.Instrumenter.run(cu, map);
        }

        // Now run the TraceProcessor with a fully populated map
        TraceProcessor processor = new TraceProcessor(map, tempTraceFile.getPath(), codeRoot);
        try {
            processor.start();
        } catch (Exception e) {
            throw new RuntimeException("Processing failed: " + e.getMessage(), e);
        }

        List<TraceNode> nodeList = processor.getNodeList();
        String json = new Gson().toJson(nodeList);

        ProcessedTrace pt = new ProcessedTrace();
        pt.setTraceDataId(traceDataId);
        pt.setProcessedJson(json);
        pt = procRepo.save(pt);

        return pt.getId();
    }
}