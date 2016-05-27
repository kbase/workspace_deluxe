package us.kbase.workspace.database;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import com.fasterxml.jackson.core.JsonParseException;

import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;

public class WorkspaceObjectData {
	
	private final ByteArrayFileCache data;
	private final ObjectInformation info;
	private final Provenance prov;
	private final List<String> references;
	private Reference copied;
	private boolean isCopySourceInaccessible = false;
	private final Map<String, List<String>> extIDs;

	public WorkspaceObjectData(
			final ObjectInformation info,
			final Provenance prov,
			final List<String> references,
			final Reference copied,
			final Map<String, List<String>> extIDs) {
		if (info == null || prov == null || references == null) {
			throw new IllegalArgumentException(
					"references, prov and info cannot be null");
		}
		this.info = info;
		this.prov = prov;
		this.references = references;
		this.copied = copied;
		this.extIDs = extIDs == null ? new HashMap<String, List<String>>() :
			extIDs;
		this.data = null;
	}
	
	public WorkspaceObjectData(
			final ByteArrayFileCache data,
			final ObjectInformation info,
			final Provenance prov,
			final List<String> references,
			final Reference copied,
			final Map<String, List<String>> extIDs) {
		if (info == null || prov == null || references == null) {
			throw new IllegalArgumentException(
					"references, prov and info cannot be null");
		}
		this.info = info;
		this.prov = prov;
		this.references = references;
		this.copied = copied;
		this.extIDs = extIDs == null ? new HashMap<String, List<String>>() :
			extIDs;
		this.data = data;
	}

	public ObjectInformation getObjectInfo() {
		return info;
	}

	public Provenance getProvenance() {
		return prov;
	}
	
	public List<String> getReferences() {
		return references;
	}
	
	public Reference getCopyReference() {
		return copied;
	}
	
	public Map<String, List<String>> getExtractedIds() {
		return extIDs;
		//could make this immutable I suppose
	}
	
	public ByteArrayFileCache getSerializedData() {
		return data;
	}
	
	public boolean hasData() {
		return data != null;
	}
	
	void setCopySourceInaccessible() {
		copied = null;
		isCopySourceInaccessible = true;
	}
	
	public boolean isCopySourceInaccessible() {
		return isCopySourceInaccessible;
	}
	
	/**
	 * @return Maps/lists/scalars
	 * @throws IOException 
	 * @throws JsonParseException 
	 */
	public Object getData() throws IOException {
		try {
			return data.getUObject().asClassInstance(Object.class);
		} catch (JsonParseException jpe) {
			//this should never happen since the data's already been type
			//checked
			throw new RuntimeException("somethin's dun broke", jpe);
		}
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("WorkspaceObjectData [data=");
		builder.append(data);
		builder.append(", info=");
		builder.append(info);
		builder.append(", prov=");
		builder.append(prov);
		builder.append(", references=");
		builder.append(references);
		builder.append(", copied=");
		builder.append(copied);
		builder.append(", isCopySourceInaccessible=");
		builder.append(isCopySourceInaccessible);
		builder.append(", extIDs=");
		builder.append(extIDs);
		builder.append("]");
		return builder.toString();
	}
}
