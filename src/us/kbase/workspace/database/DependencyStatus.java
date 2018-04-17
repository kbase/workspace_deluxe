package us.kbase.workspace.database;

/** Provides the status of a Workspace dependency.
 * @author gaprice@lbl.gov
 *
 */
public class DependencyStatus {

	private final boolean ok;
	private final String status;
	private final String name;
	private final String version;
	
	public DependencyStatus(
			final boolean ok,
			final String status,
			final String name,
			final String version) {
		this.ok = ok;
		this.status = status;
		this.name = name;
		this.version = version;
	}

	public boolean isOk() {
		return ok;
	}

	public String getStatus() {
		return status;
	}

	public String getName() {
		return name;
	}

	public String getVersion() {
		return version;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + (ok ? 1231 : 1237);
		result = prime * result + ((status == null) ? 0 : status.hashCode());
		result = prime * result + ((version == null) ? 0 : version.hashCode());
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
		if (getClass() != obj.getClass()) {
			return false;
		}
		DependencyStatus other = (DependencyStatus) obj;
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!name.equals(other.name)) {
			return false;
		}
		if (ok != other.ok) {
			return false;
		}
		if (status == null) {
			if (other.status != null) {
				return false;
			}
		} else if (!status.equals(other.status)) {
			return false;
		}
		if (version == null) {
			if (other.version != null) {
				return false;
			}
		} else if (!version.equals(other.version)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("DependencyStatus [ok=");
		builder.append(ok);
		builder.append(", status=");
		builder.append(status);
		builder.append(", name=");
		builder.append(name);
		builder.append(", version=");
		builder.append(version);
		builder.append("]");
		return builder.toString();
	}
	
	
}
