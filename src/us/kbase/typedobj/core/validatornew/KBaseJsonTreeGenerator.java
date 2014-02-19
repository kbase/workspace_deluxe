package us.kbase.typedobj.core.validatornew;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * This class is used only in the case when we need to transform objects
 * into JSON tree. In this case serializers write their tokens directly
 * into this generator. Many methods are not implemented here because
 * they are not used by serializers so we don't need to worry about it. 
 * @author rsutormin
 */
public class KBaseJsonTreeGenerator extends JsonGenerator {
	private final ObjectCodec codec;
	private final JsonNodeFactory nodeFactory;
	private final boolean sortKeys;
	private JsonNode root = null;
	private List<NodeWrapper> branchStack = new ArrayList<NodeWrapper>();
	private String currentFieldName = null;
	
	public KBaseJsonTreeGenerator(ObjectMapper codec) {
		this.codec = codec;
		this.nodeFactory = codec.getNodeFactory();
		this.sortKeys = codec.getSerializationConfig().isEnabled(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
	}

	public JsonNode getTree() {
		checkFlushObject();
		return root;
	}
	
	@Override
	public void close() throws IOException {
		checkFlushObject();
	}
	
	@Override
	public void copyCurrentEvent(JsonParser jp) throws IOException,
			JsonProcessingException {
		throw new IllegalStateException("Method is not supported");
	}
	
	@Override
	public void copyCurrentStructure(JsonParser jp) throws IOException,
			JsonProcessingException {
		throw new IllegalStateException("Method is not supported");
	}
	
	@Override
	public JsonGenerator disable(Feature f) {
		throw new IllegalStateException("Method is not supported");
	}
	
	@Override
	public JsonGenerator enable(Feature f) {
		throw new IllegalStateException("Method is not supported");
	}
	
	@Override
	public boolean isEnabled(Feature f) {
		throw new IllegalStateException("Method is not supported");
	}
	
	@Override
	public void writeRawUTF8String(byte[] text, int offset, int length)
			throws IOException, JsonGenerationException {
		throw new IllegalStateException("Method is not supported");
	}
	
	@Override
	public void writeUTF8String(byte[] text, int offset, int length)
			throws IOException, JsonGenerationException {
		throw new IllegalStateException("Method is not supported");
	}
	
	@Override
	public void writeStartArray() throws IOException, JsonGenerationException {
		push(nodeFactory.arrayNode());
	}
	
	private void push(JsonNode node) throws JsonGenerationException {
		if (branchStack.size() == 0) {
			root = node;
		} else {
			NodeWrapper parent = branchStack.get(branchStack.size() - 1);
			if (parent.node instanceof ArrayNode) {
				((ArrayNode)parent.node).add(node);
			} else {
				if (currentFieldName == null)
					throw new JsonGenerationException("No field name before pushing object node child");
				if (parent.tempProps == null) {
					parent.tempProps = sortKeys ? new TreeMap<String, JsonNode>() : 
						new LinkedHashMap<String, JsonNode>();
				}
				parent.tempProps.put(currentFieldName, node);
				currentFieldName = null;
			}
		}
		if (node instanceof ArrayNode || node instanceof ObjectNode) {
			NodeWrapper wrapper = new NodeWrapper();
			wrapper.node = node;
			branchStack.add(wrapper);
		}
	}
	
	@Override
	public void writeStartObject() throws IOException, JsonGenerationException {
		push(nodeFactory.objectNode());
	}
	
	@Override
	public void writeNull() throws IOException, JsonGenerationException {
		push(nodeFactory.nullNode());
	}
	
	@Override
	public void writeRawValue(String text) throws IOException,
			JsonGenerationException {
		throw new IllegalStateException("Method is not supported");
	}
	
	@Override
	public void writeRawValue(String text, int offset, int len)
			throws IOException, JsonGenerationException {
		throw new IllegalStateException("Method is not supported");
	}
	
	@Override
	public void writeRawValue(char[] text, int offset, int len)
			throws IOException, JsonGenerationException {
		throw new IllegalStateException("Method is not supported");
	}
	
	@Override
	public ObjectCodec getCodec() {
		return codec;
	}
	
	@Override
	public JsonStreamContext getOutputContext() {
		throw new IllegalStateException("Method is not supported");
	}
	
	@Override
	public boolean isClosed() {
		throw new IllegalStateException("Method is not supported");
	}
	
	@Override
	public JsonGenerator setCodec(ObjectCodec oc) {
		throw new IllegalStateException("Method is not supported");
	}
	
	@Override
	public Version version() {
		throw new IllegalStateException("Method is not supported");
	}
	
	@Override
	public void writeBinary(Base64Variant b64variant, byte[] data, int offset,
			int len) throws IOException, JsonGenerationException {
		throw new IllegalStateException("Method is not supported");
	}
	
	@Override
	public int writeBinary(Base64Variant b64variant, InputStream data,
			int dataLength) throws IOException, JsonGenerationException {
		throw new IllegalStateException("Method is not supported");
	}
	
	@Override
	public void writeBoolean(boolean v) throws IOException,
			JsonGenerationException {
		push(nodeFactory.booleanNode(v));
	}
	
	@Override
	public void writeEndArray() throws IOException, JsonGenerationException {
		branchStack.remove(branchStack.size() - 1);
	}
	
	private void checkFlushObject() {
		if (branchStack.size() == 0)
			return;
		NodeWrapper parent = branchStack.get(branchStack.size() - 1);
		if (parent.tempProps == null)
			return;
		ObjectNode obj = (ObjectNode)parent.node;
		for (Map.Entry<String, JsonNode> entry : parent.tempProps.entrySet())
			obj.put(entry.getKey(), entry.getValue());
		parent.tempProps = null;
	}
	
	@Override
	public void writeEndObject() throws IOException, JsonGenerationException {
		checkFlushObject();
		branchStack.remove(branchStack.size() - 1);
	}
	
	@Override
	public void writeFieldName(SerializableString name) throws IOException,
			JsonGenerationException {
		currentFieldName = name.getValue();
	}
	
	@Override
	public void writeFieldName(String name) throws IOException,
			JsonGenerationException {
		currentFieldName = name;
	}
	
	@Override
	public void writeNumber(BigDecimal v) throws IOException,
			JsonGenerationException {
		push(nodeFactory.numberNode(v));
	}
	
	@Override
	public void writeNumber(BigInteger v) throws IOException,
			JsonGenerationException {
		push(nodeFactory.numberNode(v));
	}
	
	@Override
	public void writeNumber(double v) throws IOException,
			JsonGenerationException {
		push(nodeFactory.numberNode(v));
	}
	
	@Override
	public void writeNumber(float v) throws IOException,
			JsonGenerationException {
		push(nodeFactory.numberNode(v));
	}
	
	@Override
	public void writeNumber(int v) throws IOException, JsonGenerationException {
		push(nodeFactory.numberNode(v));
	}
	
	@Override
	public void writeNumber(long v) throws IOException, JsonGenerationException {
		push(nodeFactory.numberNode(v));
	}
	
	@Override
	public void writeNumber(String encodedValue) throws IOException,
			JsonGenerationException, UnsupportedOperationException {
		throw new IllegalStateException("Method is not supported");
	}
	
	@Override
	public void writeObject(Object pojo) throws IOException,
			JsonProcessingException {
		throw new IllegalStateException("Method is not supported");
	}
	
	@Override
	public void writeRaw(char c) throws IOException, JsonGenerationException {
		throw new IllegalStateException("Method is not supported");
	}
	
	@Override
	public void writeRaw(char[] text, int offset, int len) throws IOException,
			JsonGenerationException {
		throw new IllegalStateException("Method is not supported");
	}
	
	@Override
	public void writeRaw(String text) throws IOException,
			JsonGenerationException {
		throw new IllegalStateException("Method is not supported");
	}
	
	@Override
	public void writeRaw(String text, int offset, int len) throws IOException,
			JsonGenerationException {
		throw new IllegalStateException("Method is not supported");
	}
	
	@Override
	public void writeString(char[] text, int offset, int len)
			throws IOException, JsonGenerationException {
		throw new IllegalStateException("Method is not supported");
	}
	
	@Override
	public void writeString(SerializableString text) throws IOException,
			JsonGenerationException {
		push(new TextNode(text.getValue()));
	}
	
	@Override
	public void writeString(String text) throws IOException,
			JsonGenerationException {
		push(new TextNode(text));
	}
	
	@Override
	public void writeTree(TreeNode rootNode) throws IOException,
			JsonProcessingException {
		throw new IllegalStateException("Method is not supported");
	}
	
	@Override
	public JsonGenerator useDefaultPrettyPrinter() {
		throw new IllegalStateException("Method is not supported");
	}
	
	@Override
	public void flush() throws IOException {
		checkFlushObject();
	}
	
	static class NodeWrapper {
		JsonNode node;
		Map<String, JsonNode> tempProps;
	}
}
