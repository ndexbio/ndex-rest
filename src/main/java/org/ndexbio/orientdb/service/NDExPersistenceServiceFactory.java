package org.ndexbio.orientdb.service;


/*
 * Single to return an implementation of the NDExPersitenceService interface
 * For the Beta version this will be limited to an instance of the NDExMemoryPersistance class
 */
public enum NDExPersistenceServiceFactory {
	INSTANCE;
	
	public NDExPersistenceService getNDExPersistenceService() {
		return  NDExMemoryPersistence.INSTANCE;
	}
}
