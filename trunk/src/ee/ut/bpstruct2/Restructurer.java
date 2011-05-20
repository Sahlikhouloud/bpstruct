package ee.ut.bpstruct2;

import hub.top.petrinet.PetriNet;
import hub.top.petrinet.Place;
import hub.top.petrinet.Transition;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.hpi.bpt.graph.abs.AbstractDirectedEdge;
import de.hpi.bpt.graph.algo.rpst.RPST;
import de.hpi.bpt.graph.algo.rpst.RPSTNode;
import de.hpi.bpt.graph.algo.tctree.TCType;
import de.hpi.bpt.process.ControlFlow;
import de.hpi.bpt.process.Gateway;
import de.hpi.bpt.process.GatewayType;
import de.hpi.bpt.process.Node;
import de.hpi.bpt.process.Process;
import de.hpi.bpt.process.Task;
import de.hpi.bpt.process.serialize.Process2DOT;
import ee.ut.bpstruct.CannotStructureException;
import ee.ut.bpstruct2.BehavioralProfiler;
import ee.ut.bpstruct2.util.DFSLabeler;
import ee.ut.bpstruct2.util.GraphUtils;
import ee.ut.bpstruct2.jbpt.Pair;
import ee.ut.bpstruct2.jbpt.PlaceHolder;
import ee.ut.graph.moddec.ColoredGraph;
import ee.ut.graph.moddec.MDTNode;
import ee.ut.graph.moddec.MDTVisitor;
import ee.ut.graph.moddec.ModularDecompositionTree;

public class Restructurer {
	public Process proc;
	Set<Node> labeledElements = new HashSet<Node>();

	public Restructurer(Process proc) {
		this.proc = proc;
	}

	public boolean perform() {
		labeledElements.clear();
		labeledElements.addAll(proc.getTasks());

		RPST<ControlFlow, Node> rpst = new RPST<ControlFlow, Node>(proc);

		if (rpst.getVertices(TCType.R).size() >= 0) {
			RPSTNode<ControlFlow, Node> root = rpst.getRoot();
			Set<Pair> edges = flattenEdgeSet(root.getFragment().getEdges());
			Set<Node> vertices = new HashSet<Node>(root.getFragment().getVertices());
			try {
				traverse(rpst, root, edges, vertices);
				proc = installStructured(edges, vertices, root);
			} catch (CannotStructureException e) {
				return false;
			}
		}
		return true;
	}

	private Set<Pair> flattenEdgeSet(Collection<ControlFlow> edges) {
		Set<Pair> set = new HashSet<Pair>();
		for (AbstractDirectedEdge<Node> flow: edges)
			set.add(new Pair(flow.getSource(), flow.getTarget()));
		return set;
	}

	private Process installStructured(Set<Pair> edges,
			Set<Node> vertices, RPSTNode<ControlFlow, Node> root) {
		Process nproc = new Process();
		Pair pair = new Pair();
		installStructured(nproc, edges, vertices, root.getEntry(), root.getExit(), pair);
		
		edges = flattenEdgeSet(nproc.getEdges());
		
		
		Map<Node, List<Node>> adjlist = GraphUtils.edgelist2adjlist(edges, pair.getSecond());
		Map<Node, ControlFlow> toremove = new HashMap<Node, ControlFlow>();
		for (Gateway gw: nproc.getGateways())
			if (nproc.getIncomingEdges(gw).size() == 1 && nproc.getOutgoingEdges(gw).size() == 1)
				toremove.put(gw, nproc.getIncomingEdges(gw).iterator().next());
		
		simplify(adjlist, pair.getFirst(), toremove, nproc, new HashSet<Node>(), pair.getSecond());
		
		nproc.removeVertices(toremove.keySet());
		
		return nproc;
	}

	/**
	 * This method does a depth-first traversal to update control flow so as to skip superfluous gateways.
	 */
	protected void simplify(Map<Node, List<Node>> adjlist, Node curr,
			Map<Node, ControlFlow> toremove, Process nproc, Set<Node> visited, Node last) {
		visited.add(curr);
		if (!toremove.containsKey(curr))
			last = curr;
		for (Node succ: adjlist.get(curr)) {
			if (toremove.containsKey(succ))
				nproc.removeEdge(toremove.get(succ));
			else
				nproc.addControlFlow(last, succ);
			if (!visited.contains(succ))
				simplify(adjlist, succ, toremove, nproc, visited, last);
		}
	}

