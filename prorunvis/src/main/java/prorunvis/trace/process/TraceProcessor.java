package prorunvis.trace.process;

import com.github.javaparser.Range;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.nodeTypes.*;
import com.github.javaparser.ast.stmt.*;
import com.google.common.collect.Iterables;
import prorunvis.trace.TraceNode;
import prorunvis.trace.TracedCode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Converts a previously generated id-trace into a tree of TraceNodes.
 */
public class TraceProcessor {

    private final List<TraceNode> nodeList;
    private final Map<Integer, Node> traceMap;
    private TraceNode current;
    private Node nodeOfCurrent;
    private final Scanner scanner;
    private Stack<Integer> tokens;
    private List<Range> methodCallRanges;
    private JumpPackage jumpPackage;
    private final Path rootDir;

    public TraceProcessor(final Map<Integer, Node> trace, final String traceFilePath, final Path rootDir) {
        this.nodeList = new LinkedList<>();
        this.traceMap = trace;
        this.scanner = new Scanner(traceFilePath);
        this.methodCallRanges = new ArrayList<>();
        this.rootDir = rootDir.toAbsolutePath();
    }

    public void start() throws IOException {
        try {
            tokens = scanner.readFile();
        } catch (IOException e) {
            throw new IOException("Could not read trace file.", e);
        }
        createRoot();
    }

    private void createRoot() {
        TraceNode root = new TraceNode(null, "root");
        nodeList.add(root);
        current = root;

        // Add first node (main) as a child
        createNewTraceNode();

        // Set a default link for the main method
        TraceNode main = nodeList.get(current.getChildrenIndices().get(0));
        Node mainNode = traceMap.get(Integer.parseInt(main.getTraceID()));
        Path path = mainNode.findCompilationUnit().get().getStorage().get().getPath();
        String file = rootDir.relativize(path).toString();
        Range range = ((MethodDeclaration) mainNode).getName().getRange().get();
        JumpLink link = new JumpLink(range, file);
        main.setLink(link);
    }

    private boolean processChild() {
        if (tokens.empty()) {
            return false;
        }

        Node node = traceMap.get(tokens.peek());
        if (node instanceof MethodDeclaration) {
            return createMethodCallTraceNode();
        } else {
            Optional<Range> range = node.getRange();
            Optional<Range> currentRange = nodeOfCurrent.getRange();
            if (range.isPresent() && currentRange.isPresent()
                    && node.findCompilationUnit().get().getStorage().get().getFileName().equals(
                    nodeOfCurrent.findCompilationUnit().get().getStorage().get().getFileName())) {
                if (currentRange.get().strictlyContains(range.get())) {
                    createNewTraceNode();
                    return true;
                }
            }
        }
        return false;
    }

