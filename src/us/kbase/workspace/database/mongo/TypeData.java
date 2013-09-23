package us.kbase.workspace.database.mongo;

import static us.kbase.typedobj.util.TypeUtils.checkString;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;

import us.kbase.typedobj.core.AbsoluteTypeDefId;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

public class TypeData {
	
	@JsonIgnore
	private String data = null;
	@JsonIgnore
	private AbsoluteTypeDefId type = null;
	
	//these attributes are actually saved in mongo
	private List<Integer> workspaces;
	private String chksum;
	@JsonInclude(value=JsonInclude.Include.ALWAYS)
	private Map<String, Object> subdata;
	private int size;
	private int version;
	
	public TypeData(final String data, final AbsoluteTypeDefId type,
			final ResolvedMongoWSID firstWorkspace,
			final Map<String,Object> subdata) {
		//TODO might be better to generate subdata here
		checkString(data, "data");
		if (type == null) {
			throw new IllegalArgumentException("type may not be null");
		}
		if (firstWorkspace == null) {
			throw new IllegalArgumentException("firstWorkspace cannot be null");
		}
		this.data = data;
		this.type = type;
		this.workspaces = new ArrayList<Integer>();
		this.workspaces.add(firstWorkspace.getID());
		this.subdata = subdata;
		this.size = data.length();
		this.chksum = DigestUtils.md5Hex(data);
		this.version = type.getMinorVersion();
		
	}

	public String getData() {
		return data;
	}
	
	public AbsoluteTypeDefId getType() {
		return type;
	}
	
	public String getChksum() {
		return chksum;
	}
	
	public int getSize() {
		return size;
	}
	
	//subdata is mutable!
	public DBObject getSafeUpdate() {
		final String soi = "$setOnInsert";
		final DBObject dbo = new BasicDBObject();
		final DBObject wsids = new BasicDBObject();
		wsids.put("$each", workspaces);
		final DBObject ws = new BasicDBObject();
		ws.put("workspaces", wsids);
		dbo.put("$addToSet", ws);
		final DBObject chksum = new BasicDBObject();
		chksum.put("chksum", getChksum());
		dbo.put(soi, chksum);
		final DBObject subdata = new BasicDBObject();
		subdata.put("subdata", subdata);
		dbo.put(soi, subdata);
		final DBObject size = new BasicDBObject();
		size.put("size", getSize());
		dbo.put(soi, size);
		final DBObject version = new BasicDBObject();
		version.put("version", this.version);
		dbo.put(soi, version);
		return dbo;
	}

	@Override
	public String toString() {
		return "TypeData [data=" + data + ", type=" + type + ", workspaces="
				+ workspaces + ", chksum=" + chksum + ", subdata=" + subdata
				+ ", size=" + size + ", version=" + version + "]";
	}
}
