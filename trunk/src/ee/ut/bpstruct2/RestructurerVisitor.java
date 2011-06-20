package ee.ut.bpstruct2;

import hub.top.petrinet.PetriNet;
import hub.top.uma.DNode;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import de.hpi.bpt.hypergraph.abs.Vertex;
import de.hpi.bpt.process.Node;
import de.hpi.bpt.process.Process;
import de.hpi.bpt.process.petri.PNSerializer;
import ee.ut.bpstruct.CannotStructureException;
import ee.ut.bpstruct2.UnfoldingRestructurer;
import ee.ut.bpstruct2.UnfoldingHelper;
import ee.ut.bpstruct2.jbpt.Pair;
import ee.ut.bpstruct2.util.DFSLabeler;
import ee.ut.bpstruct2.util.GraphUtils;

public class RestructurerVisitor implements Visitor {
	
	private Helper helper;

	public RestructurerVisitor(Helper helper) {
		this.helper = helper;
	}
	
	public void visitRigid(Process proc, Set<Pair> edges,
			Set<Node> vertices, Node entry, Node exit) throws CannotStructureException {
		System.out.println("Found a rigid");
		Map<Node, List<Node>> adjlist = GraphUtils.edgelist2adjlist(edges, exit);
		DFSLabeler labeler =  new DFSLabeler(adjlist, entry);

		if (labeler.isCyclic())
			restructureCyclicRigid(proc, edges, vertices, entry, exit);
		else
			restructureAcyclicRigid(proc, edges, vertices, entry, exit, adjlist);
	}

