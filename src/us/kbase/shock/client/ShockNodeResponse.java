package us.kbase.shock.client;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import us.kbase.shock.client.exceptions.ShockHttpException;

class ShockNodeResponse extends ShockResponse {
	
	private ShockNodeResponse(){}
	
	@JsonProperty("data")
	private ShockNode data;
	
	@JsonIgnore
	ShockNode getShockData() throws ShockHttpException {
		checkErrors();
		return data;
	}
}
