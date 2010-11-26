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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import de.bpt.hpi.graph.Edge;
import de.bpt.hpi.graph.Graph;
import ee.ut.bpstruct.unfolding.Unfolding;
import ee.ut.graph.moddec.ColoredGraph;
import ee.ut.graph.moddec.ModularDecompositionTree;
import ee.ut.graph.util.GraphUtils;

public abstract class AbstractRestructurerHelper implements RestructurerHelper {
	static Logger logger = Logger.getLogger(AbstractRestructurerHelper.class);

	protected Graph graph;
	protected Map<Object, Integer> map;
	protected Map<Integer, Object> rmap;
	protected Map<Integer, Object> gwmap;
		
	protected File debugDir = new File(".");
	
	public AbstractRestructurerHelper() {
		graph = null;
		map = new HashMap<Object, Integer>();
		rmap = new HashMap<Integer, Object>();
		gwmap = new HashMap<Integer, Object>();
	}
	
	protected abstract void initGraph();
	
	public Graph getGraph() {
		if (graph == null) initGraph();
		return graph;
	}
	
	public Object getModelElementId(Integer vertex) { return rmap.get(vertex); }
	
	public Object gatewayType(Integer vertex) { return gwmap.get(vertex); }
	
	public File getDebugDir() { return debugDir; }

	public void setDebugDir(File debugDir) { this.debugDir = debugDir; }
	
	public void processOrderingRelations(Set<Edge> edges,
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
			Integer vertexp = graph.addVertex(label);
			// Add code to complete the cloning (e.g. when mapping BPMN->BPEL)
			tasks.put(label, vertexp);
			Integer vertex = clones.get(label);

			if (blocks.containsKey(vertex))
				blocks.put(vertexp, blocks.get(vertex));
		}