	private void installStructured(Process nproc,
			Set<Pair> edges, Set<Node> vertices, Node entry, Node exit, Pair pair) {
		Map<Node, Pair> lmap = new HashMap<Node, Pair>();

		for (Node v: vertices) {
			System.out.println("Analyzing: " + v);
			if (v instanceof PlaceHolder) {
				PlaceHolder pholder = (PlaceHolder) v;
				Pair cpair = new Pair();
				installStructured(nproc, pholder.getEdges(), pholder.getVertices(), pholder.getEntry(), pholder.getExit(), cpair);
				lmap.put(v, cpair);
			} else {
				Node nv = null;
				if (v instanceof Gateway)
					nv = new Gateway(((Gateway)v).getGatewayType(), v.getName());
				else 
					nv = new Task(v.getName(), v.getDescription());
				nproc.addVertex(nv);
				lmap.put(v, new Pair(nv, nv));
			}
		}
		for (Pair e: edges) {
			if (lmap.containsKey(e.getSource()) && lmap.containsKey(e.getTarget())) {
				Node src = lmap.get(e.getSource()).getSecond();
				Node tgt = lmap.get(e.getTarget()).getFirst();
				nproc.addControlFlow(src, tgt);
			}
		}
		pair.setFirst(lmap.get(entry).getFirst());
		pair.setSecond(lmap.get(exit).getSecond());
	}

	private void traverse(RPST<ControlFlow, Node> rpst, RPSTNode<ControlFlow, Node> current,
			Set<Pair> edges, Set<Node> vertices) throws CannotStructureException {
		if (current.getType() == TCType.T) return;

		for (RPSTNode<ControlFlow, Node> child: rpst.getChildren(current)) {
			if (child.getType() == TCType.T) continue;
			Set<Pair> ledges = flattenEdgeSet(child.getFragment().getEdges());
			Set<Node> lvertices = new HashSet<Node>(child.getFragment().getVertices());
			Set<Pair> cledges = new HashSet<Pair>(ledges);
			traverse(rpst, child, ledges, lvertices);
			Node entry = child.getEntry();
			Node exit = child.getExit();
			switch (child.getType()) {
			case P:
				visitPolygon(ledges, lvertices, entry, exit);
				break;
			case B:
				visitBond(ledges, lvertices, entry, exit);
				break;
			case R:
				visitRigid(ledges, lvertices, entry, exit);
				break;
			}
			edges.removeAll(cledges);
			vertices.removeAll(child.getFragment().getVertices());
			edges.addAll(ledges);
			vertices.addAll(lvertices);
		}
	}

	private void visitRigid(Set<Pair> ledges,
			Set<Node> vertices, Node entry, Node exit) throws CannotStructureException {
		System.out.println("Found a rigid");
		Map<Node, List<Node>> adjlist = GraphUtils.edgelist2adjlist(ledges, exit);
		DFSLabeler labeler =  new DFSLabeler(adjlist, entry);

		if (labeler.isCyclic())
			restructureCyclicRigid(ledges, vertices, entry, exit);
		else
			restructureAcyclicRigid(ledges, vertices, entry, exit, adjlist);
	}

	private void restructureCyclicRigid(Set<Pair> ledges,
			Set<Node> vertices, Node entry, Node exit) {
		System.out.println("\tCyclic rigid");

		foldComponent(ledges, vertices, entry, exit);
	}

