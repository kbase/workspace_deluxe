package us.kbase.shock.client;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties({"relatives", "type", "indexes", "tags", "linkages"})
public class ShockNode {

	//TODO make private
	public ShockNode(){}
	
	private Map<String, Object> attributes;
	private ShockFileInformation file;
	private ShockNodeId id;
	private String version;
	
	public Map<String, Object> getAttributes() {
		return attributes;
	}
	
	public ShockFileInformation getFile() {
		return file;
	}
	
	public ShockNodeId getId() {
		return id;
	}
	
	public String getVersion() {
		return version;
	}
}
