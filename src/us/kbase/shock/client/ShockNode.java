package us.kbase.shock.client;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties({"relatives", "type", "indexes", "tags", "linkages"})
public class ShockNode {

	private ShockNode(){}
	
	private Map<String, Object> attributes;
	private ShockFileInformation file;
	private ShockNodeId id;
	private ShockVersionStamp version;
	
	public Map<String, Object> getAttributes() {
		return attributes;
	}
	
	public ShockFileInformation getFile() {
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
