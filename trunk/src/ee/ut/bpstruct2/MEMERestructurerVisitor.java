package ee.ut.bpstruct2;

import hub.top.petrinet.PetriNet;
import hub.top.uma.DNode;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import de.hpi.bpt.hypergraph.abs.Vertex;
import de.hpi.bpt.process.Node;
import de.hpi.bpt.process.Process;
import de.hpi.bpt.process.petri.PNSerializer;
import ee.ut.bpstruct.CannotStructureException;
import ee.ut.bpstruct2.UnfoldingRestructurer;
import ee.ut.bpstruct2.UnfoldingHelper;
import ee.ut.bpstruct2.jbpt.Pair;
import ee.ut.bpstruct2.util.DFSLabeler;
import ee.ut.bpstruct2.util.GraphUtils;

public class MEMERestructurerVisitor extends RestructurerVisitor {
	
	private Helper helper;

	public MEMERestructurerVisitor(Helper helper) {
		super(helper);
		this.helper = helper;
	}
	
	public void visitRigid(Process proc, Set<Pair> edges,
			Set<Node> vertices, Node entry, Node exit) throws CannotStructureException {
		System.out.println("Found a rigid");
		Map<Node, List<Node>> adjlist = GraphUtils.edgelist2adjlist(edges, exit);
		DFSLabeler labeler =  new DFSLabeler(adjlist, entry);

		if (labeler.isCyclic()) {
//			restructureCyclicRigid(proc, edges, vertices, entry, exit);
			throw new CannotStructureException("Cyclic structuring is not supported for MEME models");
		} else
			restructureAcyclicRigid(proc, edges, vertices, entry, exit, adjlist);
	}

	public void restructureAcyclicRigid(Process proc, Set<Pair> edges,
			Set<Node> vertices, Node entry, Node exit, Map<Node, List<Node>> adjlist) throws CannotStructureException {
		System.out.println("\tAcyclic rigid");
		PetriNet net = helper.petrify(edges, vertices, entry, exit);
		MEMEUnfolder unfolder = new MEMEUnfolder(net);
			Unfolding unf = unfolder.perform();
	
			final Map<String, Node> tasks = new HashMap<String, Node>();		
			for (Node vertex: vertices)
				if (helper.getLabeledElements().contains(vertex))
					tasks.put(vertex.getName(), vertex);
	
			helper.synthesizeFromOrderingRelations(proc, edges, vertices, entry, exit,
					unf, tasks);
	}

}
