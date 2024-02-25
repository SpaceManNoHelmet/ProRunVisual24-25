package prorunvis.trace.process;

import com.github.javaparser.Range;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.nodeTypes.NodeWithStatements;
import com.github.javaparser.ast.nodeTypes.NodeWithBody;
import com.github.javaparser.ast.nodeTypes.NodeWithOptionalBlockStmt;
import com.github.javaparser.ast.nodeTypes.NodeWithBlockStmt;
import com.github.javaparser.ast.stmt.*;
import com.google.common.collect.Iterables;
import prorunvis.trace.TraceNode;
import prorunvis.trace.TracedCode;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

/**
 * This class is used to convert a previously generated id-trace
 * to a type of tree representation.
 * The resulting tree consists of {@link TraceNode} objects in a
 * list with index-based references to parents and children.
 */
public class TraceProcessor {

    /**
     * A list containing all the trace nodes in the tree.
     */
    private final List<TraceNode> nodeList;

    /**
     * A map which contains the corresponding {@link Node} of
     * the AST for every trace id.
     */
    private final Map<Integer, Node> traceMap;

    /**
     * The current trace node, serves as a global save state
     * across multiple recursions.
     */
    private TraceNode current;

    /**
     * The corresponding AST node for current.
     */
    private Node nodeOfCurrent;

    /**
     * A scanner object used to convert the trace file
     * into single trace id's.
     */
    private final Scanner scanner;

    /**
     * A stack containing the trace id's in correct
     * order generated by {@link #scanner}.
     */
    private Stack<Integer> tokens;

    /**
     * A list that is used to track ranges of method-calls that
     * have already been added as caller for a trace node, so that
     * multiple calls to a method in the same scope can be correctly
     * associated with the respective call-statement.
     */
    private List<Range> methodCallRanges;

    /**
     * Object which is instantiated when a jump keyword
     * has been found. It contains information about how
     * to set up the corresponding links.
     */
    private JumpPackage jumpPackage;

    /**
     * Constructs a TraceProcessor for the given parameters.
     *
     * @param trace         A map containing all the possible trace-id's
     *                      and their corresponding nodes in the AST.
     * @param traceFilePath A string representation of the path to
     *                      the trace file containing the actual
     *                      recorded trace.
     */
    public TraceProcessor(final Map<Integer, Node> trace, final String traceFilePath) {
        this.nodeList = new LinkedList<>();
        this.traceMap = trace;
        this.scanner = new Scanner(traceFilePath);
        this.methodCallRanges = new ArrayList<>();
    }

    /**
     * Start the processor by creating the token stack and
     * the root for the tree.
     *
     * @throws IOException If the scanner can not open
     *                     or correctly read the trace file.
     */
    public void start() throws IOException {

        //read tokens to stack
        try {
            tokens = scanner.readFile();
        } catch (IOException e) {
            throw new IOException("Could not read trace file.", e);
        }

        createRoot();
    }

    /**
     * Creates the root node for the tree, which has no parent
     * and one guaranteed child-node for the first id in the trace.
     */
    private void createRoot() {
        TraceNode root = new TraceNode(null, "root");
        nodeList.add(root);
        current = root;

        //add the first node as child to root
        createNewTraceNode();
    }

