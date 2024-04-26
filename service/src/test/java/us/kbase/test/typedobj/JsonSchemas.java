package us.kbase.test.typedobj;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;

import us.kbase.test.common.MapBuilder;

/** Json schema strings for use in tests.
 * @author gaprice@lbl.gov
 *
 */
public class JsonSchemas {
	
	private static final Map<String, Object> JS = MapBuilder.<String, Object>newHashMap()
			.with("id", "sometype")
			.with("description", "@optional dontusethiskeyunlessyoureallywant")
			.with("type", "object")
			.with("original-type", "kidl-structure")
			.with("properties", ImmutableMap.of("dontusethiskeyunlessyoureallywant",
					ImmutableMap.of(
							"type", "integer",
							"original-type", "kidl-int")))
			.with("additionalProperties", true)
			.build();
	
	/** A json schema that defines a top level structure with one optional integer value key
	 * called 'dontusethiskeyunlessyoureallywant'.
	 */
	public static final String EMPTY_STRUCT;
	static {
		try {
			EMPTY_STRUCT = new ObjectMapper().writeValueAsString(JS);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("whoops: " + e.getMessage(), e);
		}
	}

}
