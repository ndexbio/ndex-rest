package org.ndexbio.rest.util;

import java.text.DecimalFormat;

public class MemoryUtilization {
	
	private static DecimalFormat df = new DecimalFormat("#,###");
	
	public static String getMemoryUtiliztaion() {
		
	    Runtime runtime = Runtime.getRuntime();
	    
	    return "heap="  + df.format(runtime.totalMemory()) + 
	           " max="  + df.format(runtime.maxMemory()) +
		       " used=" + df.format(runtime.totalMemory() - runtime.freeMemory()) +
	           " free=" + df.format(runtime.freeMemory());
	}
}
