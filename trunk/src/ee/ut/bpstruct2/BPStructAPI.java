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
package ee.ut.bpstruct2;

import java.io.File;

import org.apache.log4j.PropertyConfigurator;

import de.hpi.bpt.process.Process;

/**
 * BPStruct API version 0-1-0
 * 
 * The entry point to the BPStruct functionality 
 */
public class BPStructAPI {
	
	/**
	 * Structure a process
	 * 
	 * @param p Process to structure
	 * @param ac Acyclic configuration
	 * @param cc Cyclic configuration
	 * @return Structuring result
	 * @throws Exception
	 */
	public static BPStructResult structure(Process p, AcyclicConfiguration ac, CyclicConfiguration cc) throws Exception {
		BPStructResult result = new BPStructResult();
		
		PropertyConfigurator.configure("log4j.properties");
		File debugdir = new File("bpstruct2");
		if (!debugdir.exists()) debugdir.mkdir();

		Restructurer str = new Restructurer(p);
		
		if (str.perform()) {
			result.proc = str.proc;
			result.hasChanged = true;
			result.isStructured = true;
		}
		else {
			result.proc = p;
			result.hasChanged = false;
			result.isStructured = false;
		}
		
		return result;
	}
}
