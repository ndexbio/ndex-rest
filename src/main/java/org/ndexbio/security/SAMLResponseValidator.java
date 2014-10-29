package org.ndexbio.security;

import javax.xml.crypto.*;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dom.*;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.*;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.security.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * This is a simple example of validating an XML
 * Signature using the JSR 105 API. It assumes the key needed to
 * validate the signature is contained in a KeyValue KeyInfo.
 */
public class SAMLResponseValidator {

    private static String ndexAudience = "http://www.ndexbio.org/rest/";

	
    //
    // Synopsis: java Validate [document]
    //
    //    where "document" is the name of a file containing the XML document
    //    to be validated.
    //
	public static void main(String args[]) throws Exception {
		String xmldoc = Util.readFileContents(args[0]);
		validate (xmldoc);
	}
	
    public static boolean validate(String xmldoc) throws Exception {

        // Instantiate the document to be validated
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document doc =
            dbf.newDocumentBuilder() .parse(new ByteArrayInputStream(xmldoc.getBytes("utf-8")));

        // Find Signature element
        NodeList nl =
            doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        if (nl.getLength() == 0) {
            throw new Exception("Cannot find Signature element");
        }

        // Create a DOM XMLSignatureFactory that will be used to unmarshal the
        // document containing the XMLSignature
        XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");

        // Create a DOMValidateContext and specify a KeyValue KeySelector
        // and document context
        DOMValidateContext valContext = new DOMValidateContext
            (new KeyValueKeySelector(), nl.item(0));

        // unmarshal the XMLSignature
        XMLSignature signature = fac.unmarshalXMLSignature(valContext);

        // Validate the XMLSignature (generated above)
        boolean coreValidity = signature.validate(valContext);
        
        // Check core validation status
        if (coreValidity == false) {
            System.err.println("Signature failed core validation");
            boolean sv = signature.getSignatureValue().validate(valContext);
            System.out.println("signature validation status: " + sv);
            // check the validation status of each Reference
            Iterator i = signature.getSignedInfo().getReferences().iterator();
            for (int j=0; i.hasNext(); j++) {
                boolean refValid =
                    ((Reference) i.next()).validate(valContext);
                System.out.println("ref["+j+"] validity status: " + refValid);
            }
            return false;
        } 
        
        // check status 
        NodeList statusNodeList = doc.getElementsByTagName("samlp:StatusCode");
        if ( statusNodeList.getLength() == 0) {
            throw new Exception("Cannot find StatusCode element");
        } 
        
        Element eElement = (Element) statusNodeList.item(0);
        String statusStr = eElement.getAttribute("Value");
        System.out.println("Status is: " + statusStr);
        if ( !statusStr.equals("urn:oasis:names:tc:SAML:2.0:status:Success")) {
        	throw new Exception ("Status code has to be urn:oasis:names:tc:SAML:2.0:status:Success in SAML response.");
        }
        
        // check Conditions 
        NodeList conditionNodeList = doc.getElementsByTagName( "Conditions");
        if ( conditionNodeList.getLength() == 0) {			   
            throw new Exception("Cannot find Conditions element");
        } 
        
        Element cElement = (Element) conditionNodeList.item(0);
        String notBeforeStr = cElement.getAttribute("NotBefore");
        String notAfterStr = cElement.getAttribute("NotOnOrAfter");
        //System.out.println("Conditions are : " + statusStr);
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH);
        Date notBefore =  df.parse(notBeforeStr);
        Date notAfter = df.parse(notAfterStr);
        
     /*  Commented out for testing now. Will put it back when go live.  
      *  Date now = Calendar.getInstance().getTime();
        if ( now.after(notAfter) || now.before(notBefore)) {
        	throw new Exception ("Expired SAML response.");
        } */
        System.out.println(notBefore + "," + notAfter);
        
        // check the Audience
        NodeList audienceNodeList = doc.getElementsByTagName( "Audience");
        if ( audienceNodeList.getLength() == 0) {			   
            throw new Exception("Cannot find Conditions element");
        } 
        
        cElement = (Element) audienceNodeList.item(0);
        String t = cElement.getTextContent();
        System.out.println(t);
        if ( !t.equals(ndexAudience)) {
        	throw new Exception("Response is not for an allowed NDEx audience.");
        }
        
        // get the user name
        NodeList accountNodeList = doc.getElementsByTagName( "NameID");
        if ( accountNodeList.getLength() == 0) {			   
            throw new Exception("Cannot find user ID element");
        } 
        
        cElement = (Element) accountNodeList.item(0);
        String userName = cElement.getTextContent().trim().toLowerCase();
        System.out.println("User:" + userName);
        
        return true;
    }

    /**
     * KeySelector which retrieves the public key out of the
     * KeyValue element and returns it.
     * NOTE: If the key algorithm doesn't match signature algorithm,
     * then the public key will be ignored.
     */
    private static class KeyValueKeySelector extends KeySelector {
        public KeySelectorResult select(KeyInfo keyInfo,
                                        KeySelector.Purpose purpose,
                                        AlgorithmMethod method,
                                        XMLCryptoContext context)
            throws KeySelectorException {
            if (keyInfo == null) {
                throw new KeySelectorException("Null KeyInfo object!");
            }
            SignatureMethod sm = (SignatureMethod) method;
            List list = keyInfo.getContent();

            for (int i = 0; i < list.size(); i++) {
                XMLStructure xmlStructure = (XMLStructure) list.get(i);
                if (xmlStructure instanceof KeyValue) {
                    PublicKey pk = null;
                    try {
                        pk = ((KeyValue)xmlStructure).getPublicKey();
                    } catch (KeyException ke) {
                        throw new KeySelectorException(ke);
                    }
                    // make sure algorithm is compatible with method
                    if (algEquals(sm.getAlgorithm(), pk.getAlgorithm())) {
                        return new SimpleKeySelectorResult(pk);
                    }
                }
            }
            throw new KeySelectorException("No KeyValue element found!");
        }

        //@@@FIXME: this should also work for key types other than DSA/RSA
        static boolean algEquals(String algURI, String algName) {
            if (algName.equalsIgnoreCase("DSA") &&
                algURI.equalsIgnoreCase(SignatureMethod.DSA_SHA1)) {
                return true;
            } else if (algName.equalsIgnoreCase("RSA") &&
                       algURI.equalsIgnoreCase(SignatureMethod.RSA_SHA1)) {
                return true;
            } else {
                return false;
            }
        }
    }

    private static class SimpleKeySelectorResult implements KeySelectorResult {
        private PublicKey pk;
        SimpleKeySelectorResult(PublicKey pk) {
            this.pk = pk;
        }

        public Key getKey() { return pk; }
    }
    
}
