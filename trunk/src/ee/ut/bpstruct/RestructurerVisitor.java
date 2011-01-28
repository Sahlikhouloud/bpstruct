/* 
 * Copyright (C) 2010 - Luciano Garcia Banuelos, Artem Polyvyanyy
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
import hub.top.uma.DNode;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.apache.log4j.Logger;

import com.google.gwt.dev.util.collect.HashSet;

import de.bpt.hpi.graph.Edge;
import de.bpt.hpi.graph.Graph;
import ee.ut.bpstruct.Helper.BLOCK_TYPE;
import ee.ut.bpstruct.unfolding.Unfolder;
import ee.ut.bpstruct.unfolding.Unfolding;
import ee.ut.bpstruct.unfolding.UnfoldingHelper;
import ee.ut.bpstruct.unfolding.UnfoldingRestructurer;
import ee.ut.comptech.DJGraph;
import ee.ut.comptech.DJGraphHelper;
import ee.ut.graph.util.GraphUtils;

public class RestructurerVisitor implements Visitor {
	static Logger logger = Logger.getLogger(RestructurerVisitor.class);

	private RestructurerHelper helper;

	// "instances" and "labels" are used for cloning labels in cyclic rigid components
	// Should they be handled elsewere ?
	Map<Integer, Stack<Integer>> instances = new HashMap<Integer, Stack<Integer>>();
	Map<String, Integer> labels = new HashMap<String, Integer>();


	public RestructurerVisitor(RestructurerHelper helper) {
		this.helper = helper;
	}

	/**
	 * This method analyze the flow relation of a given rigid component and
	 * calls the method to handle either cyclic or acyclic components, accordingly.
	 * 
	 * @param graph		The original process graph
	 * @param edges		The subset of edges that conform the rigid component
	 * @param vertices	The subset of vertices that conform the rigid component
	 * @param entry		The entry vertex of the rigid component
	 * @param exit		The exit vertex of the rigid component
	 */
	public void visitRNode(Graph graph, Set<Edge> edges, Set<Integer> vertices,
			Integer entry, Integer exit) throws CannotStructureException {
		if (logger.isInfoEnabled()) logger.info("Rigid component: " + edges);

		// We use a simple DFS method to: a) identify loops (cf. |backedges| > 0),
		// b) characterize logic of the component (cf. xor, and, mixed) 
		DFSLabeler labeler =  new DFSLabeler(helper, GraphUtils.edgelist2adjlist(edges, exit), entry);

		if (labeler.isCyclic())
			restructureCyclicRigid(graph, edges, vertices, entry, exit);
		else
			restructureAcyclicRigid(graph, edges, vertices, entry, exit);

		//helper.foldComponent(graph, edges, vertices, entry, exit, BLOCK_TYPE.RIGID);
	}

	private void restructureAcyclicRigid(Graph graph, Set<Edge> edges,
			Set<Integer> vertices, Integer entry, Integer exit) throws CannotStructureException {
		if (logger.isInfoEnabled()) logger.info("Acyclic case");

		// In the case of Multi-source/Mult-sink fragments, check if the internal logic is homogeneous
		// if it is homogeneous dummy entry/exit nodes are set to the corresponding logic.
		checkHomogeneousLogic(vertices, entry, exit);
		
		// STEP 1: Petrify process component
		Petrifier petrifier = helper.getPetrifier(vertices, edges, entry, exit);
		PetriNet net = petrifier.petrify();


		// STEP 2: Compute Complete Prefix Unfolding
		Unfolder unfolder = new Unfolder(helper, net);
		Unfolding unf = unfolder.perform();

		Map<String, Integer> tasks = new HashMap<String, Integer>();		
		for (Integer vertex: vertices)
			if (helper.gatewayType(vertex) == null)
				tasks.put(graph.getLabel(vertex), vertex);

		// Rewire the unfolding using according to definition ... in new paper
		UnfoldingHelper unfhelper = new UnfoldingHelper(helper, unf);		
		unfhelper.rewire2();

//		try {
//			Graph subgraph = unfhelper2.getGraph();
//			subgraph.serialize2dot("debug/acyclic_rewiredgraph.dot");
//		} catch (FileNotFoundException e) {
//			e.printStackTrace();
//		}
		
		edges.clear(); vertices.clear();
		UnfoldingRestructurer restructurer = new UnfoldingRestructurer(helper, unfhelper, graph, vertices, edges, entry, exit, tasks, labels, instances);
		restructurer.process();
	}

	/**
	 * For acyclic multi-source/multi-sink rigids having homogeneous logic in the internal gateways,
	 * it is worth correcting the logic of the source/sink node (this allow us to avoid the combinatorial problem
	 * associated to the unfolding of multi-source rigids). This method performs a pre-processing analysis
	 * and correct the logic of source/sink node.
	 * 
	 * @param vertices
	 * @param entry
	 * @param exit
	 */
	private void checkHomogeneousLogic(Set<Integer> vertices, Integer entry,
			Integer exit) {
		if (!(helper.isChoice(entry) || helper.isParallel(entry)) ||
				!(helper.isChoice(exit) || helper.isParallel(exit))) {
			Object logic = null;
			boolean parallel = false;
			boolean mixed = false;
			
			for (Integer v: vertices) {
				// skip entry/exit nodes
				if (v.equals(entry) || v.equals(exit)) continue;
				if (helper.gatewayType(v) != null) {
					if (logic == null) {
						logic = helper.gatewayType(v);
						parallel = helper.isParallel(v);
					} else if (logic != helper.gatewayType(v)) {
						mixed = true;
						break;
					}
				}
			}
			
			if (!mixed)
				if (parallel) {
					helper.setANDGateway(entry);
					helper.setANDGateway(exit);
				} else {
					helper.setXORGateway(entry);
					helper.setXORGateway(exit);
				}
		}
	}

	private void restructureCyclicRigid(final Graph graph, final Set<Edge> edges,
			final Set<Integer> vertices, final Integer entry, final Integer exit) throws CannotStructureException {
		if (logger.isInfoEnabled()) logger.info("Cyclic case");

		// STEP 1: Petrify process component
		PetriNet net = helper.getPetrifier(vertices, edges, entry, exit).petrify();

		// STEP 2: Compute Complete Prefix Unfolding
		Unfolder unfolder = new Unfolder(helper, net);
		final Unfolding unf = unfolder.perform();
		
		Map<String, Integer> tasks = new HashMap<String, Integer>();
		for (Integer vertex: vertices)
			if (helper.gatewayType(vertex) == null)
				tasks.put(graph.getLabel(vertex), vertex);

		// Expand cyclic cutoffs
		final UnfoldingHelper unfhelper = new UnfoldingHelper(helper, unf);
		Set<DNode> toExpand = analyzeIterationOne(unf, unfhelper);  // Identify whatever needs to be extended
		unfolder.expand(toExpand, 1);
		
		// Expand cyclic cutoffs of Multi-Entry loops
		toExpand = analyzeIterationTwo(unf, unfhelper); // Identify whatever needs to be extended
		unfolder.expand(toExpand, 2);
		
		// Rewire unfolding
		unfhelper.rewire2();

//		try {
//			Graph subgraph = unfhelper3.getGraph();
//			subgraph.serialize2dot("debug/rewiredgraph3.dot");
//		} catch (FileNotFoundException e) {
//			e.printStackTrace();
//		}
			

		// Restructure the rewired unfolding
		edges.clear(); vertices.clear();
		UnfoldingRestructurer restructurer = new UnfoldingRestructurer(helper, unfhelper, graph, vertices, edges, entry, exit, tasks, labels, instances);
		restructurer.process();
	}

	private Set<DNode> analyzeIterationOne(final Unfolding unf,
			final UnfoldingHelper unfhelper) {
		unfhelper.rewire();

		Graph subgraph = unfhelper.getGraph();

		try {
			subgraph.serialize2dot("debug/rewiredgraph1.dot");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

	
		Integer subentry = subgraph.getSourceNodes().iterator().next();
		Integer subexit = subgraph.getSinkNodes().iterator().next();
		
		Map<Integer, List<Integer>> adjList = GraphUtils.edgelist2adjlist(new HashSet<Edge>(subgraph.getEdges()), subexit);
		adjList.get(subentry).add(subexit);
		
		DJGraph djgraph = new DJGraph(subgraph, adjList, subentry);
		
		final Map<Integer, DNode> candidates = new HashMap<Integer, DNode>();
		final Set<DNode> toExpand = new HashSet<DNode>();
		for (DNode cutoff: unf.getCutoffs()) {
			Integer cutoff_vertex = unfhelper.getVertex(cutoff);			
			candidates.put(cutoff_vertex, cutoff);
		}
		
		djgraph.identifyLoops(new DJGraphHelper() {
			public List<Integer> processSEME(Set<Integer> loopbody) {
				Set<Integer> intersection = new HashSet<Integer>(loopbody);
				intersection.retainAll(candidates.keySet());
				
				for (Integer cutoff_vertex: intersection) {
					DNode cutoff = candidates.get(cutoff_vertex);
					DNode corresponding = unf.getCorr(cutoff);
					Integer corresponding_vertex = unfhelper.getVertex(corresponding);
					
					if (!loopbody.contains(corresponding_vertex)) {
						System.err.printf("Cutoff %s, Corresponding %s", cutoff, corresponding);
						System.err.println("\t\t>>> Needs to be expanded");
						toExpand.add(cutoff);
					}
				}
				
				return null;
			}
			
			public List<Integer> processMEME(Set<Integer> loopbody) {
				Set<Integer> intersection = new HashSet<Integer>(loopbody);
				intersection.retainAll(candidates.keySet());

				for (Integer cutoff_vertex: intersection) {
					DNode cutoff = candidates.get(cutoff_vertex);
					DNode corresponding = unf.getCorr(cutoff);
					Integer corresponding_vertex = unfhelper.getVertex(corresponding);
					
					if (!loopbody.contains(corresponding_vertex)) {
						System.err.printf("Cutoff %s, Corresponding %s", cutoff, corresponding);
						System.err.println("\t\t>>> Needs to be expanded");
						toExpand.add(cutoff);
					}
				}
				
				return null;
			}
		});
		
		return toExpand;
	}

	private Set<DNode> analyzeIterationTwo(final Unfolding unf,
			final UnfoldingHelper unfhelper) {
		unfhelper.rewire();

		final Graph subgraph = unfhelper.getGraph();

		try {
			subgraph.serialize2dot("debug/rewiredgraph2.dot");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		Integer subentry = subgraph.getSourceNodes().iterator().next();
		Integer subexit = subgraph.getSinkNodes().iterator().next();
		
		Map<Integer, List<Integer>> adjList = GraphUtils.edgelist2adjlist(new HashSet<Edge>(subgraph.getEdges()), subexit);
		adjList.get(subentry).add(subexit);
		
		DJGraph djgraph = new DJGraph(subgraph, adjList, subentry);
		
		final Map<Integer, DNode> candidates = new HashMap<Integer, DNode>();
		final Set<DNode> toExpand = new HashSet<DNode>();
		for (DNode cutoff: unf.getCutoffs()) {
			Integer cutoff_vertex = unfhelper.getVertex(cutoff);			
			candidates.put(cutoff_vertex, cutoff);
		}

		djgraph.identifyLoops(new DJGraphHelper() {			
			public List<Integer> processSEME(Set<Integer> loopbody) {
				for (Integer n: loopbody)
					candidates.remove(n);
				return null;
			}
			
			public List<Integer> processMEME(Set<Integer> loopbody) {
				Set<Integer> intersection = new HashSet<Integer>(loopbody);
				intersection.retainAll(candidates.keySet());

				for (Integer cutoff_vertex: intersection) {
					DNode cutoff = candidates.get(cutoff_vertex);
					DNode corresponding = unf.getCorr(cutoff);
					Integer corresponding_vertex = unfhelper.getVertex(corresponding);
					
					if (!loopbody.contains(corresponding_vertex)) {
						System.err.printf("Cutoff %s, Corresponding %s", cutoff, corresponding);
						System.err.println("\t\t>>> Needs to be expanded");
						toExpand.add(cutoff);
					}
				}

				return null;
			}
		});		
		return toExpand;
	}


	// ------------------------------------------------
	// ------------ Structured Components
	// ------------ Only dummy methods
	// ------------------------------------------------

	public void visitSNode(Graph graph, Set<Edge> edges, Set<Integer> vertices,
			Integer entry, Integer exit) {
		helper.foldComponent(graph, edges, vertices, entry, exit, BLOCK_TYPE.POLYGON);		
	}

	public void visitPNode(Graph graph, Set<Edge> edges, Set<Integer> vertices,
			Integer entry, Integer exit) {
		helper.foldComponent(graph, edges, vertices, entry, exit, BLOCK_TYPE.BOND);
	}

	public void visitRootSNode(Graph graph, Set<Edge> edges,
			Set<Integer> vertices, Integer entry, Integer exit) {		
		helper.foldComponent(graph, edges, vertices, entry, exit, BLOCK_TYPE.POLYGON);		
	}
}
