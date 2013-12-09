package org.ndexbio.xbel.splitter;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.ndexbio.xbel.model.Header;
import org.ndexbio.xbel.model.NamespaceGroup;

public class HeaderSplitter extends XBelSplitter {

	private Header header;
	private static final String xmlElement = "header";
	public HeaderSplitter(JAXBContext context) {
		super(context, xmlElement);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected void process() throws JAXBException {
		this.header = (Header) unmarshallerHandler
				.getResult();
	}
	
	public Header getHeader() { return this.header;}

}
