package prorunvis.instrument;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.utils.ProjectRoot;
import prorunvis.trace.TraceVisitor;

import java.io.*;
import java.nio.file.Paths;
import java.util.Map;

public final class Instrumenter {

    private static File traceFile;

    private Instrumenter() {
        throw new IllegalStateException("Class can not be instantiated");
    }

    public static void setupTrace(final File file) {
        traceFile = file;
        try {
            File parent = traceFile.getParentFile();
            if (parent != null && !parent.exists()) {
                if (!parent.mkdirs()) {
                    throw new IOException("Could not create directories for trace file: " + parent);
                }
            }

            if (traceFile.exists() && !traceFile.delete()) {
                throw new IOException("Could not delete existing trace file: " + traceFile);
            }

            if (!traceFile.createNewFile()) {
                throw new IOException("Could not create new trace file: " + traceFile);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error setting up trace file: " + e.getMessage(), e);
        }
    }

    public static void saveInstrumented(final ProjectRoot pr, final String instrumentedOutPath) {
        File instrumented = new File(instrumentedOutPath);
        if (!instrumented.exists() && !instrumented.mkdirs()) {
            throw new RuntimeException("Could not create instrumented output directory: " + instrumentedOutPath);
        }

        // Save all compilation units to the specified directory
        pr.getSourceRoots().forEach(sr -> sr.saveAll(Paths.get(instrumentedOutPath)));

        File proRunVisDir = new File(instrumented, "prorunvis");
        if (!proRunVisDir.exists() && !proRunVisDir.mkdirs()) {
            throw new RuntimeException("Could not create prorunvis directory: " + proRunVisDir);
        }

        File proRunVisClass = new File(proRunVisDir, "Trace.java");
        if (proRunVisClass.exists() && !proRunVisClass.delete()) {
            throw new RuntimeException("Could not delete existing Trace.java");
        }

        try {
            if (!proRunVisClass.createNewFile()) {
                throw new IOException("Could not create Trace.java file");
            }
        } catch (IOException e) {
            throw new RuntimeException("Error creating Trace.java: " + e.getMessage(), e);
        }

        String content = "package prorunvis;\n" +
                "import java.io.BufferedWriter;\n" +
                "import java.io.FileWriter;\n" +
                "import java.io.IOException;\n" +
                "public final class Trace {\n" +
                "    public static void next_elem(int num) {\n" +
                "        try (BufferedWriter writer = new BufferedWriter(new FileWriter(\"Trace.tr\", true))) {\n" +
                "            writer.write(\"\" + num + System.lineSeparator());\n" +
                "        } catch (IOException e) {\n" +
                "            throw new RuntimeException(e.getMessage());\n" +
                "        }\n" +
                "    }\n" +
                "}\n";

        try (BufferedWriter bf = new BufferedWriter(new FileWriter(proRunVisClass, false))) {
            bf.write(content);
        } catch (IOException e) {
            throw new RuntimeException("Error writing Trace.java: " + e.getMessage(), e);
        }
    }

    public static void run(final CompilationUnit cu, final Map<Integer, Node> map) {
        new TraceVisitor().visit(cu, map);
    }
}