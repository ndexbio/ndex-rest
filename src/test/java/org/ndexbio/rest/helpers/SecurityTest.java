package org.ndexbio.rest.helpers;

import static org.junit.Assert.*;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.junit.Test;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.rest.Configuration;

public class SecurityTest {
	
	
	@Test
	public void test() throws NdexException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, UnsupportedEncodingException {
		
		
		ClassLoader classLoader = getClass().getClassLoader();
		File file = new File(classLoader.getResource("ndex.properties").getFile());
		String absolutePath = file.getAbsolutePath();
		
		System.setProperty(Configuration.ndexConfigFilePropName, absolutePath);
		
		Configuration.createInstance();
		
		//SecretKeySpec  s = Configuration.getInstance().getSecretKeySpec();
		
		String s0= "adlels:030302adf";
		String s2 = Security.encrypt(s0);
		
		System.out.println (s2);
		
		//String acct = Configuration.getInstance().getDOICreatorString();
		
		//assertEquals(acct, s2);
		
		String sf = Security.decrypt(s2,Configuration.getInstance().getSecretKeySpec());
		assertEquals(sf, s0);
	
	}

}
