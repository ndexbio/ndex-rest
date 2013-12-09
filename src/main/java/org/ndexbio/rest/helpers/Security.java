package org.ndexbio.rest.helpers;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.List;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.MultivaluedMap;
import org.jboss.resteasy.util.Base64;
import org.ndexbio.rest.domain.IBaseTerm;
import org.ndexbio.rest.domain.IFunctionTerm;
import org.ndexbio.rest.domain.IGroup;
import org.ndexbio.rest.domain.IGroupInvitationRequest;
import org.ndexbio.rest.domain.IGroupMembership;
import org.ndexbio.rest.domain.IJoinGroupRequest;
import org.ndexbio.rest.domain.INetworkAccessRequest;
import org.ndexbio.rest.domain.INetworkMembership;
import org.ndexbio.rest.domain.IUser;
import org.ndexbio.rest.models.User;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentPool;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;
import com.tinkerpop.frames.FramedGraph;
import com.tinkerpop.frames.FramedGraphFactory;
import com.tinkerpop.frames.modules.gremlingroovy.GremlinGroovyModule;
import com.tinkerpop.frames.modules.typedgraph.TypedGraphModuleBuilder;

public class Security
{
    /**************************************************************************
    * Authenticates the user against the OrientDB database.
    * 
    * @param authInfo
    *            A string array containing the username/password.
    * @throws Exception
    *            Various exceptions if accessing the database fails.
    * @returns True if the user is authenticated, false otherwise.
    **************************************************************************/
    public static User authenticateUser(final String[] authInfo) throws Exception
    {
        final FramedGraphFactory graphFactory = new FramedGraphFactory(new GremlinGroovyModule(),
            new TypedGraphModuleBuilder()
                .withClass(IGroup.class)
                .withClass(IUser.class)
                .withClass(IGroupMembership.class)
                .withClass(INetworkMembership.class)
                .withClass(IGroupInvitationRequest.class)
                .withClass(IJoinGroupRequest.class)
                .withClass(INetworkAccessRequest.class)
                .withClass(IBaseTerm.class)
                .withClass(IFunctionTerm.class)
                .build());

        ODatabaseDocumentTx ndexDatabase = null;
        try
        {
            //TODO: Refactor this to connect using a configurable username/password, and database
            ndexDatabase = ODatabaseDocumentPool.global().acquire("remote:localhost/ndex", "admin", "admin");
            final FramedGraph<OrientBaseGraph> orientDbGraph = graphFactory.create((OrientBaseGraph)new OrientGraph(ndexDatabase));

            Collection<ODocument> usersFound = ndexDatabase
                .command(new OCommandSQL("select from User where username = ?"))
                .execute(authInfo[0]);
            
            if (usersFound.size() < 1)
                return null;

            IUser authUser = orientDbGraph.getVertex(usersFound.toArray()[0], IUser.class);
            String hashedPassword = Security.hashText(authInfo[1]);
            if (!hashedPassword.equals(authUser.getPassword()))
                return null;

            return new User(authUser, true);
        }
        finally
        {
            if (ndexDatabase != null)
                ndexDatabase.close();
        }
    }

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
        final String symbolCharacters = "-=[];~!@#$%^&*()_+{}|:<>?";
        
        StringBuilder randomPassword = new StringBuilder();
        for (int passwordIndex = 0; passwordIndex < passwordLength; passwordIndex++)
        {
            //Determine if the character will be alpha, numeric, or a symbol
            final int charType = randomNumber(1, 3);
            
            //Add the random character
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
    **************************************************************************/
    public static String[] parseCredentials(ContainerRequestContext requestContext) throws IOException
    {
        final MultivaluedMap<String, String> headers = requestContext.getHeaders();
        final List<String> authHeader = headers.get("Authorization");
        
        if (authHeader == null || authHeader.isEmpty())
            return null;

        final String encodedAuthInfo = authHeader.get(0).replaceFirst("Basic" + " ", "");
        final String decodedAuthInfo = new String(Base64.decode(encodedAuthInfo));
        
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
