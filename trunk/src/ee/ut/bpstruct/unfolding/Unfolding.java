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
package ee.ut.bpstruct.unfolding;

import hub.top.uma.DNode;
import hub.top.uma.DNodeBP;
import hub.top.uma.DNodeSet;
import hub.top.uma.DNodeSys;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import ee.ut.bpstruct.Helper;

/**
 * This class is a kind of container for storing the information about an
 * unfolding. It is intended for cloning and pruning (as required during the
 * restructuring of cyclic rigid components). As DNodeSet and DNodeBP are
 * immutable, we decided to add this utility class.
 * 
 * @author lgbanuelos
 */
public class Unfolding {
	static Logger logger = Logger.getLogger(Unfolding.class);
	private Helper helper = null;

	private List<DNode> initialConditions = null;
	private List<DNode> allConditions = null;
	private List<DNode> allEvents = null;
	private Set<DNode> cutoffs = null;
	private HashMap<DNode, DNode> elementary_ccPair = null;
	private DNodeBP brproc = null;
	private DNodeSys dnodesys = null;
	
	private Map<DNode, Unfolding> container = new HashMap<DNode, Unfolding>();
	private Set<DNode> localCorrSet = new HashSet<DNode>();
	
	/**
	 * This constructor copies the information from a concrete unfolding.
	 * 
	 * @param brproc
	 */
	public Unfolding(Helper helper, DNodeBP brproc) {
		this.helper = helper;
		this.brproc = brproc;
		this.dnodesys = brproc.getSystem();
		DNodeSet nodeSet = brproc.getBranchingProcess();
		allEvents = new LinkedList<DNode>(nodeSet.getAllEvents());
		elementary_ccPair = new HashMap<DNode, DNode>(brproc
				.getElementary_ccPair());
		initialConditions = new LinkedList<DNode>(nodeSet.initialConditions);
		cutoffs = new HashSet<DNode>();

		// "elementary_ccPair" includes postset of cutoffs
		for (DNode n : elementary_ccPair.keySet())
			if (n.isEvent) {
				cutoffs.add(n);
				container.put(elementary_ccPair.get(n), this);
			}
		
		allConditions = new LinkedList<DNode>(nodeSet.allConditions);
		
		if (logger.isTraceEnabled()) {
			try {
				String filename = String.format(helper.getDebugDir().getName() + "/unf_%s.dot", helper.getModelName());
				PrintStream out = new PrintStream(filename);
				out.print(brproc.toDot());
				out.close();
				logger.trace("Unfolding serialized into: " + filename);
			} catch (FileNotFoundException e) {
				logger.error(e);
			}
		}
	}
	

	/**
	 * This constructor is supposed to be used for CLONING parts of a concrete
	 * unfolding.
	 */
	private Unfolding(DNodeBP parent) {
		allConditions = new LinkedList<DNode>();
		allEvents = new LinkedList<DNode>();
		elementary_ccPair = new HashMap<DNode, DNode>();
		initialConditions = new LinkedList<DNode>();
		cutoffs = new HashSet<DNode>();
		dnodesys = parent.getSystem();
	}

	public List<DNode> getAllEvents() {
		return allEvents;
	}

	public List<DNode> getInitialConditions() {
		return initialConditions;
	}

	public List<DNode> getAllConditions() {
		return allConditions;
	}

	public Set<DNode> getCutoffs() {
		return cutoffs;
	}

	public DNode getCorr(DNode cutoff) {
		return elementary_ccPair.get(cutoff);
	}

	public Set<DNode> getLocalCorrSet() {
		return localCorrSet;
	}
	
	public String properName(DNode n) {
		return dnodesys.properNames[n.id];
	}
		
	/**
	 * Computes the local configuration for a given cutoff event
	 * 
	 * @param cutoff
	 */
	public Set<DNode> getPrimeConfiguration(DNode cutoff) {
		Set<DNode> result = null;
		if (elementary_ccPair.containsKey(cutoff)) {
			brproc.getBranchingProcess().getPrimeCut(cutoff, true, true); // TODO: could be avoided?
			result = brproc.getBranchingProcess().getPrimeConfiguration;
		} else
			result = new HashSet<DNode>();
		return result;
	}
	
