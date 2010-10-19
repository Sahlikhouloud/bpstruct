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

import org.apache.log4j.Logger;

import de.bpt.hpi.graph.Edge;
import de.bpt.hpi.graph.Graph;
import de.bpt.hpi.graph.Pair;
import ee.ut.bpstruct.BehavioralProfiler;
import ee.ut.bpstruct.CannotStructureException;
import ee.ut.bpstruct.RestructurerHelper;
import ee.ut.bpstruct.Visitor;
import ee.ut.graph.moddec.ColoredGraph;
import ee.ut.graph.moddec.ModularDecompositionTree;

public class UnfoldingRestructurerVisitor implements Visitor {
	static Logger logger = Logger.getLogger(UnfoldingRestructurerVisitor.class);

	protected RestructurerHelper helper;
	protected UnfoldingHelper unfhelper;
	protected Integer entry;
	protected Map<String, Integer> tasks;
	protected Map<String, Integer> labels;
	protected Integer exit;
	protected Map<Integer, Stack<Integer>> instances;
	protected Set<Integer> vertices;
	protected Graph graph;
	protected Set<Edge> edges;
	protected Map<Integer, Pair> fragEntries = new HashMap<Integer, Pair>();
	protected Map<Integer, Pair> fragExits = new HashMap<Integer, Pair>();
	protected Map<Integer, Integer> gateways = new HashMap<Integer, Integer>();
	protected Map<Integer, Pair> nullFragments = new HashMap<Integer, Pair>();
	protected int fragment = 0;
	protected int gateway = 0;
	
	public UnfoldingRestructurerVisitor(RestructurerHelper helper,
			UnfoldingHelper unfhelper, Graph graph, Set<Integer> vertices,
			Set<Edge> edges, Integer entry, Integer exit,
			Map<String, Integer> tasks, Map<String, Integer> labels, Map<Integer, Stack<Integer>> instances) {
		this.helper = helper;
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

	protected void processOrderingRelations(Set<Edge> edges,
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
//				System.out.print(label + ".");
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

	protected Integer testAndClone(Graph graph, Map<String, Integer> tasks,
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
		DNode boundary = (DNode) unfhelper.gatewayType(_entry);
		if (boundary.isEvent)
			processANDRigid(_graph, _edges, _vertices, _entry, _exit);
		else 
			processXORCyclicRigid(_graph, _edges, _vertices, _entry, _exit);
	}

	protected void processXORCyclicRigid(Graph _graph, Set<Edge> _edges,
			Set<Integer> _vertices, Integer _entry, Integer _exit) {
		System.out.println("--- unstructured loop");
		
		try {
			PrintStream out = new PrintStream(new File(String.format("debug/partial_%d.dot", UnfoldingRestructurer.counter++)));
			out.println(helper.toDot(vertices, edges));
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
	}

	protected void processANDRigid(Graph _graph, Set<Edge> _edges,
			Set<Integer> _vertices, Integer _entry, Integer _exit) throws CannotStructureException {
		System.out.println("--- AND rigid");
		Map<Integer, Integer> linstances = new HashMap<Integer, Integer>();

		Unfolding inner = unfhelper.extractSubnet(_edges, _vertices, _entry, _exit);
		System.out.println(inner.toDot());
		
		_edges.clear();
		_vertices.clear();
		Integer entry = graph.addVertex(_graph.getLabel(_entry));
		Integer exit = graph.addVertex(_graph.getLabel(_exit));
		processOrderingRelations(_edges, _vertices, entry, exit, graph, inner, tasks);
		_vertices.add(entry); _vertices.add(exit);
		System.out.println(helper.toDot(_vertices, _edges));

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
}