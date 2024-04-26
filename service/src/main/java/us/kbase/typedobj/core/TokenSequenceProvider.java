package us.kbase.typedobj.core;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;

/**
 * This interface is subset of methods present in JsonParser. This set of 
 * methods is enough to read all kinds of json data as token sequence.
 * @author rsutormin
 */
public interface TokenSequenceProvider {
	/**
	 * Type of next token in token sequence. Types are start/end of array/map,
	 * map field or string value (use getText() method after that to get name 
	 * of field or actual value of string), integer or floating point number 
	 * (use getNumberValue() method after that to get actual value of number), 
	 * true, false, null.
	 */
	public JsonToken nextToken() throws IOException, JsonParseException;
	
	/**
	 * Name of map field or value of string.
	 */
	public String getText() throws IOException, JsonParseException;

	/**
	 * Integer and floating point numbers.
	 */
	public Number getNumberValue() throws IOException, JsonParseException;
	
	/**
	 * Closing token sequence.
	 */
	public void close() throws IOException;
	
	/** 
	 * Signifies that the task of the token provider is complete. For instance,
	 * the token provider may be required to advance through the JSON until
	 * a particular position is found and then return information about that
	 * position.
	 */
	public boolean isComplete();
}
