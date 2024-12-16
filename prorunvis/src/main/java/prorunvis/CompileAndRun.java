package prorunvis;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This utility class compiles and runs the instrumented code.
 */
public final class CompileAndRun {

    private CompileAndRun() {
        throw new IllegalStateException();
    }

    /**
     * Compile and run the main class found in the provided compilation units.
     * @param cus a List of CompilationUnit with possibly one having a main method
     * @param instrumentedInPath path to instrumented source
     * @param compiledOutPath where compiled classes go
     * @throws IOException if compilation fails due to I/O
     * @throws InterruptedException if process is interrupted
     */
    public static void run(final List<CompilationUnit> cus,
                           final String instrumentedInPath, final String compiledOutPath)
            throws IOException, InterruptedException {
        File compiled = new File(compiledOutPath);
        if (!compiled.exists() && !compiled.mkdirs()) {
            throw new IOException("Failed to create compiled output directory: " + compiledOutPath);
        }

        // Find main class
        List<CompilationUnit> mains = cus.stream()
                .filter(cu -> cu.findFirst(MethodDeclaration.class,
                        m -> m.getNameAsString().equals("main")).isPresent())
                .toList();
        if (mains.isEmpty()) {
            throw new RuntimeException("No main method found in the instrumented code. Cannot run.");
        }

        CompilationUnit mainUnit = mains.get(0);
        String fileName = mainUnit.getStorage().get().getFileName();
        Path sourcePath = mainUnit.getStorage().get().getDirectory();

        // Compile
        ProcessBuilder compilePb = new ProcessBuilder("javac", "-sourcepath",
                instrumentedInPath, "-d", compiledOutPath, sourcePath.resolve(fileName).toString());
        compilePb.directory(new File(instrumentedInPath));
        Process compileProc = compilePb.start();
        int compileExit = compileProc.waitFor();
        if (compileExit != 0) {
            String compileError = new BufferedReader(new InputStreamReader(compileProc.getErrorStream()))
                    .lines().collect(Collectors.joining("\n"));
            throw new InterruptedException("An error occurred during compilation.\n" + compileError);
        }

        // Derive the main class name
        // Convert the path difference to a package name
        String prefix = Paths.get(instrumentedInPath).toAbsolutePath().toString();
        String fullPath = sourcePath.toAbsolutePath().toString();
        String packageName = "";
        if (fullPath.length() > prefix.length()) {
            packageName = fullPath.substring(prefix.length())
                    .replace(File.separatorChar, '.');
            if (packageName.startsWith(".")) {
                packageName = packageName.substring(1);
            }
        }
        String mainClass = packageName.isEmpty() ? fileName.replace(".java", "") : packageName + "." + fileName.replace(".java", "");

        // Run
        ProcessBuilder runPb = new ProcessBuilder("java", "-cp", compiledOutPath, mainClass);
        runPb.directory(new File(instrumentedInPath)); // Run from the instrumented directory
        Process runProc = runPb.start();
        int runExit = runProc.waitFor();
        if (runExit != 0) {
            String runError = new BufferedReader(new InputStreamReader(runProc.getErrorStream()))
                    .lines().collect(Collectors.joining("\n"));
            if (!runError.isEmpty()) {
                System.out.println("There was an error running the input code.\n" + runError);
            }
        }
    }
}