package us.kbase.typedobj.core.validatornew;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;

public interface TokenSequenceProvider {
	public JsonToken nextToken() throws IOException, JsonParseException;
	public String getText() throws IOException, JsonParseException;
	public long getLongValue() throws IOException, JsonParseException;
	public double getDoubleValue() throws IOException, JsonParseException;
}
