package us.kbase.shock.client;

import java.io.IOException;
import java.util.Map;

import us.kbase.shock.client.exceptions.ExpiredTokenException;
import us.kbase.shock.client.exceptions.ShockHttpException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties({"relatives", "type", "indexes", "tags", "linkages"})
public class ShockNode extends ShockData {

	private Map<String, Object> attributes;
	private ShockFileInformation file;
	private ShockNodeId id;
	private ShockVersionStamp version;
	private BasicShockClient client;
	
	private ShockNode(){}
	
	void addClient(BasicShockClient client) {
		this.client = client;
	}
	
	public Map<String, Object> getAttributes() {
		return attributes;
	}
	
	public byte[] getFile() throws ShockHttpException, IOException,
			ExpiredTokenException {
		return client.getFile(getId());
	}
	
	public ShockFileInformation getFileInformation() {
		return file;
	}
	
	public ShockNodeId getId() {
		return id;
	}
	
	public ShockVersionStamp getVersion() {
		return version;
	}
	
	@Override
	public String toString() {
		return "ShockNode [attributes=" + attributes + ", file=" + file
				+ ", id=" + id + ", version=" + version + "]";
	}
}
