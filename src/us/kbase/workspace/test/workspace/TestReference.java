package us.kbase.workspace.test.workspace;

import org.apache.commons.lang.NotImplementedException;

import us.kbase.workspace.database.Reference;

public class TestReference implements Reference {
	
	private final long wsid;
	private final long objid;
	private final int ver;
	
	public TestReference(Reference ref) {
		this.wsid = ref.getWorkspaceID();
		this.objid = ref.getObjectID();
		this.ver = ref.getVersion();
	}

	public TestReference(long wsid, long objid, int ver) {
		this.wsid = wsid;
		this.objid = objid;
		this.ver = ver;
	}

	@Override
	public String getId() {
		throw new NotImplementedException();
	}

	@Override
	public long getWorkspaceID() {
		return wsid;
	}

	@Override
	public long getObjectID() {
		return objid;
	}

	@Override
	public int getVersion() {
		return ver;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (objid ^ (objid >>> 32));
		result = prime * result + ver;
		result = prime * result + (int) (wsid ^ (wsid >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TestReference other = (TestReference) obj;
		if (objid != other.objid)
			return false;
		if (ver != other.ver)
			return false;
		if (wsid != other.wsid)
			return false;
		return true;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("TestReference [wsid=");
		builder.append(wsid);
		builder.append(", objid=");
		builder.append(objid);
		builder.append(", ver=");
		builder.append(ver);
		builder.append("]");
		return builder.toString();
	}

}
