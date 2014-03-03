package us.kbase.common.service;

import java.io.IOException;
import java.math.BigInteger;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TreeTraversingParser;

/**
 * Class helps to check long and double values and to simplify subtree extraction 
 * during reading json tree object as stream of tokens.
 * @author rsutormin
 */
public class JsonTreeTraversingParser extends TreeTraversingParser {
	public JsonTreeTraversingParser(JsonNode tree, ObjectCodec oc) {
		super(tree, oc);
	}
	
	/**
	 * Method helps to avoid copying of subtree during deserialization it into 
	 * UObject.
	 */
    @SuppressWarnings("unchecked")
	public <T extends TreeNode> T readValueAsTree() throws IOException, JsonProcessingException {
		JsonNode curRoot = currentNode();
		if (curRoot != null) {
			skipChildren();
			return (T)curRoot;
		}
    	return super.readValueAsTree();
    }
    
    /**
     * Checking of bounds of long values in json data.
     */
	@Override
	public long getLongValue() throws IOException, JsonParseException {
		JsonNode node = currentNumericNode();
		if (node.isInt() || node.isLong()) {
			return node.longValue();
		} else if (node.isBigInteger()) {
			BigInteger numberBigInt = node.bigIntegerValue();
			BigInteger BI_MIN_LONG = BigInteger.valueOf(Long.MIN_VALUE);
			BigInteger BI_MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
			if (BI_MIN_LONG.compareTo(numberBigInt) > 0 
					|| BI_MAX_LONG.compareTo(numberBigInt) < 0) {
				_reportError("Numeric value ("+getText()+") out of range of long (" +
					Long.MIN_VALUE + " - " + Long.MAX_VALUE + ")");
			}				
		} else {
			_reportError("Can not parse value [" + getText() + "] into long for node type: " + 
					node.getClass().getSimpleName());
		}
		return node.longValue();
	}
	
    /*
     * Preventing to have Inf values in json data (probably it's not good idea).
     *
	public double getDoubleValue() throws IOException, JsonParseException {
		double ret = super.getDoubleValue();
		if (Double.isInfinite(ret)) {
			JsonNode node = currentNumericNode();
			_reportError("Can not parse value [" + getText() + "] into double for node type: " + 
					node.getClass().getSimpleName());
		}
		return ret;
	}*/
}
