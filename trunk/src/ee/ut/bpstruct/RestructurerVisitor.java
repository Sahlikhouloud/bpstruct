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
import hub.top.uma.DNode;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
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
import de.bpt.hpi.graph.Pair;
import ee.ut.bpstruct.unfolding.Unfolder;
import ee.ut.bpstruct.unfolding.Unfolding;
import ee.ut.bpstruct.unfolding.UnfoldingHelper;
import ee.ut.bpstruct.unfolding.UnfoldingRestructurer;
import ee.ut.comptech.DJGraph;
import ee.ut.comptech.DJGraph2dot;
import ee.ut.comptech.DJGraphHelper;
import ee.ut.comptech.Processor;
import ee.ut.graph.moddec.ColoredGraph;
import ee.ut.graph.moddec.ModularDecompositionTree;

public class RestructurerVisitor implements Visitor {
	static Logger logger = Logger.getLogger(RestructurerVisitor.class);

	private Helper helper;
	
	public RestructurerVisitor(Helper helper) {
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
		
		final Map<String, Integer> tasks = new HashMap<String, Integer>();
		final Map<Integer, Stack<Integer>> instances = new HashMap<Integer, Stack<Integer>>();
		final Map<String, Integer> labels = new HashMap<String, Integer>();
		
		for (Integer vertex: vertices)
			if (helper.gatewayType(vertex) == null)
				tasks.put(graph.getLabel(vertex), vertex);

		edges.clear(); vertices.clear();
		UnfoldingRestructurer restructurer = new UnfoldingRestructurer(unfhelper, new Visitor() {
			Map<Integer, Pair> fragEntries = new HashMap<Integer, Pair>();
			Map<Integer, Pair> fragExits = new HashMap<Integer, Pair>();
			Map<Integer, Integer> gateways = new HashMap<Integer, Integer>();
			Map<Integer, Pair> nullFragments = new HashMap<Integer, Pair>();
			
			int fragment = 0;
			int gateway = 0;
			
			public void visitSNode(Graph _graph, Set<Edge> _edges, Set<Integer> _vertices,
					Integer _entry, Integer _exit) {
				System.out.println("--- found a sequence !!!");
				System.out.println("Entry: " + _graph.getLabel(_entry) + ", exit: " + _graph.getLabel(_exit));
				Integer fragId = _graph.addVertex("fragment" + fragment++);
				
				Map<Integer, Integer> successor = new HashMap<Integer, Integer>();
				for (Edge e: _edges) successor.put(e.getSource(), e.getTarget());
			
				Integer first = null;
				Integer last = null;
				Integer _curr = _entry;
				
				while (_curr != _exit) {
					String label = _graph.getLabel(_curr);
//					System.out.print(label + ".");
					if (tasks.containsKey(label)) {
						
						// --- This happens in the external graph
						Integer curr = testAndClone(graph, tasks, null,
								label, _curr);
						
						System.out.printf(" %s", graph.getLabel(curr));
						
						if (first == null)
							first = curr;
						else
							edges.add(new Edge(last, curr));
						last = curr;
						vertices.add(curr);
						// ----
					} else if (fragEntries.containsKey(_curr)) {
						System.out.printf(" %s", label);
						
						// --- This happens in the external graph
						Integer curr = fragEntries.get(_curr).getSecond();
						
						if (curr != null) {
							if (first == null)
								first = curr;
							else
								edges.add(new Edge(last, curr));
							last = fragExits.get(_curr).getSecond();
						} else {
							System.out.println("oops !!!");
						}
						// ----
					}
					_curr = successor.get(_curr);
				}
				System.out.println();
								
				fragEntries.put(fragId, new Pair(_entry, first));
				fragExits.put(fragId, new Pair(_exit, last));
				if (first == null) nullFragments.put(fragId, new Pair(_entry, _exit));
				
				_edges.clear();
				_vertices.clear();
				_vertices.add(_entry); _vertices.add(fragId); _vertices.add(_exit);
				_edges.add(new Edge(_entry, fragId)); _edges.add(new Edge(fragId, _exit));
			}

			private Integer testAndClone(final Graph graph, final Map<String, Integer> tasks,
					Map<Integer, Integer> linstances, String label, Integer _curr) {
				Integer curr = tasks.get(label);
				if (curr == null) return _curr;
				if (linstances != null && linstances.containsKey(_curr))
					return linstances.get(_curr);
				if (instances.containsKey(curr)) {
					Stack<Integer> ins = instances.get(curr);
					
					curr = graph.addVertex(label + "_" + labels.get(label));
					labels.put(label, labels.get(label) + 1);
					
					ins.push(curr);
				} else {
					Stack<Integer> ins = new Stack<Integer>();
					
					if (!labels.containsKey(label))
						labels.put(label, 0);
					else {
						curr = graph.addVertex(label + "_" + labels.get(label));
						labels.put(label, labels.get(label) + 1);
					}

					ins.push(curr);
					instances.put(curr, ins);
				}
				return curr;
			}
			
			public void visitRootSNode(Graph _graph, Set<Edge> _edges,
					Set<Integer> _vertices, Integer _entry, Integer _exit) {
				System.out.println("--- Reached Root!!");
				if (_vertices.size() > 3) 
					visitSNode(_graph, _edges, _vertices, _entry, _exit);
				for (Edge e: _edges) {
					if (e.getSource().equals(_entry)) {
						Integer fragId = e.getTarget();
						Integer fragentry = fragEntries.get(fragId).getSecond();
						Integer fragexit = fragExits.get(fragId).getSecond();
						if (graph.getLabel(entry).equals(graph.getLabel(fragentry)))
							graph.setLabel(fragentry, graph.getLabel(fragentry) + "_");
						if (graph.getLabel(exit).equals(graph.getLabel(fragexit)))
							graph.setLabel(fragexit, graph.getLabel(fragexit) + "_");

						edges.add(new Edge(entry, fragentry));
						edges.add(new Edge(fragexit, exit));
					}
				}
				System.out.println("done");
			}
			
			public void visitRNode(Graph _graph, Set<Edge> _edges, Set<Integer> _vertices,
					Integer _entry, Integer _exit) throws CannotStructureException {
				System.out.println("--- found a rigid !!!");
				Map<Integer, Integer> linstances = new HashMap<Integer, Integer>();
				DNode boundary = (DNode) unfhelper.gatewayType(_entry);
				if (boundary.isEvent) {
					/// ----------------    AND Rigid
					System.out.println("--- AND rigid");

					Unfolding inner = unfhelper.extractSubnet(_edges, _vertices, _entry, _exit);
					System.out.println(inner.toDot());
					
					_edges.clear();
					_vertices.clear();
					Integer entry = graph.addVertex(_graph.getLabel(_entry));
					Integer exit = graph.addVertex(_graph.getLabel(_exit));
					processOrderingRelations(_edges, _vertices, entry, exit, graph, inner, tasks);
					_vertices.add(entry); _vertices.add(exit);
					helper.serializeDot(System.out, _vertices, _edges);
	
					System.out.println("Entry: " + graph.getLabel(entry));
					System.out.println("Exit: " + graph.getLabel(exit));
					Integer fragId = _graph.addVertex("fragment" + fragment++);
	
					for (Edge e: _edges) {
						Integer _source = e.getSource();
						Integer _target = e.getTarget();
						
						Integer source = null;
						Integer target = null;
	
						if (_source.equals(entry)) {
							target = testAndClone(graph, tasks, linstances, graph.getLabel(_target), _target);
							System.out.println("-Target: " + graph.getLabel(target));
							fragEntries.put(fragId, new Pair(_entry, target));
						} else if (_target.equals(exit)) {
							if (linstances.containsKey(_source))
								source = linstances.get(_source);
							else {
								source = testAndClone(graph, tasks, linstances, graph.getLabel(_source), _source);
								linstances.put(_source, source);
							}
							System.out.println("-Source: " + graph.getLabel(source));
							fragExits.put(fragId, new Pair(_exit, source));
						} else {
							if (linstances.containsKey(_target))
								target = linstances.get(_target);
							else {
								target = testAndClone(graph, tasks, linstances, graph.getLabel(_target), _target);
								linstances.put(_target, target);
							}
							if (linstances.containsKey(_source))
								source = linstances.get(_source);
							else {
								source = testAndClone(graph, tasks, linstances, graph.getLabel(_source), _source);
								linstances.put(_source, source);
							}
							System.out.println("Source: " + graph.getLabel(source));
							System.out.println("Target: " + graph.getLabel(target));
							edges.add(e);
						}
						
						e.setSource(source);
						e.setTarget(target);
						vertices.add(source);
					}				
					vertices.remove(entry);
					
					_edges.clear();
					_vertices.clear();
					_vertices.add(_entry); _vertices.add(fragId); _vertices.add(_exit);
					_edges.add(new Edge(_entry, fragId)); _edges.add(new Edge(fragId, _exit));
				} else {
					/// ----------------    Unstructured loop (XOR logic)
					System.out.println("--- unstructured loop");
					
					try {
						helper.serializeDot(new PrintStream(new File(String.format("debug/partial_%d.dot", UnfoldingRestructurer.counter++))), vertices, edges);
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					}

					Set<Integer> nullfrags = new HashSet<Integer>();
					
					for (Edge e: _edges) {
						Integer src = e.getSource();
						Integer tgt = e.getTarget();
						
						if (fragExits.containsKey(src)) {
							Integer gw = gateways.get(tgt);
							if (gw == null) {
								gw = graph.addVertex(_graph.getLabel(tgt) + gateway++);
								gateways.put(tgt, gw);
								helper.setXORGateway(gw);
								vertices.add(gw);
							}
							if (fragExits.get(src).getSecond() != null) {
								vertices.add(fragExits.get(src).getSecond());
								edges.add(new Edge(fragExits.get(src).getSecond(), gw));
							} else 
								nullfrags.add(src);
						} else if (fragEntries.containsKey(tgt)){
							Integer gw = gateways.get(src);
							if (gw == null) {
								gw = graph.addVertex(_graph.getLabel(src) + gateway++);
								gateways.put(src, gw);
								helper.setXORGateway(gw);
								vertices.add(gw);
							}
							if (fragExits.get(tgt).getSecond() != null) {
								vertices.add(fragEntries.get(tgt).getSecond());
								edges.add(new Edge(gw, fragEntries.get(tgt).getSecond()));
							} else
								nullfrags.add(tgt);
						} else {
							Integer gwsrc = gateways.get(src);
							if (gwsrc == null) {
								gwsrc = graph.addVertex(_graph.getLabel(src) + gateway++);
								gateways.put(src, gwsrc);
								helper.setXORGateway(gwsrc);
							}
							Integer gwtgt = gateways.get(tgt);
							if (gwtgt == null) {
								gwtgt = graph.addVertex(_graph.getLabel(tgt) + gateway++);
								gateways.put(tgt, gwtgt);
								helper.setXORGateway(gwtgt);
							}
							vertices.add(gwsrc); vertices.add(gwtgt);
							edges.add(new Edge(gwsrc, gwtgt));
						}
					}
					
					for (Integer frag: nullfrags) {
						Integer src = nullFragments.get(frag).getFirst();
						Integer tgt = nullFragments.get(frag).getSecond();
						edges.add(new Edge(gateways.get(src), gateways.get(tgt)));
					}
					
					Integer fragId = _graph.addVertex("fragment" + fragment++);
					fragEntries.put(fragId, new Pair(_entry, gateways.get(_entry)));
					fragExits.put(fragId, new Pair(_exit, gateways.get(_exit)));

					_edges.clear();
					_vertices.clear();
					_vertices.add(_entry); _vertices.add(fragId); _vertices.add(_exit);
					_edges.add(new Edge(_entry, fragId)); _edges.add(new Edge(fragId, _exit));

//					final Graph subgraph = new Graph();
//					for (Edge e: _edges) {
//						subgraph.addVertex(e.getSource(), _graph.getLabel(e.getSource()));
//						subgraph.addVertex(e.getTarget(), _graph.getLabel(e.getTarget()));
//						subgraph.addEdge(e);
//					}
//
//					Integer _entry_ = subgraph.addVertex("_entry_");
//					Integer _exit_ = subgraph.addVertex("_exit_");
//					subgraph.addEdge(_entry_, _entry);
//					subgraph.addEdge(_exit, _exit_);
//					subgraph.addEdge(_entry_, _exit_);
//					try {
//						subgraph.serialize2dot("debug/fragment.dot");
//					} catch (FileNotFoundException e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}
//
//					DJGraph djgraph = new DJGraph(subgraph, edgelist2adjlist(new HashSet<Edge>(subgraph.getEdges()), _exit_), _entry_);
//					djgraph.identifyLoops(new DJGraphHelper() {
//						public List<Integer> processSEME(Set<Integer> loopbody) {
//							return null;
//						}
//						
//						public List<Integer> processMEME(Set<Integer> loopbody) {
//							return null;
//						}
//					});					
//					Processor proc = new Processor(subgraph, edgelist2adjlist(new HashSet<Edge>(subgraph.getEdges()), _exit_), _entry_);
//					try {
//						DJGraph2dot.printRestructured(proc, new PrintStream(new File("debug/fromproc.dot")));
//					} catch (FileNotFoundException e1) {
//						e1.printStackTrace();
//					}
//					System.exit(0);
				}
			}
			
			public void visitPNode(Graph _graph, Set<Edge> _edges, Set<Integer> _vertices,
					Integer _entry, Integer _exit) {
				System.out.println("--- found a bond !!!");
				Integer fragId = _graph.addVertex("fragment" + fragment++);
				Set<Integer> vertices_ = new HashSet<Integer>();
				Set<Edge> edges_ = new HashSet<Edge>();
				
				Integer first = gateways.get(_entry);
				if (first == null) {
					first = graph.addVertex(_graph.getLabel(_entry) + gateway++);
					DNode n = (DNode)unfhelper.gatewayType(_entry);
					if (n.isEvent)
						helper.setANDGateway(first);
					else
						helper.setXORGateway(first);
					System.out.println("GW: " + graph.getLabel(first));
					gateways.put(_entry, first);
				}
				Integer last = gateways.get(_exit);
				if (last == null) {
					last = graph.addVertex(_graph.getLabel(_exit) + gateway++);
					DNode n = (DNode)unfhelper.gatewayType(_exit);
					if (n.isEvent)
						helper.setANDGateway(last);
					else
						helper.setXORGateway(last);
					System.out.println("GW: " + graph.getLabel(last));
					gateways.put(_exit, last);
				}
				
				for (Integer childId: _vertices) {
					if (childId == _entry || childId == _exit) continue;
					Pair pair1 = fragEntries.get(childId);
					Pair pair2 = fragExits.get(childId);
					if (pair1.getFirst().equals(_entry)) {
						if (pair1.getSecond() != null) {
							edges_.add(new Edge(first, pair1.getSecond()));
							edges_.add(new Edge(pair2.getSecond(), last));
						} else
							edges_.add(new Edge(first, last));
					} else {
						if (pair1.getSecond() != null) {
							edges_.add(new Edge(last, pair1.getSecond()));
							edges_.add(new Edge(pair2.getSecond(), first));
						} else
							edges_.add(new Edge(last, first));
					}
				}
				
				fragEntries.put(fragId, new Pair(_entry, first));
				fragExits.put(fragId, new Pair(_exit, last));

				_edges.clear();
				_vertices.clear();
				_vertices.add(_entry); _vertices.add(fragId); _vertices.add(_exit);
				_edges.add(new Edge(_entry, fragId)); _edges.add(new Edge(fragId, _exit));
				System.out.println("Entry: " + _graph.getLabel(_entry) + ", exit: " + _graph.getLabel(_exit));
				for (Edge e: edges_) {
					vertices_.add(e.getSource());
					vertices_.add(e.getTarget());
				}
				vertices.addAll(vertices_);
				edges.addAll(edges_);
			}
		});
		restructurer.process(System.out);

		
//		DJGraph djgraph = new DJGraph(subgraph,
//				edgelist2adjlist(new HashSet<Edge>(subgraph.getEdges()), subgraph.getSinkNodes().iterator().next()),
//				subgraph.getSourceNodes().iterator().next());
//		
//		djgraph.identifyLoops(new DJGraphHelper() {
//			
//			public List<Integer> processSEME(Set<Integer> loopbody) {
//				// TODO Auto-generated method stub
//				return null;
//			}
//			
//			public List<Integer> processMEME(Set<Integer> loopbody) {
//				// TODO Auto-generated method stub
//				return null;
//			}
//		});
		
//		System.exit(0);

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
	 * Information about nested cycles (Restructuring cyclic rigid components)
	 */
	class CycleInfo {
		Set<Edge> edges;
		Set<Integer> vertices;
		Integer entry;
		public CycleInfo(Set<Integer> vertices, Set<Edge> edges,
				Integer entry) {
			this.vertices = vertices;
			this.edges = edges;
			this.entry = entry;
		}
	}

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
