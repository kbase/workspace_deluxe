package us.kbase.typedobj.core;

import us.kbase.typedobj.idref.WsIdReference;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Json token validation callback.
 * @author rsutormin
 */
public interface JsonTokenValidationListener {
	/**
	 * Method is for adding new error message.
	 * @param message error message
	 * @throws JsonTokenValidationException
	 */
	public void addError(String message) throws JsonTokenValidationException;
	
	/**
	 * Method is for adding id-reference into flat list which will be used to 
	 * extract resolved values from workspace db.
	 * @param ref description of id-reference
	 */
	public void addIdRefMessage(WsIdReference ref);
	
	/**
	 * Method is for registering searchable ws-subset object.
	 * @param searchData
	 */
	public void addSearchableWsSubsetMessage(JsonNode searchData);
}
