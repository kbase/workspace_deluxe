package us.kbase.typedobj.tests;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.report.LogLevel;
import com.github.fge.jsonschema.report.ProcessingMessage;
import com.github.fge.jsonschema.report.ProcessingReport;

import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.TypedObjectValidationReport;
import us.kbase.typedobj.exceptions.RelabelIdReferenceException;
import us.kbase.typedobj.idref.IdReference;
import us.kbase.typedobj.idref.WsIdReference;

/**
 * for testing, you can instantiate this report without running any validation code.
 * this dummy report always says that it was a success, that no subset data is extracted (get an empty object), there
 * are no errors, no IDs detected, and no Ids that can be renamed.  If you try to get the original json instance,
 * you get an emtpy object
 * @author msneddon
 *
 */
public class DummyTypedObjectValidationReport extends
		TypedObjectValidationReport {

	public DummyTypedObjectValidationReport() {
		super(null, null, null);
	}


	@Override
	public AbsoluteTypeDefId getValidationTypeDefId() {
		throw new RuntimeException("cannot get the AbsoluteTypeDefId from a dummy typed object validation report.");
	}
	
	@Override
	public boolean isInstanceValid() {
		return true;
	}
	
	@Override
	public int getErrorCount() {
		return 0;
	}
	
	@Override
	public List <String> getErrorMessagesAsList() {
		return new ArrayList<String>();
	}
	
	
	@Override
	public ProcessingReport getRawProcessingReport() {
		throw new RuntimeException("cannot get the processing report from a dummy typed object validation report.");
	}
	
	/** @deprecated **/
	@Override
	public List <String> getListOfIdReferences() {
		return new ArrayList<String>();
	}
	
	@Override
	public List<WsIdReference> getWsIdReferences() {
		return new ArrayList<WsIdReference>();
	}
	@Override
	public List<IdReference> getAllIdReferences() {
		return new ArrayList<IdReference>();
	}
	@Override
	public List<String> getAllIds() {
		return new ArrayList<String>();
	}
	
	@Override
	public List<IdReference> getAllIdReferencesOfType(String type) {
		return new ArrayList<IdReference>();
	}
	/** @deprecated **/
	@Override
	public void setAbsoluteIdReferences(Map<String,String> absoluteIdRefMapping) {
	}
	
	@Override
	public JsonNode relabelWsIdReferences(Map<String,String> absoluteIdRefMapping) throws RelabelIdReferenceException {
		ObjectMapper mapper = new ObjectMapper();
		return mapper.createObjectNode();
	}

	@Override
	public JsonNode getJsonInstance() {
		ObjectMapper mapper = new ObjectMapper();
		return mapper.createObjectNode();
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