    private void createNewTraceNode() {
        int tokenValue = tokens.pop();
        String traceID = String.valueOf(tokenValue);
        int parentIndex = nodeList.indexOf(current);
        TraceNode traceNode = new TraceNode(parentIndex, traceID);

        nodeList.add(traceNode);
        current.addChildIndex(nodeList.indexOf(traceNode));

        TraceNode savedCurrent = current;
        Node savedNodeOfCurrent = nodeOfCurrent;
        List<Range> savedMethodCallRanges = methodCallRanges;

        current = traceNode;
        nodeOfCurrent = traceMap.get(tokenValue);
        methodCallRanges = new ArrayList<>();

        // ---------- NEW: Classify node and set method name if it's a method ----------
        if (nodeOfCurrent instanceof MethodDeclaration methodDecl) {
            traceNode.setNodeType("Function");
            // get the real name from the source code (e.g. "main", "snowWhiteMirror", etc.)
            String signature = methodDecl.getSignature().asString();
// signature might look like: "snowWhiteMirror(String[], int[], int)"

            traceNode.setNodeMethodName(signature);
        } else if (nodeOfCurrent instanceof ForStmt
                || nodeOfCurrent instanceof WhileStmt
                || nodeOfCurrent instanceof DoStmt
                || nodeOfCurrent instanceof ForEachStmt) {
            traceNode.setNodeType("Loop");
        } else if (nodeOfCurrent instanceof ThrowStmt) {
            traceNode.setNodeType("Throw");
        } else {
            traceNode.setNodeType("Other");
        }
        // ----------

        fillRanges((getBlockStmt() == null)
                ? nodeOfCurrent.getChildNodes()
                : getBlockStmt().getChildNodes(), null);

        // If node is a loop, set iteration
        if (nodeOfCurrent instanceof NodeWithBody<?>) {
            int iteration = 0;
            for (int i : nodeList.get(current.getParentIndex()).getChildrenIndices()) {
                if (nodeList.get(i).getTraceID().equals(current.getTraceID())) {
                    iteration++;
                }
            }
            current.setIteration(iteration);
        }
        // ------ NEW: set uniqueTraceId that merges base traceId + iteration (if iteration != 0) ------
        if (current.getIteration() != null && current.getIteration() > 0) {
            // e.g.  traceId="3", iteration=2  =>  uniqueTraceId="3_iter2"
            String combined = current.getTraceID() + "_iter" + current.getIteration();
            current.setUniqueTraceId(combined);
        } else {
            // If no iteration, just use the base traceId
            current.setUniqueTraceId(current.getTraceID());
        }
        //
        // If jumpPackage is set and target is reached
        if (jumpPackage != null && jumpPackage.isTarget(nodeOfCurrent)) {
            Path targetPath = nodeOfCurrent.findCompilationUnit().get()
                    .getStorage().get().getPath();
            targetPath = rootDir.relativize(targetPath);
            JumpLink outLink = new JumpLink(jumpPackage.getJumpFrom(), targetPath.toString());

            if (nodeOfCurrent instanceof MethodDeclaration) {
                current.addOutLink(outLink);
            }
            if (nodeOfCurrent instanceof TryStmt) {
                if (!tokens.empty()
                        && nodeOfCurrent.getRange().get().contains(traceMap.get(tokens.peek()).getRange().get())) {
                    nodeList.get(jumpPackage.getStart()).addOutLink(outLink);
                    nodeList.get(jumpPackage.getStart()).setOut(nodeList.size());
                    jumpPackage = null;
                }
            } else {
                jumpPackage = null;
            }
        }

        // If node is a loop or function, set link if not already set
        if (nodeOfCurrent instanceof NodeWithBody<?>) {
            savedMethodCallRanges.addAll(methodCallRanges);
            String loopLink;
            if (nodeOfCurrent instanceof WhileStmt) loopLink = "while";
            else if (nodeOfCurrent instanceof DoStmt) loopLink = "do";
            else loopLink = "for";

            Range linkRange = new Range(nodeOfCurrent.getBegin().get(),
                    nodeOfCurrent.getBegin().get().right(loopLink.length() - 1));
            JumpLink link = new JumpLink(linkRange, null);
            current.setLink(link);
        }

        // If nodeOfCurrent is a MethodDeclaration and no link set, set link based on method name
        if (nodeOfCurrent instanceof MethodDeclaration methodDecl) {
            Path path = methodDecl.findCompilationUnit().get().getStorage().get().getPath();
            String relativeFile = rootDir.relativize(path).toString();
            Range methodNameRange = methodDecl.getName().getRange().orElse(null);
            if (methodNameRange != null && current.getLink() == null) {
                JumpLink funcLink = new JumpLink(methodNameRange, relativeFile);
                current.setLink(funcLink);
            }
        }

        current = nodeList.get(traceNode.getParentIndex());
        nodeOfCurrent = savedNodeOfCurrent;
        methodCallRanges = savedMethodCallRanges;
    }

