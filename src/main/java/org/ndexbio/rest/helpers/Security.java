package org.ndexbio.rest.helpers;

import java.security.MessageDigest;

public class Security
{
    /**************************************************************************
    * Converts bytes into hexadecimal text.
    * 
    * @param data The byte data.
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
    
    /**************************************************************************
    * Generates a password of random characters.
    * 
    * @param passwordLength The length of the password.
    * @return A String containing the random password.
    **************************************************************************/
    public static String generatePassword(int passwordLength)
    {
        final String alphaCharacters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        final String numericCharacters = "0123456789";
        final String symbolCharacters = "`-=[]\\;',./~!@#$%^&*()_+{}|:\"<>?";
        
        StringBuilder randomPassword = new StringBuilder();
        for (int passwordIndex = 0; passwordIndex < passwordLength; passwordIndex++)
        {
            //Determine if the character will be alpha, numeric, or a symbol
            final int charType = randomNumber(1, 3);
            
            //Add the random character
            if (charType == 1)
                randomPassword.append(alphaCharacters.charAt(randomNumber(0, 25)));
            else if (charType == 2)
                randomPassword.append(numericCharacters.charAt(randomNumber(0, 9)));
            else
                randomPassword.append(symbolCharacters.charAt(randomNumber(0, 31)));
        }
        
        return randomPassword.toString();
    }
    
    /**************************************************************************
    * Computes a SHA-512 hash against the supplied text.
    * 
    * @param textToHash The text to compute the hash against.
    * @return A String containing the SHA-512 hash in hexadecimal format.
    **************************************************************************/
    public static String hashText(String textToHash) throws Exception
    {
        final MessageDigest sha512 = MessageDigest.getInstance("SHA-512");
        sha512.update(textToHash.getBytes());
        
        return convertByteToHex(sha512.digest());
    }
    
    /**************************************************************************
    * Generates a random number between the two values.
    * 
    * @param minValue The minimum range of values.
    * @param maxValue The maximum range of values.
    * @return A random number between the range.
    **************************************************************************/
    public static int randomNumber(int minValue, int maxValue)
    {
        return minValue + (int)(Math.random() * ((maxValue - minValue) + 1));
    }
}
