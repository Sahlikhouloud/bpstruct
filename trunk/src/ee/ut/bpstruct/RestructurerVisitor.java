/* 
 * Copyright (C) 2010 - Luciano Garcia Banuelos
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ee.ut.bpstruct;

import hub.top.petrinet.PetriNet;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.apache.log4j.Logger;

import de.bpt.hpi.graph.Edge;
import de.bpt.hpi.graph.Graph;
import ee.ut.bpstruct.unfolding.Unfolder;
import ee.ut.bpstruct.unfolding.Unfolding;
import ee.ut.bpstruct.unfolding.UnfoldingHelper;
import ee.ut.bpstruct.unfolding.UnfoldingRestructurer;
import ee.ut.graph.moddec.ColoredGraph;
import ee.ut.graph.moddec.ModularDecompositionTree;

public class RestructurerVisitor implements Visitor {
	static Logger logger = Logger.getLogger(RestructurerVisitor.class);

	private RestructurerHelper helper;
	
	// "instances" and "labels" are used for cloning labels in cyclic rigid components
	// Should they be used elsewere ?
	Map<Integer, Stack<Integer>> instances = new HashMap<Integer, Stack<Integer>>();
	Map<String, Integer> labels = new HashMap<String, Integer>();

	
	public RestructurerVisitor(RestructurerHelper helper) {
		this.helper = helper;
	}
	
	public void visitRNode(Graph graph, Set<Edge> edges, Set<Integer> vertices,
			Integer entry, Integer exit) throws CannotStructureException {
		if (logger.isInfoEnabled()) logger.info("Rigid component: " + edges);

		// We use a simple DFS method to: a) identify loops (cf. |backedges| > 0),
		// b) characterize logic of the component (cf. xor, and, mixed) 
		DFSLabeler labeler =  new DFSLabeler(helper, edgelist2adjlist(edges, exit), entry);

		if (labeler.isCyclic())
			restructureCyclicRigid(graph, edges, vertices, entry, exit);
		else
			restructureAcyclicRigid(graph, edges, vertices, entry, exit);	
	}
	
	private void restructureAcyclicRigid(Graph graph, Set<Edge> edges,
			Set<Integer> vertices, Integer entry, Integer exit) throws CannotStructureException {
		if (logger.isInfoEnabled()) logger.info("Acyclic case");
		
		// STEP 1: Petrify process component
		PetriNet net = helper.getPetrifier(vertices, edges, entry, exit).petrify();
		
		// STEP 2: Compute Complete Prefix Unfolding
		Unfolder unfolder = new Unfolder(helper, net);
		Unfolding unf = unfolder.perform();

		Map<String, Integer> tasks = new HashMap<String, Integer>();		
		for (Integer vertex: vertices)
			if (helper.gatewayType(vertex) == null)
				tasks.put(graph.getLabel(vertex), vertex);
		
		edges.clear(); vertices.clear();
		processOrderingRelations(edges, vertices, entry, exit, graph,
				unf, tasks);

		
	}

	private void processOrderingRelations(Set<Edge> edges,
			Set<Integer> vertices, Integer entry, Integer exit, Graph graph,
			Unfolding unf, Map<String, Integer> tasks) throws CannotStructureException {
		// STEP 3: Compute Ordering Relations and Restrict them to observable transitions
		Map<String, Integer> clones = new HashMap<String, Integer>();
		BehavioralProfiler prof = new BehavioralProfiler(unf, tasks, clones);
		ColoredGraph orgraph = prof.getOrderingRelationsGraph();
		ModularDecompositionTree mdec = new ModularDecompositionTree(orgraph);

		if (logger.isDebugEnabled()) {
			if (logger.isTraceEnabled()) {
				logger.trace("------------------------------------");
				logger.trace("ORDERING RELATIONS GRAPH");
				logger.trace("------------------------------------");
				logger.trace("\n" + prof.getOrderingRelationsGraph());
				logger.trace("------------------------------------");
				logger.trace("\n" + prof.serializeOrderRelationMatrix());				
			}
			logger.debug("------------------------------------");
			logger.debug("MODULAR DECOMPOSITION");
			logger.debug("------------------------------------");
			logger.debug(mdec.getRoot());
			logger.debug("------------------------------------");
		}

		for (String label: clones.keySet()) {
			Integer vertex = graph.addVertex(label);
			// Add code to complete the cloning (e.g. when mapping BPMN->BPEL)
			tasks.put(label, vertex);
		}

		// STEP 4: Synthesize structured version from MDT
		helper.synthesizeFromMDT(vertices, edges, entry, exit, mdec, tasks);
	}
	
	private void restructureCyclicRigid(final Graph graph, final Set<Edge> edges,
			final Set<Integer> vertices, final Integer entry, final Integer exit) throws CannotStructureException {
		if (logger.isInfoEnabled()) logger.info("Cyclic case");
		
		// STEP 1: Petrify process component
		PetriNet net = helper.getPetrifier(vertices, edges, entry, exit).petrify();
				
		// STEP 2: Compute Complete Prefix Unfolding
		Unfolder unfolder = new Unfolder(helper, net);
		Unfolding unf = unfolder.perform();
		
		final UnfoldingHelper unfhelper = new UnfoldingHelper(helper, unf);
		unfhelper.rewire();
		
		Graph subgraph = unfhelper.getGraph();
		
		try {
			subgraph.serialize2dot("debug/rewiredgraph.dot");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		Map<String, Integer> tasks = new HashMap<String, Integer>();
		
		for (Integer vertex: vertices)
			if (helper.gatewayType(vertex) == null)
				tasks.put(graph.getLabel(vertex), vertex);

		edges.clear(); vertices.clear();
		UnfoldingRestructurer restructurer = new UnfoldingRestructurer(helper, unfhelper, graph, vertices, edges, entry, exit, tasks, labels, instances);
		restructurer.process(System.out);
	}


	// ------------------------------------------------
	// ------------ Structured Components
	// ------------ Only dummy methods
	// ------------------------------------------------
	public void visitRootSNode(Graph graph, Set<Edge> edges,
			Set<Integer> vertices, Integer entry, Integer exit) {
		// TODO: remove all dummy gateways and self loops
		vertices.clear();
		Set<Edge> toremove = new HashSet<Edge>();
		for (Edge e: edges) {
			if (e.getSource() == null || e.getTarget() == null) continue;
			if (e.getSource().equals(e.getTarget())) toremove.add(e); vertices.add(e.getSource()); vertices.add(e.getTarget()); }
		edges.removeAll(toremove);
	}

	public void visitSNode(Graph graph, Set<Edge> edges, Set<Integer> vertices,
			Integer entry, Integer exit) {}
	
	public void visitPNode(Graph graph, Set<Edge> edges, Set<Integer> vertices,
			Integer entry, Integer exit) {}

	// ------------------------------------------------
	// ------------ Utilities
	// ------------------------------------------------
	
	/**
	 * This method takes a set of edges and builds a adjacency list representation. This is required
	 * by some DFS-based methods (e.g. DFSLabeler). Note that the structure of the graph is modified,
	 * by adding/deleting edges in the set of edges "edges".
	 */
	private Map<Integer, List<Integer>> edgelist2adjlist(Set<Edge> edges,
			Integer exit) {
		Map<Integer, List<Integer>> adjList = new HashMap<Integer, List<Integer>>();
		for (Edge e: edges) {
			List<Integer> list = adjList.get(e.getSource());
			if (list == null) {
				list = new LinkedList<Integer>();
				adjList.put(e.getSource(), list);
			}
			list.add(e.getTarget());
		}
		if (exit != null && adjList.get(exit) == null)
			adjList.put(exit, new LinkedList<Integer>());
		return adjList;
	}
}
