package ee.ut.bpstruct.unfolding;

import hub.top.uma.DNode;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;

import org.apache.log4j.Logger;

import de.bpt.hpi.graph.Edge;
import de.bpt.hpi.graph.Graph;
import de.bpt.hpi.graph.Pair;
import ee.ut.bpstruct.AbstractRestructurerHelper;
import ee.ut.bpstruct.CannotStructureException;
import ee.ut.bpstruct.RestructurerHelper;
import ee.ut.bpstruct.Visitor;
import ee.ut.bpstruct.Helper.BLOCK_TYPE;

public class UnfoldingRestructurerVisitor implements Visitor {
	static Logger logger = Logger.getLogger(UnfoldingRestructurerVisitor.class);

	protected AbstractRestructurerHelper helper;
	protected UnfoldingHelper unfhelper;
	protected Map<String, Integer> tasks;
	protected Map<String, Integer> labels;
	protected Integer entry;
	protected Integer exit;
	protected Map<Integer, Stack<Integer>> instances;
	protected Set<Integer> vertices;
	protected Graph graph;
	protected Set<Edge> edges;
	protected Map<Integer, Integer> gateways = new HashMap<Integer, Integer>();
	protected Map<Integer, Integer> fragments = new HashMap<Integer, Integer>();
	protected int fragment = 0;
	protected int gateway = 0;
	
	public UnfoldingRestructurerVisitor(RestructurerHelper helper,
			UnfoldingHelper unfhelper, Graph graph, Set<Integer> vertices,
			Set<Edge> edges, Integer entry, Integer exit,
			Map<String, Integer> tasks, Map<String, Integer> labels, Map<Integer, Stack<Integer>> instances) {
		this.helper = (AbstractRestructurerHelper)helper;
		this.unfhelper = unfhelper;
		this.entry = entry;
		this.tasks = tasks;
		this.labels = labels;
		this.exit = exit;
		this.instances = instances;
		this.vertices = vertices;
		this.graph = graph;
		this.edges = edges;
	}

	public void visitSNode(Graph _graph, Set<Edge> _edges, Set<Integer> _vertices,
			Integer _entry, Integer _exit) {
		System.out.println("--- found a sequence !!!");
		System.out.println("Entry: " + _graph.getLabel(_entry) + ", exit: " + _graph.getLabel(_exit));
		Integer fragId = _graph.addVertex("fragment" + fragment++);
		
		Map<Integer, Integer> successor = new HashMap<Integer, Integer>();
		for (Edge e: _edges) successor.put(e.getSource(), e.getTarget());
	
		Set<Integer> tmpVertices = new HashSet<Integer>();
		Set<Edge> tmpEdges = new HashSet<Edge>();
		
		Integer first = null;
		Integer last = null;
		Integer _curr = _entry;
		
		while (_curr != _exit) {
			String label = _graph.getLabel(_curr);
//			System.out.print(label + ".");
			if (tasks.containsKey(label)) {
				
				// --- This happens in the external graph
				Integer curr = testAndClone(graph, tasks, null,
						label, _curr);
				
				tmpVertices.add(curr);
				
				System.out.printf(" %s", graph.getLabel(curr));
				
				if (first == null)
					first = curr;
				else
					tmpEdges.add(new Edge(last, curr));     //// <<<<<<< HERE
				last = curr;
				// ----
			} else if (fragments.containsKey(_curr)) {
				System.out.printf(" %s", label);
				
				// --- This happens in the external graph
				Integer curr = fragments.get(_curr);
				
				tmpVertices.add(curr); /// TODO: Clone block vertex ?
				
				System.out.printf("[%s]", graph.getLabel(curr));
				if (curr != null) {
					if (first == null)
						first = curr;
					else
						tmpEdges.add(new Edge(last, curr));     //// <<<<<<< HERE
					last = curr;
				} else {
					System.out.println("oops !!!");
				}
				// ----
			}
			_curr = successor.get(_curr);
		}
		System.out.println();
						
		if (first == null) // There is a direct edge from entry to exit nodes (or vice versa)
			fragments.put(fragId, null);
		else {
			Integer blockId = helper.foldComponent(graph, tmpEdges, tmpVertices, first, last, BLOCK_TYPE.POLYGON);
			fragments.put(fragId, blockId);
		}
		
		_edges.clear();
		_vertices.clear();
		_vertices.add(_entry); _vertices.add(fragId); _vertices.add(_exit);
		_edges.add(new Edge(_entry, fragId)); _edges.add(new Edge(fragId, _exit));
	}

