package us.kbase.workspace.database;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Util {
	
	private static final ObjectMapper MAPPER = new ObjectMapper();

	public static void xorNameId(final String name, final Long id, 
			final String type) {
		if (!(name == null ^ id == null)) {
			throw new IllegalArgumentException(String.format(
					"Must provide one and only one of %s name (was: %s) or id (was: %s)",
					type, name, id));
		}
	}
	
	public static void checkSize(final Map<String, ? extends Object> o,
			final String dataName, final int maxsize) {
		if (o != null) {
			final String jsonmeta;
			try {
				jsonmeta = MAPPER.writeValueAsString(o);
			} catch (JsonProcessingException jpe) {
				throw new IllegalArgumentException(
						"Unable to serialize metadata", jpe);
			}
			if (jsonmeta.length() > maxsize) {
				throw new IllegalArgumentException(String.format(
						dataName + " is > %s bytes", maxsize));
			}
		}
	}

}
