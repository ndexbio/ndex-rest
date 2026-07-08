package org.ndexbio.rest.services.v3;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;

/**
 * Reusable pagination query parameters (a zero-based start offset and a page size)
 * for v3 list/search endpoints.
 *
 * <p>Bind it with {@code @BeanParam} on a resource method so every endpoint exposes,
 * documents, and defaults pagination identically:
 * <pre>{@code
 *   public Response listThings(@BeanParam PagingParameters paging) { ... }
 * }</pre>
 * The fields carry the JAX-RS {@code @QueryParam}/{@code @DefaultValue} bindings and
 * the OpenAPI {@code @Parameter} documentation, so the generated Swagger shows
 * consistent {@code start}/{@code size} query parameters wherever this class is used.
 */
public class PagingParameters {

	@QueryParam("start")
	@DefaultValue("0")
	@Parameter(description = "Zero-based index of the first result to return (pagination offset).",
			schema = @Schema(type = "integer", defaultValue = "0", minimum = "0"))
	private int start = 0;

	@QueryParam("size")
	@DefaultValue("100")
	@Parameter(description = "Maximum number of results to return per page.",
			schema = @Schema(type = "integer", defaultValue = "100", minimum = "0"))
	private int size = 100;

	public PagingParameters() {
	}

	public PagingParameters(int start, int size) {
		this.start = start;
		this.size = size;
	}

	public int getStart() {
		return start;
	}

	public void setStart(int start) {
		this.start = start;
	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}
}
