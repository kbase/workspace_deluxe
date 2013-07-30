package us.kbase;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.Version;
import org.codehaus.jackson.map.BeanDescription;
import org.codehaus.jackson.map.BeanProperty;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.DeserializationContext;
import org.codehaus.jackson.map.DeserializerProvider;
import org.codehaus.jackson.map.JsonDeserializer;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.map.SerializerProvider;
import org.codehaus.jackson.map.module.SimpleDeserializers;
import org.codehaus.jackson.map.module.SimpleModule;
import org.codehaus.jackson.map.module.SimpleSerializers;
import org.codehaus.jackson.type.JavaType;

public class JacksonTupleModule extends SimpleModule {
	public JacksonTupleModule() {
		super(JacksonTupleModule.class.getSimpleName(), new Version(1, 0, 0, null));
		setSerializers(new SimpleSerializers() {
			@Override
			public JsonSerializer<?> findSerializer(SerializationConfig config,
					JavaType type, BeanDescription beanDesc,
					BeanProperty property) {
				Class<?> rawClass = type.getRawClass();
				int tupleSizeIfTuple = getTupleSize(rawClass);
				if (tupleSizeIfTuple > 0) {
					return new TupleSerializer(tupleSizeIfTuple);
				}
				return super.findSerializer(config, type, beanDesc, property);
			}
		});
		setDeserializers(new SimpleDeserializers() {
			@Override
			public JsonDeserializer<?> findBeanDeserializer(JavaType type,
					DeserializationConfig config,
					DeserializerProvider provider, BeanDescription beanDesc,
					BeanProperty property) throws JsonMappingException {
				Class<?> rawClass = type.getRawClass();
				if (isTuple(rawClass)) {
					int paramCount = type.containedTypeCount();
					List<JavaType> params = new ArrayList<JavaType>();
					for (int i = 0; i < paramCount; i++)
						params.add(type.containedType(i));
					return new TupleDeserializer(rawClass, params);
				}
				return super.findBeanDeserializer(type, config, provider, beanDesc, property);
			}
		});
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
					jgen.writeObject(res);
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
					Object val = p.getCodec().readValue(p, types.get(i));
					m.invoke(res, val);
				}
				p.nextToken();
				return res;
			} catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		}
	}
}
