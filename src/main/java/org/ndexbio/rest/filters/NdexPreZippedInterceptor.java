package org.ndexbio.rest.filters;

import java.io.IOException;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.ext.Provider;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;

import org.ndexbio.rest.services.NdexService;

@Provider
public class NdexPreZippedInterceptor implements WriterInterceptor {

	@Override
	public void aroundWriteTo(WriterInterceptorContext context)
			throws IOException, WebApplicationException {

		Object o = context.getProperty(NdexService.NdexZipFlag);
		if ( o!=null && o == Boolean.TRUE) {
			context.getHeaders().putSingle("Content-Encoding", "gzip");
			context.getHeaders().putSingle("Vary", "Accept-Encoding");
		}
        context.proceed();

	}

}
