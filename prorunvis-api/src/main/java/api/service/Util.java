package api.service;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.utils.ProjectRoot;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.utils.SymbolSolverCollectionStrategy;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Util {

    public static ProjectRoot parseProject(File inputDir) {
        StaticJavaParser.getConfiguration().setSymbolResolver(new JavaSymbolSolver(new CombinedTypeSolver()));
        return new SymbolSolverCollectionStrategy().collect(inputDir.toPath());
    }

    public static List<CompilationUnit> getCUs(ProjectRoot projectRoot) {
        return projectRoot.getSourceRoots().stream()
                .flatMap(sr -> {
                    try {
                        return sr.tryToParse().stream().filter(res -> res.getResult().isPresent()).map(res -> res.getResult().get());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());
    }

    public static String zipAndEncode(ProjectRoot projectRoot) {
        // This zips the instrumented code output (assuming saved in "resources/out/instrumented")
        File instrumentedDir = new File("resources/out/instrumented");
        if (!instrumentedDir.exists()) {
            throw new RuntimeException("Instrumented directory not found.");
        }

        File zipFile = new File("instrumented.zip");
        zipDirectory(instrumentedDir, zipFile);
        byte[] content;
        try {
            content = Files.readAllBytes(zipFile.toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return Base64.getEncoder().encodeToString(content);
    }

    public static void zipDirectory(File sourceDir, File zipFile) {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            Path sourcePath = sourceDir.toPath();

            Files.walk(sourcePath)
                    .filter(p -> !Files.isDirectory(p))
                    .forEach(path -> {
                        ZipEntry zipEntry = new ZipEntry(sourcePath.relativize(path).toString());
                        try {
                            zos.putNextEntry(zipEntry);
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static File unzipAndDecode(String base64Zip) {
        // For simplicity, we'll just write out the zip and manually extract it
        byte[] data = Base64.getDecoder().decode(base64Zip);
        File zipFile = new File("instrumented_downloaded.zip");
        try {
            Files.write(zipFile.toPath(), data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        File outputDir = new File("resources/out/downloaded_instrumented");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(outputDir, entry.getName());
                newFile.getParentFile().mkdirs();
                try (FileOutputStream fos = new FileOutputStream(newFile)) {
                    zis.transferTo(fos);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return outputDir;
    }

    public static List<CompilationUnit> loadCUs(File instrumentedDir) {
        ProjectRoot pr = parseProject(instrumentedDir);
        return getCUs(pr);
    }

    public static String readFileAsString(File f) {
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static File createTempTraceFile(String content) {
        try {
            File f = File.createTempFile("trace", ".tr");
            Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
            return f;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}