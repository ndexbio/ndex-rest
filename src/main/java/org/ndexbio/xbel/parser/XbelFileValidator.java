package org.ndexbio.xbel.parser;

import java.io.File;
import java.io.IOException;

import com.google.common.base.Strings;

import org.openbel.framework.common.xbel.parser.XBELValidator;
import org.xml.sax.SAXException;

public class XbelFileValidator {

	private String xmlFileName;
	private final static String XBEL_ROOT = "xbel/";
	private final static String XBEL_XSD = XBEL_ROOT + "xbel.xsd";
	private final static String ANNO_XSD = XBEL_ROOT + "xbel-annotations.xsd";

	private XBELValidator xv;
	private final ValidationState validationState;

	public XbelFileValidator(String fileName) {
		if (Strings.isNullOrEmpty(fileName)) {
			this.validationState = new ValidationState(false,
					"Null or empty filename parameter");
			return;
		}
		this.xmlFileName = fileName;
		if (!this.xsdCheck() || !this.initValidator()) {
			this.validationState = new ValidationState(false,
					"Valid BEL XSD file(s) unavailable");
			return;
		}

		this.validationState = this.run();

	}

	public boolean xsdCheck() {
		if (!new File(XBEL_XSD).canRead()) {
			System.err.println("can't read " + XBEL_XSD);
			return false;
		}
		if (!new File(ANNO_XSD).canRead()) {
			System.err.println("can't read " + ANNO_XSD);
			return false;
		}

		return true;
	}

	private boolean initValidator() {
		try {
			this.xv = new XBELValidator(XBEL_XSD, ANNO_XSD);
			return true;
		} catch (SAXException e) {
			String err = "SAX exception validating XSDs";
			err += ", exception message follows:\n\t";
			err += e.getMessage();
			System.err.println(err);
		}
		return false;
	}

	private ValidationState run() {

		try {

			xv.validate(new File(this.xmlFileName));
			String message = "File " + this.xmlFileName
					+ " is a valid xbel file";
			return new ValidationState(true, message);
		} catch (SAXException e) {
			String err = "SAX exception validating " + this.xmlFileName;
			err += ", exception message follows:\n\t";
			err += e.getMessage();
			return new ValidationState(false, err);
		} catch (IOException e) {
			String err = "IO exception validating " + this.xmlFileName;
			err += ", exception message follows:\n\t";
			err += e.getMessage();
			return new ValidationState(false, err);
		}
	}

	public ValidationState getValidationState() {
		return validationState;
	}

	public class ValidationState {
		private final boolean valid;
		private final String validationMessage;

		ValidationState(boolean v, String m) {
			this.valid = v;
			this.validationMessage = m;
		}

		public boolean isValid() {
			return valid;
		}

		public String getValidationMessage() {
			return validationMessage;
		}
	}

}
