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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import ee.ut.bpstruct.Helper;

/**
 * This class is a kind of container for storing the information about an
 * unfolding. It is intended for cloning and pruning (as required during the
 * restructuring of cyclic rigid components). As DNodeSet and DNodeBP are
 * immutable, we decided to add this utility class.
 * 
 * @author Luciano Garcia Banuelos
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
				String filename = String.format(this.helper.getDebugDir().getName() + "/unf_%s.dot", helper.getModelName());
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
			
			events.retainAll(getCutoffs());
			
			for (DNode cutoff: events) {
				DNode corr = getCorr(cutoff);
				if (subnet.allEvents.contains(corr)) {
					subnet.elementary_ccPair.put(cutoff, corr);
				}
				subnet.allConditions.addAll(Arrays.asList(cutoff.post));
			}
		}
				
		return subnet;
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