	protected Integer testAndClone(Graph graph, Map<String, Integer> tasks,
			Map<Integer, Integer> linstances, String label, Integer _curr) {
				
		Integer overtex, curr = overtex = tasks.get(label);
		
		if (curr == null) {
			if (!linstances.containsKey(_curr)) {
				curr = graph.addVertex(graph.getLabel(_curr));
				if (helper.isChoice(_curr)) helper.setXORGateway(curr);
				else helper.setANDGateway(curr);
				linstances.put(_curr, curr);
			} else
				curr = linstances.get(_curr);
			return curr;
		}
		
		
		if (linstances != null && linstances.containsKey(_curr))
			return linstances.get(_curr);
		if (instances.containsKey(curr)) {
			Stack<Integer> ins = instances.get(curr);
			
			curr = graph.addVertex(label + "_" + labels.get(label));
			labels.put(label, labels.get(label) + 1);
			
			ins.push(curr);
			
			helper.recordBlockClone(overtex, curr);
			
		} else {
			Stack<Integer> ins = new Stack<Integer>();
			
			if (!labels.containsKey(label))
				labels.put(label, 0);
			else {
				curr = graph.addVertex(label + "_" + labels.get(label));
				helper.recordBlockClone(overtex, curr);
				labels.put(label, labels.get(label) + 1);
			}

			ins.push(curr);
			instances.put(curr, ins);
		}
		return curr;
	}

	public void visitPNode(Graph _graph, Set<Edge> _edges, Set<Integer> _vertices,
			Integer _entry, Integer _exit) {
		System.out.println("--- found a bond !!!");
		Integer fragId = _graph.addVertex("fragment" + fragment++);
		Set<Integer> tmpVertices = new HashSet<Integer>();
		Set<Edge> tmpEdges = new HashSet<Edge>();
		
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
			if (n == null) {
				// n is null whenever it is the result of a node split (e.g. there is mixed gateway in the original model)
				n = (DNode)unfhelper.gatewayType(_entry); // So ... we copy the same type as the entry node
			}
			
			if (n.isEvent)
				helper.setANDGateway(last);
			else
				helper.setXORGateway(last);
			System.out.println("GW: " + graph.getLabel(last));
			gateways.put(_exit, last);
		}
				
		for (Edge e: _edges) {
			Integer src = null;
			Integer tgt = null;
			if (e.getSource().equals(_entry)) {
				src = first;
				if (e.getTarget().equals(_exit))
					tgt = last;
				else {
					if (fragments.get(e.getTarget()) == null)
						System.out.println("oops ...");
					
					tmpVertices.add(tgt = fragments.get(e.getTarget()));
				}
			} else if (e.getSource().equals(_exit) && e.getTarget().equals(_entry)) {
				src = last;
				tgt = first;
			} else {
				if (e.getSource().equals(_exit)) {
					src = last;
					tmpVertices.add(tgt = fragments.get(e.getTarget()));
				} else {
					tmpVertices.add(src = fragments.get(e.getSource()));
					if (e.getTarget().equals(_entry))
						tgt = first;
					else
						tgt = last;
				}
			}
			
			tmpEdges.add(new Edge(src, tgt));
		}
		
		tmpVertices.add(first);
		tmpVertices.add(last);
		
		try {
			PrintStream out = new PrintStream(new File(String.format("debug/partial_%d.dot", UnfoldingRestructurer.counter++)));
			out.println(helper.toDot(tmpVertices, tmpEdges));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		
		Integer blockId = helper.foldComponent(graph, tmpEdges, tmpVertices, first, last, BLOCK_TYPE.BOND);
		fragments.put(fragId, blockId);

		_edges.clear();
		_vertices.clear();
		_vertices.add(_entry); _vertices.add(fragId); _vertices.add(_exit);
		_edges.add(new Edge(_entry, fragId)); _edges.add(new Edge(fragId, _exit));
		System.out.println("Entry: " + _graph.getLabel(_entry) + ", exit: " + _graph.getLabel(_exit));
	}
	
	public void visitRootSNode(Graph _graph, Set<Edge> _edges,
			Set<Integer> _vertices, Integer _entry, Integer _exit) {
		System.out.println("--- Reached Root!!");
		for (Edge e: _edges) {
			if (e.getSource().equals(_entry)) {
				Integer fragId = e.getTarget();
				Integer blockId = fragments.get(fragId);
				
				edges.add(new Edge(entry, blockId));
				edges.add(new Edge(blockId, exit));
				vertices.add(blockId);
				break;
			}
		}

		vertices.add(entry); vertices.add(exit);
		System.out.println("done");
	}

	public void visitRNode(Graph _graph, Set<Edge> _edges, Set<Integer> _vertices,
			Integer _entry, Integer _exit) throws CannotStructureException {
		System.out.println("--- found a rigid !!!");
		DNode b_entry = (DNode) unfhelper.gatewayType(_entry);
		DNode b_exit = (DNode) unfhelper.gatewayType(_exit);
		if (b_entry.isEvent && b_exit.isEvent)
			processANDRigid(_graph, _edges, _vertices, _entry, _exit);
		else if (!b_entry.isEvent && !b_exit.isEvent)
			processXORCyclicRigid(_graph, _edges, _vertices, _entry, _exit);
		else {
			System.err.println("ERROR: There might be something wrong with the unfolding");
			System.exit(-1);
		}
	}

