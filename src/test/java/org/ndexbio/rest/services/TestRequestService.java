package org.ndexbio.rest.services;

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.ndexbio.common.exceptions.DuplicateObjectException;
import org.ndexbio.common.exceptions.NdexException;
import org.ndexbio.common.models.object.Request;
import org.ndexbio.common.helpers.IdConverter;
import com.orientechnologies.orient.core.id.ORID;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestRequestService extends TestNdexService
{
    private static final RequestService _requestService = new RequestService(_mockRequest);

    
    
    @Test
    public void createRequest()
    {
        Assert.assertTrue(createNewRequest());
    }

    @Test(expected = IllegalArgumentException.class)
    public void createRequestInvalid() throws IllegalArgumentException, NdexException
    {
        _requestService.createRequest(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createRequestInvalidRequestType() throws IllegalArgumentException, NdexException
    {
        final Request newRequest = new Request();
        newRequest.setFrom("fjcriscuolo");
        newRequest.setFromId(IdConverter.toJid(getRid("fjcriscuolo")));
        newRequest.setMessage("This is a test request.");
        newRequest.setRequestType("Bogus Request Type");
        newRequest.setTo("REACTOME TEST");
        newRequest.setToId(IdConverter.toJid(getRid("REACTOME TEST")));
        
        _requestService.createRequest(newRequest);
    }

    @Test
    public void deleteRequest()
    {
        Assert.assertTrue(createNewRequest());

        final ORID testRequestRid = getRid("This is a (unit) test request.");
        Assert.assertTrue(deleteTargetRequest(IdConverter.toJid(testRequestRid)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void deleteRequestInvalid() throws IllegalArgumentException, NdexException
    {
        _requestService.deleteRequest("");
    }

    @Test
    public void getRequest()
    {
        try
        {
            final ORID testRequestRid = getRid("John, we'd like to invite you to join triptychjs.");
            final Request testRequest = _requestService.getRequest(IdConverter.toJid(testRequestRid));
            Assert.assertNotNull(testRequest);
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void getRequestInvalid() throws IllegalArgumentException, NdexException
    {
        _requestService.getRequest("");
    }

    @Test
    public void updateRequest()
    {
        try
        {
            Assert.assertTrue(createNewRequest());
            
            final ORID testRequestRid = getRid("This is a (unit) test request.");

            final Request testRequest = _requestService.getRequest(IdConverter.toJid(testRequestRid));
            testRequest.setResponse("DECLINED");
            testRequest.setResponseMessage("Because this is a test.");
            testRequest.setResponder(testRequest.getToId());

            _requestService.updateRequest(testRequest);
            Assert.assertEquals(_requestService.getRequest(testRequest.getId()).getResponse(), testRequest.getResponse());
            
            Assert.assertTrue(deleteTargetRequest(testRequest.getId()));
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void updateRequestInvalid() throws IllegalArgumentException, NdexException
    {
        _requestService.updateRequest(null);
    }
    
    
    
    private boolean createNewRequest()
    {
        final Request newRequest = new Request();
        newRequest.setFrom("fjcriscuolo");
        newRequest.setFromId(IdConverter.toJid(getRid("fjcriscuolo")));
        newRequest.setMessage("This is a (unit) test request.");
        newRequest.setRequestType("Network Access");
        newRequest.setTo("REACTOME TEST");
        newRequest.setToId(IdConverter.toJid(getRid("REACTOME TEST")));
        
        try
        {
            _requestService.createRequest(newRequest);
            return true;
        }
        catch (DuplicateObjectException doe)
        {
            return true;
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
        
        return false;
    }
    
    private boolean deleteTargetRequest(String requestId)
    {
        try
        {
            _requestService.deleteRequest(requestId);
            Assert.assertNull(_requestService.getRequest(requestId));
            
            return true;
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
        
        return false;
    }
}
