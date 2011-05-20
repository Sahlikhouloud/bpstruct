package ee.ut.bpstruct2.util;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import com.google.gwt.dev.util.collect.HashSet;

import de.bpt.hpi.graph.Graph;
import de.hpi.bpt.graph.abs.AbstractDirectedEdge;
import de.hpi.bpt.process.Node;

public class GraphUtils {
	/**
	 * This method takes a set of edges and builds a adjacency list representation. This is required
	 * by some DFS-based methods (e.g. DFSLabeler). Note that the structure of the graph is modified,
	 * by adding/deleting edges in the set of edges "edges".
	 */
	public static Map<Node, List<Node>> edgelist2adjlist(Set<AbstractDirectedEdge<Node>> edges,
			Node exit) {
		Map<Node, List<Node>> adjList = new HashMap<Node, List<Node>>();
		for (AbstractDirectedEdge<Node> e: edges) {
			List<Node> list = adjList.get(e.getSource());
			if (list == null) {
				list = new LinkedList<Node>();
				adjList.put(e.getSource(), list);
			}
			list.add(e.getTarget());
		}
		if (exit != null && adjList.get(exit) == null)
			adjList.put(exit, new LinkedList<Node>());
		return adjList;
	}

	public static Set<Set<Integer>> computeSCCs(Graph graph) {
		Set<Set<Integer>> scc = new HashSet<Set<Integer>>();
		Stack<Integer> stack = new Stack<Integer>();
		Set<Integer> visited = new HashSet<Integer>();
		for (Integer vertex: graph.getVertices())
			if (!visited.contains(vertex))
				searchForward(graph, vertex, stack, visited);			

		visited.clear();
		while(!stack.isEmpty()) {
			Set<Integer> component = new HashSet<Integer>();
			searchBackward(graph, stack.peek(), visited, component);
			scc.add(component);
			stack.removeAll(component);
		}
		return scc;
	}

	private static void searchBackward(Graph graph, Integer node, Set<Integer> visited, Set<Integer> component) {
		Stack<Integer> worklist = new Stack<Integer>();
		worklist.push(node);
		while (!worklist.isEmpty()) {
			Integer curr = worklist.pop();
			visited.add(curr);
			component.add(curr);
			for (Integer pred: graph.getPredecessorsOfVertex(curr))
				if (!visited.contains(pred) && !worklist.contains(pred))
					worklist.add(pred);
		}
	}

	private static void searchForward(Graph graph, Integer curr, Stack<Integer> stack, Set<Integer> visited) {
		visited.add(curr);
		for (Integer succ: graph.getSuccessorsOfVertex(curr))
			if (!visited.contains(succ))
				searchForward(graph, succ, stack, visited);
		stack.push(curr);
	}
	
	public static void gatherControlFlow(Set<AbstractDirectedEdge<Node>> edges,
			Node entry, Node exit,
			Map<Node, List<AbstractDirectedEdge<Node>>> incoming,
			Map<Node, List<AbstractDirectedEdge<Node>>> outgoing) {
		for (AbstractDirectedEdge<Node> e: edges) {
			List<AbstractDirectedEdge<Node>> in = incoming.get(e.getSource());
			if (in == null) {
				in = new LinkedList<AbstractDirectedEdge<Node>>();
				incoming.put(e.getSource(), in);
			}
			
			List<AbstractDirectedEdge<Node>> out = outgoing.get(e.getTarget());
			if (out == null) {
				out = new LinkedList<AbstractDirectedEdge<Node>>();
				outgoing.put(e.getTarget(), out);
			}			
			in.add(e);
			out.add(e);
		}
		if (entry != null && incoming.get(entry) == null)
			incoming.put(entry, new LinkedList<AbstractDirectedEdge<Node>>());
		if (exit != null && outgoing.get(exit) == null)
			outgoing.put(exit, new LinkedList<AbstractDirectedEdge<Node>>());		
	}


}
