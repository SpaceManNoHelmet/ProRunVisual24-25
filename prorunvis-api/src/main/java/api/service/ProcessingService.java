package api.service;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.utils.ProjectRoot;
import com.google.gson.Gson;
import org.springframework.stereotype.Service;
import prorunvis.trace.TraceNode;
import prorunvis.trace.process.TraceProcessor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
public class ProcessingService {

    private static final String LOCAL_STORAGE_DIR = "resources/local_storage";

    /**
     * We do NOT return a path anymore. We'll just do it as `void`
     * or you can still return the path to "processedTrace.json" if you want.
     */
    public void processTrace(String traceId) {
        // 1) local_storage/<traceId> folder
        File localIdFolder = new File(LOCAL_STORAGE_DIR, traceId);
        if (!localIdFolder.exists() || !localIdFolder.isDirectory()) {
            throw new RuntimeException("Local ID folder does not exist: " + localIdFolder.getAbsolutePath());
        }

        // 2) local_storage/<traceId>/Trace.tr must exist
        File traceFile = new File(localIdFolder, "Trace.tr");
        if (!traceFile.exists()) {
            throw new RuntimeException("Trace file not found: " + traceFile.getAbsolutePath());
        }

        // 3) re-parse code from resources/out/instrumented
        // (or wherever your instrumented code remains)
        Path codeRoot = Paths.get("resources/in");
        ProjectRoot projectRoot = Util.parseProject(codeRoot.toFile());
        List<CompilationUnit> cus = Util.getCUs(projectRoot);

        // 4) build the map
        Map<Integer, Node> map = new HashMap<>();
        for (CompilationUnit cu : cus) {
            prorunvis.preprocess.Preprocessor.run(cu);
            prorunvis.instrument.Instrumenter.run(cu, map);
        }

        // 5) run the TraceProcessor
        prorunvis.trace.process.TraceProcessor processor =
                new prorunvis.trace.process.TraceProcessor(map, traceFile.getAbsolutePath(), codeRoot);

        try {
            processor.start();
        } catch (Exception e) {
            throw new RuntimeException("Processing failed: " + e.getMessage(), e);
        }

        // 6) convert the final node list to JSON
        List<TraceNode> nodeList = processor.getNodeList();
        String json = new Gson().toJson(nodeList);

        // 7) store processedTrace.json in local_storage/<traceId>/processedTrace.json
        File outputJson = new File(localIdFolder, "processedTrace.json");
        try (FileOutputStream fos = new FileOutputStream(outputJson)) {
            fos.write(json.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write processedTrace.json at: " + outputJson.getAbsolutePath(), e);
        }

        System.out.println("Processing complete. JSON stored at: " + outputJson.getAbsolutePath());
    }
}