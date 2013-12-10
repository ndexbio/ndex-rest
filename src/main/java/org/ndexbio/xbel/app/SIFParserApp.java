package org.ndexbio.xbel.app;

import org.ndexbio.xbel.parser.SIFFileParser;
import org.ndexbio.xbel.parser.XbelFileParser;

/*
 * Java application to evaluate parsing a specified file in XBEL format
 */

public class SIFParserApp {
	public static void main(String[] args) throws Exception {
		String filename = null;
		if (args.length > 0) {
			filename = args[0];
		} else {
			filename = "galFiltered.sif";
		}
		SIFFileParser parser = new SIFFileParser(filename);
		parser.parseSIFFile();
		for (String msg : parser.getMsgBuffer()) {
			System.out.println(msg);
			/*
			 * if (parser.getValidationState().isValid()){ parser.parseFile();
			 * for (String msg : parser.getMsgBuffer()){
			 * System.out.println(msg); } } else {
			 * System.out.println(parser.getValidationState
			 * ().getValidationMessage()); }
			 */
		}
	}

}
