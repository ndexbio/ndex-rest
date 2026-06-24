package org.ndexbio.rest.services.v3;

import org.junit.Assert;
import org.junit.Test;
import org.ndexbio.model.exceptions.BadRequestException;
import org.ndexbio.model.network.query.CXObjectFilter;
import org.ndexbio.model.object.CXSimplePathQuery;

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