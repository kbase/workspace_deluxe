package us.kbase.typedobj.core;

import us.kbase.typedobj.idref.IdReference;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceHandlerException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.TooManyIdsException;

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
	 * Method is for adding id-reference into a reference handler.
	 * @param ref description of id-reference
	 * @param loc the location of the id reference
	 * @throws IdReferenceHandlerException if an ID could not be handled
	 * appropriately due to a syntax error or other issue. 
	 * @throws TooManyIdsException if the object undergoing validation
	 * contains too many IDs.
	 * @throws JsonTokenValidationException if a validation exception occurs
	 */
	public void addStringIdRefMessage(
			IdReference<String> ref,
			JsonDocumentLocation loc)
			throws TooManyIdsException, JsonTokenValidationException;
	
//	/**
//	 * Method is for adding id-reference into a reference handler.
//	 * @param ref description of id-reference
//	 * @throws IdReferenceHandlerException if an ID could not be handled
//	 * appropriately due to a syntax error or other issue. 
//	 * @throws TooManyIdsException if the object undergoing validation
//	 * contains too many IDs.
//	 */
//	public void addLongIdRefMessage(IdReference<Long> ref)
//			throws TooManyIdsException, IdReferenceHandlerException;
	
	/**
	 * Method for registering the selection of metadata extraction.
	 * @param selection
	 */
	public void addMetadataWsMessage(JsonNode selection);
}
