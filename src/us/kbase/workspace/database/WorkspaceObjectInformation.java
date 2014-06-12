package us.kbase.workspace.database;

import java.util.List;

public class WorkspaceObjectInformation {
	
	private final ObjectInformation info;
	private final Provenance prov;
	private final List<String> references;

	public WorkspaceObjectInformation(final ObjectInformation info,
			final Provenance prov, final List<String> references) {
		if (info == null || prov == null || references == null) {
			throw new IllegalArgumentException(
					"references, prov and info cannot be null");
		}
		this.info = info;
		this.prov = prov;
		this.references = references;
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

	@Override
	public String toString() {
		return "WorkspaceObjectData [info=" + info
				+ ", prov=" + prov + ", references=" + references + "]";
	}
}

