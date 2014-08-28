package us.kbase.workspace.database;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorkspaceObjectInformation {
	
	private final ObjectInformation info;
	private final Provenance prov;
	private final List<String> references;
	private Reference copied;
	private boolean isCopySourceInaccessible = false;
	private final Map<String, List<String>> extIDs;

	public WorkspaceObjectInformation(
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
	
	void setCopySourceInaccessible() {
		copied = null;
		isCopySourceInaccessible = true;
	}
	
	public boolean isCopySourceInaccessible() {
		return isCopySourceInaccessible;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("WorkspaceObjectInformation [info=");
		builder.append(info);
		builder.append(", prov=");
		builder.append(prov);
		builder.append(", references=");
		builder.append(references);
		builder.append(", copied=");
		builder.append(copied);
		builder.append(", extIDs=");
		builder.append(extIDs);
		builder.append("]");
		return builder.toString();
	}
}

