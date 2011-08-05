package ee.ut.bpstruct2.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import de.hpi.bpt.hypergraph.abs.Vertex;
import de.hpi.bpt.process.Process;
import de.hpi.bpt.process.petri.Node;
import de.hpi.bpt.process.petri.PetriNet;
import de.hpi.bpt.process.petri.Place;
import de.hpi.bpt.process.petri.Transition;
import de.hpi.bpt.process.petri.util.Process2PetriNet;
import de.hpi.bpt.process.petri.util.TransformationException;

public class EqualityChecker {

	/**
	 * Check whether the two Processes behave equally, thus the order of observable transitions is similar.
	 * @param process1
	 * @param process2
	 * @return
	 */
	public static boolean areEqual(Process process1, Process process2) {
		try {
			return areEqual(Process2PetriNet.convert(process1), Process2PetriNet.convert(process2));
		} catch (TransformationException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	/**
	 * Check whether the two PetriNets behave equally, thus the order of observable transitions is similar.
	 * @param net1
	 * @param net2
	 * @return true if both nets behave equally
	 */
	public static boolean areEqual(PetriNet net1, PetriNet net2) {
		setInitialMarking(net1);
		setInitialMarking(net2);
		HashMap<Place, Integer> marking1 = getMarking(net1);
		HashMap<Place, Integer> marking2 = getMarking(net2);
		Set<Vertex> enabled1 = net1.getEnabledElements();
		Set<Vertex> enabled2 = net2.getEnabledElements();
		for (Vertex v:enabled1) {
			setMarking(net1, marking1);
			setMarking(net2, marking2);
			// check every path on its own
			if (!check(net1, net2, v))
				return false;
		}
		// test also the other way
		for (Vertex v:enabled2) {
			setMarking(net1, marking1);
			setMarking(net2, marking2);
			if (!check(net2, net1, v))
				return false;
		}
		return true;
	}
	
	/**
	 * Fires the {@link Transition} with the specified label if it exists.
	 * @param {@link PetriNet} net
	 * @param label
	 * @return true if Transition was fired
	 */
	private static boolean fire(PetriNet net, String label) {
		Set<Vertex> enabled = net.getEnabledElements();
		for (Vertex v:enabled) {
			if (!v.getName().equals("")) {
				// it is no unlabeled transition
				if (v.getName().equals(label)) {
					// we found the transition, we were looking for
					net.fire(v);
					return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * Try to find and fire the transition with the given label.
	 * While searching the transition, fire as much unlabeled transitions as necessary.
	 * @param net
	 * @param label of the transition
	 * @return true if transition was found and fired
	 */
	private static boolean find(PetriNet net, String label) {
		// check if the transition is there
		if (fire(net, label))
			return true;
		// otherwise run some unlabeled transitions
		Set<Vertex> unlabeled = getUnlabeledEnabledTransitions(net);
		HashMap<Place, Integer> marking = getMarking(net);
		for (Vertex v:unlabeled) {
			setMarking(net, marking);
			net.fire(v);
			if (find(net, label))
				return true;
		}
		return false;
	}
	
	/**
	 * Fires the given {@link Vertex} v of net1 and checks if net2 contains 
	 * a similar {@link Vertex} transition, which is enabled.
	 * @param net1 - a {@link PetriNet}
	 * @param net2 - a {@link PetriNet}
	 * @param v - the {@link Vertex} to be fired
	 * @return true if both nets behave similar
	 */
	private static boolean check(PetriNet net1, PetriNet net2, Vertex v) {
		net1.fire(v);
		HashMap<Place, Integer> marking1 = getMarking(net1);
		HashMap<Place, Integer> marking2 = getMarking(net2);
		if (!v.getName().equals("")) {
			// it's a labeled transition
			if (!fire(net2, v.getName()) && !find(net2, v.getName())) {
				// the transition wasn't enabled yet
				// find: trigger some unlabeled transitions and see if the required transition gets enabled
				return false;
			}
			marking2 = getMarking(net2);
		}
		// run next transition in line
		Set<Vertex> enabled = net1.getEnabledElements();
		for (Vertex next:enabled) {
			// reset the net for the next run
			setMarking(net1, marking1);
			setMarking(net2, marking2);
			if (!check(net1, net2, next))
				return false;
		}
		return true;
	}
	
	/**
	 * Returns a set of all unlabeled transitions of the given {@link PetriNet} 
	 * that are currently enabled.
	 * @param net - a {@link PetriNet}
	 * @return set of transitions
	 */
	private static Set<Vertex> getUnlabeledEnabledTransitions(PetriNet net) {
		Set<Vertex> trans = new HashSet<Vertex>();
		for (Vertex v:net.getEnabledElements()) {
			if (v.getName().equals(""))
				trans.add(v);
		}
		return trans;
	}
	
	/**
	 * Returns a map with the current marking (token count).
	 * @param net - a {@link PetriNet}
	 * @return map with marking
	 */
	private static HashMap<Place, Integer> getMarking(PetriNet net) {
		HashMap<Place, Integer> marking = new HashMap<Place, Integer>();
		for (Place p:net.getPlaces()) {
			marking.put(p, p.getTokens());
		}
		return marking;
	}
	
	/**
	 * Set the given net to the given marking.
	 * @param net - a {@link PetriNet}
	 * @param marking
	 */
	private static void setMarking(PetriNet net, HashMap<Place, Integer> marking) {
		for (Place p:net.getPlaces()) {
			Integer value = marking.get(p);
			if (value != null) 
				p.setTokens(value);
			else
				p.setTokens(0);
		}
	}
	
	/**
	 * Initializes all starting places with one token and the rest with zero tokens.
	 * @param {@link PetriNet} net
	 */
	private static void setInitialMarking(PetriNet net) {
		for (Place p:net.getPlaces()) 
			p.setTokens(0);
		for (Node node:net.getSourceNodes()) {
			if (node instanceof Place)
				((Place) node).setTokens(1);
		}
	}
}
