package org.ndexbio.security;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.ndexbio.common.models.dao.orientdb.UserDocDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.NewUser;
import org.ndexbio.model.object.User;
import org.ndexbio.task.Configuration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class GoogleOpenIDAuthenticator {

	private static final String GOOGLE_OAUTH_KEY = "GOOGLE_OAUTH_KEY";
	private static final String GOOGLE_OAUTH_CLIENT_ID = "GOOGLE_OAUTH_CLIENT_ID";
	private static final String GOOGLE_OAUTH_CLIENT_SECRET = "GOOGLE_OAUTH_CLIENT_SECRET";

	private String apiState;
	private String clientID;
	private String clientSecret;

	// a table to support google OAuth authentication. a access_token -> user uuid mapping table
	private Map<String,OAuthUserRecord> googleTokenTable ;  
	
	
	public GoogleOpenIDAuthenticator(Configuration config) throws NdexException {
		
		apiState = config.getRequiredProperty(GOOGLE_OAUTH_KEY);

		clientID = config.getRequiredProperty(GOOGLE_OAUTH_CLIENT_ID);
		clientSecret = config.getRequiredProperty(GOOGLE_OAUTH_CLIENT_SECRET);
		
		googleTokenTable = new TreeMap<>();

	}

	public String getAPIStateKey () {return apiState;}
	
//	public String getClientID () { return clientID;}
//	public String getClientSecret() { return clientSecret;}
	

	public void revokeAllTokens() {
		for (String token : googleTokenTable.keySet() ) {
			//TODO: revoke all tokens when shutdown. 
		}
	}
	
	
	public String getIDTokenFromQueryStr(String googleQueryString) throws NdexException, ClientProtocolException, IOException {
		
	
		 Pattern p = Pattern.compile("state=(.*)url%3D(http://.*/user/google/authenticate)&code=(.*)");
		 Matcher m = p.matcher(googleQueryString);
		 if ( !m.matches())
			 throw new NdexException("Incorrect URL format received from Google Oauth.");
		 String state = m.group(1);
		 String redirectURI = m.group(2);
		 String code = m.group(3);
		 System.out.println(state + "," + redirectURI + "," + code);

		 if ( state ==null || !state.equals(apiState))
			 throw new NdexException(GOOGLE_OAUTH_KEY + " value mismatch between config and Google request");
		 
		 HttpClient httpclient = HttpClients.createDefault();
		 HttpPost httppost = new HttpPost("https://www.googleapis.com/oauth2/v4/token");

		 // Request parameters and other properties.
		 List<NameValuePair> params = new ArrayList<>(5);
		 params.add(new BasicNameValuePair("code", code));
		 params.add(new BasicNameValuePair("client_id", clientID)); 
				 //"7378376161-vu7audi0s6fck7bbl9ojo31onjpedhs2.apps.googleusercontent.com"));
		 params.add(new BasicNameValuePair("client_secret", clientSecret));
				 //"bReyi0bTMzvy9ayu97fYYZyx"));
		 params.add(new BasicNameValuePair("redirect_uri", redirectURI ));
		 params.add(new BasicNameValuePair("grant_type", "authorization_code" ));
		 httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));

		 //Execute and get the response.
		 HttpResponse response = httpclient.execute(httppost);
		 HttpEntity entity = response.getEntity();

		 String theString =null;
		 if (entity != null) {
		     InputStream instream = entity.getContent();
		     try {
		         // do something useful
		    	 java.util.Scanner s = new java.util.Scanner(instream).useDelimiter("\\A");
		    	 theString = s.hasNext() ? s.next() : "";
		    	 System.out.println( theString);
		     } finally {
		         instream.close();
		     }
		 }
		 
		 ObjectMapper mapper = new ObjectMapper();
		 
		 Map<String,Object> googleToken = mapper.readValue(theString, new TypeReference<Map<String,Object>>() {});

