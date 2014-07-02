package us.kbase.typedobj.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.common.service.UObject;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.TypedObjectValidationReport;
import us.kbase.typedobj.idref.IdReferenceHandlers;
import us.kbase.typedobj.idref.IdReferenceHandlersFactory;

/**
 * for testing, you can instantiate this report without running any validation code.
 * this dummy report always says that it was a success, that no subset data is extracted (get an empty object), there
 * are no errors, no IDs detected, and no Ids that can be renamed.
 * @author msneddon
 *
 */
public class DummyTypedObjectValidationReport extends
		TypedObjectValidationReport {
	
	public DummyTypedObjectValidationReport(final AbsoluteTypeDefId type, 
			final UObject data) {
		
		super(data, type, Collections.<String>emptyList(), null, null, null,
				new IdReferenceHandlersFactory(6).createHandlers(String.class).lock());
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
	
	
	
}
