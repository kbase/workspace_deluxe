package us.kbase.shock.client;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import us.kbase.shock.client.exceptions.ShockAuthorizationException;
import us.kbase.shock.client.exceptions.ShockHttpException;

public class ShockNodeResponse {
	
	private static final int STAT_AUTH = 401;
	
	private ShockNodeResponse(){}
	
	private List<String> error;
	@JsonProperty("data")
	private ShockNode data;
	private int status;
	
	public List<String> getError() {
		return error;
	}
	
	public boolean hasError() {
		return error != null;
	}
	
	@JsonIgnore
	public ShockNode getShockNode() throws ShockHttpException {
		if (error != null) {
			String err = StringUtils.join(error, ", ");
			if (status == STAT_AUTH) {
				throw new ShockAuthorizationException(err);
			} else {
				throw new ShockHttpException(status, err);
			}
		}
		return data;
	}
	
	public int getStatus() {
		return status;
	}

	@Override
	public String toString() {
		return "ShockNodeResponse [error=" + error + ", data=" + data
				+ ", status=" + status + "]";
	}
}
