package ee.ut.bpstruct2.test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

import de.hpi.bpt.process.Process;
import de.hpi.bpt.process.serialize.Process2DOT;
import ee.ut.bpstruct2.Restructurer;
import ee.ut.bpstruct2.util.BPMN2Reader;
import junit.framework.TestCase;

public class MaxBPStructTest extends TestCase {

	public void testStructuring() throws Exception {
		Process proc = BPMN2Reader.parse(new File(
				"models/cyclic/model10780.bpmn"));

		try {
			String filename = String.format("bpstruct2/original.dot", proc
					.getName());
			PrintStream out = new PrintStream(filename);
			out.print(Process2DOT.convert(proc));
			out.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		
		Restructurer str = new Restructurer(proc);
		
		if (str.perform())
			try {
				String filename = String.format("bpstruct2/proc_%s.dot", proc
						.getName());
				PrintStream out = new PrintStream(filename);
				out.print(Process2DOT.convert(str.proc));
				out.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		else
			System.out.println("Model cannot be restructured");
	}
}
