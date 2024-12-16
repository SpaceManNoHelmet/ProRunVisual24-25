package prorunvis;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.utils.SymbolSolverCollectionStrategy;
import com.github.javaparser.utils.ProjectRoot;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.cli.*;
import prorunvis.instrument.Instrumenter;
import prorunvis.preprocess.Preprocessor;
import prorunvis.trace.process.TraceProcessor;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

public final class ProRunVis {
    /**
     * Private constructor for main is never called.
     */
    private ProRunVis() {
    }

    /**
     * Entry point for the standalone usage of ProRunVis.
     */
    public static void main(final String[] args) {

        boolean instrumentOnly = false;
        String inputPath;
        String outputPath = "resources/out";

        Options options = new Options();
        options.addOption(Option.builder("h")
                .longOpt("help")
                .desc("Prints this help message")
                .build());
        options.addOption(Option.builder("i")
                .longOpt("instrument")
                .desc("If the input should only be instrumented")
                .build());
        options.addOption(Option.builder("o")
                .longOpt("output")
                .hasArg()
                .argName("output_directory")
                .desc("Output file path")
                .build());

        CommandLineParser commandLineParser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;


        try {
            cmd = commandLineParser.parse(options, args);

            //check if required input has been provided
            String[] positionalArgs = cmd.getArgs();
            if (positionalArgs.length < 1) {
                throw new ParseException("Input file is required.");
            }

            inputPath = positionalArgs[0];
            if (cmd.hasOption("o")) {
                outputPath = cmd.getOptionValue("o");
            }
            if (cmd.hasOption("i")) {
                instrumentOnly = true;
            }
            if (!Paths.get(inputPath).toFile().exists()
                    || !Paths.get(inputPath).toFile().isDirectory()) {
                throw new ParseException(inputPath + " is not an existing directory.");
            }
            if (!Paths.get(outputPath).toFile().exists()) {
                if (!Paths.get(outputPath).toFile().mkdirs()) {
                    throw new ParseException(outputPath + " is not an existing directory and could not be created.");
                }
            }
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            formatter.printHelp("java -jar <prorunvis.jar> <input_path> [options] \n\nWith options: \n", options);
            System.exit(1);
            return;
        }

        StaticJavaParser.getParserConfiguration().setSymbolResolver(new JavaSymbolSolver(new CombinedTypeSolver()));
        ProjectRoot projectRoot = new SymbolSolverCollectionStrategy()
                .collect(Paths.get(inputPath).toAbsolutePath());

        File traceFile = new File(outputPath + "/Trace.tr");

        List<CompilationUnit> cus = new ArrayList<>();
        projectRoot.getSourceRoots().forEach(sr -> {
            try {
                sr.tryToParse().forEach(cu -> cus.add(cu.getResult().orElseThrow()));
            } catch (IOException | NoSuchElementException e) {
                throw new RuntimeException("Error parsing compilation units: " + e.getMessage(), e);
            }
        });

        Map<Integer, Node> map = new HashMap<>();
        Instrumenter.setupTrace(traceFile);
        cus.forEach(cu -> {
            Preprocessor.run(cu);
            Instrumenter.run(cu, map);
        });
        Instrumenter.saveInstrumented(projectRoot, outputPath + "/instrumented");

        // If not instrument-only, compile, run and process trace
        if (!instrumentOnly) {
            try {
                CompileAndRun.run(cus, outputPath + "/instrumented", outputPath + "/compiled");
                TraceProcessor processor = new TraceProcessor(map, traceFile.getPath(), Paths.get(inputPath));
                processor.start();

                //save json trace to file
                File jsonTrace = new File(outputPath + "/Trace.json");
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(jsonTrace))) {
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    writer.write(gson.toJson(processor.getNodeList()));
                }
            } catch (IOException | InterruptedException e) {
                System.err.println("Error during run or process: " + e.getMessage());
            }
        }

    }
}