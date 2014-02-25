package us.kbase.typedobj.core;

import java.util.List;


import com.fasterxml.jackson.databind.JsonNode;

public interface JsonTokenValidationListener {
	public void addError(String message) throws JsonTokenValidationException;
	public void addIdRefMessage(String id, JsonNode idRefSpecificationData, List<String> path, boolean isField);
	public void addSearchableWsSubsetMessage(JsonNode searchData);
}
