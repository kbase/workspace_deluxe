package us.kbase.workspace.database;

import java.util.List;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class TypeData {
	//TODO
	
	@JsonIgnore
	private String data = null;
	@JsonIgnore
	private WorkspaceType type = null;
	private List<String> workspaces;
	private String chsum;
	private Map<String, Object> subdata;
	private int size;
	private String shocknode;
	private String shockver;
	private boolean gridfs;
	
	
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
}
