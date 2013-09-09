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
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

public class TypeData {
	//TODO TypeData
	
	@JsonIgnore
	private String data = null;
	@JsonIgnore
	private AbsoluteTypeId type = null;
	
	//these attributes are actually saved in mongo
	private List<Integer> workspaces;
	private String chksum;
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
		this.chksum = DigestUtils.md5Hex(data);
		
	}

	public String getData() {
		return data;
	}
	
	public TypeId getType() {
		return type;
	}
	
	public String getChksum() {
		return chksum;
	}
	
	public int getSize() {
		return size;
	}
	
	public ShockNodeId getShockNodeId() {
		if (!isShockBlob()) {
			throw new IllegalStateException(
					"This data has not been set as shock backended data");
		}
		return shocknodeid;
	}
	
	public ShockVersionStamp getShockVersion() {
		if (!isShockBlob()) {
			throw new IllegalStateException(
					"This data has not been set as shock backended data");
		}
		return shockver;
	}
	
	public boolean isShockBlob() {
		return shocknodeid != null;
	}
	
	public boolean isGridFSBlob() {
		return gridfs;
	}
	
	public void setGridFS() {
		if (isGridFSBlob() || isShockBlob()) {
			throw new IllegalStateException(
					"The backend type for this data has already been set");
		}
		gridfs = true;
		
	}
	
	public void addShockInformation(ShockNode sn) {
		if (isGridFSBlob() || isShockBlob()) {
			throw new IllegalStateException(
					"The backend data for this data has already been set");
		}
		try {
			shocknodeid = sn.getId();
			shockver = sn.getVersion();
		} catch (ShockNodeDeletedException snde) {
			throw new RuntimeException("something is very broken", snde);
		}
	}
	
	//subdata is mutable!
	public DBObject getSafeUpdate() {
		if (!(isGridFSBlob() || isShockBlob())) {
			throw new IllegalStateException(
					"Cannot update without blob type set");
		}
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
		if (isGridFSBlob()) {
			final DBObject gfs = new BasicDBObject();
			gfs.put("gridfs", isGridFSBlob());
			dbo.put(soi, gfs);
		} else {
			final DBObject sn = new BasicDBObject();
			sn.put("shocknode", getShockNodeId());
			dbo.put(soi, sn);
			final DBObject sv = new BasicDBObject();
			sv.put("shockver", getShockVersion());
			dbo.put(soi, sv);
		}
		return dbo;
	}

	@Override
	public String toString() {
		return "TypeData [data=" + data + ", type=" + type + ", workspaces="
				+ workspaces + ", chksum=" + chksum + ", subdata=" + subdata
				+ ", size=" + size + ", shocknode=" + shocknodeid + ", shockver="
				+ shockver + ", gridfs=" + gridfs + "]";
	}
}
