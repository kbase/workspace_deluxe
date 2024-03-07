package us.kbase.typedobj.core;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.Version;

public class NullJsonGenerator extends JsonGenerator {

	public NullJsonGenerator() {
	}

	@Override
	public JsonGenerator setCodec(ObjectCodec oc) {
		return null;
	}

	@Override
	public ObjectCodec getCodec() {
		return null;
	}

	@Override
	public Version version() {
		return null;
	}

	@Override
	public JsonGenerator enable(Feature f) {
		return null;
	}

	@Override
	public JsonGenerator disable(Feature f) {
		return null;
	}

	@Override
	public boolean isEnabled(Feature f) {
		return false;
	}

	@Override
	public JsonGenerator useDefaultPrettyPrinter() {
		return null;
	}

	@Override
	public void writeStartArray() throws IOException, JsonGenerationException {
	}

	@Override
	public void writeEndArray() throws IOException, JsonGenerationException {
	}

	@Override
	public void writeStartObject() throws IOException, JsonGenerationException {
	}

	@Override
	public void writeEndObject() throws IOException, JsonGenerationException {
	}

	@Override
	public void writeFieldName(String name) throws IOException,
			JsonGenerationException {
	}

	@Override
	public void writeFieldName(SerializableString name) throws IOException,
			JsonGenerationException {
	}

	@Override
	public void writeString(String text) throws IOException,
			JsonGenerationException {
	}

	@Override
	public void writeString(char[] text, int offset, int len)
			throws IOException, JsonGenerationException {
	}

	@Override
	public void writeString(SerializableString text) throws IOException,
			JsonGenerationException {
	}

	@Override
	public void writeRawUTF8String(byte[] text, int offset, int length)
			throws IOException, JsonGenerationException {
	}

	@Override
	public void writeUTF8String(byte[] text, int offset, int length)
			throws IOException, JsonGenerationException {
	}

	@Override
	public void writeRaw(String text) throws IOException,
			JsonGenerationException {
	}

	@Override
	public void writeRaw(String text, int offset, int len) throws IOException,
			JsonGenerationException {
	}

	@Override
	public void writeRaw(char[] text, int offset, int len) throws IOException,
			JsonGenerationException {
	}

	@Override
	public void writeRaw(char c) throws IOException, JsonGenerationException {
	}

	@Override
	public void writeRawValue(String text) throws IOException,
			JsonGenerationException {
	}

	@Override
	public void writeRawValue(String text, int offset, int len)
			throws IOException, JsonGenerationException {
	}

	@Override
	public void writeRawValue(char[] text, int offset, int len)
			throws IOException, JsonGenerationException {
	}

	@Override
	public void writeBinary(Base64Variant b64variant, byte[] data, int offset,
			int len) throws IOException, JsonGenerationException {
	}

	@Override
	public int writeBinary(Base64Variant b64variant, InputStream data,
			int dataLength) throws IOException, JsonGenerationException {
		return 0;
	}

	@Override
	public void writeNumber(int v) throws IOException, JsonGenerationException {

	}

	@Override
	public void writeNumber(long v) throws IOException, JsonGenerationException {

	}

	@Override
	public void writeNumber(BigInteger v) throws IOException,
			JsonGenerationException {
	}

	@Override
	public void writeNumber(double d) throws IOException,
			JsonGenerationException {
	}

	@Override
	public void writeNumber(float f) throws IOException,
			JsonGenerationException {
	}

	@Override
	public void writeNumber(BigDecimal dec) throws IOException,
			JsonGenerationException {
	}

	@Override
	public void writeNumber(String encodedValue) throws IOException,
			JsonGenerationException, UnsupportedOperationException {
	}

	@Override
	public void writeBoolean(boolean state) throws IOException,
			JsonGenerationException {
	}

	@Override
	public void writeNull() throws IOException, JsonGenerationException {
	}

	@Override
	public void writeObject(Object pojo) throws IOException,
			JsonProcessingException {
	}

	@Override
	public void writeTree(TreeNode rootNode) throws IOException,
			JsonProcessingException {
	}

	@Override
	public void copyCurrentEvent(JsonParser jp) throws IOException,
			JsonProcessingException {
	}

	@Override
	public void copyCurrentStructure(JsonParser jp) throws IOException,
			JsonProcessingException {
	}

	@Override
	public JsonStreamContext getOutputContext() {
		return null;
	}

	@Override
	public void flush() throws IOException {
	}

	@Override
	public boolean isClosed() {
		return false;
	}

	@Override
	public void close() throws IOException {
	}

	@Override
	public int getFeatureMask() {
		return 0;
	}

	@Override
	@Deprecated
	public JsonGenerator setFeatureMask(int values) {
		return null;
	}
}
