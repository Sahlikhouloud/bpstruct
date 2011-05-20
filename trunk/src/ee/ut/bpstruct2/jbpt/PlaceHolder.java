package ee.ut.bpstruct2.jbpt;

import java.util.Collection;
import java.util.Set;

import com.google.gwt.dev.util.collect.HashSet;

import de.hpi.bpt.graph.abs.AbstractDirectedEdge;
import de.hpi.bpt.process.Node;

public class PlaceHolder extends Node {
	Set<AbstractDirectedEdge<Node>> edges;
	Set<Node> vertices;
	Node entry, exit;
	public PlaceHolder(Collection<AbstractDirectedEdge<Node>> edges,
			Collection<Node> vertices, Node entry, Node exit) {
		this.edges = new HashSet<AbstractDirectedEdge<Node>>(edges);
		this.vertices = new HashSet<Node>(vertices);
		this.entry = entry;
		this.exit = exit;
	}
	public Set<AbstractDirectedEdge<Node>> getEdges() {
		return edges;
	}
	public Set<Node> getVertices() {
		return vertices;
	}
	public Node getEntry() {
		return entry;
	}
	public Node getExit() {
		return exit;
	}
}
