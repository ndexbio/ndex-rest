window.onload = function() {
  //<editor-fold desc="Changeable Configuration Block">
  function HideTopbarPlugin() {
	// this plugin overrides the Topbar component to return nothing
	return {
	  components: {
		Topbar: function() { return null }
	  }
	}
  }
  // the following lines will be replaced by docker/configurator, when it runs in a docker-container
  window.ui = SwaggerUIBundle({
    //url: window.location.protocol + "//" + window.location.hostname + "/rest/openapi.json",
	url: "../openapi.json",
    dom_id: '#swagger-ui',
    deepLinking: true,
    presets: [
      SwaggerUIBundle.presets.apis,
      SwaggerUIStandalonePreset
    ],
    plugins: [
      SwaggerUIBundle.plugins.DownloadUrl,
	  HideTopbarPlugin
    ],
    layout: "StandaloneLayout"
  });

  //</editor-fold>
};
