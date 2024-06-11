package org.ndexbio.rest.filters;

import io.swagger.v3.core.filter.AbstractSpecFilter;
import io.swagger.v3.core.model.ApiDescription;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.ServerVariable;
import io.swagger.v3.oas.models.servers.ServerVariables;
import java.io.IOException;
import java.time.Year;
import java.util.Arrays;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.ndexbio.rest.Configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filters swagger open api documentation based on configuration passed into
 * this App
 * @author churas
 */
public class SwaggerFilter extends AbstractSpecFilter {
	
    static Logger _logger = LoggerFactory.getLogger(SwaggerFilter.class.getSimpleName());
    
	/**
	 * Provides alternate open api config with information pulled from configuration
	 * 
	 * @param openAPI
	 * @param params
	 * @param cookies
	 * @param headers
	 * @return 
	 */
    @Override
    public Optional<OpenAPI> filterOpenAPI(OpenAPI openAPI, Map<String,
            List<String>> params, Map<String, String> cookies,
            Map<String, List<String>> headers) {
        
        Info info = openAPI.getInfo();
		if (info == null){
			info = new Info();
		}
        String desc = getSwaggerDescription();
        if (desc != null){
            info.setDescription(desc);
        }
        
        String title = getSwaggerTitle();
        if (title != null){
            info.setTitle(title);
        }
        
        String version = getVersion();
        if (version != null){
            info.setVersion(version);
        }
      
        Server ndexServer = new Server();
		ndexServer.setDescription("NDEx public server");
		ndexServer.setUrl(getServerUrl());
		
		Server customServer = new Server();
		customServer.setDescription("The host server URL can be overriden.\n");
		customServer.setUrl("{Host}");
		ServerVariables serverVariables = new ServerVariables();
		ServerVariable hostVariable = new ServerVariable();
		hostVariable.setDefault(getServerUrl());
		
		serverVariables.addServerVariable("Host", hostVariable);
		customServer.setVariables(serverVariables);
		
		openAPI.setServers(Arrays.asList(ndexServer, customServer));
        
        openAPI.setInfo(info);
        return Optional.of(openAPI);
    }

    protected String getServerUrl(){
		return Configuration.getInstance().getHostURI();
    }
    
    protected String getSwaggerDescription(){
		String currentYear = Year.now().toString();
		return "This [OpenAPI Specification](https://github.com/OAI/OpenAPI-Specification) document defines the **N**etwork **D**ata **Ex**change (**NDEx**) REST API which is used to communicate with NDEx public and private servers.\n"
				+ "\nThis document and all references to the NDEx REST API, source code and ancillary documentation are copyrighted: *© 2013-"
				+ currentYear + ", The Regents of the University of California, The Cytoscape Consortium.  All rights reserved.*  "
				+ "Please abide with the [Terms of Use, Licensing and Sources](https://home.ndexbio.org/disclaimer-license/). "
				+ "Likewise, the [Swagger-UI](https://github.com/swagger-api/swagger-ui) document reader that displays "
				+ "this OpenAPI document is copyrighted by *Smartbear Software*. Its open-source software license is "
				+ "found [here](https://github.com/swagger-api/swagger-ui/blob/master/LICENSE).\n\nGoogle's "
				+ "OAuth2 (OpenID Connect) login is not currently supported.  Basic Authentication and all other "
				+ "API endpoints are supported.";
    }
    
    protected String getSwaggerTitle(){
		return "NDEx " + getVersion() + " REST API";
    }

	/**
	 * Only allow v2 and v3 endpoints through and add a tag V2/V3 - (group)
	 * @param operation
	 * @param api
	 * @param params
	 * @param cookies
	 * @param headers
	 * @return 
	 */
	@Override
	public Optional<Operation> filterOperation(Operation operation, ApiDescription api, Map<String, List<String>> params, Map<String, String> cookies, Map<String, List<String>> headers) {
		String apiVersion = null;
		if (api.getPath().startsWith("/v2")){
			apiVersion = "V2";
		} else if (api.getPath().startsWith("/v3")) {
			apiVersion = "V3";
		}
		
		if (apiVersion != null){
			String[] splitPath = api.getPath().split("/");
			String group = "";
			if (splitPath.length >= 3){
				group = " - " + splitPath[2];
			}
			String theTag = apiVersion + group;
			boolean addTag = true;
			if (operation.getTags() != null){
				for (String tagsItem : operation.getTags()){
					if (theTag.equalsIgnoreCase(tagsItem)){
						addTag = false;
					}
				}
			}
			if (addTag == true){
				operation.addTagsItem(theTag);
			}
			return Optional.of(operation);
		}
		return Optional.empty();
	}	
    
    /**
     * Reads /META-INFO/MANIFEST.MF for version and build information
     * setting _version and _buildNumber to those values if found.
     */
    protected String getVersion(){
        String jarPath = SwaggerFilter.class.getProtectionDomain().getCodeSource().getLocation().getPath();

        JarFile jar = null;
        try {
            jar = new JarFile(jarPath);
            Manifest manifest = jar.getManifest();
            Attributes aa = manifest.getMainAttributes();	
            return aa.getValue("NDEx-Version");
           
        } catch (IOException e) {
            _logger.error("failed to read MANIFEST.MF", e);
        } finally {
            
            if (jar != null){
                try {
                    jar.close();
                } catch(IOException io){
                    _logger.warn("Not a show stopper, but caught IOException closing jar", io);
                }
            }
        }
        return null;
    }
}