package org.ndexbio.rest.services.v3;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;

import org.ndexbio.model.exceptions.BadRequestException;

/**
 * Reusable pagination query parameters (a zero-based start offset and a page size)
 * for v3 list/search endpoints.
 *
 * <p>Bind it with {@code @BeanParam} on a resource method so every endpoint exposes,
 * documents, and defaults pagination identically:
 * <pre>{@code
 *   public Response listThings(@BeanParam PagingParameters paging) { ... }
 * }</pre>
 * The fields carry the JAX-RS {@code @QueryParam} bindings and the OpenAPI
 * {@code @Parameter} documentation, so the generated Swagger shows consistent
 * {@code start}/{@code size} query parameters wherever this class is used.
 *
 * <p><b>The default page size belongs to the endpoint, not to this class.</b> JAX-RS has no
 * per-injection-site {@code @DefaultValue} override — the annotation lives on the field and applies to
 * every {@code @BeanParam} binding — so {@code size} carries no {@code @DefaultValue} and stays null
 * when the request omits it. {@link #getSize()} then applies {@link #DEFAULT_SIZE}, while an endpoint
 * that wants a different default calls {@link #getSize(int)}; a listing that must stay unbounded for
 * backward compatibility passes {@link #UNBOUNDED_SIZE}. Each endpoint states its own default in its
 * own {@code @Operation} description.
 */
public class PagingParameters {

	/** Page size applied by {@link #getSize()} when the request omits {@code size}. */
	public static final int DEFAULT_SIZE = 100;

	/** A {@code size} meaning "no cap — return every matching row". Any non-positive value means this. */
	public static final int UNBOUNDED_SIZE = -1;

	@QueryParam("start")
	@DefaultValue("0")
	@Parameter(description = "Zero-based index of the first result to return (pagination offset).",
			schema = @Schema(type = "integer", defaultValue = "0", minimum = "0"))
	private int start = 0;

	// No @DefaultValue, and boxed so an omitted `size` is distinguishable from an explicit one: the
	// default is the endpoint's to choose via getSize(int). See the class javadoc.
	@QueryParam("size")
	@Parameter(description = "Maximum number of results to return per page. When the request omits it, "
			+ "the endpoint's own default applies (100 unless its description says otherwise). "
			+ "A non-positive value requests every matching row.",
			schema = @Schema(type = "integer", minimum = "1"))
	private Integer size;

	public PagingParameters() {
	}

	public PagingParameters(int start, int size) {
		this.start = start;
		this.size = Integer.valueOf(size);
	}

	/**
	 * Rejects a page window that cannot be honoured. A non-positive {@code size} is not an error — it
	 * requests every row — so only {@code start} is range-checked.
	 */
	public void validate() throws BadRequestException {
		if (start < 0) {
			throw new BadRequestException("'start' must be >= 0.");
		}
	}

	public int getStart() {
		return start;
	}

	public void setStart(int start) {
		this.start = start;
	}

	/** The requested page size, or {@link #DEFAULT_SIZE} when the request omitted {@code size}. */
	public int getSize() {
		return getSize(DEFAULT_SIZE);
	}

	/**
	 * The requested page size, or {@code defaultWhenAbsent} when the request omitted {@code size}.
	 *
	 * @param defaultWhenAbsent the endpoint's own default; {@link #UNBOUNDED_SIZE} for a listing that
	 *        returns everything unless the caller asks for a page.
	 */
	public int getSize(int defaultWhenAbsent) {
		return size == null ? defaultWhenAbsent : size.intValue();
	}

	public void setSize(int size) {
		this.size = Integer.valueOf(size);
	}
}