//		 System.out.println (googleToken);

		 String accessToken = (String)googleToken.get("access_token");
		 Integer expiresIn = (Integer)googleToken.get("expires_in");
		 long expirationTime = Calendar.getInstance().getTimeInMillis() + expiresIn * 1000; 
		 
		 // get user profile from access_token
		 Map<String, String > userProfile = getGoogleUserProfileFromAccessToken(accessToken);
		 System.out.println (userProfile);
		 
		 String userEmail = userProfile.get("email");
		
		 if ( userEmail == null || userEmail.length() < 6) 
			 throw new NdexException ( "Failed to get email address from Google.");
		 
		 try (UserDocDAO userDao = new UserDocDAO()) {
			 String userUUID = userDao.getUserUUIDByEmail(userEmail);
			 if ( userUUID ==null) { 
				 // create this user and store the token.
				 NewUser newUser = new NewUser();
				 newUser.setAccountName(userEmail);
				 newUser.setEmailAddress(userEmail);
				 newUser.setFirstName(userProfile.get("given_name"));
				 newUser.setLastName(userProfile.get("family_name"));
				 newUser.setImage(userProfile.get("picture"));
				 newUser.setWebsite(userProfile.get("link"));
				 newUser.setPassword(RandomStringUtils.random(25));
				 User user = userDao.createNewUser(newUser, null);
				 userUUID = user.getExternalId().toString();
			 }

			 // store the token in in-memory table
			 googleTokenTable.put(accessToken, new OAuthUserRecord(userUUID,expirationTime));
		 }			 
		return theString;
	
	}
	
	private static Map<String,String> getGoogleUserProfileFromAccessToken(String accessToken) throws ClientProtocolException, IOException {
		
		 HttpClient httpclient = HttpClients.createDefault();
		 HttpGet httpget = new HttpGet("https://www.googleapis.com/oauth2/v1/userinfo");
		 
		 httpget.addHeader("Authorization", "Bearer " + accessToken);

		 //Execute and get the response.
		 HttpResponse response = httpclient.execute(httpget);
		 HttpEntity entity = response.getEntity();

		 String theString =null;
		 if (entity != null) {
		     
		     try (InputStream instream = entity.getContent();) {
		         // do something useful
		    	 java.util.Scanner s = new java.util.Scanner(instream).useDelimiter("\\A");
		    	 theString = s.hasNext() ? s.next() : "";
		    	 s.close();
		    	 System.out.println( theString);
		     } 
		 }
		
		 ObjectMapper mapper = new ObjectMapper();
		 
		Map<String,String> r = mapper.readValue(theString, new TypeReference<Map<String,String>>() {});

		return r;
	}
	

	public String GetUserUUIDFromAccessToke(String accessToken) throws NdexException {
		OAuthUserRecord r = googleTokenTable.get(accessToken);
		if ( r== null) throw new NdexException ("Invalid access token received.");
		if (!r.isExpired())
			return r.getUserUUID();
		
		throw new NdexException ("Access token already expired.");
	}
	
	public void revokeAccessToken ( String accessToken) throws ClientProtocolException, IOException, NdexException {
		 HttpClient httpclient = HttpClients.createDefault();
		 HttpGet httpget = new HttpGet("https://accounts.google.com/o/oauth2/revoke?token="
				 + accessToken);

		 //Execute and get the response.
		 HttpResponse response = httpclient.execute(httpget);
		 if (response.getStatusLine().getStatusCode() != 200) 
			 throw new NdexException ("Failed to revoke accessToken on Google.");
		 
		 googleTokenTable.remove(accessToken);
		
	}
	
	public String getNewAccessTokenByRefreshToken(String expiredAccessToken, String refreshToken) throws ClientProtocolException, IOException, NdexException {
		
		OAuthUserRecord r = googleTokenTable .get(expiredAccessToken);
		
		if ( r == null) throw new NdexException ("AccessToken not found in Ndex server.");
		
		
		HttpClient httpclient = HttpClients.createDefault();
		 HttpPost httppost = new HttpPost("https://www.googleapis.com/oauth2/v4/token");

		 // Request parameters and other properties.
		 List<NameValuePair> params = new ArrayList<>(5);
		 params.add(new BasicNameValuePair("refresh_token", refreshToken));
		 params.add(new BasicNameValuePair("client_id", clientID)); 
				 //"7378376161-vu7audi0s6fck7bbl9ojo31onjpedhs2.apps.googleusercontent.com"));
		 params.add(new BasicNameValuePair("client_secret", clientSecret));
				 //"bReyi0bTMzvy9ayu97fYYZyx"));
		 params.add(new BasicNameValuePair("grant_type", "refresh_token" ));
		 httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));

		 //Execute and get the response.
		 HttpResponse response = httpclient.execute(httppost);
		 HttpEntity entity = response.getEntity();

		 String theString =null;
		 if (entity != null) {
		     InputStream instream = entity.getContent();
		     try {
		         // do something useful
		    	 java.util.Scanner s = new java.util.Scanner(instream).useDelimiter("\\A");
		    	 theString = s.hasNext() ? s.next() : "";
		    	 System.out.println( theString);
		     } finally {
		         instream.close();
		     }
		 }
		 
		 ObjectMapper mapper = new ObjectMapper();
		 
		 Map<String,Object> googleToken = mapper.readValue(theString, new TypeReference<Map<String,Object>>() {});

		 String accessToken = (String)googleToken.get("access_token");
		 Integer expiresIn = (Integer)googleToken.get("expires_in");
		 long newExpirationTime = Calendar.getInstance().getTimeInMillis() + expiresIn.longValue() * 1000; 
		 
		 // get user profile from access_token
		 OAuthUserRecord userRec = this.googleTokenTable.remove(expiredAccessToken);
		 
		 if ( userRec == null)
			 throw new NdexException ("Access token " + expiredAccessToken + " not found in Ndex.");
		 
		 userRec.setExpirationTime(newExpirationTime);
		 googleTokenTable.put(accessToken, userRec);

		 return theString;
	}
	
}
