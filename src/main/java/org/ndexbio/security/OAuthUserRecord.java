package org.ndexbio.security;

import java.util.Calendar;

public class OAuthUserRecord {

	private String userUUID;
	private long expirationTime;
	
	public OAuthUserRecord(String uuid, long expiresAt) {
		this.userUUID = uuid;
		this.expirationTime = expiresAt;
	}

	public String getUserUUID() {return userUUID;}
	
	public boolean isExpired () {
		return Calendar.getInstance().getTimeInMillis() > expirationTime;
	}
}
