package ee.ut.bpstruct2.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.hpi.bpt.process.ControlFlow;
import de.hpi.bpt.process.Gateway;
import de.hpi.bpt.process.Node;
import de.hpi.bpt.process.Process;
import de.hpi.bpt.process.Task;

public class ProcessUtils {
	
	Map<String, ControlFlow> materializedFlow = new HashMap<String, ControlFlow>();
	
	/**
	 * This is a quite important method. It adds a graph node representing XOR split outgoing edges.
	 * Though it may seem a hacking, this operation allow us to ensure that conflict relation is
	 * preserved when restructuring Rigid components (e.g. while computing ordering relations graphs
	 * for modular decomposition).
	 * I hesitated to do it in this way, but this provides a "hook" where we can attach metadata such
	 * as predicates associated to outgoing flow from XOR splits, branching probabilities for simulation
	 * or QoS computation, etc.
	 * Besides, I decided to use a separated method (meaning that we iterate one additional time over
	 * the XML document) for readability reasons and to ease its removal (e.g. if somebody found
	 * another way to solve the same problem -- a proper one :p ).
	 */
	public void materializeDecisions(Process proc) {
		int counter = 0;
		for (Gateway gw: proc.getGateways()) {
			if (gw.isXOR() && proc.getOutgoingEdges(gw).size() > 1) {
				for (ControlFlow outflow: proc.getOutgoingEdges(gw)) {
					String label = String.format("_flow_%d_", counter++);
					Task task = new Task(label);
					proc.addTask(task);
					Node succ = outflow.getTarget();
					proc.addControlFlow(task, succ);
					outflow.setTarget(task);
					materializedFlow.put(label, outflow);
				}
			}
		}
	}
	
	public void dematerializeDecisions(Process proc) {
		Set<Node> nodestoremove = new HashSet<Node>();
		Set<ControlFlow> flowstoremove = new HashSet<ControlFlow>();
		
		for (Task task: proc.getTasks())
			if (materializedFlow.containsKey(task.getName()))
				nodestoremove.add(task);
		
		for (Node node: nodestoremove) {
			ControlFlow in = proc.getIncomingEdges(node).iterator().next();
			ControlFlow out = proc.getOutgoingEdges(node).iterator().next();
			if (in == null || out == null) continue;
			
			ControlFlow old = materializedFlow.get(node.getName());
			
			ControlFlow flow = proc.addControlFlow(in.getSource(), out.getTarget());
			
			flow.setLabel(old.getLabel());
			
			flowstoremove.add(in);
			flowstoremove.add(out);
		}
		proc.removeVertices(nodestoremove);
		proc.removeControlFlows(flowstoremove);
	}

}
