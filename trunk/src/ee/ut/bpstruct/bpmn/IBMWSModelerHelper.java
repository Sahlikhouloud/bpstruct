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
package ee.ut.bpstruct.bpmn;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;
import org.jdom.Element;
import org.jdom.Namespace;

import de.bpt.hpi.graph.Edge;
import de.bpt.hpi.graph.Graph;

/**
 * This helper class parses IBM WS-Modeler files and creates jdbt graph representation of 
 * the process graph. Note that the implementation uses JDOM, for simplicity and generality.
 * 
 * @author Luciano Garcia Banuelos
 */
public class IBMWSModelerHelper extends BPMNHelper {
	// log4j ---
	static Logger logger = Logger.getLogger(IBMWSModelerHelper.class);
	
	// IBM WSModeler URI --- JDOM Namespace
	public static Namespace ns = Namespace.getNamespace("http://www.ibm.com/wbim/bomSchema1.0");
	
	private Element process;
	
	public IBMWSModelerHelper(Element process) throws Exception {
		super();
		if (logger.isInfoEnabled()) logger.info("Parsing process: " + process.getAttributeValue("name"));
		
		this.process = process;
		initGraph();
		serializeDot(System.out, graph.getVertices(), new HashSet<Edge>(graph.getEdges()));
	}
	
	void initGraph() {
		if (logger.isInfoEnabled()) logger.info("Creating graph");
		
		graph = new Graph();
		
		Element flowElems = process.getChild("flowContent", ns);
		
		List<Element> edges = new LinkedList<Element>();
		for (Object obj : flowElems.getChildren())
			if (obj instanceof Element) {
				Element elem = (Element) obj;
				String ename = elem.getName();
				if (ename.equals("connection")) {
					edges.add(elem);
					continue;
				}
				String label = elem.getAttributeValue("name");
				if (label == null) continue;
				if (ename.equals("callToTask") || ename.equals("callToProcess") || ename.equals("callToService")
					|| ename.equals("startNode") || ename.equals("endNode")
					) {
					Integer vertex = graph.addVertex(label);
					map.put(label, vertex);
					rmap.put(vertex, label);
				} else if (ename.equals("decision") || ename.equals("merge")) {
						Integer vertex = graph.addVertex(label);
						map.put(label, vertex);
						rmap.put(vertex, label);
						gwmap.put(vertex, GWType.XOR);
				} else if (ename.equals("fork") || ename.equals("join")) {
					Integer vertex = graph.addVertex(label);
					map.put(label, vertex);
					rmap.put(vertex, label);
					gwmap.put(vertex, GWType.AND);
//				} else if (elem.getName().equals("inclusiveGateway")) {
//					Integer vertex = graph.addVertex(label);
//					map.put(label, vertex);
//					rmap.put(vertex, label);
//					gwmap.put(vertex, GWType.OR);
				} else if (logger.isInfoEnabled()) logger.info("Unprocessed Element: " + elem.getName() + ", label: " + elem.getAttributeValue("name"));
			}

		for (Element flow : edges) {			
			Integer source = map.get(flow.getChild("source", ns).getAttributeValue("node"));
			Integer target = map.get(flow.getChild("target", ns).getAttributeValue("node"));
			Edge edge = new Edge(source, target);
			if (source != null && target != null && !graph.getEdges().contains(edge)) {
				graph.addEdge(edge);
			}

//			else {
//				logger.fatal("Malformed graph! Dangling edge: " + flow.getAttributeValue("id"));
//				throw new RuntimeException("Malformed graph");
//			}
		}
		
		if (logger.isInfoEnabled()) logger.info("Graph created");
	}
}
