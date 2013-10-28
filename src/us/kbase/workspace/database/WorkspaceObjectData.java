package us.kbase.workspace.database;

public class WorkspaceObjectData {

	private final Object data;
	private final ObjectUserMetaData meta;
	private final Provenance prov;

	public WorkspaceObjectData(final Object data,
			final ObjectUserMetaData meta, final Provenance prov) {
		if (data == null || meta == null || prov == null) {
			throw new IllegalArgumentException(
					"data, prov and meta cannot be null");
		}
		this.data = data;
		this.meta = meta;
		this.prov = prov;
	}

	public Object getData() {
		return data;
	}

	public ObjectUserMetaData getMeta() {
		return meta;
	}

	public Provenance getProvenance() {
		return prov;
	}

	@Override
	public String toString() {
		return "WorkspaceObjectData [data=" + data + ", meta=" + meta
				+ ", prov=" + prov + "]";
	}
}
