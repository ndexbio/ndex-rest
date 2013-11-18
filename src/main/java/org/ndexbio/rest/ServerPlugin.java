package org.ndexbio.rest;

import com.orientechnologies.orient.server.OServer;
import com.orientechnologies.orient.server.config.OServerParameterConfiguration;
import com.orientechnologies.orient.server.network.OServerNetworkListener;
import com.orientechnologies.orient.server.network.protocol.http.ONetworkProtocolHttpAbstract;
import com.orientechnologies.orient.server.plugin.OServerPluginAbstract;
import org.ndexbio.rest.actions.groups.*;
import org.ndexbio.rest.actions.networks.*;
import org.ndexbio.rest.actions.tasks.*;
import org.ndexbio.rest.actions.users.*;
import org.ndexbio.rest.actions.worksurface.*;

public class ServerPlugin extends OServerPluginAbstract
{
    @Override
    public void config(OServer server, OServerParameterConfiguration[] serverParameterConfigurations)
    {
        OServerNetworkListener httpListener = server.getListenerByProtocol(ONetworkProtocolHttpAbstract.class);

        httpListener.registerStatelessCommand(new DeleteGroup());
        httpListener.registerStatelessCommand(new GetGroup());
        httpListener.registerStatelessCommand(new PostGroup());
        httpListener.registerStatelessCommand(new PutGroup());
        
        httpListener.registerStatelessCommand(new DeleteNetwork());
        httpListener.registerStatelessCommand(new FindNetworks());
        httpListener.registerStatelessCommand(new GetEdges());
        httpListener.registerStatelessCommand(new GetMetadata());
        httpListener.registerStatelessCommand(new GetNetwork());
        httpListener.registerStatelessCommand(new GetNodes());
        httpListener.registerStatelessCommand(new PostNetwork());
        httpListener.registerStatelessCommand(new PutNetwork());

        httpListener.registerStatelessCommand(new PutTask());
        httpListener.registerStatelessCommand(new DeleteTask());
        httpListener.registerStatelessCommand(new PostTask());
        httpListener.registerStatelessCommand(new GetTask());

        httpListener.registerStatelessCommand(new DeleteUser());
        httpListener.registerStatelessCommand(new GetUser());
        httpListener.registerStatelessCommand(new PostUser());
        httpListener.registerStatelessCommand(new PutUser());
        
        httpListener.registerStatelessCommand(new DeleteWorkSurface());
        httpListener.registerStatelessCommand(new GetWorkSurface());
        httpListener.registerStatelessCommand(new PutWorkSurface());
    }

    @Override
    public String getName()
    {
        return "NDEx REST Service";
    }

    @Override
    public void startup()
    {
        super.startup();
    }

    @Override
    public void shutdown()
    {
        super.shutdown();
    }
}