package org.ndexbio.rest.actions.networks;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.orientechnologies.orient.server.network.protocol.http.OHttpRequest;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.blueprints.impls.orient.OrientElement;
import com.tinkerpop.frames.FramedGraph;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.ndexbio.rest.actions.NdexAction;
import org.ndexbio.rest.helpers.RidConverter;
import java.io.IOException;
import java.util.List;

/******************************************************************************
* HTTP DELETE /networks 
******************************************************************************/
public class DeleteNetwork extends NdexAction<DeleteNetwork.DeleteNetworkContext>
{
    public static final class DeleteNetworkContext implements NdexAction.Context
    {
        private String networkId;
    }
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    
    
    @Override
    public String[] getNames()
    {
        return new String[] { "DELETE|networks/*" };
    }


    
    @Override
    protected void action(DeleteNetworkContext requestContext, FramedGraph<OrientBaseGraph> orientDbGraph)
    {
        ORID networkRid = RidConverter.convertToRid(requestContext.networkId);

        if (orientDbGraph.getVertex(networkRid) == null)
            return;

        ODatabaseDocumentTx databaseDocumentTx = orientDbGraph.getBaseGraph().getRawGraph();
        List<ODocument> networkChildren = databaseDocumentTx.query(new OSQLSynchQuery<Object>("select @rid from (TRAVERSE * FROM " + networkRid + " while @class <> 'xUser')"));

        for (ODocument networkChild : networkChildren)
        {
            ORID childId = networkChild.field("rid", OType.LINK);
            OrientElement element = orientDbGraph.getBaseGraph().getElement(childId);

            if (element != null)
                element.remove();
        }
    }

    @Override
    protected String getDescription()
    {
        return "Deletes a network.";
    }

    @Override
    protected DeleteNetworkContext parseRequest(OHttpRequest httpRequest) throws IOException
    {
        final JsonNode serializedNetwork = OBJECT_MAPPER.readTree(httpRequest.content);
        final String networkId = serializedNetwork.get("networkId").asText();

        final DeleteNetworkContext requestContext = new DeleteNetworkContext();
        requestContext.networkId = networkId;

        return requestContext;
    }

    @Override
    protected Object serializeResult(DeleteNetworkContext context)
    {
        return null;
    }
}
