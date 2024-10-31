package org.ndexbio.security;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.SQLException;
import java.util.Base64;
import java.util.UUID;

import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.User;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

public class KeyCloakOpenIDAuthenticator implements OAuthAuthenticator {
		
	JWTVerifier verifier;
	 
	public KeyCloakOpenIDAuthenticator(String pubKeyStr, String issuer) throws InvalidKeySpecException, NoSuchAlgorithmException {
		
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");

		byte[] encodedPub = Base64.getDecoder().decode(pubKeyStr);
		
		RSAPublicKey pubk = (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(encodedPub));
		
		verifier = JWT.require (Algorithm.RSA256(pubk, null))
		          .withIssuer(issuer)//"http://localhost:8080/realms/googleauth")
		          .build();
	}

	
	private String getUserEmailFromIdToken(String idToken) {
	
		DecodedJWT jwt = getDecodedJWT(idToken);
				
		return jwt.getClaim("email").asString();
		
	}

	//decode the token and verify it.
	private DecodedJWT getDecodedJWT(String idToken) {
		DecodedJWT jwt = JWT.decode(idToken);
		verifier.verify(jwt);
		return jwt;
	}

	@Override
	public UUID getUserUUIDByIdToken(String idTokenString) throws GeneralSecurityException, IOException,
			IllegalArgumentException, ObjectNotFoundException, NdexException {
		String username = getUserNameFromIdToken(idTokenString);
		//try to get user by username	
		if (username != null) {
			try (UserDAO userDao = new UserDAO()) {
				User u = userDao.getUserByAccountName(username, true, false);
				if ( !u.getIsVerified())
					throw UnauthorizedOperationException.createUnVerifiedAccountError(u.getUserName(), u.getEmailAddress());
				return u.getExternalId();
			} catch (SQLException e1) {
				e1.printStackTrace();
				throw new NdexException("SQL Error when getting user by username: " + e1.getMessage());
			} catch (ObjectNotFoundException e) {
				// Didn't find the user by username. Go to the next step.
			}
		}

		//if username is not specified in id token, try to get user by email
		String email = getUserEmailFromIdToken(idTokenString);
		if ( email == null)
			throw new UnauthorizedOperationException("Email is not found in the token.");
		try (UserDAO userDao = new UserDAO()) {
			UUID userUUID = userDao.getUUIDByEmail(email.toLowerCase());
			return userUUID;
		} catch (SQLException e1) {
			e1.printStackTrace();
			throw new UnauthorizedOperationException("SQL Error when getting user by email: " + e1.getMessage());
		}
	}


	private String getUserNameFromIdToken(String idToken) {
		DecodedJWT jwt = getDecodedJWT(idToken);

		return jwt.getClaim("preferred_username").asString();

	}
	
	@Override
	public User getUserByIdToken(String idTokenString) throws GeneralSecurityException, IOException,
			IllegalArgumentException, ObjectNotFoundException, NdexException {
		
		//Try to get user by username first
		String username = getUserNameFromIdToken(idTokenString);
		if (username != null) {
			try (UserDAO userDao = new UserDAO()) {
				User u = userDao.getUserByAccountName(username, false, true);
				if ( !u.getIsVerified())
					throw UnauthorizedOperationException.createUnVerifiedAccountError(u.getUserName(), u.getEmailAddress());
                    
				return u;
			} 
			catch (ObjectNotFoundException e) {
				// Didn't find the user by username. Go to the next step. 
		    }
			catch (SQLException e1) {
				throw new NdexException("SQL Error when getting user by username: " + e1.getMessage());
			}
		}
		//Try to get user by email and return the user
		String email = getUserEmailFromIdToken(idTokenString);
		if ( email == null)
			throw new UnauthorizedOperationException("No username or email is specified in the token.");
		
		 try (UserDAO userDao = new UserDAO()) {
			 User user = userDao.getUserByEmail(email.toLowerCase(),true);
			 return user;	
		 }	catch ( SQLException e1) {
			 e1.printStackTrace();
			  throw new NdexException("SQL Error when getting user by email: " + e1.getMessage());
		 }	
	}


	@Override
	public User generateUserFromToken(User tmpUser, String idToken)
			throws UnauthorizedOperationException, GeneralSecurityException, IOException {
		 DecodedJWT jwt = JWT.decode(idToken);
		
		  User newUser = new User();
		  String email = jwt.getClaim("email").asString();
		  newUser.setUserName(tmpUser.getUserName()==null? email: tmpUser.getUserName());
		  newUser.setEmailAddress(email);
		  newUser.setFirstName(jwt.getClaim("given_name").asString());
		  String pictureUrl = (jwt.getClaim("picture").asString());
		  if ( pictureUrl !=null) 
			  newUser.setImage(pictureUrl);
		  newUser.setLastName(jwt.getClaim("family_name").asString());
		  newUser.setDisplayName(jwt.getClaim("name").asString());
		  
		return newUser;	
	}
	
	
}
