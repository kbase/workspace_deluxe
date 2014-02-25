package us.kbase.typedobj.core;

import us.kbase.typedobj.idref.WsIdReference;

import com.fasterxml.jackson.databind.JsonNode;

public interface JsonTokenValidationListener {
	public void addError(String message) throws JsonTokenValidationException;
	public void addIdRefMessage(WsIdReference ref);  //, JsonNode idRefSpecificationData, List<String> path, boolean isField);
	public void addSearchableWsSubsetMessage(JsonNode searchData);
}
