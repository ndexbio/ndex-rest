package org.ndexbio.xbel.app;

import org.ndexbio.xbel.parser.ExcelFileParser;

/*
 * Java application to evaluate parsing a specified file in XBEL format
 */

public class ExcelParserApp {
	public static void main(String[] args) throws Exception {
		String filename = null;
		if (args.length > 0) {
			filename = args[0];
		} else {
			filename = "excelnetworksmall.xls";
		}
		ExcelFileParser parser = new ExcelFileParser(filename);
		parser.parseExcelFile();
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
