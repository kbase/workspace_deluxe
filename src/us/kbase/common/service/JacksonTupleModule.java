package us.kbase.common.service;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleDeserializers;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import com.fasterxml.jackson.databind.node.TreeTraversingParser;
import com.fasterxml.jackson.databind.util.TokenBuffer;

@SuppressWarnings("serial")
public class JacksonTupleModule extends SimpleModule {
	public JacksonTupleModule() {
		super(JacksonTupleModule.class.getSimpleName(), new Version(1, 0, 0, null, null, null));
		setSerializers(new SimpleSerializers() {
			@Override
			public JsonSerializer<?> findSerializer(SerializationConfig config,
					JavaType type, BeanDescription beanDesc) {
				Class<?> rawClass = type.getRawClass();
				//if ()
				int tupleSizeIfTuple = getTupleSize(rawClass);
				if (tupleSizeIfTuple > 0) {
					return new TupleSerializer(tupleSizeIfTuple);
				}
				return super.findSerializer(config, type, beanDesc);
			}
		});
		setDeserializers(new SimpleDeserializers() {
			@Override
			public JsonDeserializer<?> findBeanDeserializer(JavaType type,
					DeserializationConfig config, BeanDescription beanDesc) throws JsonMappingException {
				Class<?> rawClass = type.getRawClass();
				if (isTuple(rawClass)) {
					int paramCount = type.containedTypeCount();
					List<JavaType> params = new ArrayList<JavaType>();
					for (int i = 0; i < paramCount; i++)
						params.add(type.containedType(i));
					return new TupleDeserializer(rawClass, params);
				}
				return super.findBeanDeserializer(type, config, beanDesc);
			}
		});
		addSerializer(UObject.class, new UObjectSerializer());
		addDeserializer(UObject.class, new UObjectDeserializer());
	}
	
	private boolean isTuple(Class<?> rawClass) {
		return getTupleSize(rawClass) > 0;
	}
	
	private int getTupleSize(Class<?> rawClass) {
		String name = rawClass.getSimpleName();
		if (name.startsWith("Tuple")) {
			try {
				return Integer.parseInt(name.substring(5));
			} catch (NumberFormatException ignore) {}
		}
		return 0;
	}

	public static class TupleSerializer extends JsonSerializer<Object> {
		private int paramCount;
		
		public TupleSerializer(int paramCount) {
			this.paramCount = paramCount;
		}
		
		public void serialize(Object value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonProcessingException {
			try {
				jgen.writeStartArray();
				for (int i = 0; i < paramCount; i++) {
					Method m = value.getClass().getMethod("getE" + (i + 1));
					Object res = m.invoke(value);
					jgen.getCodec().writeValue(jgen, res);
				}
				jgen.writeEndArray();
			} catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		}
	}	
	
	public static class TupleDeserializer extends JsonDeserializer<Object> {
		private Class<?> retClass;
		private List<JavaType> types = new ArrayList<JavaType>();
		
		public TupleDeserializer(Class<?> retClass, List<JavaType> types) {
			this.retClass = retClass;
			this.types.addAll(types);
		}

		public Object deserialize(JsonParser p, DeserializationContext ctx) throws IOException, JsonProcessingException {
			try {
				Object res = retClass.newInstance();
				if (!p.isExpectedStartArrayToken()) {
					System.out.println("Bad parse in TupleDeserializer: " + p.getCurrentToken());
					return null;
				}
				p.nextToken();
				for (int i = 0; i < types.size(); i++) {
					Method m = res.getClass().getMethod("setE" + (i + 1), Object.class);
					Object val;
					if (p.getCurrentToken() == JsonToken.VALUE_EMBEDDED_OBJECT) {
						Object tempObj = p.getEmbeddedObject();
						JsonNode tempNode = valueToTree(p.getCodec(), tempObj);
						val = p.getCodec().readValue(new JsonTreeTraversingParser(tempNode, p.getCodec()), types.get(i));
						p.nextToken();
					} else {
						val = p.getCodec().readValue(p, types.get(i));
					}
					m.invoke(res, val);
				}
				p.nextToken();
				return res;
			} catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		}

		public static JsonNode valueToTree(ObjectCodec oc, Object fromValue) throws Exception {
			if (fromValue == null) return null;
			TokenBuffer buf = new TokenBuffer(oc);
			oc.writeValue(buf, fromValue);
			JsonParser jp = buf.asParser();
			JsonNode result = oc.readTree(jp);
			jp.close();
			return result;
		} 
	}

	public static class UObjectSerializer extends JsonSerializer<UObject> {		
		
		public void serialize(UObject value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonProcessingException {
			try {
				UObject obj = (UObject)value;
				jgen.getCodec().writeValue(jgen, obj.getUserObject());
			} catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		}
	}	
	
	public static class UObjectDeserializer extends JsonDeserializer<UObject> {

		public UObject deserialize(JsonParser p, DeserializationContext ctx) throws IOException, JsonProcessingException {
			try {
				return new UObject(p.readValueAsTree());
			} catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		}
	}
}
