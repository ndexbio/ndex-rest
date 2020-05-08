/**
 * Copyright (c) 2013, 2016, The Regents of the University of California, The Cytoscape Consortium
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
package org.ndexbio.rest.helpers;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.MultivaluedMap;

public class Security
{
    /**************************************************************************
    * Converts bytes into hexadecimal text.
    * 
    * @param data
    *            The byte data.
    * @return A String containing the byte data as hexadecimal text.
    **************************************************************************/
    public static String convertByteToHex(byte data[])
    {
        StringBuffer hexData = new StringBuffer();
        for (int byteIndex = 0; byteIndex < data.length; byteIndex++)
            hexData.append(Integer.toString((data[byteIndex] & 0xff) + 0x100, 16).substring(1));
        
        return hexData.toString();
    }

    /**************************************************************************
    * Generates a password of 10 random characters.
    * 
    * @return A String containing the random password.
    **************************************************************************/
    public static String generatePassword()
    {
        return generatePassword(10);
    }

    public static String generateLongPassword()
    {
        return generatePassword(20);
    }
    
    
    public static String generateVerificationCode()
    {
        final String alphaCharacters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        final String numericCharacters = "0123456789";
        
        StringBuilder randomPassword = new StringBuilder();
        for (int passwordIndex = 0; passwordIndex < 10; passwordIndex++)
        {
            //Determine if the character will be alpha, numeric, or a symbol
            final int charType = randomNumber(1, 2);
            
            if (charType == 1)
                randomPassword.append(alphaCharacters.charAt(randomNumber(0, alphaCharacters.length() - 1)));
            else if (charType == 2)
                randomPassword.append(numericCharacters.charAt(randomNumber(0, numericCharacters.length() - 1)));
        }
        
        return randomPassword.toString();
    }

    
    /**************************************************************************
    * Generates a password of random characters.
    * 
    * @param passwordLength
    *            The length of the password.
    * @return A String containing the random password.
    **************************************************************************/
    public static String generatePassword(int passwordLength)
    {
        final String alphaCharacters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        final String numericCharacters = "0123456789";
        final String symbolCharacters = "`-=;~!@#$%^&*_+|:?";
        
        StringBuilder randomPassword = new StringBuilder();
        for (int passwordIndex = 0; passwordIndex < passwordLength; passwordIndex++)
        {
            //Determine if the character will be alpha, numeric, or a symbol
            final int charType = randomNumber(1, 3);
            
            if (charType == 1)
                randomPassword.append(alphaCharacters.charAt(randomNumber(0, alphaCharacters.length() - 1)));
            else if (charType == 2)
                randomPassword.append(numericCharacters.charAt(randomNumber(0, numericCharacters.length() - 1)));
            else
                randomPassword.append(symbolCharacters.charAt(randomNumber(0, symbolCharacters.length() - 1)));
        }
        
        return randomPassword.toString();
    }
    
    /**************************************************************************
    * Computes a SHA-512 hash against the supplied text.
    * 
    * @param textToHash
    *            The text to compute the hash against.
    * @return A String containing the SHA-512 hash in hexadecimal format.
    **************************************************************************/
    public static String hashText(String textToHash) throws Exception
    {
        final MessageDigest sha512 = MessageDigest.getInstance("SHA-512");
        sha512.update(textToHash.getBytes());
        
        return convertByteToHex(sha512.digest());
    }

    /**************************************************************************
    * Base64-decodes and parses the Authorization header to get the username
    * and password.
    * 
    * @param requestContext
    *            The servlet HTTP request context.
    * @throws IOException
    *            Decoding the Authorization header failed.
    * @return a String array containing the username and password.
    **************************************************************************/
    public static String[] parseCredentials(ContainerRequestContext requestContext) throws IOException
    {
        final MultivaluedMap<String, String> headers = requestContext.getHeaders();
        final List<String> authHeader = headers.get("Authorization");
        
        if (authHeader == null || authHeader.isEmpty())
            return null;

        final String encodedAuthInfo = authHeader.get(0).replaceFirst("Basic" + " ", "");
        final String decodedAuthInfo = new String(Base64.getDecoder().decode(encodedAuthInfo));
        
        return decodedAuthInfo.split(":");
    }
    
    /**************************************************************************
    * Generates a random number between the two values.
    * 
    * @param minValue
    *            The minimum range of values.
    * @param maxValue
    *            The maximum range of values.
    * @return A random number between the range.
    **************************************************************************/
    public static int randomNumber(int minValue, int maxValue)
    {
        return minValue + (int)(Math.random() * ((maxValue - minValue) + 1));
    }
}
