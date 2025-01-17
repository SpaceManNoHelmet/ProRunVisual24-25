package prorunvis.trace;

import com.github.javaparser.Range;
import prorunvis.trace.process.JumpLink;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a node in a trace tree structure, where each node corresponds
 * to an executed code block and its associated information,
 * including executed lines (ranges), child nodes, and navigation links.
 */
public class TraceNode {

    /**
     * A list of {@link Range} objects representing executed segments of code within this node.
     */
    private List<Range> ranges;

    /**
     * A list of indices representing the children of this node.
     * Each index refers to another {@link TraceNode} in the node list.
     */
    private List<Integer> childrenIndices;

    /**
     * The index of the parent node. If this node is the root, parentIndex is null.
     */
    private final Integer parentIndex;

    /**
     * The {@link JumpLink} that serves as the entry link to this node (for clickable navigation).
     */
    private JumpLink link;

    /**
     * A list of {@link JumpLink} objects that serve as exit links from this node (outgoing jumps).
     */
    private List<JumpLink> outLinks;

    /**
     * The index of the target node to jump to after following one of the {@link #outLinks}.
     */
    private int outIndex;

    /**
     * The iteration number if this node represents a loop iteration.
     */
    private Integer iteration;

    /**
     * A unique ID mapping this {@link TraceNode} to a corresponding AST node (JavaParser's Node).
     */
    private String traceId;
    private String uniqueTraceId;

    /**
     * The nodeType, e.g. "Function", "Loop", "Throw", or "Other".
     */
    private String nodeType;

    /**
     * The actual method name if nodeType == "Function".
     * For example, "main" or "snowWhiteMirror".
     */
    private String nodeMethodName; // NEW FIELD

    /**
     * Constructs a new TraceNode with a specified parent and trace ID.
     *
     * @param parentIndex the index of the parent node, or null if this node is the root
     * @param traceId     a unique ID mapping this node to an AST node
     */
    public TraceNode(final Integer parentIndex, final String traceId) {
        this.ranges = new ArrayList<>();
        this.childrenIndices = new ArrayList<>();
        this.outLinks = new ArrayList<>();
        this.parentIndex = parentIndex;
        this.traceId = traceId;
        this.iteration = null;

        // Defaults
        this.nodeType = "Other";
        this.nodeMethodName = null;
    }

    // ----------------- GETTERS/SETTERS FOR NEW FIELDS ---------------------
    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public String getNodeMethodName() {
        return nodeMethodName;
    }

    public void setNodeMethodName(String nodeMethodName) {
        this.nodeMethodName = nodeMethodName;
    }
    // ----------------------------------------------------------------------

    /**
     * Adds a new executed code range to this node.
     */
    public void addRange(final Range range) {
        this.ranges.add(range);
    }

    public List<Range> getRanges() {
        return this.ranges;
    }

    public void setRanges(final List<Range> newRanges) {
        this.ranges = newRanges;
    }

    public void addChildIndex(final int childIndex) {
        this.childrenIndices.add(childIndex);
    }

    public List<Integer> getChildrenIndices() {
        return this.childrenIndices;
    }

    public void setChildrenIndices(final List<Integer> childrenIndices) {
        this.childrenIndices = childrenIndices;
    }

    public Integer getParentIndex() {
        return this.parentIndex;
    }

    public JumpLink getLink() {
        return this.link;
    }

    public void setLink(final JumpLink newLink) {
        this.link = newLink;
    }

    public List<JumpLink> getOutLinks() {
        return this.outLinks;
    }

    public void addOutLink(final JumpLink newOutLink) {
        this.outLinks.add(newOutLink);
    }

    public int getOutIndex() {
        return this.outIndex;
    }

    public void setOut(final int outIndex) {
        this.outIndex = outIndex;
    }

    public String getTraceID() {
        return this.traceId;
    }

    public void setIteration(final Integer iteration) {
        this.iteration = iteration;
    }

    public Integer getIteration() {
        return iteration;
    }
    public String getUniqueTraceId() {
        return uniqueTraceId;
    }
    public void setUniqueTraceId(String value) {
        this.uniqueTraceId = value;
    }

}