	public Unfolding extractSubnet(DNode entry, DNode exit, Set<DNode> events, Set<DNode> conditions) {
		Unfolding subnet = new Unfolding(this.brproc);
		
		if (entry.isEvent) {
			DNode icond = new DNode(entry.pre[0].id, 0);
			icond.addPostNode(entry); entry.pre[0] = icond;
			DNode ocond = new DNode(exit.post[0].id, 1);
			ocond.pre[0] = exit; exit.post[0] = ocond;
			
			subnet.allEvents.addAll(events);
			subnet.allConditions.addAll(conditions);
			subnet.allConditions.add(icond);
			subnet.allConditions.add(ocond);
			subnet.initialConditions.add(icond);
		}
		
//		DNode ievent = null;
//		DNode oevent = null;
//		for (DNode n: nodes) {
//			if (n.isEvent) {
//				subnet.allEvents.add(n);
//				if (n.pre[0].equals(entry))
//					ievent = n;
//				if (n.post[0].equals(exit))
//					oevent = n;
//			} else
//				subnet.allConditions.add(n);
//		}
//		DNode _icond = new DNode(entry.id, 0);
//		DNode _ocond = new DNode(exit.id, 1);
//		
//		_icond.addPostNode(ievent); ievent.pre[0] = _icond;
//		_ocond.pre[0] = oevent; oevent.post[0] = _ocond;
//		subnet.allConditions.add(_icond);
//		subnet.allConditions.add(_ocond);
		
		return subnet;
	}
	
//	/**
//	 * Given a cutoff event, it gathers the unfolding subnet corresponding
//	 * to a loop
//	 * 
//	 * @param cutoff
//	 * @return An object instance containing the information about the reproduction process
//	 */
//	public ReproductionProcess identifyReproductionProcess(DNode cutoff) {
//		Set<DNode> nodes = new HashSet<DNode>();
//		ReproductionProcess repproc = null;
//		boolean found = false;
//		DNode corr = getCorr(cutoff);
//		Set<DNode> br_conds = new HashSet<DNode>();
//
//		nodes.addAll(Arrays.asList(cutoff.post));
//
//		Stack<DNode> worklist = new Stack<DNode>();
//		worklist.push(cutoff);
//		while (!worklist.isEmpty()) {
//			DNode curr = worklist.pop();
//			nodes.add(curr);
//			if (!curr.isEvent && brconds.containsKey(curr) && brconds.get(curr).size() > 1)
//				br_conds.add(curr);			
//			for (DNode pred : curr.pre)
//				if (corr.equals(pred))
//					found = true;
//				else if (!worklist.contains(pred) && !nodes.contains(pred))
//					worklist.push(pred);
//		}
//		
//		if (found)
//			repproc = new ReproductionProcess(nodes, br_conds);
//
//		return repproc;
//	}

//	/**
//	 * Separates a branch from the unfolding corresponding to a SESE loop, i.e. the
//	 * corresponding reproduction process. NOTE: It is assumed that "repproc" 
//	 * corresponds to a SESE loop and no further check is performed.
//	 * 
//	 * @param cutoff
//	 * @param repproc
//	 * @return
//	 */
//	public Unfolding abstractReproductionProcess(DNode cutoff,
//			ReproductionProcess repproc) {
//		Unfolding unf = new Unfolding();
//		DNode icond = getCorr(cutoff).post[0];  // Only one !!!
//		DNode succ = null;
//		
//		for (DNode node: repproc.nodes) {
//			if (node.isEvent) {
//				if (node.pre[0].equals(icond))
//					succ = node;
//				unf.allEvents.add(node);
//				
//				// --- keep track of corresponding
//				if (container.containsKey(node)) {
//					container.put(node, unf);
//					unf.localCorrSet.add(node);
//				}
//			} else if (!node.equals(icond))
//				unf.allConditions.add(node);
//		}
//		
//		DNode initial = new DNode(icond.id, 0);
//		unf.allConditions.add(initial);
//		unf.initialConditions.add(initial);
//		initial.addPostNode(succ);
//		succ.pre[0] = initial;
//		
//		brconds.get(icond).remove(succ); // Isolate the reproduction process
//		
//		if (logger.isTraceEnabled()) {
//			try {
//				String filename = String.format(helper.getDebugDir().getName()
//						+ "/rproc_%d.dot", System.currentTimeMillis());
//				PrintStream out = new PrintStream(filename);
//				out.print(unf.toDot());
//				out.close();
//				logger.trace("Reproduction process serialized into: " + filename);
//			} catch (FileNotFoundException e) {
//				logger.error(e);
//			}
//		}
//		
//		return unf;
//	}
//	
//	public Unfolding pruneAcyclicPrefix() {
//		Unfolding unf = new Unfolding();
//		
//		for (DNode brcond: brconds.keySet()) {
//			Set<DNode> post = brconds.get(brcond);
//			brcond.post = new DNode[post.size()];
//			int i = 0;
//			for (DNode succ: post)
//				brcond.post[i++] = succ;
//			System.out.println();
//		}
//
//		Set<DNode> visited = new HashSet<DNode>();
//		Stack<DNode> worklist = new Stack<DNode>();
//		worklist.push(initialConditions.get(0));
//		unf.initialConditions.add(initialConditions.get(0));
//
//		while (!worklist.isEmpty()) {
//			DNode curr = worklist.pop();
//			visited.add(curr);
//			if (curr.isEvent) {
//				unf.allEvents.add(curr);
//				// --- keep track of corresponding
//				if (container.containsKey(curr)) {
//					container.put(curr, unf);
//					unf.localCorrSet.add(curr);
//				}
//			} else unf.allConditions.add(curr);
//			
//			if (curr.post != null)
//				for (DNode succ: curr.post)
//					if (!worklist.contains(succ) && !visited.contains(succ))
//						worklist.push(succ);
//		}
//				
//		if (logger.isTraceEnabled()) {
//			try {
//				String filename = String.format(helper.getDebugDir().getName()
//						+ "/prefix_%d.dot", System.currentTimeMillis());
//				PrintStream out = new PrintStream(filename);
//				out.print(unf.toDot());
//				out.close();
//				logger.trace("Reproduction process serialized into: " + filename);
//			} catch (FileNotFoundException e) {
//				logger.error(e);
//			}
//		}
//		
//		return unf;
//	}

