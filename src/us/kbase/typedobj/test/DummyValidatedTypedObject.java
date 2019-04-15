package us.kbase.typedobj.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.common.service.UObject;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.JsonTokenValidationSchema;
import us.kbase.typedobj.core.ValidatedTypedObject;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactoryBuilder;

/**
 * for testing, you can instantiate this report without running any validation code.
 * this dummy report always says that it was a success, that no subset data is extracted
 * (get an empty object), there are no errors, no IDs detected, and no Ids that can be renamed.
 * 
 * The data must be a map at the top level (see {@link JsonSchemas#EMPTY_STRUCT}).
 * @author msneddon
 *
 */
public class DummyValidatedTypedObject extends ValidatedTypedObject {
	
	public DummyValidatedTypedObject(final AbsoluteTypeDefId type, final UObject data)
			throws Exception {
		
		super(data, type, Collections.<String>emptyList(), null,
				JsonTokenValidationSchema.parseJsonSchema(JsonSchemas.EMPTY_STRUCT),
				IdReferenceHandlerSetFactoryBuilder.getBuilder(6).build().getFactory(null)
						.createHandlers(String.class).processIDs());
	}
	
	@Override
	public boolean isInstanceValid() {
		return true;
	}
	
	@Override
	public List <String> getErrorMessages() {
		return new ArrayList<String>();
	}

	public JsonNode extractSearchableWsSubset() {
		ObjectMapper mapper = new ObjectMapper();
		return mapper.createObjectNode();
	}
	
	@Override
	public String toString() { 
		return "DummyTypedObjectValidationReport";
	}
}
