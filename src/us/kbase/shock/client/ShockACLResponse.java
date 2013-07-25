package us.kbase.shock.client;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import us.kbase.shock.client.exceptions.ShockHttpException;

class ShockACLResponse extends ShockResponse {
	
	private ShockACLResponse(){}
	
	@JsonProperty("data")
	private ShockACL data;
	
	@JsonIgnore
	ShockACL getShockData() throws ShockHttpException {
		checkErrors();
		return data;
	}
}