    /**
     * Process a child of the current node by determining if the next
     * code block is a child of current and if yes, create it.
     *
     * @return false if a child was created to indicate that the children
     * for current are not finished, true otherwise.
     */
    private boolean processChild() {

        if (tokens.empty()) {
            return false;
        }

        Node node = traceMap.get(tokens.peek());

        //check if the node is a method declaration or not
        if (node instanceof MethodDeclaration) {
            return createMethodCallTraceNode();
        } else {

            Optional<Range> range = node.getRange();
            Optional<Range> currentRange = nodeOfCurrent.getRange();

            //check if the next traced node is located within the node
            //of current
            if (range.isPresent() && currentRange.isPresent()) {
                if (currentRange.get().strictlyContains(range.get())) {
                    //create the new trace node
                    createNewTraceNode();
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Creates a new TraceNode, which will be added as child to current.
     * At the end of this method, the node including all its children are
     * correctly set up without need for further processing.
     */
    private void createNewTraceNode() {
        //create a new node and remove the token from the stack
        int tokenValue = tokens.pop();
        String name = String.valueOf(tokenValue);
        int parentIndex = nodeList.indexOf(current);
        TraceNode traceNode = new TraceNode(parentIndex, name);

        //add the node to the list and it's index as child of current
        nodeList.add(traceNode);
        current.addChildIndex(nodeList.indexOf(traceNode));

        //save the current state
        current = traceNode;
        Node tempNodeOfCurrent = nodeOfCurrent;
        List<Range> tempRanges = methodCallRanges;
        nodeOfCurrent = traceMap.get(tokenValue);
        methodCallRanges = new ArrayList<>();

        fillRanges((getBlockStmt() == null)
                ? nodeOfCurrent.getChildNodes()
                : getBlockStmt().getChildNodes(), null);

        //if current node is a loop: calculate and set iteration
        if (nodeOfCurrent instanceof NodeWithBody<?>) {
            int iteration = 0;
            for (int i : nodeList.get(current.getParentIndex()).getChildrenIndices()) {
                if (nodeList.get(i).getTraceID().equals(current.getTraceID())) {
                    iteration++;
                }
            }
            current.setIteration(iteration);
        }

        if (jumpPackage != null && jumpPackage.isTarget(nodeOfCurrent)) {
            if (nodeOfCurrent instanceof MethodDeclaration) {
                current.addOutLink(jumpPackage.getJumpFrom());
            }
            jumpPackage = null;
        }

        //if node was a loop, add the executed method calls from inside the loop to the
        //executed calls of the previous node to prevent false positives in the
        //deep search
        if (nodeOfCurrent instanceof NodeWithBody<?>) {
            tempRanges.addAll(methodCallRanges);
        }

        //restore state
        current = nodeList.get(traceNode.getParentIndex());
        nodeOfCurrent = tempNodeOfCurrent;
        methodCallRanges = tempRanges;
    }


    /**
     * Create a new trace node explicitly for a method call. For that the method
     * first checks within what type of node the call is located to get the
     * correct {@link MethodCallExpr} and then creates the node with the correct
     * link and out-link for that expression.
     *
     * @return a boolean to indicate if current may have further children.
     * True if the node was created, false otherwise.
     */
    private boolean createMethodCallTraceNode() {
        MethodDeclaration node = (MethodDeclaration) traceMap.get(tokens.peek());
        SimpleName nameOfDeclaration = node.getName();
        SimpleName nameOfCall;


        List<MethodCallExpr> callExprs = new ArrayList<>();

        //if the current statement is a statement-block, search statements individually for calls
        if (nodeOfCurrent instanceof NodeWithStatements<?> block) {
            for (Statement statement : block.getStatements()) {
                //exempt return statements without expression, break and
                //continue statements from method call deep search
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
                nameOfCall = expr.getName();
                createNewTraceNode();
                //set link, out-link and index of out
                int lastAddedIndex = current.getChildrenIndices()
                        .get(current.getChildrenIndices().size() - 1);
                TraceNode lastAdded = nodeList.get(lastAddedIndex);

                //check if ranges are present, should always be true due to preprocessing
                if (nameOfCall.getRange().isPresent()
                        && nameOfDeclaration.getRange().isPresent()) {
                    lastAdded.setLink(nameOfCall.getRange().get());
                    lastAdded.addOutLink(nameOfDeclaration.getRange().get());
                }
                lastAdded.setOut(lastAdded.getParentIndex());

                return true;
            }
        }
        return false;
    }

    /**
     * Advance through all parsable code of the current node and save ranges
     * which are not turned into their own tracenodes in a list,
     * while creating new child-tracenodes for specific codetypes.
     *
     * @param childrenOfCurrent the list of code blocks in the current node
     * @param nextRangeToIgnore range of the next child tracenode, necessary in order to
     *                          skip it while adding ranges
     */
    @SuppressWarnings("checkstyle:FinalParameters")
    private void fillRanges(final List<Node> childrenOfCurrent, Range nextRangeToIgnore) {

        boolean skipNext = false;

        for (int i = 0; i < childrenOfCurrent.size();) {

            Node currentNode = childrenOfCurrent.get(i);

            //determine the range of the next child
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
                markStatementsInChild(currentNode);
            }

            if (jumpPackage != null && currentNode.getRange().get().contains(nextRangeToIgnore)) {
                if (jumpPackage.getTarget().contains(ThrowStmt.class) && processChild()) {
                    TraceNode tryNode = nodeList.get(current.getChildrenIndices().get(
                            current.getChildrenIndices().size() - 2));
                    nextRangeToIgnore = traceMap.get(Integer.valueOf(nodeList.get(Iterables.getLast(
                            current.getChildrenIndices())).getTraceID())).getRange().get();
                    nodeList.get(jumpPackage.getStart()).addOutLink(jumpPackage.getJumpFrom());
                    nodeList.get(jumpPackage.getStart()).setOut(nodeList.indexOf(tryNode));
                    jumpPackage = null;

                } else {return;}
            }

            //current range is a child, let it resolve and wait for the next child
            if (currentNode.getRange().get().contains(nextRangeToIgnore)) {
                nextRangeToIgnore = null;
                if (!(traceMap.get(
                        Integer.valueOf(nodeList.get(Iterables.getLast(current.getChildrenIndices())).getTraceID()))
                        instanceof MethodDeclaration)) {
                    skipNext = true;
                }
            } else {
                //if the next child lies ahead, advance and save current range in ranges if
                //the skip flag isn't set (i.e. the current range isn't a child)
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

        //if the current node is a forStmt, and it has iteration steps, add them to the ranges
        if (nodeOfCurrent instanceof ForStmt forStmt) {
            for (boolean cont = true; cont;) {
                cont = processChild();
            }
            forStmt.getUpdate().forEach(node -> current.addRange(node.getRange().get()));
        }
    }

    /**
     * private method used by {@link #fillRanges} to determine whether the current statement
     * is a child node in which certain codeblocks are always executed
     * (like the condition in an if statement) in order to mark it.
     *
     * @param currentNode Node currently being analyzed
     */
    private void markStatementsInChild(final Node currentNode) {
        if (currentNode instanceof IfStmt ifStmt) {
            current.addRange(ifStmt.getCondition().getRange().get());
        } else if (currentNode instanceof ForStmt forStmt) {
            List<Node> inits = new ArrayList<>(forStmt.getInitialization());
            inits.forEach(init -> current.addRange(init.getRange().get()));
            if (forStmt.getCompare().isPresent()) {
                current.addRange(forStmt.getCompare().get().getRange().get());
            }
        } else if (currentNode instanceof WhileStmt whileStmt) {
            current.addRange(whileStmt.getCondition().getRange().get());
        } else if (currentNode instanceof ForEachStmt forEachStmt) {
            current.addRange(forEachStmt.getVariable().getRange().get());
            current.addRange(forEachStmt.getIterable().getRange().get());
        } else if (currentNode instanceof DoStmt doStmt) {
            current.addRange(doStmt.getCondition().getRange().get());
        } else if (currentNode instanceof TryStmt tryStmt) {
            tryStmt.getResources().forEach(resource -> current.addRange(resource.getRange().get()));
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
            jumpPackage = new JumpPackage(List.of(ThrowStmt.class),
                                          new Range(throwStmt.getBegin().get(),
                                                    throwStmt.getBegin().get().right("throw".length())),
                                          nodeList.indexOf(current));
            return true;
        }
        return false;
    }

    /**
     * Get the block-statement surrounding a call-expression.
     * Should only be called from {@link #createMethodCallTraceNode()}.
     *
     * @return A {@link BlockStmt} within which the call for the current
     * AST node is located, null if none is found.
     */
    private BlockStmt getBlockStmt() {
        BlockStmt block = null;

        //check if call is within a method
        if (nodeOfCurrent instanceof NodeWithOptionalBlockStmt<?> method) {
            if (method.getBody().isPresent()) {
                block = method.getBody().get();
            }
        }

        //check if call is in a statement, i.e. a then -or else clause
        //or a finally-block
        if (nodeOfCurrent instanceof Statement stmt) {
            if (stmt instanceof BlockStmt b) {
                block = b;
            }
        }

        //check if call is in a loop
        if (nodeOfCurrent instanceof NodeWithBody<?> loop) {
            Statement body = loop.getBody();
            if (body instanceof BlockStmt z) {
                block = z;
            }
        }

        //check if call is in a switch entry
        if (nodeOfCurrent instanceof NodeWithStatements<?> switchCase) {
            block = new BlockStmt();
            NodeList<Statement> statements = switchCase.getStatements();
            block.setStatements(statements);
        }

        //check if call is in a catch clause
        if (nodeOfCurrent instanceof NodeWithBlockStmt<?> catchClause) {
            block = catchClause.getBody();
        }

        return block;
    }

    private boolean isValidCall(final MethodCallExpr callExpr, final SimpleName name) {
        return !methodCallRanges.contains(callExpr.getRange().get())
                && callExpr.getName().equals(name);
    }

    /**
     * Gets the nodes created by this preprocessor.
     *
     * @return A List containing the created {@link TraceNode}
     * objects.
     */
    public List<TraceNode> getNodeList() {
        return this.nodeList;
    }

    /**
     * Convert the node list to a String representation.
     *
     * @return A String containing a representation of
     * each node with the value for every field.
     */
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (TraceNode node : nodeList) {
            nodeToString(builder, node);
            builder.append("\n\n");
        }
        builder.delete(builder.length() - 2, builder.length() - 1);
        return builder.toString();
    }

    /**
     * Converts a given {@link TraceNode} to a string.
     * @param builder used to convert the {@link TraceNode}
     * @param node The {@link TraceNode} to be converted
     */
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