		// STEP 4: Synthesize structured version from MDT
		synthesizeFromMDT(vertices, edges, entry, exit, mdec, tasks);
	}

	protected abstract void synthesizeFromMDT(final Set<Integer> vertices, final Set<Edge> edges,
			final Integer entry, final Integer exit, final ModularDecompositionTree mdec,
			final Map<String, Integer> tasks) throws CannotStructureException;

	// -------------
	//
	protected Block rootcomponent = null;
	
	protected Map<Integer, Block> blocks = new HashMap<Integer, Block>();
	
	public Integer foldComponent(Graph graph, Set<Edge> edges,
			Set<Integer> vertices, Integer entry, Integer exit, BLOCK_TYPE type) {
		Block block = new Block(edges, vertices, entry, exit, type);
		Integer blockid = graph.addVertex("BLOCK_" + graph.getVertices().size());
		vertices.clear(); edges.clear();
		vertices.add(entry);
		vertices.add(exit);
		vertices.add(blockid);
		edges.add(graph.addEdge(entry, blockid));
		edges.add(graph.addEdge(blockid, exit));
		blocks.put(blockid, block);
		rootcomponent = block;
		return blockid;
	}

	protected Map<Integer, Integer> strMap = new HashMap<Integer, Integer>();
	
	static class Block {
		Set<Edge> edges;
		Set<Integer> vertices;
		Integer entry, exit;
		BLOCK_TYPE type;
		public Block(Set<Edge> edges, Set<Integer> vertices, Integer entry, Integer exit, BLOCK_TYPE type) {
			this.edges = new HashSet<Edge>(edges);
			this.vertices = new HashSet<Integer>(vertices);
			this.entry = entry;
			this.exit = exit;
			this.type = type;
		}
	}

	
	
	public void installStructured() {
		installStructured(new Edge(null, null));
	}
	
	protected void installStructured(Edge pair) {
		if (rootcomponent == null) return;
		
		Graph structured = new Graph();
		
		Map<Integer, Object> strgwmap = new HashMap<Integer, Object>();

		traverseBlocks(structured, rootcomponent, pair, strgwmap);
		
		Map<Integer, List<Integer>> outgoing = GraphUtils.edgelist2adjlist(new HashSet<Edge>(structured.getEdges()), pair.getSecond());
		Map<Integer, List<Integer>> incoming = new HashMap<Integer, List<Integer>>();
		incoming.put(pair.getFirst(), new LinkedList<Integer>());
		for (Integer src: outgoing.keySet())
			for (Integer tgt: outgoing.get(src)) {
				List<Integer> list = incoming.get(tgt);
				if (list == null)
					incoming.put(tgt, list = new LinkedList<Integer>());
				list.add(src);
			}
		
		Set<Integer> toremove = new HashSet<Integer>();
		for (Integer gw: strgwmap.keySet())
			if (outgoing.get(gw).size() == 1 && incoming.get(gw).size() == 1)
				toremove.add(gw);
		
		Set<Integer> visited = new HashSet<Integer>();
		simplify(outgoing, pair.getFirst(), toremove, structured, visited, pair.getFirst());
		
		structured.removeVertices(toremove);
		graph = structured;
		gwmap = strgwmap;		
	}

	protected void simplify(Map<Integer, List<Integer>> adjlist, Integer curr,
			Set<Integer> toremove, Graph structured, Set<Integer> visited, Integer last) {
		visited.add(curr);
		if (!toremove.contains(curr))
			last = curr;
		for (Integer succ: adjlist.get(curr)) {
			if (toremove.contains(succ))
				structured.removeEdge(new Edge(curr, succ));
			else
				structured.addEdge(new Edge(last, succ));
			if (!visited.contains(succ))
				simplify(adjlist, succ, toremove, structured, visited, last);
		}
	}

	protected void traverseBlocks(Graph structured, Block block, Edge pair, Map<Integer, Object> strgwmap) {		
		Map<Integer, Edge> localpairs = new HashMap<Integer, Edge>();
		if (pair.getFirst() == null) {
			Integer entry = structured.addVertex(graph.getLabel(block.entry));
			Integer exit = structured.addVertex(graph.getLabel(block.exit));
			strgwmap.put(entry, gwmap.get(block.entry));
			strgwmap.put(exit, gwmap.get(block.exit));
			pair.setFirst(entry);
			pair.setSecond(exit);
			localpairs.put(block.entry, new Edge(entry, entry));
			localpairs.put(block.exit, new Edge(exit, exit));
		}
		
		for (Edge e: block.edges) {
			Integer src = e.getSource();
			Integer tgt = e.getTarget();
			
			Edge srcpair = cloneVertex(structured, localpairs, src, strgwmap);
			Edge tgtpair = cloneVertex(structured, localpairs, tgt, strgwmap);
			
			structured.addEdge(srcpair.getSecond(), tgtpair.getFirst());
		}
	}

	protected Edge cloneVertex(Graph structured, Map<Integer, Edge> localpairs,
			Integer vertex, Map<Integer, Object> strgwmap) {
		Edge pair = localpairs.get(vertex);
		if (pair == null) {
			if (blocks.containsKey(vertex)) {
				pair = new Edge(null, null);
				traverseBlocks(structured, blocks.get(vertex), pair, strgwmap);
			} else {
				Integer srcp = structured.addVertex(graph.getLabel(vertex));
				strMap.put(vertex, srcp);
				if (gwmap.containsKey(vertex))
					strgwmap.put(srcp, gwmap.get(vertex));
				pair = new Edge(srcp, srcp);
			}
			localpairs.put(vertex, pair);
		}
		return pair;
	}	

	public void serialize2dot(String fileName, Graph graph) throws FileNotFoundException {
		File file = new File(fileName);
        PrintStream out = new PrintStream(file);

        //Close the output stream
		out.println("digraph G {");
		
		for (Integer i: graph.getVertices()) {
			out.printf("\tn%s[shape=circle,label=\"%s\"];\n", i.toString(), graph.getLabel(i));
		}
		
		for (Edge e: graph.getEdges())
			if (e.getSource() != null && e.getTarget() != null)
				out.printf("\tn%s->n%s;\n", e.getSource().toString(), e.getTarget().toString());
		
		out.println("}");
		
		out.close();
	}

}
