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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import de.bpt.hpi.graph.Edge;
import de.bpt.hpi.graph.Graph;
import de.bpt.hpi.graph.Pair;
import ee.ut.bpstruct.Helper;
import ee.ut.bpstruct.Petrifier;
import ee.ut.graph.moddec.ModularDecompositionTree;

public class UnfoldingHelper implements Helper {
	private Helper modelHelper;
	private Unfolding unf;
	private Map<DNode, Set<DNode>> brconds = new HashMap<DNode, Set<DNode>>();
	private Set<DNode> revents;
	private Set<DNode> rconditions;

	private Graph graph = new Graph();
	private Map<DNode, Integer> map = new HashMap<DNode, Integer>();
	private Map<Integer, DNode> rmap = new HashMap<Integer, DNode>();
	
	private Set<Integer> eventSet = new HashSet<Integer>();
	
	private Map<DNode, Pair> entryexit = new HashMap<DNode, Pair>();
	
	public UnfoldingHelper(Helper modelHelper, Unfolding unf) {
		this.modelHelper = modelHelper;
		this.unf = unf;
		this.revents = new HashSet<DNode>(unf.getAllEvents());
		this.rconditions = new HashSet<DNode>(unf.getAllConditions());
		
		for (DNode cond : unf.getAllConditions()) {
			if (cond.post != null && cond.post.length > 1) {
				if (!brconds.containsKey(cond)) brconds.put(cond, new HashSet<DNode>());
				Set<DNode> post = brconds.get(cond);
				for (DNode ev: cond.post)
					post.add(ev);
			}
		}

	}
	
	/**
	 * Given a cutoff event, it gathers the unfolding subnet corresponding
	 * to a loop
	 * 
	 * @param cutoff
	 * @return An object instance containing the information about the reproduction process
	 */
	public ReproductionProcess identifyReproductionProcess(DNode cutoff) {
		Set<DNode> events = new HashSet<DNode>();
		Set<DNode> conditions = new HashSet<DNode>();
		ReproductionProcess repproc = null;
		boolean found = false;
		DNode corr = unf.getCorr(cutoff);
		Set<DNode> br_conds = new HashSet<DNode>();

		conditions.addAll(Arrays.asList(cutoff.post));

		Stack<DNode> worklist = new Stack<DNode>();
		worklist.push(cutoff);
		while (!worklist.isEmpty()) {
			DNode curr = worklist.pop();
			if (curr.isEvent)
				events.add(curr);
			else conditions.add(curr);
			if (!curr.isEvent && brconds.containsKey(curr) && brconds.get(curr).size() > 1)
				br_conds.add(curr);			
			for (DNode pred : curr.pre)
				if (corr.equals(pred))
					found = true;
				else if (!worklist.contains(pred) && !events.contains(pred) && !conditions.contains(pred))
					worklist.push(pred);
		}
		
		if (found)
			repproc = new ReproductionProcess(events, conditions, br_conds);

		return repproc;
	}
	
