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
package ee.ut.bpstruct.bpmn;

import hub.top.petrinet.Node;
import hub.top.petrinet.PetriNet;
import hub.top.petrinet.Place;
import hub.top.petrinet.Transition;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import de.bpt.hpi.graph.Edge;
import de.bpt.hpi.graph.Graph;
import ee.ut.bpstruct.BehavioralProfiler;
import ee.ut.bpstruct.CannotStructureException;
import ee.ut.bpstruct.Petrifier;
import ee.ut.bpstruct.RestructurerHelper;
import ee.ut.bpstruct.unfolding.Unfolding;
import ee.ut.graph.moddec.ColoredGraph;
import ee.ut.graph.moddec.MDTNode;
import ee.ut.graph.moddec.MDTVisitor;
import ee.ut.graph.moddec.ModularDecompositionTree;

public abstract class BPMNHelper implements RestructurerHelper {
	// log4j ---
	static Logger logger = Logger.getLogger(BPMNHelper.class);

	public enum GWType {XOR, AND, OR, EXOR};
	
	protected Graph graph;
	
	protected Map<String, Integer> map;
	protected Map<Integer, String> rmap;
	protected Map<Integer, GWType> gwmap;
	
	protected Set<String> tasks;
	
	protected File debugDir = new File(".");
	
	public BPMNHelper() {
		graph = null;
		map = new HashMap<String, Integer>();
		rmap = new HashMap<Integer, String>();
		gwmap = new HashMap<Integer, GWType>();
		tasks = new HashSet<String>();
	}
	
	protected abstract void initGraph();
	
	public Object getModelElementId(Integer vertex) { return rmap.get(vertex); }
	public boolean isParallel(Integer vertex) { return gwmap.get(vertex) != null && gwmap.get(vertex).equals(GWType.AND); }
	public boolean isChoice(Integer vertex) { return gwmap.get(vertex) != null && gwmap.get(vertex).equals(GWType.XOR); }

	public Graph getGraph() {
		if (graph == null) initGraph();
		return graph;
	}
	
	public void setXORGateway(Integer vertex) {
		gwmap.put(vertex, GWType.XOR);
	}
	
	public void setANDGateway(Integer vertex) {
		gwmap.put(vertex, GWType.AND);
	}

	public Petrifier getPetrifier(final Set<Integer> vertices, final Set<Edge> edges, final Integer _entry, final Integer _exit) {
		return new Petrifier() {
			
			private Map<String, Integer> prmap = new HashMap<String, Integer>();

			private Node getNode(Integer node, PetriNet net, Map<Integer, Node> map) {
				Node res = map.get(node);
				if (res==null) {
					if (gwmap.get(node) == GWType.XOR || gwmap.get(node) == GWType.OR)
						res = net.addPlace(graph.getLabel(node));
					else 
						res = net.addTransition(graph.getLabel(node));	
					
					map.put(node, res);
					prmap.put(graph.getLabel(node), node);
				}
				return res;
			}

			public PetriNet petrify() {
				Map<Integer, Node> map = new HashMap<Integer, Node>();
				Node entry = null, exit = null;
				PetriNet net = new PetriNet();
				
				for (Edge edge : edges) {
					Integer src = edge.getSource();
					Integer tgt = edge.getTarget();
								
					if (gwmap.get(src) == null || gwmap.get(src) == GWType.AND) {
						if (gwmap.get(tgt) == null || gwmap.get(tgt) == GWType.AND) {
							Transition psrc = (Transition)getNode(src, net, map);
							Transition ptgt = (Transition)getNode(tgt, net, map);
							Place p = net.addPlace(psrc.getName() + "_" + ptgt.getName());
							net.addArc(psrc, p);
							net.addArc(p, ptgt);
						} else if (gwmap.get(tgt) == GWType.XOR) {
							Transition psrc = (Transition)getNode(src, net, map);					
							Place ptgt = (Place)getNode(tgt, net, map);
							net.addArc(psrc, ptgt);
						}
					} else if (gwmap.get(src) == GWType.XOR) {
						if (gwmap.get(tgt) == null || gwmap.get(tgt) == GWType.AND) {
							Place psrc = (Place)getNode(src, net, map);
							Transition ptgt = (Transition)getNode(tgt, net, map);

							Place pintp = net.addPlace(psrc.getName() + "_p_" + ptgt.getName());
							Transition pintt = net.addTransition(psrc.getName() + "_t_" + ptgt.getName());
							net.addArc(psrc, pintt);
							net.addArc(pintt, pintp);
							net.addArc(pintp, ptgt);							
						} else if (gwmap.get(tgt) == GWType.XOR) {
							Place psrc = (Place)getNode(src, net, map);
							Place ptgt = (Place)getNode(tgt, net, map);
							Transition inter = net.addTransition(psrc.getName() + "_" + ptgt.getName());
							net.addArc(psrc, inter);
							net.addArc(inter, ptgt);
						}
					}
				}

				// fix entry/exit
				entry = getNode(_entry, net, map);
				exit = getNode(_exit, net, map);
				
				if (entry instanceof Transition) {
					Place p = net.addPlace("_entry_");
					net.addArc(p, (Transition)entry);
					net.setTokens(p, 1);
				}
				else if (graph.isJoin(_entry)) {
					Place p = net.addPlace("_entry_");
					Transition t = net.addTransition("_from_entry_");
					
					net.addArc(p, t);
					net.addArc(t, (Place)entry);
					net.setTokens(p, 1);
				} else
					net.setTokens((Place)entry, 1);
				
				if (exit instanceof Transition) {
					Place p = net.addPlace("_exit_");
					net.addArc((Transition)exit, p);
				}
				
				if (exit instanceof Place && graph.isSplit(_exit) && gwmap.get(_exit) == GWType.XOR) {
					Transition t = net.addTransition("_to_exit_");
					Place p = net.addPlace("_exit_");
					net.addArc((Place)exit, t);
					net.addArc(t, p);
				}
				
				if (logger.isTraceEnabled()) {
					try {
						String filename = String.format(getDebugDir().getName() + "/pnet_%d.dot", System.currentTimeMillis());
						PrintStream out = new PrintStream(filename);
						out.print(net.toDot());
						out.close();
						logger.trace("Petri net serialized into: " + filename);
					} catch (FileNotFoundException e) {
						logger.error(e);
					}
				}
				
				return net;
			}
		};
	}
	