	public void restructureCyclicRigid(Process proc, Set<Pair> edges,
			Set<Node> vertices, Node entry, Node exit) throws CannotStructureException {
		System.out.println("\tCyclic rigid");

		PetriNet net = helper.petrify(edges, vertices, entry, exit);
		Unfolder unfolder = new Unfolder(net);
		Unfolding unf = unfolder.perform();

		try {
			String filename = String.format("bpstruct2/unf_%s.dot", proc.getName());
			PrintStream out = new PrintStream(filename);
			out.print(unf.toDot());
			out.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		final Map<String, Node> tasks = new HashMap<String, Node>();		
		for (Node vertex: vertices)
			if (helper.getLabeledElements().contains(vertex))
				tasks.put(vertex.getName(), vertex);

		final UnfoldingHelper unfhelper = new UnfoldingHelper(unf);

		Set<DNode> properRepCutoffs = new java.util.HashSet<DNode>();
		boolean done = false;
		int iteration = 1;
		
		// PHASE 1 --------------------------------------------------------------------------------------
		// In this phase, we ensure that every reproductive behavior has a heading reproductive cutoff. If
		// the heading cutoff does not have its corresponding in the same local configuration, then it must
		// be expanded.
		// The analysis is made on SCCs. To deal with nesting, we iteratively disconnect outer loops, then
		// process inner loops until fixpoint. At the same time, we check for eventual "jump into a loop"
		// patterns and expand the unfolding so as to eliminate this situation.
		do {
			Set<DNode> toExpand = new HashSet<DNode>();
			done = true;	
			
			// Rewire the unfolding
			// To deal with nesting, proper reproductive cutoffs are not rewired!
			unfhelper.rewire(properRepCutoffs);
			
			// Compute the set of Strongly Connected Components of the rewired unfolding
			de.hpi.bpt.process.petri.PetriNet rewiredUnfGraph = unfhelper.getGraph();
			Set<Set<Vertex>> sccs = GraphUtils.computeSCCs(rewiredUnfGraph);
			
			for (Set<Vertex> scc: sccs) {
				if (scc.size() == 1) continue; // Skip singletons !

				// Reference to the heading reproductive cutoff
				DNode repCutoff = null;

				for (DNode cutoff: unf.getCutoffs()) {
					DNode corr = unf.getCorr(cutoff);
					Vertex _cutoff = unfhelper.getVertex(cutoff);
					Vertex _corr = unfhelper.getVertex(corr);

					// CASE 1 --
					// Cutoffs leading to looping behavior must have its corresponding
					// in the same local configuration.
					// --------------------------------------------------------------------------------
					// Heuristic: Identify cutoff events within the SCC for which the corresponding
					// event is not in the SCC. Check whether both are in the same local configuration.
					// If it is not the case cutoff must be expanded.
					if (scc.contains(_cutoff) && !scc.contains(_corr)) {
						done = false;
						repCutoff = cutoff;
						// Reproducing cutoffs and corresponding must be in the same
						// local configuration
						// NOTE: Corresponding must be just before a branching condition!
						if (!(unfolder.isCorrInLocalConfig(cutoff, corr) &&
								corr.post[0].post.length > 1))
							toExpand.add(cutoff); // Expand if conditions do not hold
						else
							// Mark cutoff as "reproductive cutoff" 
							properRepCutoffs.add(cutoff);
					}
				}

				// Only if repCutoff is not marked to expand by CASE 1 then proceed
				// to analyze CASE 2
				if (repCutoff != null && !toExpand.contains(repCutoff)) {

					// CASE 2 --
					// A Cutoff is a "jump into a loop" if it is not in the SCC
					// but its corresponding event is in the SCC
					for (DNode cutoff: unf.getCutoffs()) {
						DNode corr = unf.getCorr(cutoff);
						Vertex _cutoff = unfhelper.getVertex(cutoff);
						Vertex _corr = unfhelper.getVertex(corr);

						// If cutoff is a "jump into a loop", then the repCutoff heading
						// the SCC has to be expanded.
						if (scc.contains(_corr) && !scc.contains(_cutoff)) {
							done = false;
							properRepCutoffs.remove(repCutoff);
							toExpand.add(repCutoff);
							break;
						}
					}
				}
			}

			if (!toExpand.isEmpty())
				unfolder.expand(toExpand, iteration++);

			// RULE 3 --
			// This loop has to be repeated until every reproductive cutoff is properly contained
			// and no more "jump into a loop" can be detected at the level of SCCs. (Fixpoint)
		} while (!done);
		
		// PHASE 2 -------------------------------------------------------------------------------
		// In the second phase we want to reach a point where every loop is explicitly represented
		// in the unfolding and no Oulsnam's LL looping patterns are present.
		// When a LL pattern is found, we basically apply Oulsnam IL-0 transformation: 
		// the heading reproductive cutoff is expanded.
		//
		// This corresponds to RULE 4
		//
		// HEURISTIC: Within an LL pattern, the heading loop (the one which reaches the entry point)
		// does not reach the exit points. The procedure is as follows:
		// Identify the heading reproductive cutoff
		// For each heading reproductive cutoff
		// If its local configuration contains a corresponding (a kind of incoming edge in LL pattern)
		// it might be the case where that corresponding is involved in a looping behavior. If it is
		// the case, it might also be that this looping behavior can reach the exit point without 
		// reentering the heading loop ... -- I bit difficult to explain ... but it works.
		do {
			done = true;
			Set<DNode> toExpand = new HashSet<DNode>();
			
			// Rewire the unfolding -- no restriction on rewiring
			unfhelper.rewire();
			
			// Compute SCCs over the induced graph
			de.hpi.bpt.process.petri.PetriNet rewiredUnfGraph = unfhelper.getGraph();
			Set<Set<Vertex>> sccs = GraphUtils.computeSCCs(rewiredUnfGraph);
			
			// Analyze one SCC at a time
			for (Set<Vertex> _scc: sccs) {
				if (_scc.size() == 1) continue; // Skip singletons
				
				Set<DNode> exitConds = new HashSet<DNode>();
				Set<DNode> pathsToExit = new HashSet<DNode>();
				// To ease the comparisons, translate "_scc" which is a set of Integers to
				// a set of DNodes (nodes in the Unfolding)
				Set<DNode> scc = new HashSet<DNode>();
				for (Vertex v: _scc) {
					DNode dnode = unfhelper.getDNode((de.hpi.bpt.process.petri.Node) v);
					scc.add(dnode);
				}
				
				// identify exit conditions (SCC exit points)
				for (DNode dnode: scc) {
					if (!dnode.isEvent && dnode.post.length > 1) { // Only branching conditions
						boolean isInternal = true;
						// If one Event in postset of the branching condition is not inside the
						// SCC then the condition is an exit condition
						for (DNode post: dnode.post)
							if (!scc.contains(post)) {
								isInternal = false;
								break;
							}
						
						if (!isInternal) {
							exitConds.add(dnode);
							// Compute the backwards closed set for this condition
							Set<DNode> backclosedset = unf.getBackwardsClosedSet(dnode);

							// Keep only the subset of nodes which are inside the strongly connected component
							backclosedset.retainAll(scc);
							// Compute the aggregated set of nodes which are included in paths leading
							// to the exit points. (Note that we are implicitly discarding some nested loops and
							// some other blocks ... uhm ... TO BE COMPLETED)
							pathsToExit.addAll(backclosedset);
						}
					}
				}
				
				// Identify the set of reproductive cutoffs which reach ten entry point of the SCC
				// also referred to as "heading reproductive cutoffs"
				Set<DNode> repCutoffsReachingSCCEntry = new HashSet<DNode>();
				// We build a reverse map:  Corresponding -> Set<Cutoff>
				Map<DNode, Set<DNode>> corr2cutoff = new HashMap<DNode, Set<DNode>>();
				
				for (DNode cutoff: unf.getCutoffs()) {
					if (!scc.contains(cutoff)) continue; // Only cutoffs within the SCC
					DNode corr = unf.getCorr(cutoff);
					
					// If corresponding is not in SCC, then cutoff is a reproductive cutoff
					// ... at this stage, SCC have only one entry point. Cutoff is then a
					// heading reproductive cutoff
					if (!scc.contains(corr))
						repCutoffsReachingSCCEntry.add(cutoff);
					else {
						// Build the reverse map
						if (!corr2cutoff.containsKey(corr))
							corr2cutoff.put(corr, new HashSet<DNode>());
						corr2cutoff.get(corr).add(cutoff);
					}
				}

				// For each "heading reproductive cutoff"
				for (DNode headingRepCutoff: repCutoffsReachingSCCEntry) {
					
					// Compute the reproductive behavior -covered- by
					// this reproductive cutoff
					Set<DNode> headingLoopingBehavior = unf.getBackwardsClosedSet(headingRepCutoff);
					headingLoopingBehavior.retainAll(scc);
					
					Set<DNode> prunedPathsToExit = null;
					// Look for corresponding events included in the reproductive behavior
					// being currently analyzed
					for (DNode innercorr: corr2cutoff.keySet()) {
						// Is corresponding within reproductive behavior?
						if (headingLoopingBehavior.contains(innercorr)) {
							if (prunedPathsToExit == null) {
								// Compute all possible paths leading to exit points
								// excluding the "heading reproductive behavior" that
								// we are analyzing
								prunedPathsToExit = new HashSet<DNode>(pathsToExit);
								prunedPathsToExit.removeAll(headingLoopingBehavior);
							}

							// Analyze every cutoff associated to corresponding "innercorr"
							for (DNode innercutoff: corr2cutoff.get(innercorr)) {
								Set<DNode> leadingBehavior = unf.getBackwardsClosedSet(innercutoff);
								for (DNode dnode: leadingBehavior)
									// If the behavior covered by "innercutoff" and "innercorr"
									// reach any exit point without going through the heading
									// loop, then ... we have an LL pattern.
									if (prunedPathsToExit.contains(dnode)) {
										// So ... we have to expand heading reproductive cutoff
										toExpand.add(headingRepCutoff);
										break;
									}
							}
						}
					}
				}
			}
			if (!toExpand.isEmpty()) {
				done = false;
				unfolder.expand(toExpand, iteration++);
			}
		} while (!done);

		unfhelper.rewire2();
		
		try {
			String filename = String.format("bpstruct2/rewired_unf_%s.dot", proc.getName());
			PNSerializer.toDOT(filename, unfhelper.getGraph());
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		// Restructure the rewired unfolding
		edges.clear(); vertices.clear();
		new UnfoldingRestructurer(helper, unfhelper, edges, vertices, entry, exit, tasks);
	}

	public void restructureAcyclicRigid(Process proc, Set<Pair> edges,
			Set<Node> vertices, Node entry, Node exit, Map<Node, List<Node>> adjlist) throws CannotStructureException {
		System.out.println("\tAcyclic rigid");
		PetriNet net = helper.petrify(edges, vertices, entry, exit);
		Unfolder unfolder = new Unfolder(net);
		Unfolding unf = unfolder.perform();

		try {
			String filename = String.format("bpstruct2/unf_%s.dot", proc.getName());
			PrintStream out = new PrintStream(filename);
			out.print(unf.toDot());
			out.close();
			
			System.out.print("[");
			boolean firsttime = true;
			for (Entry<DNode, DNode> pair :unf.elementary_ccPair.entrySet()) {
				if (!firsttime) {
					System.out.print(", ");
					firsttime = false;
				}
				System.out.printf("(%s, %s)", pair.getKey(), pair.getValue());
			}
			System.out.println("]");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		final Map<String, Node> tasks = new HashMap<String, Node>();		
		for (Node vertex: vertices)
			if (helper.getLabeledElements().contains(vertex))
				tasks.put(vertex.getName(), vertex);

		helper.synthesizeFromOrderingRelations(proc, edges, vertices, entry, exit,
				unf, tasks);
	}

	public void visitBond(Process proc, Set<Pair> edges,
			Set<Node> vertices, Node entry, Node exit) {
		helper.foldComponent(edges, vertices, entry, exit);
	}

	public void visitPolygon(Process proc, Set<Pair> edges,
			Set<Node> vertices, Node entry, Node exit) {
		helper.foldComponent(edges, vertices, entry, exit);
	}

}
