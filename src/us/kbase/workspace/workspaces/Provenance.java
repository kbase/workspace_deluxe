package us.kbase.workspace.workspaces;

import static us.kbase.workspace.util.Util.checkString;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Provenance {
	
	private final String user;
	private final Date createDate;
	private final List<ProvenanceAction> actions = new ArrayList<ProvenanceAction>();
	
	public Provenance(String user) {
		this(user, new Date());
	}
	
	public Provenance(String user, Date createDate) {
		checkString(user, "user");
		if (createDate == null) {
			throw new IllegalArgumentException("date cannot be null");
		}
		this.user = user;
		this.createDate = createDate;
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

	public Date getCreateDate() {
		return createDate;
	}

	public List<ProvenanceAction> getActions() {
		return new ArrayList<ProvenanceAction>(actions);
	}
	
	public class ProvenanceAction {
		
		//TODO remainder of provenance items
		//TODO verify workspace objects exist
		
		private String service;
		
		public ProvenanceAction() {}
		
		public ProvenanceAction withServiceName(String service) {
			checkString(service, "service");
			this.service = service;
			return this;
		}

		public String getService() {
			return service;
		}
		
	}

}
