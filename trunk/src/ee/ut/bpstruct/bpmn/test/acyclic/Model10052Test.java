package ee.ut.bpstruct.bpmn.test.acyclic;

import java.io.File;
import java.io.PrintStream;

import org.apache.log4j.PropertyConfigurator;

import ee.ut.bpstruct.Restructurer;
import ee.ut.bpstruct.RestructurerVisitor;
import ee.ut.bpstruct.bpmn.BPMN2Helper;

import junit.framework.TestCase;

public class Model10052Test extends TestCase {
	public void testBehaviour() throws Exception {		
		String modelname = "model10052";
		
		PropertyConfigurator.configure("log4j.properties");
		
		BPMN2Helper helper = new BPMN2Helper(String.format("models/acyclic/%s.bpmn", modelname));
		
		File debugdir = new File("debug");
		if (!debugdir.exists()) debugdir.mkdir();
		helper.setDebugDir(debugdir);
		Restructurer r = new Restructurer(helper, new RestructurerVisitor(helper));

		r.process(new PrintStream(String.format("tmp/acyclic/%s.dot", modelname)));

	}
}