    private boolean createMethodCallTraceNode() {
        MethodDeclaration node = (MethodDeclaration) traceMap.get(tokens.peek());
        SimpleName nameOfDeclaration = node.getName();

        List<MethodCallExpr> callExprs = new ArrayList<>();

        if (nodeOfCurrent instanceof NodeWithStatements<?> block) {
            for (Statement statement : block.getStatements()) {
                if (!(statement instanceof ReturnStmt ret && ret.getExpression().isEmpty())
                        && !(statement instanceof BreakStmt)
                        && !(statement instanceof ContinueStmt)) {
                    List<MethodCallExpr> foundCalls = statement.findAll(MethodCallExpr.class,
                            Node.TreeTraversal.POSTORDER);
                    if (!foundCalls.isEmpty()) {
                        callExprs.addAll(foundCalls);
                    }
                }
            }
        } else {
            callExprs = nodeOfCurrent.findAll(MethodCallExpr.class, Node.TreeTraversal.POSTORDER);
        }

        for (MethodCallExpr expr : callExprs) {
            if (isValidCall(expr, nameOfDeclaration)) {
                methodCallRanges.add(expr.getRange().get());
                createNewTraceNode();

                int lastAddedIndex = current.getChildrenIndices()
                        .get(current.getChildrenIndices().size() - 1);
                TraceNode lastAdded = nodeList.get(lastAddedIndex);

                SimpleName nameOfCall = expr.getName();
                if (nameOfCall.getRange().isPresent()
                        && nameOfDeclaration.getRange().isPresent()) {

                    Path targetPath = traceMap.get(Integer.valueOf(lastAdded.getTraceID()))
                            .findCompilationUnit().get().getStorage().get().getPath();
                    targetPath = rootDir.relativize(targetPath);
                    JumpLink link = new JumpLink(nameOfCall.getRange().get(), targetPath.toString());

                    Path sourcePath = traceMap.get(Integer.valueOf(nodeList.get(lastAdded.getParentIndex())
                                    .getTraceID()))
                            .findCompilationUnit().get().getStorage().get().getPath();
                    sourcePath = rootDir.relativize(sourcePath);
                    JumpLink outLink = new JumpLink(nameOfDeclaration.getRange().get(), sourcePath.toString());

                    lastAdded.setLink(link);
                    lastAdded.addOutLink(outLink);
                }
                lastAdded.setOut(lastAdded.getParentIndex());
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("checkstyle:FinalParameters")
    private void fillRanges(final List<Node> childrenOfCurrent, Range nextRangeToIgnore) {
        boolean skipNext = false;

        for (int i = 0; i < childrenOfCurrent.size();) {
            Node currentNode = childrenOfCurrent.get(i);

            if (nextRangeToIgnore == null) {
                if (processChild()) {
                    TraceNode nextChild = nodeList.get(Iterables.getLast(current.getChildrenIndices()));
                    nextRangeToIgnore =
                            (traceMap.get(Integer.parseInt(nextChild.getTraceID())) instanceof MethodDeclaration)
                                    ? nextChild.getLink()
                                    : traceMap.get(Integer.parseInt(nextChild.getTraceID())).getRange().get();
                } else {
                    nextRangeToIgnore = new Range(nodeOfCurrent.getRange().get().end.nextLine(),
                            nodeOfCurrent.getRange().get().end.nextLine());
                }
            }

            if (!skipNext) {
                markStatementsInChild(currentNode, nextRangeToIgnore);
            }

            if (currentNode.getRange().get().contains(nextRangeToIgnore)) {
                nextRangeToIgnore = null;
                if ((traceMap.get(
                        Integer.valueOf(nodeList.get(Iterables.getLast(current.getChildrenIndices())).getTraceID()))
                        instanceof MethodDeclaration)
                        && !current.getRanges().contains(currentNode.getRange().get())) {
                    current.addRange(currentNode.getRange().get());
                }
                if (jumpPackage != null) {
                    return;
                }
                skipNext = true;
            } else {
                if (skipNext) {
                    skipNext = false;
                } else {
                    if (!current.getRanges().contains(currentNode.getRange().get())
                            && !Stream.of(TracedCode.values()).map(TracedCode::getType)
                            .toList().contains(currentNode.getClass())) {
                        current.addRange(currentNode.getRange().get());

                        if (checkForJumpOut(currentNode)) {
                            return;
                        }
                    }
                }
                i++;
            }
        }

        if (nodeOfCurrent instanceof ForStmt forStmt) {
            for (boolean cont = true; cont;) {
                cont = processChild();
            }
            forStmt.getUpdate().forEach(node -> current.addRange(node.getRange().get()));
        }
    }

    private void markStatementsInChild(final Node currentNode, final Range ifCheck) {
        if (currentNode instanceof IfStmt ifStmt) {
            current.addRange(ifStmt.getCondition().getRange().get());
            if (ifStmt.getRange().get().contains(ifCheck)) {
                while (ifStmt.getElseStmt().isPresent() && ifStmt.getElseStmt().get().isIfStmt()
                        && !ifStmt.getElseStmt().get().asIfStmt().getThenStmt().getRange().get().isAfter(ifCheck)) {
                    ifStmt = ifStmt.getElseStmt().get().asIfStmt();
                    if (ifStmt.getCondition().getRange().isPresent()) {
                        current.addRange(ifStmt.getCondition().getRange().get());
                    }
                }
            }
        } else if (currentNode instanceof ForStmt forStmt) {
            List<Node> inits = new ArrayList<>(forStmt.getInitialization());
            inits.forEach(init -> current.addRange(init.getRange().get()));
            forStmt.getCompare().ifPresent(c -> current.addRange(c.getRange().get()));
        } else if (currentNode instanceof WhileStmt whileStmt) {
            current.addRange(whileStmt.getCondition().getRange().get());
        } else if (currentNode instanceof ForEachStmt forEachStmt) {
            current.addRange(forEachStmt.getVariable().getRange().get());
            current.addRange(forEachStmt.getIterable().getRange().get());
        } else if (currentNode instanceof DoStmt doStmt) {
            current.addRange(doStmt.getCondition().getRange().get());
        } else if (currentNode instanceof TryStmt tryStmt) {
            tryStmt.getResources().forEach(resource -> current.addRange(resource.getRange().get()));
        } else if (currentNode instanceof SwitchStmt switchStmt) {
            current.addRange(switchStmt.getSelector().getRange().get());
        }
    }

    private boolean checkForJumpOut(final Node currentNode) {
        if (currentNode instanceof ReturnStmt returnStmt) {
            jumpPackage = new JumpPackage(List.of(MethodDeclaration.class),
                    new Range(returnStmt.getBegin().get(),
                            returnStmt.getBegin().get().right("return".length())),
                    nodeList.indexOf(current));
            return true;
        } else if (currentNode instanceof ContinueStmt continueStmt) {
            jumpPackage = new JumpPackage(List.of(ForStmt.class, WhileStmt.class,
                    DoStmt.class, ForEachStmt.class),
                    continueStmt.getRange().get(),
                    nodeList.indexOf(current));
            return true;
        } else if (currentNode instanceof BreakStmt breakStmt) {
            jumpPackage = new JumpPackage(List.of(ForStmt.class, WhileStmt.class,
                    DoStmt.class, ForEachStmt.class, SwitchEntry.class),
                    breakStmt.getRange().get(),
                    nodeList.indexOf(current));
            return true;
        } else if (currentNode instanceof ThrowStmt throwStmt) {
            jumpPackage = new JumpPackage(List.of(TryStmt.class),
                    new Range(throwStmt.getBegin().get(),
                            throwStmt.getBegin().get().right("throw".length())),
                    nodeList.indexOf(current));
            return true;
        }
        return false;
    }

    private BlockStmt getBlockStmt() {
        BlockStmt block = null;
        if (nodeOfCurrent instanceof NodeWithOptionalBlockStmt<?> method) {
            if (method.getBody().isPresent()) {
                block = method.getBody().get();
            }
        }

        if (nodeOfCurrent instanceof Statement stmt) {
            if (stmt instanceof BlockStmt b) {
                block = b;
            }
        }

        if (nodeOfCurrent instanceof NodeWithBody<?> loop) {
            Statement body = loop.getBody();
            if (body instanceof BlockStmt z) {
                block = z;
            }
        }

        if (nodeOfCurrent instanceof NodeWithStatements<?> switchCase) {
            block = new BlockStmt();
            NodeList<Statement> statements = switchCase.getStatements();
            block.setStatements(statements);
        }

        if (nodeOfCurrent instanceof NodeWithBlockStmt<?> catchClause) {
            block = catchClause.getBody();
        }

        if (nodeOfCurrent instanceof TryStmt tryStmt) {
            block = tryStmt.getTryBlock();
        }

        return block;
    }

    private boolean isValidCall(final MethodCallExpr callExpr, final SimpleName name) {
        return !methodCallRanges.contains(callExpr.getRange().get())
                && callExpr.getName().equals(name);
    }

    public List<TraceNode> getNodeList() {
        return this.nodeList;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (TraceNode node : nodeList) {
            nodeToString(builder, node);
            builder.append("\n\n");
        }
        builder.delete(builder.length() - 2, builder.length() - 1);
        return builder.toString();
    }

    private void nodeToString(final StringBuilder builder, final TraceNode node) {
        builder.append("TraceID: ").append(node.getTraceID())
                .append("\nChildren: ").append(node.getChildrenIndices())
                .append("\nRanges: ").append(node.getRanges())
                .append("\nLink: ").append(node.getLink())
                .append("\nOutlink: ").append(node.getOutLinks())
                .append("\nOut: ").append(node.getOutIndex())
                .append("\nParent: ").append(node.getParentIndex())
                .append("\nIteration: ").append(node.getIteration());
    }
}