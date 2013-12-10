package org.ndexbio.xbel.app;

import org.ndexbio.xbel.parser.XbelFileParser;

/*
 * Java application to evaluate parsing a specified file in XBEL format
 */

public class XbelParserApp {
	public static void main(String[] args) throws Exception {
		String filename = null;
		if(args.length > 0 ){
			filename = args[0];
		} else {
			 filename = "small_corpus.xbel";
		}
		XbelFileParser parser = new XbelFileParser(filename);
		if (parser.getValidationState().isValid()){
			parser.parseXbelFile();
			for (String msg : parser.getMsgBuffer()){
				System.out.println(msg);
			}
		} else {
			System.out.println(parser.getValidationState().getValidationMessage());
		}
	
	}

}
