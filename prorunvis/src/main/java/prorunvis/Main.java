package prorunvis;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.utils.SymbolSolverCollectionStrategy;
import com.github.javaparser.utils.ProjectRoot;
import prorunvis.instrument.Instrumenter;
import prorunvis.preprocess.Preprocessor;
import prorunvis.trace.TraceProcessor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[]args) throws IOException, InterruptedException {

        //check if an argument of sufficient length has been provided
        if(args.length == 0){
            System.out.println("Missing input");
            return;
        }

        if(!Files.isDirectory(Paths.get(args[0]))) {
            System.out.println("Folder not found");
            return;
        }

        StaticJavaParser.getParserConfiguration().setSymbolResolver(new JavaSymbolSolver(new CombinedTypeSolver()));
        ProjectRoot projectRoot = new SymbolSolverCollectionStrategy().collect(Paths.get(args[0]).toAbsolutePath());
        File traceFile = new File("resources/TraceFile.tr");

        Instrumenter.setupTrace(traceFile);

        List<CompilationUnit> cus = new ArrayList<>();
        projectRoot.getSourceRoots().forEach(sr -> {
            try {
                sr.tryToParse().forEach(cu -> cus.add(cu.getResult().get()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        cus.forEach(cu -> {Preprocessor.run(cu); Instrumenter.run(cu);});

        CompileAndRun.run(projectRoot, cus);

        String JSONString = TraceProcessor.toJSON(TraceProcessor.parseToCodeEntries(traceFile));
    }
}
