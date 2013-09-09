package us.kbase.workspace.database.mongo;

import static us.kbase.workspace.util.Util.checkString;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;

import us.kbase.shock.client.ShockNode;
import us.kbase.shock.client.ShockNodeId;
import us.kbase.shock.client.ShockVersionStamp;
import us.kbase.shock.client.exceptions.ShockNodeDeletedException;
import us.kbase.workspace.workspaces.AbsoluteTypeId;
import us.kbase.workspace.workspaces.TypeId;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class TypeData {
	//TODO TypeData
	
	@JsonIgnore
	private String data = null;
	@JsonIgnore
	private AbsoluteTypeId type = null;
	
	//these attributes are actually saved in mongo
	private List<Integer> workspaces;
	private String chsum;
	private Map<String, Object> subdata;
	private int size;
	private ShockNodeId shocknodeid = null;
	private ShockVersionStamp shockver = null;
	private boolean gridfs = false;
	
	
	public TypeData(String data, AbsoluteTypeId type, int firstWorkspace,
			Map<String,Object> subdata) {
		checkString(data, "data");
		if (type == null) {
			throw new NullPointerException("type may not be null");
		}
		if (firstWorkspace < 1) {
			throw new IllegalArgumentException("firstWorkspace must be > 0");
		}
		this.data = data;
		this.type = type;
		this.workspaces = new ArrayList<Integer>();
		this.workspaces.add(firstWorkspace);
		this.subdata = subdata;
		this.size = data.length();
		this.chsum = DigestUtils.md5Hex(data);
		
	}

	public String getData() {
		return data;
	}
	
	public TypeId getType() {
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
