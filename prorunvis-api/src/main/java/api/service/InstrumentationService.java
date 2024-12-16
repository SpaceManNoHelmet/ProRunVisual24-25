package api.service;

import api.model.InstrumentedCode;
import api.repository.InstrumentedCodeRepository;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.utils.ProjectRoot;
import org.springframework.stereotype.Service;
import prorunvis.instrument.Instrumenter;
import prorunvis.preprocess.Preprocessor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class InstrumentationService {

    private final InstrumentedCodeRepository repo;

    public InstrumentationService(InstrumentedCodeRepository repo) {
        this.repo = repo;
    }

    private void ensureCleanOutputDirectories() {
        File outDir = new File("resources/out");
        if (outDir.exists()) {
            System.out.println("Cleaning existing resources/out directory...");
            try {
                Files.walk(outDir.toPath())
                        .map(java.nio.file.Path::toFile)
                        .sorted((o1, o2) -> -o1.compareTo(o2)) // delete children first
                        .forEach(File::delete);
            } catch (IOException e) {
                throw new RuntimeException("Failed to clean output directories", e);
            }
        }

        if (!outDir.mkdirs()) {
            throw new RuntimeException("Failed to create output directory at resources/out.");
        }

        File instrDir = new File("resources/out/instrumented");
        if (!instrDir.exists() && !instrDir.mkdirs()) {
            throw new RuntimeException("Failed to create instrumented directory at resources/out/instrumented.");
        }
    }

    public Long instrumentProject(String projectName, String inputDirPath) {
        File inputDir = new File(inputDirPath);
        if (!inputDir.exists() || !inputDir.isDirectory()) {
            throw new RuntimeException("Input directory does not exist or is not a directory: " + inputDirPath);
        }

        // Clean and set up output directories
        ensureCleanOutputDirectories();

        System.out.println("Parsing project at: " + inputDirPath);
        ProjectRoot projectRoot = Util.parseProject(inputDir);
        List<CompilationUnit> cus = Util.getCUs(projectRoot);

        if (cus.isEmpty()) {
            throw new RuntimeException("No Java files found in provided directory: " + inputDirPath);
        }
        System.out.println("Found " + cus.size() + " compilation units.");

        Map<Integer, Node> map = new HashMap<>();
        for (CompilationUnit cu : cus) {
            Preprocessor.run(cu);
            Instrumenter.run(cu, map);
        }

        // Setup the trace file before saving instrumented code
        File traceFile = new File("resources/out/Trace.tr");
        Instrumenter.setupTrace(traceFile);

        System.out.println("Saving instrumented code to resources/out/instrumented ...");
        Instrumenter.saveInstrumented(projectRoot, "resources/out/instrumented");

        // Verify that instrumented code was actually saved
        File instrDir = new File("resources/out/instrumented");
        String[] instrumentedFiles = instrDir.list();
        if (instrumentedFiles == null || instrumentedFiles.length == 0) {
            throw new RuntimeException("No files found in instrumented directory after saving instrumented code.");
        }
        System.out.println("Files in instrumented directory: ");
        for (String f : instrumentedFiles) {
            System.out.println(" - " + f);
        }

        // Zip and encode instrumented project
        System.out.println("Zipping instrumented code...");
        String zipBase64 = Util.zipAndEncode(projectRoot);

        if (zipBase64 == null || zipBase64.isEmpty()) {
            throw new RuntimeException("Failed to zip instrumented code.");
        }

        System.out.println("Instrumented code zipped successfully. Storing in database...");
        InstrumentedCode entity = new InstrumentedCode();
        entity.setProjectName(projectName);
        entity.setInstrumentedSourceZip(zipBase64);

        entity = repo.save(entity);
        System.out.println("Instrumented code saved with ID: " + entity.getId());

        return entity.getId();
    }
}