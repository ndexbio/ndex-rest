package org.ndexbio.rest.filters;

import io.swagger.v3.core.model.ApiDescription;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ndexbio.rest.Configuration;

import java.time.Year;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SwaggerFilterTest extends EasyMockSupport {

    private SwaggerFilter swaggerFilter;
    private OpenAPI openAPI;

    @BeforeEach
    void setUp() {
        swaggerFilter = new SwaggerFilter();
        openAPI = new OpenAPI();
        openAPI.setInfo(new Info());
    }

    @Test
    void testFilterOpenAPI() {
        Configuration config = EasyMock.mock(Configuration.class);
        EasyMock.expect(config.getHostURI()).andReturn("http://localhost:8080").anyTimes();
		
		Configuration.setInstance(config);
        EasyMock.replay(config);
		

        EasyMockSupport.injectMocks(this);

        Optional<OpenAPI> result = swaggerFilter.filterOpenAPI(openAPI, null, null, null);

        assertTrue(result.isPresent());
        assertNotNull(result.get().getInfo());
        assertEquals("NDEx null REST API", result.get().getInfo().getTitle());
        assertEquals("This [OpenAPI Specification](https://github.com/OAI/OpenAPI-Specification) document defines the **N**etwork **D**ata **Ex**change (**NDEx**) REST API which is used to communicate with NDEx public and private servers.\n" +
                "\nThis document and all references to the NDEx REST API, source code and ancillary documentation are copyrighted: *© 2013-" + Year.now() + ", The Regents of the University of California, The Cytoscape Consortium.  All rights reserved.*  " +
                "Please abide with the [Terms of Use, Licensing and Sources](https://home.ndexbio.org/disclaimer-license/). " +
                "Likewise, the [Swagger-UI](https://github.com/swagger-api/swagger-ui) document reader that displays " +
                "this OpenAPI document is copyrighted by *Smartbear Software*. Its open-source software license is " +
                "found [here](https://github.com/swagger-api/swagger-ui/blob/master/LICENSE).\n\nGoogle's " +
                "OAuth2 (OpenID Connect) login is not currently supported.  Basic Authentication and all other " +
                "API endpoints are supported.", result.get().getInfo().getDescription());
        assertEquals("http://localhost:8080", result.get().getServers().get(0).getUrl());

        EasyMock.verify(config);
    }

	/*
    @Test
    void testGetVersion() throws IOException {
        String jarPath = getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
        JarFile jarFile = EasyMock.mock(JarFile.class);
        Manifest manifest = EasyMock.mock(Manifest.class);
        Attributes attributes = EasyMock.mock(Attributes.class);

        EasyMock.expect(jarFile.getManifest()).andReturn(manifest).anyTimes();
        EasyMock.expect(manifest.getMainAttributes()).andReturn(attributes).anyTimes();
        EasyMock.expect(attributes.getValue("NDEx-Version")).andReturn("1.0.0").anyTimes();

        EasyMock.replay(jarFile, manifest, attributes);

        try {
            JarFile jarFileMock = jarFile;
            Manifest manifestMock = manifest;
            Attributes attributesMock = attributes;

            SwaggerFilter filter = EasyMock.partialMockBuilder(SwaggerFilter.class)
                    .addMockedMethod("getJarFile")
                    .createMock();

            EasyMock.expect(filter.getJarFile(jarPath)).andReturn(jarFileMock);
            EasyMock.replay(filter);

            String version = filter.getVersion();
            assertEquals("1.0.0", version);

            EasyMock.verify(filter);
        } finally {
            EasyMock.verify(jarFile, manifest, attributes);
        }
    }
	*/

    @Test
    void testFilterOperation() {
        Operation operation = new Operation();
        ApiDescription apiDescription = EasyMock.mock(ApiDescription.class);

        EasyMock.expect(apiDescription.getPath()).andReturn("/v2/test").anyTimes();
        EasyMock.replay(apiDescription);

        Optional<Operation> result = swaggerFilter.filterOperation(operation, apiDescription, null, null, null);

        assertTrue(result.isPresent());
        assertTrue(result.get().getTags().contains("V2 - test"));

        EasyMock.reset(apiDescription);
        EasyMock.expect(apiDescription.getPath()).andReturn("/v3/test").anyTimes();
        EasyMock.replay(apiDescription);

        result = swaggerFilter.filterOperation(operation, apiDescription, null, null, null);

        assertTrue(result.isPresent());
        assertTrue(result.get().getTags().contains("V3 - test"));

        EasyMock.reset(apiDescription);
        EasyMock.expect(apiDescription.getPath()).andReturn("/v1/test").anyTimes();
        EasyMock.replay(apiDescription);

        result = swaggerFilter.filterOperation(operation, apiDescription, null, null, null);

        assertFalse(result.isPresent());

        EasyMock.verify(apiDescription);
    }
}
