package us.kbase.workspace.database;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;


public class Provenance {
	
	private String user;
	private List<ProvenanceAction> actions =
			new ArrayList<ProvenanceAction>();
	
	public Provenance(WorkspaceUser user) {
		if (user == null) {
			throw new IllegalArgumentException("user cannot be null");
		}
		this.user = user.getUser();
	}
	
	protected Provenance() {} //for subclasses using mongo
	
	public void addAction(ProvenanceAction action) {
		if (action == null) {
			throw new NullPointerException("action cannot be null");
		}
		actions.add(action);
	}
	
	public WorkspaceUser getUser() {
		return new WorkspaceUser(user);
	}

	public List<ProvenanceAction> getActions() {
		return new ArrayList<ProvenanceAction>(actions);
	}
	
	@Override
	public String toString() {
		return "Provenance [user=" + user + ", actions=" + actions + "]";
	}

	public static class ProvenanceAction {
		
		//TODO remainder of provenance items
		
		private String service;
		private List<String> wsobjs = new LinkedList<String>();
		
		public ProvenanceAction() {}
		
		public ProvenanceAction withServiceName(final String service) {
			this.service = service;
			return this;
		}
		
		public ProvenanceAction withWorkspaceObjects(
				final List<String> wsobjs) {
			if (wsobjs != null) {
				this.wsobjs = new LinkedList<String>(
						new HashSet<String>(wsobjs));
			}
			return this;
		}

		public String getService() {
			return service;
		}
		
		public List<String> getWorkspaceObjects() {
			return wsobjs;
		}

		@Override
		public String toString() {
			return "ProvenanceAction [service=" + service + ", wsobjs="
					+ wsobjs + "]";
		}
		
	}

}