	public void addPlaceHolder(DNode corr, String label) {
		Unfolding unf = container.get(corr);
		DNodeSys sys = brproc.getSystem();
		DNode cond = corr.post[0]; // only one !!
		
		short id = (short)sys.nameToID.size();
		sys.nameToID.put(label, id);
		DNode ev_ph = new DNode(id, 1);
		ev_ph.isEvent = true;
		
		sys.nameToID.put(label + "_cond", ++id);
		DNode cond_ph = new DNode(id, 1);
		
		corr.post[0] = cond_ph; cond_ph.pre[0] = corr;
		cond_ph.addPostNode(ev_ph); ev_ph.pre[0] = cond_ph;
		ev_ph.addPostNode(cond); cond.pre[0] = ev_ph;
		
		allEvents.add(ev_ph); unf.allEvents.add(ev_ph);
		allConditions.add(cond_ph); unf.allConditions.add(cond_ph);
	}

	public void fixLabels() {
		// "finalize_setProperNames" is not accessible!
		// I had to update properNames here
		DNodeSys sys = brproc.getSystem();
		sys.properNames = new String[sys.nameToID.size()];
		for (Entry<String,Short> line : sys.nameToID.entrySet()) {
			sys.properNames[line.getValue()] = line.getKey();
		}
		
		if (logger.isTraceEnabled()) {
			try {
				String filename = String.format(helper.getDebugDir().getName()
						+ "/unfplus_%d.dot", System.currentTimeMillis());
				PrintStream out = new PrintStream(filename);
				out.print(toDot());
				out.close();
				logger.trace("Unfolding with place holders serialized into: " + filename);
			} catch (FileNotFoundException e) {
				logger.error(e);
			}
		}
	}

	// -----------------------------------------------------------------
	// --------   UTILITIES
	// -----------------------------------------------------------------
	
