package org.ndexbio.rest.annotations;

import java.lang.annotation.*;

@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiDoc {

	String value();

}
