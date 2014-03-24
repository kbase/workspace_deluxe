package us.kbase.workspace.database;

import java.io.IOException;
import java.util.Map;

import us.kbase.common.utils.CountingOutputStream;

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
		final CountingOutputStream cos = new CountingOutputStream();
		try {
			//writes in UTF8
			MAPPER.writeValue(cos, o);
		} catch (JsonProcessingException jpe) {
			throw new IllegalArgumentException(
					"Unable to serialize metadata", jpe);
		} catch (IOException ioe) {
			throw new RuntimeException("something's broken", ioe);
		} finally {
			try {
				cos.close();
			} catch (IOException ioe) {
				throw new RuntimeException("something's broken", ioe);
			}
		}
		if (cos.getSize() > maxsize) {
				throw new IllegalArgumentException(String.format(
						dataName + " size of %s is > %s bytes", cos.getSize(),
						maxsize));
		}
	}
}
