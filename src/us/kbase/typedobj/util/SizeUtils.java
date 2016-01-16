package us.kbase.typedobj.util;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import us.kbase.common.utils.CountingOutputStream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Utilities for calculating the size of objects as bytes, oriented towards a
 * JSON serialization.
 * @author gaprice@lbl.gov
 *
 */
public class SizeUtils {
	
	//TODO move to common
	//TODO unit tests
	
	private static final ObjectMapper MAPPER = new ObjectMapper();

	/** Check the size of a Map in bytes when serialized to a JSON map.
	 * @param o the Map to check
	 * @param dataName the name of the data to report in exceptions.
	 * @param maxsize the maximum size of the Map in bytes.
	 * @throws IllegalArgumentException if the Map cannot be serialized or
	 * exceeds maxsize.
	 */
	public static void checkJSONSizeInBytes(
			final Map<String, ? extends Object> o,
			final String dataName,
			final int maxsize) {
		final CountingOutputStream cos = new CountingOutputStream();
		try {
			//writes in UTF8
			MAPPER.writeValue(cos, o);
		} catch (JsonProcessingException jpe) {
			throw new IllegalArgumentException(
					"Unable to serialize " + dataName, jpe);
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
	
	/** Check the size of a String in UTF-8 bytes.
	 * @param s the String to check
	 * @param dataName the name of the data to report in exceptions.
	 * @param maxsize the maximum size of the String in UTF-8 in bytes.
	 * @throws IllegalArgumentException if the String exceeds maxsize.
	 */
	public static void checkSizeInBytes(
			final String s,
			final String dataName,
			final int maxsize) {
		CountingOutputStream cos = new CountingOutputStream();
		try {
			Writer writer = new OutputStreamWriter(cos, StandardCharsets.UTF_8);
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
		if (cos.getSize() > maxsize) {
			throw new IllegalArgumentException(String.format(
					dataName + " size of %s is > %s bytes", cos.getSize(),
					maxsize));
		}
	}
}
