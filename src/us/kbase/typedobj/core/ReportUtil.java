package us.kbase.typedobj.core;

import java.util.ArrayList;
import java.util.Iterator;

import us.kbase.typedobj.core.validatorconfig.WsIdRefValidationBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonschema.report.ProcessingMessage;
import com.github.fge.jsonschema.report.ProcessingReport;

public class ReportUtil {

	public static final int expectedNumberOfIds = 100;
	
	
	public static final ArrayList<String> getSimpleIdList(ProcessingReport report) {
		Iterator<ProcessingMessage> mssgs = report.iterator();
		ArrayList<String> ids = new ArrayList<String>(expectedNumberOfIds);
		while(mssgs.hasNext()) {
			ProcessingMessage m = mssgs.next();
			if( m.getMessage().compareTo(WsIdRefValidationBuilder.keyword) != 0 ) {
				continue;
			}
			ids.add(m.asJson().get("id").asText());
		}
		return ids;
	}
	
	
	public static final ArrayList<IdForValidation> getIdList(ProcessingReport report) {
		Iterator<ProcessingMessage> mssgs = report.iterator();
		ArrayList<IdForValidation> ids = new ArrayList<IdForValidation>(expectedNumberOfIds);
		while(mssgs.hasNext()) {
			ProcessingMessage m = mssgs.next();
			if( m.getMessage().compareTo(WsIdRefValidationBuilder.keyword) != 0 ) {
				continue;
			}
			String id = m.asJson().get("id").asText();
			JsonNode types = m.asJson().get("type");
			ArrayList<String> typesList = new ArrayList<String>(types.size());
			for(int k=0; k<types.size(); k++) {
				typesList.add(types.get(k).asText());
			}
			IdForValidation idForValidation = new IdForValidation(id,typesList);
			ids.add(idForValidation);
		}
		return ids;
	}
	
	
	public static final String getSearchableSubsetAsJsonString(ProcessingReport report) {
		return getSearchableSubset(report).toString();
	}
	
	public static final JsonNode getSearchableSubset(ProcessingReport report) {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode subset = mapper.createObjectNode();
		Iterator<ProcessingMessage> mssgs = report.iterator();
		while(mssgs.hasNext()) {
			ProcessingMessage m = mssgs.next();
			if( m.getMessage().compareTo("ws-searchable-fields-subset") == 0 ) {
				JsonNode fieldsSubset = m.asJson().get("value");
				Iterator<String> fieldNames = fieldsSubset.fieldNames();
				while(fieldNames.hasNext()) {
					String fieldName = fieldNames.next();
					subset.put(fieldName, fieldsSubset.findValue(fieldName));
				}
			} else if( m.getMessage().compareTo("ws-searchable-keys-subset") == 0 ) {
				JsonNode fieldsSubset = m.asJson().get("keys_of");
				Iterator<String> fieldNames = fieldsSubset.fieldNames();
				while(fieldNames.hasNext()) {
					String fieldName = fieldNames.next();
					subset.put(fieldName, fieldsSubset.findValue(fieldName));
				}
			}
		}
		return subset;
	}
	
	
	
	
	/**
	 * Wrapper class that stores a workspace ID with a list of possible typed objects
	 * that the ID points to.
	 * @author msneddon
	 *
	 */
	public static final class IdForValidation {
		
		public final String id;
		public final ArrayList <String> types;
		
		public IdForValidation(String id, ArrayList<String> types) {
			this.id=id;
			this.types=types;
		}
		
		@Override
		public String toString() {
			return id + ":"+ types;
		}
	}
	
	
}