	/**
	 * Separates a branch from the unfolding corresponding to a SESE loop, i.e. the
	 * corresponding reproduction process. NOTE: It is assumed that "repproc" 
	 * corresponds to a SESE loop and no further check is performed.
	 * 
	 * Sorry for this misleading name. In the paper, the reproduction process is
	 * rewired, but testing some properties becomes more complex (in the paper we
	 * rely on transitivity of precedence).
	 * 
	 * @param cutoff
	 * @param repproc
	 * @return
	 */
	public void rewireReproductionProcess(DNode cutoff,
			ReproductionProcess repproc) {
		DNode icond = unf.getCorr(cutoff).post[0];  // Only one !!!
		DNode bcond = cutoff.post[0];
		DNode ocond = null;
		DNode succ = null;
		
		// REPEAT-WHILE loop
		if (!repproc.brConds.contains(icond))
			ocond = repproc.brConds.iterator().next();
		
		// WHILE loop
		if (!entryexit.containsKey(icond)) {
			Integer entry = graph.addVertex(unf.properName(icond) + "_entry");
			Integer exit = graph.addVertex(unf.properName(icond) + "_exit");
			Integer inter = graph.addVertex(unf.properName(icond) + "_inter");
			
			eventSet.add(inter);
			
			graph.addEdge(entry, inter);
			graph.addEdge(inter, exit);			
			entryexit.put(icond, new Pair(entry, exit));
		}

		for (DNode node: repproc.conditions) {
			if (!node.equals(icond) && !node.equals(bcond)) {
				Integer _node = graph.addVertex(unf.properName(node));
				map.put(node, _node);
				rmap.put(_node, node);
			}
			rconditions.remove(node);
		}
		
		for (DNode node: repproc.events) {
			if (node.pre[0].equals(icond))
				succ = node;
			Integer _node = graph.addVertex(unf.properName(node));
			map.put(node, _node);
			rmap.put(_node, node);
			eventSet.add(_node);
			
			for (DNode tgt: node.post)
				if (!node.isCutOff)
					graph.addEdge(_node, map.get(tgt));
				else 
					graph.addEdge(_node, entryexit.get(icond).getFirst());
			
			if (node != succ)
				for (DNode src: node.pre) {
					if (src.equals(ocond))
						brconds.remove(node);
					graph.addEdge(map.get(src), _node);
				}
			else
				graph.addEdge(entryexit.get(icond).getSecond(), _node);
			revents.remove(node);
		}
		
		if (succ != null)
			brconds.get(icond).remove(succ); // Isolate the reproduction process
		
		try {
			graph.serialize2dot("debug/rewired_unfolding.dot");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	public void rewireAcyclicPrefix() {		
		for (DNode node: rconditions) {
			Integer _node = graph.addVertex(unf.properName(node));
			map.put(node, _node);
			rmap.put(_node, node);
		}
		
		for (DNode node: revents) {
			Integer _node = graph.addVertex(unf.properName(node));
			map.put(node, _node);
			rmap.put(_node, node);
			
			for (DNode pre: node.pre)
				if (entryexit.containsKey(pre))
					graph.addEdge(entryexit.get(pre).getSecond(), _node);
				else
					graph.addEdge(map.get(pre), _node);
			
			for (DNode post: node.post)
				if (entryexit.containsKey(post))
					graph.addEdge(_node, entryexit.get(post).getFirst());
				else
					graph.addEdge(_node, map.get(post));
		}

		try {
			graph.serialize2dot("debug/rewired_unfolding.dot");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	public Unfolding extractSubnet(Set<Edge> edges, Set<Integer> vertices, Integer entry, Integer exit) {
		DNode _entry = rmap.get(entry);
		DNode _exit = rmap.get(exit);
		
		Set<DNode> events = new HashSet<DNode>();
		Set<DNode> conditions = new HashSet<DNode>();
		for (Integer v: vertices) {
			DNode node = rmap.get(v);
			if (node.isEvent)
				events.add(node);
			else
				conditions.add(node);
		}
		
		if (!_entry.isEvent) {
			conditions.remove(_entry);
			conditions.remove(_exit);
		}
		
		return unf.extractSubnet(_entry, _exit, events, conditions);
	}

	@Override
	public Object gatewayType(Integer vertex) {
		System.err.println("gatewayType called");
		return null;
	}

	@Override
	public File getDebugDir() {
		return modelHelper.getDebugDir();
	}

	@Override
	public Graph getGraph() {
		return graph;
	}

	@Override
	public Petrifier getPetrifier(Set<Integer> vertices, Set<Edge> edges,
			Integer entry, Integer exit) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void serializeDot(PrintStream out, Set<Integer> vertices,
			Set<Edge> edges) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setLoopEntryExit(Integer entry, Integer exit) {
		// TODO Auto-generated method stub

	}

	@Override
	public void synthesizeFromMDT(Set<Integer> vertices, Set<Edge> edges,
			Integer entry, Integer exit, ModularDecompositionTree mdec,
			Map<String, Integer> tasks) {
		// TODO Auto-generated method stub

	}

}
