/* 
 * Copyright (C) 2011 - Luciano Garcia Banuelos, Artem Polyvyanyy, Dirk Fahland
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
package ee.ut.bpstruct2;

import hub.top.petrinet.PetriNet;
import hub.top.petrinet.PetriNetIO;
import hub.top.petrinet.Place;
import hub.top.petrinet.Transition;
import hub.top.petrinet.unfold.DNodeSys_OccurrenceNet;
import hub.top.uma.DNode;
import hub.top.uma.DNodeBP;
import hub.top.uma.DNodeSet.DNodeSetElement;
import hub.top.uma.InvalidModelException;
import hub.top.uma.Uma;
import hub.top.uma.synthesis.NetSynthesis;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.stixar.graph.BasicDigraph;
import net.stixar.graph.MutableDigraph;
import net.stixar.graph.Node;
import net.stixar.graph.attr.ByteNodeMatrix;
import net.stixar.graph.conn.Transitivity;
import net.stixar.graph.order.NodeOrder;
import net.stixar.graph.order.TopSorter;
import net.stixar.util.CList;

import de.hpi.bpt.process.Gateway;
import de.hpi.bpt.process.GatewayType;
import de.hpi.bpt.process.Process;
import de.hpi.bpt.process.serialize.Process2DOT;
import ee.ut.bpstruct2.eventstruct.RestrictedFlowEventStructure;
import ee.ut.graph.moddec.ColoredGraph;

public class MaxStr {

	class Pair {
		Integer first;
		BitSet second;

		public Pair(Integer f, BitSet s) {
			first = f;
			second = s;
		}

		public Integer getFirst() {
			return first;
		}

		public BitSet getSecond() {
			return second;
		}

		public void setFirst(Integer val) {
			first = val;
		}

		public void setSecond(BitSet val) {
			second = val;
		}

		public String toString() {
			return String.format("(%s,%s)", first, second);
		}

		public boolean equals(Object obj) {
			if (obj == null)
				return false;
			if (!(obj instanceof Pair))
				return false;
			Pair that = (Pair) obj;

			return this.first.equals(that.first)
					&& this.second.equals(that.second);
		}

		public int hashCode() {
			return (first == null ? 0 : first.hashCode())
					+ (second == null ? 0 : second.hashCode() * 37);
		}

		public Object clone() {
			return new Pair(first, (BitSet) second.clone());
		}
	}

	public MaxStr() {
	}

	public String getModelName() {
		return "model";
	}

	/**
	 * MAIN METHOD ---
	 * 
	 * @param graph
	 * @param edges
	 * @param vertices
	 * @param entry
	 * @param exit
	 */
	public void perform(ColoredGraph orgraph,
			Map<String, de.hpi.bpt.process.Node> tasks,
			Map<String, de.hpi.bpt.process.Node> clones, Process proc, ee.ut.bpstruct2.jbpt.Pair pair) {

		Map<String, String> labelMap = new HashMap<String, String>();
		RestrictedFlowEventStructure fes = new RestrictedFlowEventStructure(
				orgraph);
		ColoredGraph primeEventStructure = fes.computePrimeEventStructure(
				labelMap, getModelName());

		PetriNet folded = synthesize(primeEventStructure, labelMap, tasks,
				clones);

		try {

			try {
				PetriNetIO.writeToFile(folded, "bpstruct2/folded_"
						+ getModelName() + ".lola", PetriNetIO.FORMAT_LOLA, 0);
			} catch (IOException e) {

			}

			PrintStream out = new PrintStream(String.format(
					"bpstruct2/folded_%s.dot", getModelName()));
			out.println(folded.toDot());
			out.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		synthesizePM(folded, tasks, proc, pair);
		
		try {
			PrintStream out = new PrintStream(String.format(
					"bpstruct2/synt_%s.dot", getModelName()));
			out.println(Process2DOT.convert(proc));
			out.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	private void synthesizePM(PetriNet folded,
			Map<String, de.hpi.bpt.process.Node> tasks, Process proc, ee.ut.bpstruct2.jbpt.Pair pair) {
		Map<Place, Gateway> places = new HashMap<Place, Gateway>();
		for (Transition trans : folded.getTransitions()) {
			de.hpi.bpt.process.Node vertexInFlow, vertexOutFlow;
			if (tasks.containsKey(trans.getName())) {
				de.hpi.bpt.process.Node vertex = vertexInFlow = vertexOutFlow = tasks
						.get(trans.getName());
				proc.addVertex(vertex);
				if (trans.getPreSet().size() > 1) {
					Gateway gw = new Gateway(GatewayType.AND);
					proc.addGateway(gw);
					proc.addControlFlow(gw, vertex);
					vertexInFlow = gw;
				}
				if (trans.getPostSet().size() > 1) {
					Gateway gw = new Gateway(GatewayType.AND);
					proc.addGateway(gw);
					proc.addControlFlow(vertex, gw);
					vertexOutFlow = gw;
				}
			} else {
				Gateway gw0 = new Gateway(GatewayType.AND);
				proc.addGateway(gw0);
				vertexInFlow = vertexOutFlow = gw0;
				if (trans.getPreSet().size() > 1
						&& trans.getPostSet().size() > 1) {
					Gateway gw = new Gateway(GatewayType.AND);
					proc.addGateway(gw);
					proc.addControlFlow(vertexInFlow, gw);
					vertexOutFlow = gw;
				}
			}
			for (Place place : trans.getPreSet()) {
				Gateway gw = places.get(place);
				if (gw == null) {
					gw = new Gateway(GatewayType.XOR);
					proc.addGateway(gw);
					places.put(place, gw);
				}
				proc.addControlFlow(gw, vertexInFlow);
				
				if (place.getPreSet().size() == 0)
					pair.setFirst(gw);
			}
			for (Place place: trans.getPostSet()) {
				Gateway gw = places.get(place);
				if (gw == null) {
					gw = new Gateway(GatewayType.XOR);
					proc.addGateway(gw);
					places.put(place, gw);
				}
				proc.addControlFlow(vertexOutFlow, gw);
				if (place.getPostSet().size() == 0)
					pair.setSecond(gw);
			}

		}
	}

	private PetriNet synthesize(ColoredGraph primeEventStructure,
			Map<String, String> labelMap,
			Map<String, de.hpi.bpt.process.Node> tasks,
			Map<String, de.hpi.bpt.process.Node> clones) {
		PetriNet pnet = new PetriNet();

		Map<Integer, Set<Integer>> reducedFlow = new HashMap<Integer, Set<Integer>>();
		Map<Integer, Set<Integer>> reducedFlow_endEvent = new HashMap<Integer, Set<Integer>>();
		Set<Integer> sources = new HashSet<Integer>();
		Set<Integer> sinks = new HashSet<Integer>();

		separateColors(primeEventStructure, reducedFlow, reducedFlow_endEvent,
				sources, sinks, labelMap, tasks, clones);

		// ---------------------------------------------------------
		// 1. Initialize E
		// ---------------------------------------------------------
		Map<Integer, Transition> E = new LinkedHashMap<Integer, Transition>();
		for (Integer n : primeEventStructure.vertices) {
			String transitionLabel = getOriginalLabel(labelMap
					.get(primeEventStructure.getLabel(n)), tasks, clones);
			E.put(n, pnet.addTransition(transitionLabel));

			System.out.println(n + " ---> " + transitionLabel);
		}

		// ---------------------------------------------------------
		// 2. COMPUTE CE
		// ---------------------------------------------------------
		Set<BitSet> CE = new HashSet<BitSet>();
		List<BitSet> heap = new ArrayList<BitSet>();
		for (Integer e : E.keySet()) { // add singletons
			BitSet singleton = new BitSet();
			singleton.set(e);
			CE.add(singleton);
			heap.add(singleton);
		}
		while (heap.size() > 0) {
			BitSet curr = heap.remove(0);
			for (Integer e : E.keySet()) {
				if (!curr.get(e)) {
					boolean flag = true;
					for (int e2 = curr.nextSetBit(0); e2 >= 0; e2 = curr
							.nextSetBit(e2 + 1)) {
						if (!(primeEventStructure.hasEdge(e, e2) && primeEventStructure
								.hasEdge(e2, e))) // not in conflict ?
							flag = false;
						if (!flag)
							break;
					}
					if (flag) {
						BitSet x = (BitSet) curr.clone();
						x.set(e);
						CE.add(x);
						heap.add(x);
					}
				}
			}
		}

		System.out.println(reducedFlow);
		System.out.println(reducedFlow_endEvent);

		Set<Place> implicitPlaces = new HashSet<Place>();

		// ---------------------------------------------------------
		// 3. COMPUTE B
		// ---------------------------------------------------------
		Map<Pair, Place> B = new LinkedHashMap<Pair, Place>();
		Map<Place, Pair> B2 = new LinkedHashMap<Place, Pair>();
		for (Integer e : E.keySet()) {
			for (BitSet ce : CE) {

				boolean flag = true;
				boolean flagEnd = false;
				for (Integer e2 = ce.nextSetBit(0); e2 >= 0; e2 = ce
						.nextSetBit(e2 + 1)) {
					if (!reducedFlow.get(e).contains(e2)
							&& !reducedFlow_endEvent.get(e).contains(e2))
						flag = false;

					if (reducedFlow_endEvent.get(e).contains(e2))
						flagEnd = true;

					if (!flag)
						break;
				}

				if (flag) {
					Pair pair = new Pair(e, ce);

					String srcTransitionLabel = getOriginalLabel(labelMap
							.get(primeEventStructure.getLabel(e)), tasks,
							clones);

					// translate the IDs of the defining events to the labels
					// so that conditions are consistently labeled throughout
					// the occurrence net
					// then we can identify equivalent conditions based on their
					// labels
					String placeLabel = "(" + srcTransitionLabel;

					int successors = 0;
					// String placeLabel = "(";
					for (int i = 0; i < ce.length(); i++) {
						if (ce.get(i)) {
							String tgtTransitionLabel = getOriginalLabel(
									labelMap.get(primeEventStructure
											.getLabel(i)), tasks, clones);
							placeLabel += "," + tgtTransitionLabel;
							successors++;
						}
					}

					placeLabel += ")";

					if (!flagEnd || successors == 1) {

						Place place = pnet.addPlace(placeLabel);
						B.put(pair, place);
						B2.put(place, pair);
						System.out.printf("<%s, %s>\n", e, ce);

						if (flagEnd) {
							implicitPlaces.add(place);
							System.out.printf("implicit " + placeLabel + "\n");
						}
					}
				}
			}
		}

		// ---------------------------------------------------------
		// 4. COMPUTE F
		// ---------------------------------------------------------
		for (Pair pair : B.keySet()) {
			Place place = B.get(pair);
			Integer e = pair.getFirst();
			BitSet x = pair.getSecond();
			for (Integer e2 = x.nextSetBit(0); e2 >= 0; e2 = x
					.nextSetBit(e2 + 1)) {
				Transition trans = E.get(e2);
				pnet.addArc(place, trans);
			}
			Transition trans = E.get(e);
			pnet.addArc(trans, place);
		}

		// ---------------------------------------------------------
		// 5. CLEAN THE NET
		// ---------------------------------------------------------

		// --- Remove implicit places
		Set<Place> toRemove = new HashSet<Place>();
		for (Transition src : pnet.getTransitions())
			if (src.getPostSet().size() > 1)
				for (Place p1 : src.getPostSet())
					for (Place p2 : src.getPostSet()) {
						if (!p1.equals(p2)
								&& p1.getPostSet().containsAll(p2.getPostSet()))
							toRemove.add(p2);

						// remove implicit post-places with the same label:
						// these are part of the
						// occurrence net because we preserve implicit places
						// that are pre-places
						// of terminal transitions. yet, two post-places of a
						// transitions with the
						// same label encode a conflict in a spurious way and
						// would prevent a proper
						// folding, so they are removed here
						if (p1 != p2 && implicitPlaces.contains(p1)
								&& implicitPlaces.contains(p2)
								&& p1.getName().equals(p2.getName())) {
							toRemove.add(p1);
							toRemove.add(p2);
						}
					}

		for (Place place : toRemove) {
			System.out.println("removing implict " + place);
			pnet.removePlace(place);
		}

		// --- Remove transitive conflicts
		// A condition b is a transitive conflict between two events e_1 and
		// e_2, iff there exist events f_1 < e_1 and f_2 < e_2 that are in
		// conflict.
		// A condition b is a transitive conflict, iff it is a transitive
		// conflict between e_1 and e_2 for any two different post-events e_1,
		// e_2 of b.
		// !!! And there exists a path from pre-event of b to every post event
		// of b which does not contain b (causality is preserved)
		// !!! In free-choice nets can be checked directly on postsets of
		// conflict conditions

		System.out.println("searching transitive conflicts");
		boolean iterate = true;
		while (iterate) {
			iterate = false;
			for (Place b : pnet.getPlaces()) { // check if b is a transitive
												// conflict
				if (b.getPostSet().size() == 1)
					continue; // b is not a conflict

				System.out.println("checking " + b);

				boolean flag = true;
				for (Transition e1 : b.getPostSet()) {
					for (Transition e2 : b.getPostSet()) {
						// for any two different post-events e_1, e_2 of b ...
						if (e1 == e2)
							continue;

						// ... b is a transitive conflict between e_1 and e_2
						flag &= isTransitiveConflict(b, e1, e2, pnet);
						System.out.println("   " + e1 + "," + e2 + " --> "
								+ flag);

						if (!flag)
							break;
					}
					if (!flag)
						break;
				}

				if (flag) {
					Transition pre = b.getPreSet().get(0);
					boolean flag2 = true;
					for (Transition t : b.getPostSet()) {
						flag2 &= existsPathWithoutCondition(pre, t, b, pnet);
						if (!flag2)
							break;
					}

					if (flag2) {
						System.out.println("   remove " + b);
						pnet.removePlace(b);
						iterate = true;
						break;
					}
				}
			}
		}

		/*
		 * boolean iterate = true; while (iterate) { iterate = false; for (Place
		 * b: pnet.getPlaces()) { // check if b is a transitive conflict if
		 * (b.getPostSet().size()==1) continue; // b is not a conflict
		 * 
		 * boolean flag = true; for (Transition e1 : b.getPostSet()) { for
		 * (Transition e2 : b.getPostSet()) { // for any two different
		 * post-events e_1, e_2 of b ... if (e1.equals(e2)) continue; // ... b
		 * is a transitive conflict between e_1 and e_2 flag &=
		 * isTransitiveConflict(b,e1,e2,pnet);
		 * 
		 * if (!flag) break; } if (!flag) break; }
		 * 
		 * if (flag) { iterate = true; pnet.removePlace(b); }
		 * 
		 * if (iterate || !flag) break; } }
		 */

		/*
		 * for (Place p: pnet.getPlaces()) { for (Place q: pnet.getPlaces()) {
		 * if (p.equals(q)) continue; if (p.getPostSet().size()==1 ||
		 * q.getPostSet().size()==1) continue;
		 * 
		 * if (B2.get(p).first.equals(B2.get(q).first)) { BitSet pb =
		 * B2.get(p).second; BitSet qb = B2.get(q).second; BitSet ab =
		 * ((BitSet)pb.clone()); ab.and(qb); if (!ab.isEmpty()) { BitSet pbc =
		 * ((BitSet)pb.clone()); BitSet qbc = ((BitSet)qb.clone());
		 * pbc.andNot(ab); qbc.andNot(ab);
		 * 
		 * Set<Transition> pt = getTransitions(pbc,E); Set<Transition> qt =
		 * getTransitions(qbc,E);
		 * 
		 * if (existsPath(pt,qt,pnet)) { System.out.println("123"); } } } } }
		 */

		// --- Add source place

		{
			Transition src = E.get(sources.iterator().next());
			Place place = pnet.addPlace(src.getName());
			place.setTokens(1);
			pnet.addArc(place, src);
		}
		// --- Add sink places
		for (Integer sink : sinks) {
			Transition trans = E.get(sink);
			Place place = pnet.addPlace(trans.getName());
			pnet.addArc(trans, place);
		}

		try {
			PrintStream out = new PrintStream(String.format(
					"output/occnet_%s.dot", getModelName()));
			out.println(pnet.toDot());
			out.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		PetriNet folded = fold(pnet, implicitPlaces);

		System.out.println(pnet.getPlaces().size());

		return folded;
	}

	private boolean existsPathWithoutCondition(Transition t1, Transition t2,
			Place b, PetriNet pnet) {
		if (t1.equals(t2))
			return true;
		Set<Transition> visited = new HashSet<Transition>();
		Set<Transition> heap = getPostTransitions(t1, b);
		if (heap.contains(t2))
			return true;

		while (heap.size() > 0) {
			Transition tt = heap.iterator().next();
			heap.remove(tt);
			visited.add(tt);
			Set<Transition> post = getPostTransitions(tt, b);
			if (post.contains(t2))
				return true;
			post.removeAll(visited);
			heap.addAll(post);
		}

		return false;
	}

	// A condition b is a transitive conflict between two events e_1 and e_2,
	// iff there exist events f_1 < e_1 and f_2 < e_2 that are in conflict.
	private boolean isTransitiveConflict(Place b, Transition e1, Transition e2,
			PetriNet pnet) {
		if (e1.getName().equals(e2.getName()))
			return false;

		for (Place p : pnet.getPlaces()) {
			if (p.equals(b) || p.getPostSet().size() <= 1)
				continue;

			for (Transition f1 : p.getPostSet()) {
				for (Transition f2 : p.getPostSet()) {
					if (f1 == f2)
						continue;

					if (existsPath(f1, e1, pnet) && existsPath(f2, e2, pnet)) {
						return true;
					}
				}
			}

		}
		return false;
	}

	private boolean existsPath(Transition t1, Transition t2, PetriNet pnet) {
		if (t1.equals(t2))
			return true;
		Set<Transition> visited = new HashSet<Transition>();
		Set<Transition> heap = getPostTransitions(t1);
		if (heap.contains(t2))
			return true;

		while (heap.size() > 0) {
			Transition tt = heap.iterator().next();
			heap.remove(tt);
			visited.add(tt);
			Set<Transition> post = getPostTransitions(tt);
			if (post.contains(t2))
				return true;
			post.removeAll(visited);
			heap.addAll(post);
		}

		return false;
	}

	private boolean existsPath(Set<Transition> pt, Set<Transition> qt,
			PetriNet pnet) {
		for (Transition t : pt) {
			Set<Transition> heap = new HashSet<Transition>();
			Set<Transition> post = getPostTransitions(t);
			Set<Transition> visited = new HashSet<Transition>();

			if (setsIntersect(post, qt))
				return true;
			heap.addAll(post);
			while (heap.size() > 0) {
				Transition tt = heap.iterator().next();
				heap.remove(tt);
				visited.add(tt);
				post = getPostTransitions(tt);
				if (setsIntersect(post, qt))
					return true;
				else {
					heap.addAll(post);
					post.removeAll(visited);
				}
			}

		}

		return false;
	}

	private boolean setsIntersect(Set<Transition> s1, Set<Transition> s2) {
		for (Transition t : s1)
			if (s2.contains(t))
				return true;

		return false;
	}

	private Set<Transition> getPostTransitions(Transition t) {
		Set<Transition> result = new HashSet<Transition>();

		for (Place p : t.getPostSet())
			result.addAll(p.getPostSet());

		return result;
	}

	private Set<Transition> getPostTransitions(Transition t, Place b) {
		Set<Transition> result = new HashSet<Transition>();

		for (Place p : t.getPostSet())
			if (!p.equals(b))
				result.addAll(p.getPostSet());

		return result;
	}

	private Set<Integer> getMarkedBits(BitSet set) {
		Set<Integer> result = new HashSet<Integer>();

		for (int i = 0; i < set.length(); i++)
			if (set.get(i))
				result.add(i);

		return result;
	}

	private Set<Transition> getTransitions(BitSet set,
			Map<Integer, Transition> E) {
		Set<Transition> result = new HashSet<Transition>();

		Set<Integer> is = getMarkedBits(set);
		for (Integer i : is) {
			result.add(E.get(i));
		}

		return result;
	}

	private static String getOriginalLabel(String label,
			Map<String, de.hpi.bpt.process.Node> tasks,
			Map<String, de.hpi.bpt.process.Node> clones) {

		if (tasks.get(label) == null) {
			for (String originalLabel : tasks.keySet()) {
				if (tasks.get(originalLabel) == clones.get(label)) {
					label = originalLabel;
					break;
				}
			}
		}
		return label;
	}

	private PetriNet fold(PetriNet occnet, Set<Place> implicitPlaces) {
		try {

			DNodeSys_OccurrenceNet sys = new DNodeSys_OccurrenceNet(occnet,
					implicitPlaces);

			// System.out.println("implicit places: "+implicitPlaces);

			try {

				try {
					PetriNetIO.writeToFile(occnet, "output/dnode_"
							+ getModelName() + ".lola", PetriNetIO.FORMAT_LOLA,
							0);
				} catch (IOException e) {
					e.printStackTrace();
				}

				PrintStream out = new PrintStream(String.format(
						"output/dnode_%s.dot", getModelName()));
				out.println(sys.initialRun.toDot(sys.properNames));
				out.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}

			DNodeBP build = Uma.initBuildPrefix_View(sys, 0);

			Uma.out.println("equivalence..");
			build.foldingEquivalence();

			// build.debug_printFoldingEquivalence();

			Uma.out.println("join maximal..");
			build.extendFoldingEquivalence_maximal();
			Uma.out.println("fold backwards..");
			while (build.extendFoldingEquivalence_backwards()) {
				Uma.out.println("fold backwards..");
			}

			// while (build.refineFoldingEquivalence_removeSuperfluous()) {
			// Uma.out.println("remove superfluous..");
			// }

			hub.top.uma.synthesis.EquivalenceRefineSuccessor splitter = new hub.top.uma.synthesis.EquivalenceRefineSuccessor(
					build);

			Uma.out.println("relax..");
			// build.relaxFoldingEquivalence(splitter);
			Uma.out.println("determinize..");
			// while (build.extendFoldingEquivalence_deterministic()) {
			// Uma.out.println("determinize..");
			// }

			NetSynthesis synth = new NetSynthesis(build);
			DNodeSetElement nonImplied = new DNodeSetElement();
			for (DNode d : build.getBranchingProcess().getAllNodes())
				if (!d.isImplied)
					nonImplied.add(d);
			PetriNet net = synth.foldToNet_labeled(nonImplied, false);

			return net;

		} catch (InvalidModelException e) {
			e.printStackTrace();
		}

		return null;
	}

	/**
	 * This method separates causality and conflict relations into two
	 * subgraphs. Causality is copied to a directed graph (i.e. "dgraph") and
	 * Conflict is copied into an undirected graph (i.e. "uedges" & "unodes").
	 * At the same time, this method identifies the set of source and sink
	 * nodes.
	 * 
	 * @param orgraph
	 *            IN: Ordering relations graph
	 * @param dgraph
	 *            IN/OUT: Directed graph representing the causality relation
	 * @param dgnodes
	 *            IN: List of nodes in "dgraph"
	 * @param sources
	 *            OUT: Set of source nodes
	 * @param sinks
	 *            OUT: Set of sink nodes
	 */
	private void separateColors(ColoredGraph orgraph,
			Map<Integer, Set<Integer>> reducedFlow,
			Map<Integer, Set<Integer>> reducedFlow_endEvent,
			Set<Integer> sources, Set<Integer> sinks,

			Map<String, String> labelMap,
			Map<String, de.hpi.bpt.process.Node> tasks,
			Map<String, de.hpi.bpt.process.Node> clones) {
		Set<Integer> vertices = orgraph.getVertices();
		sources.addAll(vertices);
		sinks.addAll(vertices);

		BasicDigraph dgraph = new BasicDigraph();
		List<Node> dgnodes = dgraph.genNodes(vertices.size());

		for (Node node : dgnodes) {
			int src = node.nodeId();
			for (int tgt : orgraph.postSet(src)) {
				if (!orgraph.hasEdge(tgt, src)) {
					dgraph.genEdge(node, dgnodes.get(tgt));
					sources.remove(tgt);
					sinks.remove(src);
				}
			}
		}

		CList<net.stixar.graph.Edge> removed = MaxStr.acyclicReduce(dgraph,
				labelMap, tasks, clones);
		for (Node n : dgnodes) {
			reducedFlow.put(n.nodeId(), new HashSet<Integer>());
			reducedFlow_endEvent.put(n.nodeId(), new HashSet<Integer>());
		}
		for (net.stixar.graph.Edge edge : dgraph.edges()) {
			reducedFlow.get(edge.source().nodeId()).add(edge.target().nodeId());
		}

		for (net.stixar.graph.Edge edge : removed) {
			// if (RestrictedFlowEventStructure.ARTIFICIAL_END_EVENT.equals(
			// getOriginalLabel(
			// labelMap.get(orgraph.getLabel(edge.target().nodeId())), tasks,
			// clones )))
			{
				System.out.println("keeping edge " + edge + " to end event");
				reducedFlow_endEvent.get(edge.source().nodeId()).add(
						edge.target().nodeId());
			}
		}
	}

	/**
	 * Remove redundant edges from a mutable acyclic digraph.
	 * 
	 * @param mdg
	 *            the mutable acyclic digraph from which to remove edges.
	 * @return a list of the removed edges.
	 */
	public static CList<net.stixar.graph.Edge> acyclicReduce(
			MutableDigraph mdg, Map<String, String> labelMap,
			Map<String, de.hpi.bpt.process.Node> tasks,
			Map<String, de.hpi.bpt.process.Node> clones) {
		TopSorter ts = new TopSorter(mdg);
		ts.run();
		CList<Node> tsort = ts.getSort();
		mdg.sortEdges(NodeOrder.getEdgeComparator(ts.order()));
		ByteNodeMatrix m = Transitivity.acyclicClosure(mdg);
		CList<net.stixar.graph.Edge> remove = new CList<net.stixar.graph.Edge>();
		for (Node i : tsort) {
			for (net.stixar.graph.Edge e = i.out(); e != null; e = e.next()) {
				Node j = e.target();
				if (m.get(i, j) != 0) {
					for (net.stixar.graph.Edge ee = e.next(); ee != null; ee = ee
							.next()) {
						Node k = ee.target();
						if (m.get(j, k) != 0) {
							m.set(i, k, (byte) 0);
						}
					}
				} else {
					remove.add(e);
				}
			}
		}
		for (net.stixar.graph.Edge e : remove) {
			mdg.remove(e);
		}
		return remove;
	}
}
