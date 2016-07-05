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
		super();
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
}
