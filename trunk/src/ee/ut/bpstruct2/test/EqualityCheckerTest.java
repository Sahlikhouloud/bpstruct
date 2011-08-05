package ee.ut.bpstruct2.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;

import de.hpi.bpt.process.Process;
import de.hpi.bpt.process.serialize.JSON2Process;
import de.hpi.bpt.process.serialize.SerializationException;
import ee.ut.bpstruct2.util.EqualityChecker;

import junit.framework.TestCase;

public class EqualityCheckerTest extends TestCase {

	private static final String ORIGINAL_DIR = "models/json/acyclic/original";
	private static final String STRUCTURED_DIR = "models/json/acyclic/structured";
	
	public void testComparison() {
		File originalDir = new File(ORIGINAL_DIR);
		File structuredDir = new File(STRUCTURED_DIR);
		HashSet<String> files = new HashSet<String>();
		for (String name:originalDir.list()) {
			if (name.endsWith(".json"))
				files.add(name);
		}
		for (String name:structuredDir.list()) {
			if (name.endsWith(".json") && files.contains(name))
				try {
					Process original = loadProcess(ORIGINAL_DIR + File.separator + name);
					Process structured = loadProcess(STRUCTURED_DIR + File.separator + name);
					assertTrue("No equal result for structuring of: " + name, EqualityChecker.areEqual(original, structured));
					System.out.println(name + " passed");
				} catch (Exception e) {
					System.err.println("Couldn't run test for file: " + name);
					e.printStackTrace();
				}
		}
	}
	
	private Process loadProcess(String filename) throws SerializationException, IOException {
		String line;
		StringBuilder sb = new StringBuilder();
		BufferedReader reader = new BufferedReader(new FileReader(filename));
		while ((line = reader.readLine()) != null) {
			sb.append(line);
		}
		reader.close();
		return JSON2Process.convert(sb.toString());
	}
		
}
