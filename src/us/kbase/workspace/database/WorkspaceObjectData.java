package us.kbase.workspace.database;

public class WorkspaceObjectData {
	
	private final Object data;
	private final ObjectUserMetaData meta;
	
	public WorkspaceObjectData(Object data, ObjectUserMetaData meta) {
		if (data == null || meta == null) {
			throw new IllegalArgumentException("data and meta cannot be null");
		}
		this.data = data;
		this.meta = meta;
	}

	public Object getData() {
		return data;
	}

	public ObjectUserMetaData getMeta() {
		return meta;
	}

	@Override
	public String toString() {
		return "WorkspaceObjectData [data=" + data + ", meta=" + meta + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((data == null) ? 0 : data.hashCode());
		result = prime * result + ((meta == null) ? 0 : meta.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof WorkspaceObjectData)) {
			return false;
		}
		WorkspaceObjectData other = (WorkspaceObjectData) obj;
		if (data == null) {
			if (other.data != null) {
				return false;
			}
		} else if (!data.equals(other.data)) {
			return false;
		}
		if (meta == null) {
			if (other.meta != null) {
				return false;
			}
		} else if (!meta.equals(other.meta)) {
			return false;
		}
		return true;
	}

}