	/**
	 * Serializes the unfolding in the form of string, using DOT format
	 * 
	 * @return
	 */
	public String toDot() {
		StringBuilder b = new StringBuilder();
		b.append("digraph BP {\n");

		// standard style for nodes and edges
		b.append("graph [fontname=\"Helvetica\" nodesep=0.3 ranksep=\"0.2 equally\" fontsize=10];\n");
		b.append("node [fontname=\"Helvetica\" fontsize=8 fixedsize width=\".3\" height=\".3\" label=\"\" style=filled fillcolor=white];\n");
		b.append("edge [fontname=\"Helvetica\" fontsize=8 color=white arrowhead=none weight=\"20.0\"];\n");

		// String tokenFillString =
		// "fillcolor=black peripheries=2 height=\".2\" width=\".2\" ";
		String cutOffFillString = "fillcolor=gold";
		String antiFillString = "fillcolor=red";
		String impliedFillString = "fillcolor=violet";
		String hiddenFillString = "fillcolor=grey";

		// first print all conditions
		b.append("\n\n");
		b.append("node [shape=circle];\n");
		for (DNode n : getAllConditions()) {

			if (n.isAnti && n.isHot)
				b.append("  c" + n.globalId + " [" + antiFillString + "]\n");
			else if (n.isCutOff)
				b.append("  c" + n.globalId + " [" + cutOffFillString + "]\n");
			else
				b.append("  c" + n.globalId + " []\n");

			String auxLabel = "";

			b.append("  c" + n.globalId + "_l [shape=none];\n");
			b.append("  c" + n.globalId + "_l -> c" + n.globalId
					+ " [headlabel=\"" + n + " " + auxLabel + "\"]\n");
		}

		// then print all events
		b.append("\n\n");
		b.append("node [shape=box];\n");
		for (DNode n : getAllEvents()) {

			if (n.isAnti && n.isHot)
				b.append("  e" + n.globalId + " [" + antiFillString + "]\n");
			else if (n.isAnti && !n.isHot)
				b.append("  e" + n.globalId + " [" + hiddenFillString + "]\n");
			else if (n.isImplied)
				b.append("  e" + n.globalId + " [" + impliedFillString + "]\n");
			else if (n.isCutOff)
				b.append("  e" + n.globalId + " [" + cutOffFillString + "]\n");
			else
				b.append("  e" + n.globalId + " []\n");

			String auxLabel = "";

			b.append("  e" + n.globalId + "_l [shape=none];\n");
			b.append("  e" + n.globalId + "_l -> e" + n.globalId
					+ " [headlabel=\"" + n + " " + auxLabel + "\"]\n");
		}

		/*
		 * b.append("\n\n"); b.append(" subgraph cluster1\n");
		 * b.append(" {\n  "); for (CNode n : nodes) { if (n.isEvent)
		 * b.append("e"+n.localId+" e"+n.localId+"_l "); else
		 * b.append("c"+n.localId+" c"+n.localId+"_l "); }
		 * b.append("\n  label=\"\"\n"); b.append(" }\n");
		 */

		// finally, print all edges
		b.append("\n\n");
		b.append(" edge [fontname=\"Helvetica\" fontsize=8 arrowhead=normal color=black];\n");
		for (DNode n : allConditions) {
			String prefix = n.isEvent ? "  e" : "  c";
			if (n.post == null) continue;
			for (int i = 0; i < n.post.length; i++) {
				if (n.post[i] == null)
					continue;

				if (n.post[i].isEvent)
					b.append(prefix + n.globalId + " -> " + 
							 "e" + n.post[i].globalId + " [weight=10000.0]\n");
				else
					b.append(prefix + n.globalId + " -> " + 
							 "c" + n.post[i].globalId + " [weight=10000.0]\n");
			}
		}
		for (DNode n : allEvents) {
			String prefix = n.isEvent ? "  e" : "  c";
			for (int i = 0; i < n.post.length; i++) {
				if (n.post[i] == null)
					continue;

				if (n.post[i].isEvent)
					b.append(prefix + n.globalId + " -> " + 
							 "e" + n.post[i].globalId + " [weight=10000.0]\n");
				else
					b.append(prefix + n.globalId + " -> " + 
							 "c" + n.post[i].globalId + " [weight=10000.0]\n");
			}
		}

		// add links from cutoffs to corresponding events
		b.append("\n\n");
		b.append(" edge [fontname=\"Helvetica\" fontsize=8 arrowhead=normal color=red];\n");
		for (DNode n : allEvents) {
			if (n.isCutOff && getCorr(n) != null) {
				b.append("  e" + n.globalId + " -> e" + getCorr(n).globalId
						+ " [weight=10000.0]\n");
			}
		}

		b.append("}");
		return b.toString();
	}
}
