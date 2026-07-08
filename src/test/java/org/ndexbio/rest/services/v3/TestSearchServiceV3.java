package org.ndexbio.rest.services.v3;

import org.junit.Assert;
import org.junit.Test;
import org.ndexbio.model.exceptions.BadRequestException;
import org.ndexbio.model.network.query.CXObjectFilter;
import org.ndexbio.model.object.CXSimplePathQuery;
import org.ndexbio.model.object.SimpleFileQuery;
import org.ndexbio.model.object.network.VisibilityType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TestSearchServiceV3 {

    private static final SearchServiceV3 _searchService = new SearchServiceV3(null);

    // ---------- parseUuid: direct unit tests ----------

    @Test
    public void parseUuidValid() throws BadRequestException {
        String input = "550e8400-e29b-41d4-a716-446655440000";
        UUID result = _searchService.parseUuid(input);
        Assert.assertEquals(UUID.fromString(input), result);
    }

    @Test
    public void parseUuidRoundTrip() throws BadRequestException {
        UUID original = UUID.randomUUID();
        UUID result = _searchService.parseUuid(original.toString());
        Assert.assertEquals(original, result);
    }

    @Test(expected = BadRequestException.class)
    public void parseUuidMalformed() throws BadRequestException {
        _searchService.parseUuid("not-a-uuid");
    }

    @Test(expected = BadRequestException.class)
    public void parseUuidEmpty() throws BadRequestException {
        _searchService.parseUuid("");
    }

    // Passes now that the null guard is in place.
    @Test(expected = BadRequestException.class)
    public void parseUuidNull() throws BadRequestException {
        _searchService.parseUuid(null);
    }

    // ---------- controllers: malformed UUID must be rejected before any DAO work ----------

    @Test(expected = BadRequestException.class)
    public void queryNetworkAsCXRejectsBadUuid() throws Exception {
        _searchService.queryNetworkAsCX("not-a-uuid", null, false, false, validPathQuery());
    }

    @Test(expected = BadRequestException.class)
    public void interconnectQueryRejectsBadUuid() throws Exception {
        _searchService.interconnectQuery("not-a-uuid", null, false, false, validPathQuery());
    }

    @Test(expected = BadRequestException.class)
    public void getNodeAttributesRejectsBadUuid() throws Exception {
        _searchService.getNodeAttributes("not-a-uuid", null, filterWithOneAttr());
    }

    // ---------- controllers: a VALID uuid is NOT rejected as malformed ----------
    // A valid UUID gets past parseUuid and then fails downstream (no DB/Configuration
    // in unit-test context -> NullPointerException). Any non-BadRequestException proves
    // parsing accepted the UUID, which is what we're asserting here.

    @Test
    public void queryNetworkAsCXAcceptsValidUuid() {
        try {
            _searchService.queryNetworkAsCX(UUID.randomUUID().toString(), null, false, false, validPathQuery());
        } catch (BadRequestException e) {
            Assert.fail("Valid UUID should not be rejected as a bad request: " + e.getMessage());
        } catch (Exception expectedDownstream) {
            // DAO/Configuration failure is expected: proves we got past parseUuid.
        }
    }

    @Test
    public void interconnectQueryAcceptsValidUuid() {
        try {
            _searchService.interconnectQuery(UUID.randomUUID().toString(), null, false, false, validPathQuery());
        } catch (BadRequestException e) {
            Assert.fail("Valid UUID should not be rejected as a bad request: " + e.getMessage());
        } catch (Exception expectedDownstream) {
            // expected
        }
    }

    @Test
    public void getNodeAttributesAcceptsValidUuid() {
        try {
            // Non-empty attributeNames so the only possible BadRequestException
            // would be from the UUID itself (which is valid here).
            _searchService.getNodeAttributes(UUID.randomUUID().toString(), null, filterWithOneAttr());
        } catch (BadRequestException e) {
            Assert.fail("Valid UUID should not be rejected as a bad request: " + e.getMessage());
        } catch (Exception expectedDownstream) {
            // expected
        }
    }

    // ---------- Swagger @Operation doc (F6/F3) ----------

    @Test
    public void searchFilesOperationDocReflectsMultiTypeAndAuthRules() throws Exception {
        java.lang.reflect.Method m = SearchServiceV3.class.getMethod(
                "searchFiles",
                org.ndexbio.model.object.SimpleFileQuery.class,
                org.ndexbio.model.object.network.VisibilityType.class,
                PagingParameters.class);
        io.swagger.v3.oas.annotations.Operation op =
                m.getAnnotation(io.swagger.v3.oas.annotations.Operation.class);
        Assert.assertNotNull("searchFiles must carry an @Operation doc", op);
        String desc = op.description();

        // Multi-type search is documented; the stale "networks only" text is gone.
        Assert.assertTrue("doc should mention folders", desc.toLowerCase().contains("folder"));
        Assert.assertTrue("doc should mention shortcuts", desc.toLowerCase().contains("shortcut"));
        Assert.assertFalse("stale 'only supports searching networks' text should be gone",
                desc.toLowerCase().contains("only supports searching networks"));

        // visibility semantics + the PRIVATE/UNLISTED auth requirement live on the @Parameter
        // (rendered in the Parameters table), not in the operation description.
        // Parameter order: (0) query, (1) visibility, (2) paging.
        io.swagger.v3.oas.annotations.Parameter visibilityParam = findParameterAnnotation(m, 1);
        Assert.assertNotNull("visibility must carry an @Parameter doc", visibilityParam);
        String vdesc = visibilityParam.description();
        Assert.assertTrue("visibility doc should mention PRIVATE", vdesc.contains("PRIVATE"));
        Assert.assertTrue("visibility doc should state the auth requirement",
                vdesc.toLowerCase().contains("authentic") || vdesc.toLowerCase().contains("credential"));
        Assert.assertTrue("visibility doc should note UNLISTED is not a valid search mode",
                vdesc.contains("UNLISTED"));
    }

    @Test(expected = BadRequestException.class)
    public void searchFilesRejectsUnlistedVisibility() throws Exception {
        // UNLISTED is not a valid search mode; rejected before the auth lookup.
        _searchService.searchFiles(new SimpleFileQuery(), VisibilityType.UNLISTED, new PagingParameters());
    }

    private static io.swagger.v3.oas.annotations.Parameter findParameterAnnotation(
            java.lang.reflect.Method m, int paramIndex) {
        for (java.lang.annotation.Annotation a : m.getParameterAnnotations()[paramIndex]) {
            if (a instanceof io.swagger.v3.oas.annotations.Parameter) {
                return (io.swagger.v3.oas.annotations.Parameter) a;
            }
        }
        return null;
    }

    // ---------- PagingParameters (reusable @BeanParam model) ----------

    @Test
    public void pagingParametersDefaults() {
        PagingParameters p = new PagingParameters();
        Assert.assertEquals(0, p.getStart());
        Assert.assertEquals(100, p.getSize());
    }

    @Test
    public void pagingParametersRoundTrip() {
        PagingParameters p = new PagingParameters(50, 25);
        Assert.assertEquals(50, p.getStart());
        Assert.assertEquals(25, p.getSize());
        p.setStart(5);
        p.setSize(10);
        Assert.assertEquals(5, p.getStart());
        Assert.assertEquals(10, p.getSize());
    }

    // ---------- helpers ----------

    private static CXSimplePathQuery validPathQuery() {
        CXSimplePathQuery q = new CXSimplePathQuery();
        q.setSearchString("test");
        q.setSearchDepth(1);
        return q;
    }

    private static CXObjectFilter filterWithOneAttr() {
        CXObjectFilter f = new CXObjectFilter();
        Set<String> names = new HashSet<>();
        names.add("name");
        f.setAttributeNames(names);
        return f;
    }
}