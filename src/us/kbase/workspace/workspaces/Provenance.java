package us.kbase.workspace.workspaces;

import static us.kbase.common.utils.StringUtils.checkString;

import java.util.ArrayList;
import java.util.List;

public class Provenance {
	
	private final String user;
	private final List<ProvenanceAction> actions =
			new ArrayList<ProvenanceAction>();
	
	public Provenance(String user) {
		checkString(user, "user");
		this.user = user;
	}
	
	public void addAction(ProvenanceAction action) {
		if (action == null) {
			throw new NullPointerException("action cannot be null");
		}
		actions.add(action);
	}
	
	public String getUser() {
		return user;
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
		private List<String> wsobjs;
		
		public ProvenanceAction() {}
		
		public ProvenanceAction withServiceName(final String service) {
			this.service = service;
			return this;
		}
		
		public ProvenanceAction withWorkspaceObjects(
				final List<String> wsobjs) {
			this.wsobjs = wsobjs;
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
