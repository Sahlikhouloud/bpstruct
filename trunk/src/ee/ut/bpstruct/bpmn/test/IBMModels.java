package ee.ut.bpstruct.bpmn.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintStream;
import java.util.HashSet;

import org.apache.log4j.BasicConfigurator;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;

import de.bpt.hpi.graph.Edge;

import ee.ut.bpstruct.RestructurerHelper;
import ee.ut.bpstruct.Restructurer;
import ee.ut.bpstruct.RestructurerVisitor;
import ee.ut.bpstruct.bpmn.IBMWSModelerHelper;

import junit.framework.TestCase;

public class IBMModels extends TestCase {
	
	static Namespace ns = Namespace.getNamespace("http://www.ibm.com/wbim/bomSchema1.0");
	public void testBehaviour() throws Exception {		
		BasicConfigurator.configure();
		String inputfile = "A";
		
		HashSet<String> soundmodels = new HashSet<String>();
		String line;
		BufferedReader in = new BufferedReader(new FileReader("eva/ibm/soundmodels.txt"));
		while ((line = in.readLine()) != null)
			soundmodels.add(line);
		
		File debugdir = new File("bench_debug");
		if (!debugdir.exists()) debugdir.mkdir();

		Document doc = new SAXBuilder().build(String.format("eva/ibm/%s.xml", inputfile));
		
		Element root = doc.getRootElement().getChild("processModel", ns).getChild("processes", ns);
		
		int i = 0;
		for (Object obj : root.getChildren()) {
			Element proc = (Element) obj;
			String procname = proc.getAttributeValue("name").replaceAll("#", "_");
			System.out.println("Proc name:" + procname);
			
			if (!soundmodels.contains(inputfile + "." + procname)) continue;
			
			RestructurerHelper helper = new IBMWSModelerHelper(proc);
			Restructurer r = new Restructurer(helper, new RestructurerVisitor(helper));
			
			PrintStream out = new PrintStream("tmp/ibm/original/" + procname + ".dot");
			out.println(helper.toDot(helper.getGraph().getVertices(), new HashSet<Edge>(helper.getGraph().getEdges())));
			
			if (helper.getGraph().getSourceNodes().size() != 1 || helper.getGraph().getSinkNodes().size() != 1)
				System.out.println("Model has multiple source/sink nodes");
			else {
				//r.process(new PrintStream("tmp/ibm/structured/" + procname + "_structured.dot"));
			}
		}

	}
}

