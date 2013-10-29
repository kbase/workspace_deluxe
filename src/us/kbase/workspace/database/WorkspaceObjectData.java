package us.kbase.workspace.database;

public class WorkspaceObjectData {

	private final Object data;
	private final ObjectInfoUserMeta meta;
	private final Provenance prov;

	public WorkspaceObjectData(final Object data,
			final ObjectInfoUserMeta meta, final Provenance prov) {
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

	public ObjectInfoUserMeta getMeta() {
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
