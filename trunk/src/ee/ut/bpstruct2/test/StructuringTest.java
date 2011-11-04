/* 
 * Copyright (C) 2010 - Artem Polyvyanyy, Luciano Garcia Banuelos
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
package ee.ut.bpstruct2.test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

import junit.framework.TestCase;

import org.apache.log4j.PropertyConfigurator;

import de.hpi.bpt.process.Process;
import de.hpi.bpt.process.serialize.Process2DOT;
import ee.ut.bpstruct2.Restructurer;
import ee.ut.bpstruct2.util.BPMN2Reader;


/**
 * Abstract structuring test
 */
public abstract class StructuringTest extends TestCase {
	protected String MODEL_NAME = "";
	protected String MODEL_PATH_TPL = "";
	protected String OUTPUT_PATH_TPL = "";
	protected boolean CAN_STRUCTURE = true;
	
	public void testStructuring() throws Exception {
		PropertyConfigurator.configure("log4j.properties");
//		File debugdir = new File("bpstruct2");
//		if (!debugdir.exists()) debugdir.mkdir();

		Process proc = BPMN2Reader.parse(new File(
				String.format(this.MODEL_PATH_TPL, this.MODEL_NAME)));
		proc.setName(this.MODEL_NAME);

		Restructurer str = new Restructurer(proc);
		
		if (str.perform())
			try {
				String filename = String.format(this.OUTPUT_PATH_TPL, this.MODEL_NAME);
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
