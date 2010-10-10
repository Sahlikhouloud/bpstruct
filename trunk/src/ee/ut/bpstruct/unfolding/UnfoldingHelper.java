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
package ee.ut.bpstruct.unfolding;

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
import ee.ut.bpstruct.Helper;
import ee.ut.bpstruct.Petrifier;
import ee.ut.graph.moddec.ModularDecompositionTree;

public class UnfoldingHelper implements Helper {
	private Helper modelHelper;
	private Unfolding unf;

	private Graph graph = new Graph();
	private Map<DNode, Integer> map = new HashMap<DNode, Integer>();
	private Map<Integer, DNode> rmap = new HashMap<Integer, DNode>();
	private DNode exitCond = null;
	
	public UnfoldingHelper(Helper modelHelper, Unfolding unf) {
		this.modelHelper = modelHelper;
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
			condition = graph.addVertex(unf.properName(n));
			map.put(n, condition);
			rmap.put(condition, n);
		}

		return condition;
	}
	
	public void rewire() {		
		for (DNode _event: unf.getAllEvents()) {
			Integer event = map.get(_event);
			if (event == null) {
				event = graph.addVertex(unf.properName(_event));
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

	public void serializeDot(PrintStream out, Set<Integer> vertices,
			Set<Edge> edges) {
		// TODO Auto-generated method stub
		
	}

	public void synthesizeFromMDT(Set<Integer> vertices, Set<Edge> edges,
			Integer entry, Integer exit, ModularDecompositionTree mdec,
			Map<String, Integer> tasks) throws CannotStructureException {
		// TODO Auto-generated method stub
		
	}

	public void setANDGateway(Integer vertex) {		
	}

	public void setXORGateway(Integer vertex) {		
	}
}
