package ee.ut.bpstruct2.cmd;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import de.hpi.bpt.process.Gateway;
import de.hpi.bpt.process.Process;
import de.hpi.bpt.process.serialize.JSON2Process;
import de.hpi.bpt.process.serialize.Process2DOT;
import de.hpi.bpt.process.serialize.Process2JSON;
import ee.ut.bpstruct2.Restructurer;

public class BPStructCMD {

	public static void main(String args[]) throws Exception {
		BPStructCMDOptions options = new BPStructCMDOptions();
		CmdLineParser parser = new CmdLineParser(options);
		File ifile;
			try {
				parser.parseArgument(args);
				
				if (!options.odir.exists())
					options.odir.mkdir();
				if (!options.odir.isDirectory())
					throw new IOException("Cannot create/open output directory: " + options.odir.getName());
				
				ifile = new File(options.arguments.get(0));
				
				File parent = ifile.getParentFile();
				if (parent == null) parent = new File(".");
				
				if (!ifile.exists() || !ifile.isFile())
					throw new IOException("Cannot open input model file: " + ifile.getAbsoluteFile());
								
				String path = parent.getPath() + System.getProperty("file.separator") + "%s";
				String name = ifile.getName();
								
				Logger.getRootLogger().setLevel(Level.OFF);
				PrintStream out = System.out;				
				System.setOut(new PrintStream("umalog.txt"));
				
				String line;
				BufferedReader in = new BufferedReader(new FileReader(ifile));
				StringBuilder strb = new StringBuilder();
				while ((line = in.readLine()) != null)
					strb.append(line);
				Process proc = JSON2Process.convert(strb.toString());
				
				int count = 0;
				for (Gateway gw: proc.getGateways())
					if (gw.getName().isEmpty())
						gw.setName("gw"+count++);

				Restructurer str = new Restructurer(proc);
				
				if (str.perform())
					try {
						if (options.gjson || (!options.gjson && !options.gdot)) {
							File ofile = new File(options.odir, String.format("%s.json", name));
							PrintStream outstr = new PrintStream(ofile);
							outstr.print(Process2JSON.convert(str.proc));
							outstr.close();
							out.printf("JSON file with output model serialized in: '%s'\n", ofile.getPath());
						}
						if (options.gdot) {
							File ofile = new File(options.odir, String.format("%s.dot", name));
							PrintStream outstr = new PrintStream(ofile);
							outstr.print(Process2DOT.convert(str.proc));
							outstr.close();
							out.printf("DOT file with output model serialized in: '%s'\n", ofile.getPath());
						}
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					}
				else
					out.println("Model cannot be restructured");
								
			} catch (CmdLineException e) {
				System.err.println(e.getMessage());
				printUsage(parser);
			} catch (IndexOutOfBoundsException e) {
				System.err.println("Please specify the input model path");
				printUsage(parser);
			}
	}

	private static void printUsage(CmdLineParser parser) {
		System.err.println("\nUsage:");
		System.err.println("\tjava -jar bpstruct.jar [options] <inputmodel>\nOptions:");
		parser.printUsage(System.err);		
	}
}
