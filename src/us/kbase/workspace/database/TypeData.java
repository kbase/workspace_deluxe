package us.kbase.workspace.database;

import java.util.List;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;

import us.kbase.shock.client.ShockNode;
import us.kbase.shock.client.ShockNodeId;
import us.kbase.shock.client.ShockVersionStamp;
import us.kbase.shock.client.exceptions.ShockNodeDeletedException;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class TypeData {
	//TODO
	
	@JsonIgnore
	private String data = null;
	@JsonIgnore
	private WorkspaceType type = null;
	
	//these attributes are actually saved in mongo
	private List<String> workspaces;
	private String chsum;
	private Map<String, Object> subdata;
	private int size;
	private ShockNodeId shocknodeid = null;
	private ShockVersionStamp shockver = null;
	private boolean gridfs = false;
	
	
	public TypeData(String data, WorkspaceType type, List<String> workspaces,
			Map<String,Object> subdata) {
		if (data == null || type == null || workspaces == null ||
				subdata == null) {
			throw new NullPointerException("No null arguments allowed for " +
					"TypeData constructor");
		}
		this.data = data;
		this.type = type;
		this.workspaces = workspaces;
		this.subdata = subdata;
		this.size = data.length();
		this.chsum = DigestUtils.md5Hex(data);
		
	}

	public String getData() {
		return data;
	}
	
	public WorkspaceType getType() {
		return type;
	}
	
	public String getChsum() {
		return chsum;
	}
	
	public ShockNodeId getShockNodeId() {
		return shocknodeid;
	}
	
	public ShockVersionStamp getShockVersion() {
		return shockver;
	}

	public void addShockInformation(ShockNode sn) {
		try {
			shocknodeid = sn.getId();
			shockver = sn.getVersion();
		} catch (ShockNodeDeletedException snde) {
			throw new RuntimeException("something is very broken", snde);
		}
	}

	@Override
	public String toString() {
		return "TypeData [data=" + data + ", type=" + type + ", workspaces="
				+ workspaces + ", chsum=" + chsum + ", subdata=" + subdata
				+ ", size=" + size + ", shocknode=" + shocknodeid + ", shockver="
				+ shockver + ", gridfs=" + gridfs + "]";
	}
}
