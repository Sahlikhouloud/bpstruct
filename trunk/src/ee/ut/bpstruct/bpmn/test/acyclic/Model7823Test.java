package ee.ut.bpstruct.bpmn.test.acyclic;

import java.io.File;
import java.io.PrintStream;

import org.apache.log4j.PropertyConfigurator;

import junit.framework.TestCase;
import ee.ut.bpstruct.Restructurer;
import ee.ut.bpstruct.RestructurerVisitor;
import ee.ut.bpstruct.bpmn.BPMN2Helper;

public class Model7823Test extends TestCase {
	public void testBehaviour() throws Exception {
		String modelname = "model7823";
		
		PropertyConfigurator.configure("log4j.properties");
		
		BPMN2Helper helper = new BPMN2Helper(String.format("models/acyclic/%s.bpmn", modelname));
		
		File debugdir = new File("debug");
		if (!debugdir.exists()) debugdir.mkdir();
		helper.setDebugDir(debugdir);
		Restructurer r = new Restructurer(helper, new RestructurerVisitor(helper));

		r.process(new PrintStream(String.format("tmp/acyclic/%s.dot", modelname)));
	}
}