	protected void processXORCyclicRigid(Graph _graph, Set<Edge> _edges,
			Set<Integer> _vertices, Integer _entry, Integer _exit) {
		System.out.println("--- unstructured loop");
		
//		try {
//			PrintStream out = new PrintStream(new File(String.format("debug/partial_%d.dot", UnfoldingRestructurer.counter++)));
//			out.println(helper.toDot(vertices, edges));
//		} catch (FileNotFoundException e) {
//			e.printStackTrace();
//		}
		
		Map<Integer, Integer> linstances = new HashMap<Integer, Integer>();
		Set<Integer> tmpVertices = new HashSet<Integer>();
		Set<Edge> tmpEdges = new HashSet<Edge>();

		Integer entry = graph.addVertex(_graph.getLabel(_entry)); helper.setXORGateway(entry);
		Integer exit = graph.addVertex(_graph.getLabel(_exit)); helper.setXORGateway(exit);

		Integer fragId = _graph.addVertex("fragment" + fragment++);
		linstances.put(_entry, entry);
		linstances.put(_exit, exit);
		for (Integer v: _vertices) {
			if (v.equals(_entry) || v.equals(_exit)) continue;
			Integer vp = null;
			if (fragments.containsKey(v))
				vp = fragments.get(v);
			else {
				vp = graph.addVertex(_graph.getLabel(v)); helper.setXORGateway(vp);
			}
			linstances.put(v, vp);
			tmpVertices.add(vp);
		}
		tmpVertices.add(entry); tmpVertices.add(exit);
		
		for (Edge e: _edges) {
			Integer src = linstances.get(e.getSource());
			Integer tgt = linstances.get(e.getTarget());
			tmpEdges.add(new Edge(src, tgt));
		}
		
		try {
			PrintStream out = new PrintStream(new File(String.format("debug/partial_%d.dot", UnfoldingRestructurer.counter++)));
			out.println(helper.toDot(tmpVertices, tmpEdges));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		
		Integer blockId = helper.foldComponent(graph, tmpEdges, tmpVertices, entry, exit, BLOCK_TYPE.RIGID);
		fragments.put(fragId, blockId);

		_edges.clear();
		_vertices.clear();
		_vertices.add(_entry); _vertices.add(fragId); _vertices.add(_exit);
		_edges.add(new Edge(_entry, fragId)); _edges.add(new Edge(fragId, _exit));
	}

	protected void processANDRigid(Graph _graph, Set<Edge> _edges,
			Set<Integer> _vertices, Integer _entry, Integer _exit) throws CannotStructureException {
		System.out.println("--- AND rigid");
		Map<Integer, Integer> linstances = new HashMap<Integer, Integer>();
		Set<Integer> tmpVertices = new HashSet<Integer>();
		Set<Edge> tmpEdges = new HashSet<Edge>();

		Unfolding inner = unfhelper.extractSubnet(_edges, _vertices, _entry, _exit);
//		System.out.println(inner.toDot());
		
		_edges.clear();
		_vertices.clear();
		Integer entry = graph.addVertex(_graph.getLabel(_entry)); helper.setANDGateway(entry);
		Integer exit = graph.addVertex(_graph.getLabel(_exit)); helper.setANDGateway(exit);
		helper.processOrderingRelations(_edges, _vertices, entry, exit, graph, inner, tasks);

		Integer fragId = _graph.addVertex("fragment" + fragment++);
		linstances.put(entry, entry);
		linstances.put(exit, exit);
		for (Integer v: _vertices) {
			if (v.equals(entry) || v.equals(exit)) continue;
			Integer vp = testAndClone(graph, tasks, linstances, graph.getLabel(v), v);
			linstances.put(v, vp);
			tmpVertices.add(vp);
		}
		tmpVertices.add(entry); tmpVertices.add(exit);
		
		for (Edge e: _edges) {
			Integer src = linstances.get(e.getSource());
			Integer tgt = linstances.get(e.getTarget());
			tmpEdges.add(new Edge(src, tgt));
		}
				
		Integer blockId = helper.foldComponent(graph, tmpEdges, tmpVertices, entry, exit, BLOCK_TYPE.RIGID);
		fragments.put(fragId, blockId);

		_edges.clear();
		_vertices.clear();
		_vertices.add(_entry); _vertices.add(fragId); _vertices.add(_exit);
		_edges.add(new Edge(_entry, fragId)); _edges.add(new Edge(fragId, _exit));
	}
}