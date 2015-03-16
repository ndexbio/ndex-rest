package org.ndexbio.rest.services;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention (RUNTIME)
@Target({TYPE, METHOD})
public @interface NdexOpenFunction {
  // no content n this annotation because it is just a flag.
}
