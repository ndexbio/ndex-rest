package org.ndexbio.security;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.UUID;

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.User;

public interface OAuthAuthenticator {
	
	public UUID getUserUUIDByIdToken(String accessTokenString) 
			throws GeneralSecurityException, IOException, IllegalArgumentException, ObjectNotFoundException, NdexException;
	
	/**
	 * Get NDEx user from a idToken or accessToken.
	 * @param accessTokenString
	 * @return the user object which matches the the email of idToken.
	 * @throws GeneralSecurityException
	 * @throws IOException
	 * @throws IllegalArgumentException
	 * @throws ObjectNotFoundException when the user is not found in NDEx.
	 * @throws NdexException
	 */
	public User getUserByIdToken(String accessTokenString) throws GeneralSecurityException, IOException, IllegalArgumentException,
	             ObjectNotFoundException, NdexException;
	
	
	public User generateUserFromToken(User tmpUser, String token) 
			throws UnauthorizedOperationException, GeneralSecurityException, IOException;

		
}
