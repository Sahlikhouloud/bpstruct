package ee.ut.bpstruct.bpmn.test;

import java.io.File;
import java.io.PrintStream;

import org.apache.log4j.BasicConfigurator;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;

import ee.ut.bpstruct.RestructurerHelper;
import ee.ut.bpstruct.Restructurer;
import ee.ut.bpstruct.RestructurerVisitor;
import ee.ut.bpstruct.bpmn.IBMWSModelerHelper;

import junit.framework.TestCase;

public class IBMModel1319 extends TestCase {
	
	static Namespace ns = Namespace.getNamespace("http://www.ibm.com/wbim/bomSchema1.0");
	public void testBehaviour() throws Exception {

		
		BasicConfigurator.configure();
		File debugdir = new File("bench_debug");
		if (!debugdir.exists()) debugdir.mkdir();

		Document doc = new SAXBuilder().build("eva/ibm/A.xml");
		
		Element root = doc.getRootElement().getChild("processModel", ns).getChild("processes", ns);
		
		for (Object obj : root.getChildren()) {
			Element proc = (Element) obj;
			String procname = proc.getAttributeValue("name");
			if (procname.equals("s00000112##s00001319")) {
				System.out.println("Proc name:" + procname);
				
				RestructurerHelper helper = new IBMWSModelerHelper(proc);
				Restructurer r = new Restructurer(helper, new RestructurerVisitor(helper));
	
				if (helper.getGraph().getSourceNodes().size() != 1 || helper.getGraph().getSinkNodes().size() != 1)
					System.out.println("Model has multiple source/sink nodes");
				else
					r.process(new PrintStream("tmp/ibm/" + procname + ".dot"));
				
				break;
			}
		}

	}
}

