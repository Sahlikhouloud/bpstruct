package ee.ut.bpstruct.bpmn.test;

import java.io.File;
import java.io.PrintStream;

import org.apache.log4j.BasicConfigurator;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;

import ee.ut.bpstruct.Helper;
import ee.ut.bpstruct.Restructurer;
import ee.ut.bpstruct.RestructurerVisitor;
import ee.ut.bpstruct.bpmn.IBMWSModelerHelper;

import junit.framework.TestCase;

public class IBMModels extends TestCase {
	
	static Namespace ns = Namespace.getNamespace("http://www.ibm.com/wbim/bomSchema1.0");
	public void testBehaviour() throws Exception {

		
		BasicConfigurator.configure();
		File debugdir = new File("bench_debug");
		if (!debugdir.exists()) debugdir.mkdir();

		Document doc = new SAXBuilder().build("eva/ibm/A.xml");
		
		Element root = doc.getRootElement().getChild("processModel", ns).getChild("processes", ns);
		
		int i = 0;
		for (Object obj : root.getChildren()) {
			Element proc = (Element) obj;
			String procname = proc.getAttributeValue("name").replaceAll("#", "_");
			System.out.println("Proc name:" + procname);
			
			Helper helper = new IBMWSModelerHelper(proc);
			Restructurer r = new Restructurer(helper, new RestructurerVisitor(helper));

			if (helper.getGraph().getSourceNodes().size() != 1 || helper.getGraph().getSinkNodes().size() != 1)
				System.out.println("Model has multiple source/sink nodes");
			else {
				r.process(new PrintStream("tmp/ibm/" + procname + ".dot"));
				if (i++ > 10) break;
			}
		}

	}
}

