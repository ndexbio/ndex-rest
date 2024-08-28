package org.ndexbio.rest.services;

import io.swagger.v3.jaxrs2.integration.JaxrsApplicationAndResourcePackagesAnnotationScanner;
import java.util.HashSet;
import java.util.Set;
import org.ndexbio.rest.filters.SwaggerFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Special openapi scanner that only includes org.ndexbio classes.
 * This is needed because for some reason solr is getting included
 * 
 * @author churas
 */
public class SwaggerScanner extends JaxrsApplicationAndResourcePackagesAnnotationScanner {

	static Logger _logger = LoggerFactory.getLogger(SwaggerFilter.class.getSimpleName());

	/**
	 * Call the super implementation of classes and then only keep classes
	 * whose package name starts with org.ndex
	 * @return 
	 */
	@Override
	public Set<Class<?>> classes() {
		Set<Class<?>> unprocessed_classes = super.classes();
		Set<Class<?>> output = new HashSet<>();
		for (Class c : unprocessed_classes){
			if (!c.getPackageName().startsWith("org.ndex")){
				continue;
			}
			_logger.debug("Adding to swagger " + c.getCanonicalName());
			output.add(c);
		}
		return output;
	}
	
	
	
}
