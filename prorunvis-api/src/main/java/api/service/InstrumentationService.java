package api.service;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.utils.ProjectRoot;
import org.springframework.stereotype.Service;
import prorunvis.instrument.Instrumenter;
import prorunvis.preprocess.Preprocessor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A reworked InstrumentationService that does NOT store data in a DB.
 * Instead, it stores the instrumented code locally, keyed by a random ID.
 */
@Service
public class InstrumentationService {

    /**
     * The base folder where we store each run's local data,
     * keyed by a random ID (e.g. "resources/local_storage/<ID>").
     */
    private static final String LOCAL_STORAGE_DIR = "resources/local_storage";

    public InstrumentationService() {
        // No repository injection needed anymore.
    }

    /**
     * Creates/cleans "resources/out" so the instrumentation can run
     * from a clean slate. This is the same logic you had before.
     */
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

    /**
     * Instruments the code and stores results in "resources/local_storage/<randomId>/".
     *
     * @param projectName   the name of the user's project
     * @param inputDirPath  the folder containing the source code to be instrumented
     * @param randomId      a unique ID that we can use for storing output
     * @return Some success message (or path).
     */
    public String instrumentProject(String projectName,
                                    String inputDirPath,
                                    String randomId) {

        // 1) Verify input directory is valid
        File inputDir = new File(inputDirPath);
        if (!inputDir.exists() || !inputDir.isDirectory()) {
            throw new RuntimeException("Input directory does not exist or is not a directory: " + inputDirPath);
        }


        // 2) Clean & set up "resources/out"
        ensureCleanOutputDirectories();

        // 3) Parse & instrument code
        System.out.println("Parsing project at: " + inputDirPath);
        ProjectRoot projectRoot = Util.parseProject(inputDir);
        List<CompilationUnit> cus = Util.getCUs(projectRoot);
        if (cus.isEmpty()) {
            throw new RuntimeException("No Java files found in: " + inputDirPath);
        }
        System.out.println("Found " + cus.size() + " compilation units.");

        Map<Integer, Node> map = new HashMap<>();
        for (CompilationUnit cu : cus) {
            Preprocessor.run(cu);
            Instrumenter.run(cu, map);
        }


        // 5) Save instrumented code
        Instrumenter.saveInstrumented(projectRoot, "resources/out/instrumented");

        // 6) Check that something was indeed saved
        File instrDir = new File("resources/out/instrumented");
        String[] instrumentedFiles = instrDir.list();
        if (instrumentedFiles == null || instrumentedFiles.length == 0) {
            throw new RuntimeException("No files found in instrumented directory after saving instrumented code.");
        }
        System.out.println("Files in instrumented directory:");
        for (String f : instrumentedFiles) {
            System.out.println(" - " + f);
        }

        // 7) Zip & encode
        System.out.println("Zipping instrumented code...");
        String zipBase64 = Util.zipAndEncode(projectRoot);
        if (zipBase64 == null || zipBase64.isEmpty()) {
            throw new RuntimeException("Failed to zip instrumented code.");
        }

        // 8) Store the resulting Base64 in "resources/local_storage/<randomId>/instrumented.txt"
        // Make a local folder for this randomId
        File randomIdFolder = new File(LOCAL_STORAGE_DIR, randomId);
        if (!randomIdFolder.exists()) {
            randomIdFolder.mkdirs();
        }
        File outputFile = new File(randomIdFolder, "instrumented_base64.txt");
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.write(zipBase64.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Error writing local instrumented file: " + e.getMessage(), e);
        }

        System.out.println("Instrumented code stored locally in: " + outputFile.getAbsolutePath());

        // Return a success message
        return "Instrumented code saved under ID=" + randomId
                + " at: " + outputFile.getAbsolutePath();
    }
}