package us.kbase.workspace.database;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import us.kbase.workspace.database.provenance.ProvenanceAction;

//TODO TEST unit tests
//TODO JAVADOC
//TODO MEM this should keep track of its size & punt if it gets too large
//TODO CODE make this immutable and use builders.
//TODO CODE don't allow nulls for lists/maps
//TODO CODE this whole class needs a massive refactor

public class Provenance {
	
	private final WorkspaceUser user;
	private final Date date;
	private Long wsid = null;
	protected List<ProvenanceAction> actions =
			new ArrayList<ProvenanceAction>();
	
	public Provenance(final WorkspaceUser user) {
		requireNonNull(user, "user");
		this.user = user;
		this.date = new Date();
	}
	
	public Provenance(final WorkspaceUser user, final Date date) {
		requireNonNull(user, "user");
		this.user = user;
		this.date = date;
	}
	
	public Provenance addAction(ProvenanceAction action) {
		if (action == null) {
			throw new IllegalArgumentException("action cannot be null");
		}
		actions.add(action);
		return this;
	}
	
	public WorkspaceUser getUser() {
		return user;
	}
	
	public Date getDate() {
		return date;
	}
	
	public void setWorkspaceID(final Long wsid) {
		// objects saved before version 0.4.1 will have null workspace IDs
		if (wsid != null && wsid < 1) {
			throw new IllegalArgumentException("wsid must be > 0");
		}
		this.wsid = wsid;
	}
	// will be null if not set yet 
	public Long getWorkspaceID() {
		return wsid;
	}

	public List<ProvenanceAction> getActions() {
		return new ArrayList<ProvenanceAction>(actions);
	}
	
}
