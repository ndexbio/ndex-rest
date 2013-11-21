package org.ndexbio.rest.services;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import org.ndexbio.rest.domain.INetwork;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.helpers.RidConverter;
import org.ndexbio.rest.models.Network;
import com.orientechnologies.orient.core.id.ORID;
import com.tinkerpop.blueprints.Vertex;

@Path("/networks")
public class NetworkService extends NdexService
{
    @DELETE
    @Path("/{networkId}")
    @Produces("application/json")
    public void deleteNetwork(@PathParam("networkId")final String networkJid) throws NdexException
    {
        ORID networkRid = RidConverter.convertToRid(networkJid);

        final Vertex networkToDelete = _orientDbGraph.getVertex(networkRid); 
        if (networkToDelete == null)
            return;

        try
        {
            _orientDbGraph.removeVertex(networkToDelete);
            _orientDbGraph.getBaseGraph().commit();

            //TODO: Is this necessary? (Deleting a network should delete all children)
//            ODatabaseDocumentTx databaseDocumentTx = orientDbGraph.getBaseGraph().getRawGraph();
//            List<ODocument> networkChildren = databaseDocumentTx.query(new OSQLSynchQuery<Object>("select @rid from (TRAVERSE * FROM " + networkRid + " while @class <> 'xUser')"));
//
//            for (ODocument networkChild : networkChildren)
//            {
//                ORID childId = networkChild.field("rid", OType.LINK);
//                OrientElement element = orientDbGraph.getBaseGraph().getElement(childId);
//
//                if (element != null)
//                    element.remove();
//            }
        }
        catch (Exception e)
        {
            if (_orientDbGraph != null)
                _orientDbGraph.getBaseGraph().rollback();
            
            throw e;
        }
        finally
        {
            if (_ndexDatabase != null)
                _ndexDatabase.close();
        }
    }

    @GET
    @Path("/{networkId}")
    @Produces("application/json")
    public Network getNetwork(@PathParam("networkId")final String networkJid) throws NdexException
    {
        ORID networkRid = RidConverter.convertToRid(networkJid);
        final INetwork network = _orientDbGraph.getVertex(networkRid, INetwork.class);
        if (network == null)
            return null;
        else
            return new Network(network);
    }
    
    @POST
    @Produces("application/json")
    public void updateNetwork(final Network updatedNetwork) throws NdexException
    {
        final INetwork network = _orientDbGraph.getVertex(RidConverter.convertToRid(updatedNetwork.getId()), INetwork.class);
        if (network == null)
            throw new ObjectNotFoundException("Network", updatedNetwork.getId());

        try
        {
            network.setProperties(updatedNetwork.getProperties());
            _orientDbGraph.getBaseGraph().commit();
        }
        catch (Exception e)
        {
            if (_orientDbGraph != null)
                _orientDbGraph.getBaseGraph().rollback();
            
            throw e;
        }
        finally
        {
            if (_ndexDatabase != null)
                _ndexDatabase.close();
        }
    }
}
