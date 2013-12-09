package org.ndexbio.xbel.cache;

import com.google.common.cache.RemovalNotification;

public class TermRemovalListener extends BaseRemovalListener<String,Long> {

	@Override
	public void onRemoval(RemovalNotification<String,Long> notification) {
		this.removalCause = notification.getCause();
        this.removedKey = notification.getKey();
        this.removedValue = notification.getValue();
		
	}

	

}
