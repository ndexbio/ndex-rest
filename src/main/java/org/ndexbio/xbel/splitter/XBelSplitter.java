package org.ndexbio.xbel.splitter;

import java.util.Enumeration;
import java.util.concurrent.ExecutionException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.UnmarshallerHandler;

import org.ndexbio.xbel.model.NamespaceGroup;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.NamespaceSupport;
import org.xml.sax.helpers.XMLFilterImpl;

/*
 * Represents the generic JAXB/SAX XML parsing mechanism for parsing XBEL files.
 * Subclasses are instantiated with the specific XBEL element to be parsed from the 
 * document.
 * Subclasses are responsible for instantiating the appropriate JAXB class
 * the JAXB context is limited to a single Java package
 *
 */
public abstract class XBelSplitter extends XMLFilterImpl {

	protected final JAXBContext context;
	protected final String xmlElement;

	protected final String belURI = "http://belframework.org/schema/1.0/xbel";
	/**
	 * Remembers the depth of the elements as we forward SAX events to a JAXB
	 * unmarshaller.
	 */
	private int depth;

	/**
	 * Reference to the unmarshaller which is unmarshalling an object.
	 */
	protected UnmarshallerHandler unmarshallerHandler;

	/**
	 * Keeps a reference to the locator object so that we can later pass it to a
	 * JAXB unmarshaller.
	 */
	private Locator locator;

	public void setDocumentLocator(Locator locator) {
		super.setDocumentLocator(locator);
		this.locator = locator;
	}

	protected XBelSplitter(JAXBContext context, String anElement) {
		this.context = context;
		this.xmlElement = anElement;
	}

	public void startElement(String namespaceURI, String localName,
			String qName, Attributes atts) throws SAXException {

		if (depth != 0) {
			// we are in the middle of forwarding events.
			// continue to do so.
			depth++;
			super.startElement(namespaceURI, localName, qName, atts);
			return;
		}

		if (namespaceURI.equals(belURI) && localName.equals(xmlElement)) {
			// start a new unmarshaller
			Unmarshaller unmarshaller;
			try {
				unmarshaller = context.createUnmarshaller();
			} catch (JAXBException e) {
				// there's no way to recover from this error.
				// we will abort the processing.
				throw new SAXException(e);
			}
			unmarshallerHandler = unmarshaller.getUnmarshallerHandler();

			// set it as the content handler so that it will receive
			// SAX events from now on.
			setContentHandler(unmarshallerHandler);

			// fire SAX events to emulate the start of a new document.
			unmarshallerHandler.startDocument();
			unmarshallerHandler.setDocumentLocator(locator);

			Enumeration e = namespaces.getPrefixes();
			while (e.hasMoreElements()) {
				String prefix = (String) e.nextElement();
				String uri = namespaces.getURI(prefix);

				unmarshallerHandler.startPrefixMapping(prefix, uri);
			}
			String defaultURI = namespaces.getURI("bel");
			if (defaultURI != null)
				unmarshallerHandler.startPrefixMapping("", defaultURI);

			super.startElement(namespaceURI, localName, qName, atts);

			// count the depth of elements and we will know when to stop.
			depth = 1;
		}
	}

	public void endElement(String namespaceURI, String localName, String qName)
			throws SAXException {

		// forward this event
		super.endElement(namespaceURI, localName, qName);

		if (depth != 0) {
			depth--;
			if (depth == 0) {
				// just finished sending one chunk.

				// emulate the end of a document.
				Enumeration e = namespaces.getPrefixes();
				while (e.hasMoreElements()) {

					String prefix = (String) e.nextElement();

					unmarshallerHandler.endPrefixMapping(prefix);
				}
				String defaultURI = namespaces.getURI("");
				if (defaultURI != null)
					unmarshallerHandler.endPrefixMapping("");
				unmarshallerHandler.endDocument();

				// stop forwarding events by setting a dummy handler.
				// XMLFilter doesn't accept null, so we have to give it
				// something,
				// hence a DefaultHandler, which does nothing.
				setContentHandler(new DefaultHandler());

				// then retrieve the fully unmarshalled object
				try {

					 process();
				} catch (JAXBException | ExecutionException je) {
					// error was found during the unmarshalling.
					// you can either abort the processing by throwing a
					// SAXException,
					// or you can continue processing by returning from this
					// method.
					System.err
							.println("unable to process a NamespaceGroup at line "
									+ locator.getLineNumber());
					return;
				} 

				unmarshallerHandler = null;
			}
		}
	}

	/**
	 * Used to keep track of in-scope namespace bindings.
	 * 
	 * For JAXB unmarshaller to correctly unmarshal documents, it needs to know
	 * all the effective namespace declarations.
	 */
	private NamespaceSupport namespaces = new NamespaceSupport();

	public void startPrefixMapping(String prefix, String uri)
			throws SAXException {

		namespaces.pushContext();
		namespaces.declarePrefix(prefix, uri);

		super.startPrefixMapping(prefix, uri);
	}

	public void endPrefixMapping(String prefix) throws SAXException {

		namespaces.popContext();

		super.endPrefixMapping(prefix);

	}
	
	protected abstract void process() throws JAXBException, ExecutionException;
}