	private void restructureAcyclicRigid(Set<Pair> ledges,
			Set<Node> vertices, Node entry, Node exit, Map<Node, List<Node>> adjlist) throws CannotStructureException {
		System.out.println("\tAcyclic rigid");
		PetriNet net = petrify(ledges, vertices, entry, exit);
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
			if (labeledElements.contains(vertex))
				tasks.put(vertex.getName(), vertex);

		final Map<String, Node> tasksp = new HashMap<String, Node>(tasks);		
		final Map<String, Node> clones = new HashMap<String, Node>();
		BehavioralProfiler prof = new BehavioralProfiler(unf, tasks, clones);
		final ColoredGraph orgraph = prof.getOrderingRelationsGraph();

		// Compute the Modular Decomposition Tree
		ModularDecompositionTree mdec = new ModularDecompositionTree(orgraph);

		for (String label: clones.keySet()) {
			PlaceHolder ph = (PlaceHolder)clones.get(label);
			Node vertexp = new PlaceHolder(ph.getEdges(), ph.getVertices(), ph.getEntry(), ph.getExit());
			// Add code to complete the cloning (e.g. when mapping BPMN->BPEL)
			tasks.put(label, vertexp);
		}

		
		System.out.println(mdec.getRoot());
		
		final Process childProc = new Process();
		final Map<MDTNode, Node> nestedEntry = new HashMap<MDTNode, Node>();
		final Map<MDTNode, Node> nestedExit = new HashMap<MDTNode, Node>();	

		mdec.traversePostOrder(new MDTVisitor() {
			public void visitLeaf(MDTNode node, String label) {
				Node n = tasks.get(label);
				childProc.addVertex(n);
				nestedEntry.put(node, n);
				nestedExit.put(node, n);
			}
			public void visitComplete(MDTNode node, Set<MDTNode> children, int color) {
				GatewayType type = color == 0 ? GatewayType.AND : GatewayType.XOR;
				Gateway _entry = new Gateway(type);
				Gateway _exit = new Gateway(type);
				childProc.addVertex(_entry);
				childProc.addVertex(_exit);
				for (MDTNode child : children) {
					childProc.addControlFlow(_entry, nestedEntry.get(child));
					childProc.addControlFlow(nestedExit.get(child), _exit);
				}
				nestedEntry.put(node, _entry);
				nestedExit.put(node, _exit);				
			}
			public void visitLinear(MDTNode node, List<MDTNode> children) {
				for (int i = 1; i < children.size(); i++) {
					MDTNode _source = children.get(i - 1);
					MDTNode _target = children.get(i);
					Node source = nestedExit.get(_source);
					Node target = nestedEntry.get(_target);
					childProc.addControlFlow(source, target);
				}

				MDTNode _entry = children.get(0);
				MDTNode _exit = children.get(children.size() - 1);
				Node entry = nestedEntry.get(_entry);
				Node exit = nestedExit.get(_exit);

				nestedEntry.put(node, entry);
				nestedExit.put(node, exit);
			}

			public void visitPrimitive(MDTNode node, Set<MDTNode> children)
			throws CannotStructureException {
				Map<Integer, MDTNode> proxies = new HashMap<Integer, MDTNode>();				
				for (MDTNode child: children)
					proxies.put(child.getProxy(), child);
				
				ColoredGraph subgraph = orgraph.subgraph(proxies.keySet());

				///////////// ----------------------   MAXStruct
//				MaxStr maxstr = new MaxStr();
//				maxstr.perform(orgraph, tasksp, clones);
				///////////// ----------------------   MAXStruct
				
				try {
					String filename = String.format("bpstruct2/primitive_%s.dot", proc.getName());
					PrintStream out = new PrintStream(filename);
					out.print(subgraph.toDot());
					out.close();
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}
				
				throw new CannotStructureException("FAIL: Cannot structure acyclic - MDT contains primitive");				
			}
			public void openContext(MDTNode node) {}
			public void closeContext(MDTNode node) {}
		});

		try {
			String filename = String.format("bpstruct2/sub_%s.dot", proc.getName());
			PrintStream out = new PrintStream(filename);
			out.print(Process2DOT.convert(childProc));
			out.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		Node _entry = nestedEntry.get(mdec.getRoot());
		Node _exit = nestedExit.get(mdec.getRoot());

		Node placeHolder = new PlaceHolder(flattenEdgeSet(childProc.getEdges()), (Collection)childProc.getVertices(), _entry, _exit);
		labeledElements.add(placeHolder);
		placeHolder.setName("N"+count++);
		vertices.clear();
		ledges.clear();
		vertices.add(entry); vertices.add(exit); vertices.add(placeHolder);
		ledges.add(new Pair(entry, placeHolder));
		ledges.add(new Pair(placeHolder, exit));		
	}

	private hub.top.petrinet.Node getNode(Node node, PetriNet net, Map<Node, hub.top.petrinet.Node> map) {
		hub.top.petrinet.Node res = map.get(node);
		if (res==null) {
			if (isXORGateway(node) || isORGateway(node))
				res = net.addPlace(node.getName());
			else
				res = net.addTransition(node.getName());			
			map.put(node, res);
		}
		return res;
	}

	public PetriNet petrify(Set<Pair> ledges,
			Set<Node> vertices, Node _entry, Node _exit) {
		Map<Node, hub.top.petrinet.Node> map = new HashMap<Node, hub.top.petrinet.Node>();
		hub.top.petrinet.Node entry = null, exit = null;
		PetriNet net = new PetriNet();

		for (Pair edge : ledges) {
			Node src = edge.getSource();
			Node tgt = edge.getTarget();

			if (labeledElements.contains(src) || isANDGateway(src)) {
				if (labeledElements.contains(tgt) || isANDGateway(tgt)) {
					Transition psrc = (Transition)getNode(src, net, map);
					Transition ptgt = (Transition)getNode(tgt, net, map);
					Place p = net.addPlace(psrc.getName() + "_" + ptgt.getName());
					net.addArc(psrc, p);
					net.addArc(p, ptgt);
				} else if (isXORGateway(tgt)) {
					Transition psrc = (Transition)getNode(src, net, map);					
					Place ptgt = (Place)getNode(tgt, net, map);
					net.addArc(psrc, ptgt);
				}
			} else if (isXORGateway(src)) {
				if (labeledElements.contains(tgt) || isANDGateway(tgt)) {
					Place psrc = (Place)getNode(src, net, map);
					Transition ptgt = (Transition)getNode(tgt, net, map);

					Place pintp = net.addPlace(psrc.getName() + "_p_" + ptgt.getName());
					Transition pintt = net.addTransition(psrc.getName() + "_t_" + ptgt.getName());
					net.addArc(psrc, pintt);
					net.addArc(pintt, pintp);
					net.addArc(pintp, ptgt);
				} else if (isXORGateway(tgt)) {
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
		else if (hasInternalIncoming(_entry, ledges)) {
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

		if (exit instanceof Place && isXORGateway(_exit) && hasInternalOutgoing(_exit, ledges)) {
			Transition t = net.addTransition("_to_exit_");
			Place p = net.addPlace("_exit_");
			net.addArc((Place)exit, t);
			net.addArc(t, p);
		}

		try {
			String filename = String.format("bpstruct2/pnet_%s.dot", proc.getName());
			PrintStream out = new PrintStream(filename);
			out.print(net.toDot());
			out.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		return net;
	}

	private boolean hasInternalIncoming(Node node, Set<Pair> ledges) {
		for (Pair e: ledges)
			if (node.equals(e.getTarget()))
				return true;
		return false;
	}
	private boolean hasInternalOutgoing(Node node, Set<Pair> edges) {
		for (Pair e: edges)
			if (node.equals(e.getSource()))
				return true;
		return false;
	}
	private boolean isANDGateway(Node node) {
		return node instanceof Gateway && ((Gateway)node).getGatewayType() == GatewayType.AND;
	}
	private boolean isXORGateway(Node node) {
		return node instanceof Gateway && ((Gateway)node).getGatewayType() == GatewayType.XOR;
	}
	private boolean isORGateway(Node node) {
		return node instanceof Gateway && ((Gateway)node).getGatewayType() == GatewayType.OR;
	}

	private void visitBond(Set<Pair> ledges,
			Set<Node> vertices, Node entry, Node exit) {
		foldComponent(ledges, vertices, entry, exit);
	}

	private void visitPolygon(Set<Pair> ledges,
			Set<Node> vertices, Node entry, Node exit) {
		foldComponent(ledges, vertices, entry, exit);
	}

	int count = 0;
	public void foldComponent(Set<Pair> ledges,
			Set<Node> vertices, Node entry, Node exit) {
		Node placeHolder = new PlaceHolder(ledges, vertices, entry, exit);
		labeledElements.add(placeHolder);
		placeHolder.setName("N"+count++);
		vertices.clear();
		ledges.clear();
		vertices.add(entry); vertices.add(exit); vertices.add(placeHolder);
		ledges.add(new Pair(entry, placeHolder));
		ledges.add(new Pair(placeHolder, exit));		
	}
}
