package us.kbase.shock.client;

import java.io.IOException;
import java.util.Map;

import us.kbase.shock.client.exceptions.ExpiredTokenException;
import us.kbase.shock.client.exceptions.ShockHttpException;
import us.kbase.shock.client.exceptions.ShockNodeDeletedException;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties({"relatives", "type", "indexes", "tags", "linkages"})
public class ShockNode extends ShockData {

	private Map<String, Object> attributes;
	private ShockFileInformation file;
	private ShockNodeId id;
	private ShockVersionStamp version;
	@JsonIgnore
	private BasicShockClient client;
	@JsonIgnore
	private boolean deleted = false;
	
	private ShockNode(){}
	
	void addClient(BasicShockClient client) {
		this.client = client;
	}
	
	private void checkDeleted() throws ShockNodeDeletedException {
		if (deleted) {
			throw new ShockNodeDeletedException();
		}
		
	}

	public Map<String, Object> getAttributes() throws ShockNodeDeletedException {
		checkDeleted();
		return attributes;
	}

	public void delete() throws ShockHttpException, IOException,
			ExpiredTokenException, ShockNodeDeletedException {
		client.deleteNode(getId());
		deleted = true;
	}
	
	//TODO add node specific commands here
	
	public byte[] getFile() throws ShockHttpException, IOException,
			ExpiredTokenException, ShockNodeDeletedException {
		return client.getFile(getId());
	}
	
	public ShockFileInformation getFileInformation() throws 
			ShockNodeDeletedException {
		checkDeleted();
		return file;
	}
	
	public ShockNodeId getId() throws ShockNodeDeletedException {
		checkDeleted();
		return id;
	}
	
	public ShockVersionStamp getVersion() throws ShockNodeDeletedException {
		checkDeleted();
		return version;
	}
	
	@Override
	public String toString() {
		return "ShockNode [attributes=" + attributes + ", file=" + file
				+ ", id=" + id + ", version=" + version + ", deleted=" +
				deleted + "]";
	}
}
