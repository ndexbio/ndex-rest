package org.ndexbio.rest.filters;

import io.swagger.v3.core.model.ApiDescription;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ndexbio.rest.Configuration;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TestSwaggerFilter extends EasyMockSupport {

    private SwaggerFilter swaggerFilter;
    private OpenAPI openAPI;
    private Configuration savedInstance;

    @BeforeEach
    void setUp() {
        savedInstance = Configuration.getInstance();
        swaggerFilter = new SwaggerFilter();
        openAPI = new OpenAPI();
        openAPI.setInfo(new Info());
    }

    @AfterEach
    void tearDown() {
        Configuration.setInstance(savedInstance);
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

    @Test
    void testDescribeRequestSchemas_setsFieldDescriptions() {
        ObjectSchema fileQuery = new ObjectSchema();
        fileQuery.addProperty("searchString", new StringSchema());
        fileQuery.addProperty("type", new StringSchema());
        fileQuery.addProperty("accountName", new StringSchema());
        fileQuery.addProperty("permission", new StringSchema());

        Components components = new Components();
        components.addSchemas("SimpleFileQuery", fileQuery);
        openAPI.setComponents(components);

        SwaggerFilter.describeRequestSchemas(openAPI);

        Map<String, Schema> props = openAPI.getComponents().getSchemas().get("SimpleFileQuery").getProperties();
        assertHasDescription(props.get("searchString"), "*:*");
        assertHasDescription(props.get("type"), "NETWORK");
        assertHasDescription(props.get("accountName"), "owned");
        assertHasDescription(props.get("permission"), "READ");
    }

    @Test
    void testDescribeRequestSchemas_setsInheritedSearchStringOnSimpleQuery() {
        ObjectSchema simpleQuery = new ObjectSchema();
        simpleQuery.addProperty("searchString", new StringSchema());

        Components components = new Components();
        components.addSchemas("SimpleQuery", simpleQuery);
        openAPI.setComponents(components);

        SwaggerFilter.describeRequestSchemas(openAPI);

        Map<String, Schema> props = openAPI.getComponents().getSchemas().get("SimpleQuery").getProperties();
        assertHasDescription(props.get("searchString"), "*:*");
    }

    @Test
    void testDescribeRequestSchemas_isNullSafe() {
        // No components at all.
        assertDoesNotThrow(() -> SwaggerFilter.describeRequestSchemas(new OpenAPI()));

        // Components present but no schemas.
        OpenAPI noSchemas = new OpenAPI();
        noSchemas.setComponents(new Components());
        assertDoesNotThrow(() -> SwaggerFilter.describeRequestSchemas(noSchemas));

        // Schema present but missing some target properties — only present ones get described.
        ObjectSchema partial = new ObjectSchema();
        partial.addProperty("type", new StringSchema());
        Components components = new Components();
        components.addSchemas("SimpleFileQuery", partial);
        OpenAPI partialApi = new OpenAPI();
        partialApi.setComponents(components);

        assertDoesNotThrow(() -> SwaggerFilter.describeRequestSchemas(partialApi));
        Map<String, Schema> partialProps =
                partialApi.getComponents().getSchemas().get("SimpleFileQuery").getProperties();
        assertHasDescription(partialProps.get("type"), "NETWORK");
    }

    @Test
    void testRestrictSearchFilesVisibilityValues_dropsUnlisted() {
        StringSchema visibilitySchema = new StringSchema();
        visibilitySchema.setEnum(new ArrayList<>(List.of("PUBLIC", "PRIVATE", "UNLISTED")));
        Parameter visibility = new Parameter().name("visibility").in("query").schema(visibilitySchema);
        Operation post = new Operation().addParametersItem(visibility);

        Paths paths = new Paths();
        paths.addPathItem("/v3/search/files", new PathItem().post(post));
        openAPI.setPaths(paths);

        SwaggerFilter.restrictSearchFilesVisibilityValues(openAPI);

        List<?> values = openAPI.getPaths().get("/v3/search/files").getPost()
                .getParameters().get(0).getSchema().getEnum();
        assertEquals(List.of("PUBLIC", "PRIVATE"), values);
    }

    @Test
    void testRestrictSearchFilesVisibilityValues_isNullSafe() {
        assertDoesNotThrow(() -> SwaggerFilter.restrictSearchFilesVisibilityValues(new OpenAPI()));

        // Unrelated path is left untouched.
        StringSchema other = new StringSchema();
        other.setEnum(new ArrayList<>(List.of("PUBLIC", "PRIVATE", "UNLISTED")));
        Operation post = new Operation().addParametersItem(
                new Parameter().name("visibility").in("query").schema(other));
        Paths paths = new Paths();
        paths.addPathItem("/v3/networks", new PathItem().post(post));
        openAPI.setPaths(paths);

        SwaggerFilter.restrictSearchFilesVisibilityValues(openAPI);

        assertEquals(List.of("PUBLIC", "PRIVATE", "UNLISTED"),
                openAPI.getPaths().get("/v3/networks").getPost().getParameters().get(0).getSchema().getEnum());
    }

    private static void assertHasDescription(Schema<?> property, String expectedSubstring) {
        assertNotNull(property, "property should exist");
        String d = property.getDescription();
        assertNotNull(d, "property description should be set");
        assertFalse(d.isBlank(), "property description should not be blank");
        assertTrue(d.contains(expectedSubstring),
                "description should contain '" + expectedSubstring + "' but was: " + d);
    }
}