	public Object gatewayType(Integer vertex) {
		return gwmap.get(vertex);
	}
	
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
			Integer vertex = graph.addVertex(label);
			// Add code to complete the cloning (e.g. when mapping BPMN->BPEL)
			tasks.put(label, vertex);
		}

		// STEP 4: Synthesize structured version from MDT
		synthesizeFromMDT(vertices, edges, entry, exit, mdec, tasks);
	}


	public void synthesizeFromMDT(final Set<Integer> vertices, final Set<Edge> edges,
			final Integer entry, final Integer exit, final ModularDecompositionTree mdec,
			final Map<String, Integer> tasks) throws CannotStructureException {
		final Map<MDTNode, Integer> nestedEntry = new HashMap<MDTNode, Integer>();
		final Map<MDTNode, Integer> nestedExit = new HashMap<MDTNode, Integer>();	

		mdec.traversePostOrder(new MDTVisitor() {

			public void visitPrimitive(MDTNode node, Set<MDTNode> children) throws CannotStructureException {
				throw new CannotStructureException("FAIL: Cannot structure acyclic - MDT contains primitive");
			}

			public void visitLeaf(MDTNode node, String label) {
				int vertex = tasks.get(label);
				vertices.add(vertex);
				nestedEntry.put(node, vertex);
				nestedExit.put(node, vertex);				
			}

			public void visitComplete(MDTNode node, Set<MDTNode> children, int color) {
				int _entry = addGateway(vertices, color == 0 ? GWType.AND : GWType.XOR);
				int _exit = addGateway(vertices, color == 0 ? GWType.AND : GWType.XOR);
				for (MDTNode child : children) {
					edges.add(new Edge(_entry, nestedEntry.get(child)));
					edges.add(new Edge(nestedExit.get(child), _exit));
				}
				nestedEntry.put(node, _entry);
				nestedExit.put(node, _exit);				
			}

			public void visitLinear(MDTNode node, List<MDTNode> children) {
				for (int i = 1; i < children.size(); i++) {
					MDTNode _source = children.get(i - 1);
					MDTNode _target = children.get(i);
					Integer source = nestedExit.get(_source);
					Integer target = nestedEntry.get(_target);

					edges.add(new Edge(source, target));	
				}

				MDTNode _entry = children.get(0);
				MDTNode _exit = children.get(children.size() - 1);
				Integer entry = nestedEntry.get(_entry);
				Integer exit = nestedExit.get(_exit);

				nestedEntry.put(node, entry);
				nestedExit.put(node, exit);
			}

			public void closeContext(MDTNode node) {}

			public void openContext(MDTNode node) {}
			
			private Integer addGateway(Set<Integer> vertices, GWType gwType) {
				Integer gw;
				gw = graph.addVertex(gwType.name() + graph.getVertices().size());
				gwmap.put(gw, gwType);
				vertices.add(gw);
				return gw;
			}
		});

		edges.add(new Edge(entry, nestedEntry.get(mdec.getRoot())));
		edges.add(new Edge(nestedExit.get(mdec.getRoot()), exit));

	}

	public String toDot(Set<Integer> vertices, Set<Edge> edges) {
		ByteArrayOutputStream outstream = new ByteArrayOutputStream();
		PrintStream out = new PrintStream(outstream);
		serializeDot(out, vertices, edges);
		return outstream.toString();
	}
	
	public void serializeDot(PrintStream out, Set<Integer> vertices, Set<Edge> edges) {
		out.println("digraph G {");
		for (Integer v: vertices) {
			if (gwmap.get(v) == GWType.AND)
				out.printf("\t%s [shape=diamond,label=\"+\"];\n", graph.getLabel(v));
			else if (gwmap.get(v) == GWType.XOR)
				out.printf("\t%s [shape=diamond,label=\"X\"];\n", graph.getLabel(v));
			else
				out.printf("\t%s [shape=box,style=rounded,label=\"%s\"];\n", graph.getLabel(v), graph.getLabel(v));
		}
		for (Edge e: edges) {
			if (e.getSource() == null || e.getTarget() == null) continue;
			if (!vertices.contains(e.getSource()))
				continue;
			if (!vertices.contains(e.getTarget())) {
				for (Edge e2: edges) {
					if (e2.getSource() == e.getTarget()) {
						out.printf("\t%s -> %s;\n", graph.getLabel(e.getSource()), graph.getLabel(e2.getTarget()));
						break;
					}
				}
			} else
				out.printf("\t%s -> %s;\n", graph.getLabel(e.getSource()), graph.getLabel(e.getTarget()));
		}
		out.println("}");				
	}
	
	public boolean isTask(String name) {
		return tasks.contains(name);
	}
	
	public File getDebugDir() {
		return debugDir;
	}

	public void setDebugDir(File debugDir) {
		this.debugDir = debugDir;
	}
}
