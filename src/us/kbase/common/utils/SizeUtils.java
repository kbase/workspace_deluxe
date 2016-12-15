package us.kbase.common.utils;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import us.kbase.common.utils.CountingOutputStream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Utilities for calculating the size of objects as bytes, oriented towards a
 * JSON serialization.
 * @author gaprice@lbl.gov
 *
 */
public class SizeUtils {
	
	//TODO MOVE move to common
	//TODO TEST unit tests
	
	private static final ObjectMapper MAPPER = new ObjectMapper();

	/** Check the size of an Object in bytes when serialized to a JSON map.
	 * @param o the Object to check
	 * @returns the size of the map.
	 * @throws IllegalArgumentException if the object cannot be serialized to
	 * JSON.
	 */
	public static long checkJSONSizeInBytes(final Object o) {
		final CountingOutputStream cos = new CountingOutputStream();
		try {
			//writes in UTF8
			MAPPER.writeValue(cos, o);
		} catch (JsonProcessingException jpe) {
			throw new IllegalArgumentException(
					"Unable to serialize object", jpe);
		} catch (IOException ioe) {
			throw new RuntimeException("something's broken", ioe);
		} finally {
			try {
				cos.close();
			} catch (IOException ioe) {
				throw new RuntimeException("something's broken", ioe);
			}
		}
		return cos.getSize();
	}
	
	/** Check the size of a String in UTF-8 bytes.
	 * @param s the String to check
	 * @return the size of the String in UTF-8.
	 */
	public static long checkSizeInBytes(final String s) {
		final CountingOutputStream cos = new CountingOutputStream();
		try {
			//TODO PERFORMANCE Writer copies the string into a char array, doubling memory. Use better implementation.
			Writer writer = new OutputStreamWriter(cos,
					StandardCharsets.UTF_8);
			try {
				writer.write(s);
				writer.flush();
				writer.close();
			} catch (IOException ioe) {
				// what, there was an IOE accessing memory? In that case, shit's broke
				throw new RuntimeException("something's broken", ioe);
			}
		} finally {
			try {
				cos.close();
			} catch (IOException ioe) {
				throw new RuntimeException("something's broken", ioe);
			}
		}
		return cos.getSize();
	}
	
}
