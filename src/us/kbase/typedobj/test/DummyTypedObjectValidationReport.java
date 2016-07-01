package us.kbase.typedobj.test;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.common.service.JacksonTupleModule;
import us.kbase.common.service.UObject;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.JsonTokenValidationSchema;
import us.kbase.typedobj.core.TypedObjectValidationReport;
import us.kbase.typedobj.core.Writable;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory;

/**
 * for testing, you can instantiate this report without running any validation code.
 * this dummy report always says that it was a success, that no subset data is extracted (get an empty object), there
 * are no errors, no IDs detected, and no Ids that can be renamed.
 * @author msneddon
 *
 */
public class DummyTypedObjectValidationReport extends
		TypedObjectValidationReport {
	
	final UObject data;
	
	public DummyTypedObjectValidationReport(final AbsoluteTypeDefId type, 
			final UObject data) throws Exception {
		
		super(data, type, Collections.<String>emptyList(), null,
				JsonTokenValidationSchema.parseJsonSchema(
						"{\"id\": \"foo\", \"type\": \"string\", \"original-type\": \"foo\"}"),
				new IdReferenceHandlerSetFactory(6)
					.createHandlers(String.class).processIDs());
		this.data = data;
	}
	
	@Override
	public boolean isInstanceValid() {
		return true;
	}
	
	@Override
	public List <String> getErrorMessages() {
		return new ArrayList<String>();
	}
	
	@Override
	public JsonNode getInstanceAfterIdRefRelabelingForTests() throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		return mapper.createObjectNode();
	}

	/*@Override
	public JsonNode getJsonInstance() {
		ObjectMapper mapper = new ObjectMapper();
		return mapper.createObjectNode();
	}*/
	
	public JsonNode extractSearchableWsSubset() {
		ObjectMapper mapper = new ObjectMapper();
		return mapper.createObjectNode();
	}
	
	@Override
	public String toString() { 
		return "DummyTypedObjectValidationReport";
	}
	
	public Writable createJsonWritable() {
		return new Writable() {
			@Override
			public void write(OutputStream os) throws IOException {
				ObjectMapper mapper = new ObjectMapper().registerModule(new JacksonTupleModule());
				mapper.writeValue(os, data);
			}
			
		};
	}	
	
	
}
