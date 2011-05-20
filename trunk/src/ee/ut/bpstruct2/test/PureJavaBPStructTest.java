package ee.ut.bpstruct2.test;

import java.io.File;

import de.hpi.bpt.process.Process;
import de.hpi.bpt.process.serialize.Process2DOT;
import ee.ut.bpstruct2.Restructurer;
import ee.ut.bpstruct2.util.BPMN2Reader;
import junit.framework.TestCase;

public class PureJavaBPStructTest extends TestCase {

	public void testStructuring() throws Exception {
		Process proc = BPMN2Reader.parse(new File("models/acyclic/model7820.bpmn"));
		
		Restructurer str = new Restructurer(proc);
		
		if (str.perform())
			System.out.print(Process2DOT.convert(str.proc));
		else
			System.out.println("Model cannot be restructured");
	}
}
