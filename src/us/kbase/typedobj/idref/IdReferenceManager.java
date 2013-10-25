package us.kbase.typedobj.idref;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.validatorconfig.IdRefValidationBuilder;
import us.kbase.typedobj.exceptions.RelabelIdReferenceException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonschema.report.ProcessingMessage;
import com.github.fge.jsonschema.report.ProcessingReport;


/**
 * Manages a set of ID references parsed from a typed object instance.
 * @author msneddon
 */
public class IdReferenceManager {

	/**
	 * tunable parameter for initializing the list of IDs; we may get minor performance gains by tweaking this
	 */
	private final static int EXPECTED_NUMBER_OF_IDS = 25;
	
	/**
	 * tunable parameter for initializing the list of IDs; we may get minor performance gains by tweaking this
	 */
	private final static int EXPECTED_OBJ_DEPTH = 5;
	
	
	
	private ProcessingReport processingReport;
	private boolean idsHaveBeenExtracted;
	
	public IdReferenceManager(ProcessingReport processingReport) {
		this.processingReport = processingReport;
		this.idsHaveBeenExtracted = false;
	}
	
	
	
	/** list containing all IdReferences found in the processing report */
	private List<IdReference> allIdReferences;
	
	/** list containing all original ids of the IDReferences found in the processing report */
	private List<String> allIds;
	

	/**
	 * A special container for ws references, because we do special things with these ids.
	 * The position in the first list indicates the depth of the ID position.  This is required because when IDs are
	 * renamed, we must rename them in a depth first order (if you care, this is because IDs can be keys in mappings,
	 * which are indistinguishable from JSON objects, so paths to deeper IDs will be incorrect if a key at a higher
	 * level in the path is renamed)
	 */
	private List<List<WsIdReference>> wsIdReferences;
	
	/** another list for convenience and performance **/
	private List <WsIdReference> allWsIdReferences;
	
	
	
	public List<WsIdReference> getAllWsIdReferences() {
		buildIdList();
		return Collections.unmodifiableList(allWsIdReferences);
	}
	
	public List<IdReference> getAllIdReferences() {
		buildIdList();
		return Collections.unmodifiableList(allIdReferences);
	}
	
	public List<IdReference> getAllIdReferencesOfType(String type) {
		buildIdList();
		List<IdReference> idrefs = new ArrayList<IdReference>();
		for(IdReference id:allIdReferences) {
			if(id.getType().equals(type)) {
				idrefs.add(id);
			}
		}
		return Collections.unmodifiableList(idrefs);
	}
	
	public List<String> getAllIds() {
		buildIdList();
		return Collections.unmodifiableList(allIds);
	}
	
	public void setWsReplacementNames(Map<String,String> absoluteIdRefMapping) {
		buildIdList();
		for(WsIdReference wsid: allWsIdReferences) {
			String replacement = absoluteIdRefMapping.get(wsid);
			if(replacement != null) {
				wsid.setReplacementId(replacement);
			}
		}
	}
	
	public void relabelWsIds(JsonNode target) throws RelabelIdReferenceException {
		// traverse them in reverse depth order
		for(int depth=wsIdReferences.size()-1; depth>=0; depth--) {
			List<WsIdReference> wsIdsAtDepth = wsIdReferences.get(depth);
			//doesn't matter the order if we are all at the same depth
			for(WsIdReference wsid : wsIdsAtDepth) {
				//System.out.println("Looking at:"+ref.getIdReference()+" at "+ref.getLocation());
				//if there is nothing to relabel, then we can just quit
				wsid.relabel(target);
			}
		}
	}
	
	/**
	 * given the internal processing report, compute the list of IDs.  this is not done in
	 * the constructor for performance- we only want to waste time building the list if we
	 * need to.
	 */
	protected void buildIdList() {
		if(idsHaveBeenExtracted) return;
		
		// initialize the lists
		allIdReferences  = new ArrayList<IdReference> (EXPECTED_NUMBER_OF_IDS);
		allIds           = new ArrayList<String> (EXPECTED_NUMBER_OF_IDS);
		wsIdReferences   = new ArrayList<List<WsIdReference>>(EXPECTED_OBJ_DEPTH);
		allWsIdReferences= new ArrayList<WsIdReference>(EXPECTED_NUMBER_OF_IDS);
		
		// process each message that lists an ID
		Iterator<ProcessingMessage> mssgs = processingReport.iterator();
		while(mssgs.hasNext()) {
			ProcessingMessage m = mssgs.next();
			if( m.getMessage().compareTo(IdRefValidationBuilder.keyword) != 0 ) {
				continue;
			}
			
			//construct the IdReference object (note that we don't do any error checking here! we assume things are ok)
			JsonNode mssgContent = m.asJson();
			String id           = mssgContent.get("id").asText();           // the id parsed from the json node instance
			JsonNode idInfo     = mssgContent.get("id-spec-info");          // the object containing info on the type of id
			String type         = idInfo.get("id-type").asText();           // the id-type must be defined
			ArrayNode location  = (ArrayNode)mssgContent.get("location");   // the pointer so that we can navigate to this exact id later
			boolean isFieldName = mssgContent.get("is-field-name").asBoolean();
			
			// add the ID to the list
			allIds.add(id);
			
			// construct the IdReference object
			if(type.equals(WsIdReference.typestring)) {
				WsIdReference wsidref = new WsIdReference(id, location, (ObjectNode)idInfo, isFieldName);
				// make sure our storage container can go deep enough
				int depth = wsidref.getDepth();
				while(wsIdReferences.size()<depth+1) {
					wsIdReferences.add(new ArrayList<WsIdReference>(EXPECTED_NUMBER_OF_IDS));
				}
				wsIdReferences.get(depth).add(wsidref);
				allWsIdReferences.add(wsidref);
				allIdReferences.add(wsidref);
			}
			
			else {
				// catch all other idref types that we don't explicitly handle
				IdReference idref = new IdReference(type, id, location, (ObjectNode)idInfo, isFieldName);
				allIdReferences.add(idref);
			}
		}
		
		//done and done.
		idsHaveBeenExtracted=true;
	}
	
	
	
	
	
}
