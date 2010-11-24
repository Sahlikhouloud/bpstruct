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
package ee.ut.bpstruct.unfolding;

import hub.top.petrinet.PetriNet;
import hub.top.petrinet.Place;
import hub.top.petrinet.Transition;
import hub.top.uma.DNode;

import java.io.File;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.bpt.hpi.graph.Edge;
import de.bpt.hpi.graph.Graph;
import ee.ut.bpstruct.CannotStructureException;
import ee.ut.bpstruct.Petrifier;
import ee.ut.bpstruct.RestructurerHelper;

public class UnfoldingHelper implements RestructurerHelper {
	private Unfolding unf;

	private Graph graph = new Graph();
	private Map<DNode, Integer> map = new HashMap<DNode, Integer>();
	private Map<Integer, DNode> rmap = new HashMap<Integer, DNode>();
	private DNode exitCond = null;
	
	private PetriNet rewiredNet = new PetriNet();
	
	public UnfoldingHelper(Unfolding unf) {
		this.unf = unf;

	}
	
	private Integer getCondition(DNode n) {
		if (n.post == null) {
			if (exitCond == null)
				exitCond = n;
			n = exitCond;
		}
		
		Integer condition = map.get(n);
		if (condition == null) {
			condition = graph.addVertex(unf.getProperName(n));
			map.put(n, condition);
			rmap.put(condition, n);
		}

		return condition;
	}
	
	public void rewire() {		
		for (DNode _event: unf.getAllEvents()) {
			Integer event = map.get(_event);
			if (event == null) {
				event = graph.addVertex(unf.getProperName(_event));
				map.put(_event, event);
				rmap.put(event, _event);
			}
			
			for (DNode _cond: _event.pre) {
				Integer cond = getCondition(_cond);
				graph.addEdge(cond, event);
			}
			
			DNode _eventp = _event;
			if (_event.isCutOff) _eventp = unf.getCorr(_event);
			for (DNode _cond: _eventp.post) {
				Integer cond = getCondition(_cond);
				graph.addEdge(event, cond);
			}
		}
	}
	
	public PetriNet getRewiredNet() {
		return this.rewiredNet;
	}
	
	public void rewireNet(RestructurerHelper helper) {
		Map<Integer,Place> i2p = new HashMap<Integer, Place>();
		Map<Integer,Transition> i2t = new HashMap<Integer, Transition>();
		
		// transitions
		/*for (DNode e : this.unf.getCutoffs()) {
			DNode corr = this.unf.getCorr(e);
			if (this.unf.getProperName(e).equals(this.unf.getProperName(corr)) && this.unf.getProperName(e).equals("_to_exit_")) {
				Transition t;
				if (!i2t.containsKey(corr.globalId))
					t = rewiredNet.addTransition(this.unf.getProperName(corr));
				else
					t = i2t.get(corr.globalId);
					
				i2t.put(e.globalId, t);
				i2t.put(corr.globalId, t);
			}
		}*/
		
		for (DNode e : this.unf.getAllEvents()) {
			if (!i2t.containsKey(e.globalId)) {
				Transition t = rewiredNet.addTransition(this.unf.getProperName(e));
				i2t.put(e.globalId, t);
			}
		}
		
		// places
		for (DNode e : this.unf.getCutoffs()) {
			DNode corr = this.unf.getCorr(e);
			
			for (int i=0; i<e.post.length; i++) {
				for (int j=0; j<corr.post.length; j++) {
					if (this.unf.getProperName(e.post[i]).equals(this.unf.getProperName(corr.post[j]))){
						Place p;
						if (!i2p.containsKey(corr.post[j].globalId))
							p = this.rewiredNet.addPlace(this.unf.getProperName(e.post[i]));
						else
							p = i2p.get(corr.post[j].globalId);
							
						i2p.put(e.post[i].globalId, p);
						i2p.put(corr.post[j].globalId, p);
					}
				}
			}
		}
		
		for (DNode b : this.unf.getAllConditions()) {
			if (!i2p.containsKey(b.globalId)) {
				Place p = this.rewiredNet.addPlace(this.unf.getProperName(b));
				i2p.put(b.globalId, p);
			}
		}

		
		// arcs
		for (DNode n : this.unf.getAllConditions()) {
			for (int i=0; i<n.pre.length; i++) {
				Place p = i2p.get(n.globalId);
				Transition t = i2t.get(n.pre[i].globalId);
				this.rewiredNet.addArc(t,p);
			}
		}
		
		for (DNode n : this.unf.getAllEvents()) {
			for (int i=0; i<n.pre.length; i++) {
				Place p = i2p.get(n.pre[i].globalId);
				Transition t = i2t.get(n.globalId);
				this.rewiredNet.addArc(p,t);
			}
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

	public Graph getGraph() {
		return graph;
	}

	public Object gatewayType(Integer vertex) {
		return rmap.get(vertex);
	}

	public File getDebugDir() {
		// TODO Auto-generated method stub
		return null;
	}

	public String getModelName() {
		// TODO Auto-generated method stub
		return null;
	}

	public Petrifier getPetrifier(Set<Integer> vertices, Set<Edge> edges,
			Integer entry, Integer exit) {
		// TODO Auto-generated method stub
		return null;
	}

	public void processOrderingRelations(Set<Edge> edges,
			Set<Integer> vertices, Integer entry, Integer exit, Graph graph,
			Unfolding unf, Map<String, Integer> tasks)
			throws CannotStructureException {
		// TODO Auto-generated method stub
		
	}

	public void setANDGateway(Integer vertex) {
		// TODO Auto-generated method stub
		
	}

	public void setXORGateway(Integer vertex) {
		// TODO Auto-generated method stub
		
	}

	public Object getModelElementId(Integer vertex) {
		// TODO Auto-generated method stub
		return null;
	}

	public boolean isChoice(Integer vertex) {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean isParallel(Integer vertex) {
		// TODO Auto-generated method stub
		return false;
	}

	public String toDot(Set<Integer> vertices, Set<Edge> edges) {
		// TODO Auto-generated method stub
		return null;
	}

	public Integer foldComponent(Graph graph, Set<Edge> edges,
			Set<Integer> vertices, Integer entry, Integer exit, BLOCK_TYPE type) {
		// TODO Auto-generated method stub
		return null;
	}

	public void installStructured() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void serializeDot(PrintStream out) {
		// TODO Auto-generated method stub
		
	}
}
