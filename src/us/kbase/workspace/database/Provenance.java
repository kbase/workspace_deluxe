package us.kbase.workspace.database;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import us.kbase.common.exceptions.UnimplementedException;

public class Provenance {
	
	private String user;
	private Date date;
	protected List<ProvenanceAction> actions =
			new ArrayList<ProvenanceAction>();
	
	public Provenance(WorkspaceUser user) {
		if (user == null) {
			throw new IllegalArgumentException("user cannot be null");
		}
		this.user = user.getUser();
		this.date = new Date();
	}
	
	protected Provenance() {} //for subclasses using mongo
	
	public Provenance addAction(ProvenanceAction action) {
		if (action == null) {
			throw new IllegalArgumentException("action cannot be null");
		}
		actions.add(action);
		return this;
	}
	
	public WorkspaceUser getUser() {
		return new WorkspaceUser(user);
	}
	
	public Date getDate() {
		return date;
	}

	public List<ProvenanceAction> getActions() {
		return new ArrayList<ProvenanceAction>(actions);
	}
	
	@Override
	public String toString() {
		return "Provenance [user=" + user + ", actions=" + actions + "]";
	}

	public static class ProvenanceAction {
		
		protected Date time;
		protected String service;
		protected String serviceVersion;
		protected String method;
		protected List<Object> methodParameters;
		protected String script;
		protected String scriptVersion;
		protected String commandLine;
		protected List<String> wsobjs = new LinkedList<String>();
		protected List<String> incomingArgs;
		protected List<String> outgoingArgs;
		protected String description;
		
		public ProvenanceAction() {}
		
		//copy constructor
		public ProvenanceAction(final ProvenanceAction action) {
			time = action.time;
			service = action.service;
			serviceVersion = action.serviceVersion;
			method = action.method;
			methodParameters = action.methodParameters;
			script = action.script;
			scriptVersion = action.scriptVersion;
			commandLine = action.commandLine;
			wsobjs = action.wsobjs;
			incomingArgs = action.incomingArgs;
			outgoingArgs = action.outgoingArgs;
			description = action.description;
		}
		
		public Date getTime() {
			return time;
		}

		public void setTime(final Date time) {
			this.time = time;
		}

		public ProvenanceAction withTime(final Date time) {
			this.time = time;
			return this;
		}
		
		public String getServiceName() {
			return service;
		}

		public void setServiceName(final String service) {
			this.service = service;
		}
		
		public ProvenanceAction withServiceName(final String service) {
			this.service = service;
			return this;
		}

		public String getServiceVersion() {
			return serviceVersion;
		}

		public void setServiceVersion(final String serviceVersion) {
			this.serviceVersion = serviceVersion;
		}
		
		public ProvenanceAction withServiceVersion(final String serviceVersion) {
			this.serviceVersion = serviceVersion;
			return this;
		}

		public String getMethod() {
			return method;
		}

		public void setMethod(final String method) {
			this.method = method;
		}
		
		public ProvenanceAction withMethod(final String method) {
			this.method = method;
			return this;
		}

		public List<Object> getMethodParameters() {
			return methodParameters;
		}

		public void setMethodParameters(final List<Object> methodParameters) {
			this.methodParameters = methodParameters;
		}
		
		public ProvenanceAction withMethodParameters(
				final List<Object> methodParameters) {
			this.methodParameters = methodParameters;
			return this;
		}

		public String getScript() {
			return script;
		}

		public void setScript(final String script) {
			this.script = script;
		}
		
		public ProvenanceAction withScript(final String script) {
			this.script = script;
			return this;
		}

		public String getScriptVersion() {
			return scriptVersion;
		}

		public void setScriptVersion(final String scriptVersion) {
			this.scriptVersion = scriptVersion;
		}

		public ProvenanceAction withScriptVersion(final String scriptVersion) {
			this.scriptVersion = scriptVersion;
			return this;
		}
		
		public String getCommandLine() {
			return commandLine;
		}

		public void setCommandLine(final String commandLine) {
			this.commandLine = commandLine;
		}

		public ProvenanceAction withCommandLine(final String commandLine) {
			this.commandLine = commandLine;
			return this;
		}
		
		public List<String> getWorkspaceObjects() {
			return wsobjs;
		}
		
		public void setWorkspaceObjects(final List<String> wsobjs) {
			if (wsobjs != null) {
				this.wsobjs = new LinkedList<String>(
						new HashSet<String>(wsobjs));
			}
		}

		public ProvenanceAction withWorkspaceObjects(
				final List<String> wsobjs) {
			if (wsobjs != null) {
				this.wsobjs = new LinkedList<String>(
						new HashSet<String>(wsobjs));
			}
			return this;
		}
		
		public List<String> getIncomingArgs() {
			return incomingArgs;
		}

		public void setIncomingArgs(final List<String> incomingArgs) {
			this.incomingArgs = incomingArgs;
		}

		public ProvenanceAction withIncomingArgs(
				final List<String> incomingArgs) {
			this.incomingArgs = incomingArgs;
			return this;
		}
		
		public List<String> getOutgoingArgs() {
			return outgoingArgs;
		}

		public void setOutgoingArgs(final List<String> outgoingArgs) {
			this.outgoingArgs = outgoingArgs;
		}
		
		public ProvenanceAction withOutgoingArgs(
				final List<String> outgoingArgs) {
			this.outgoingArgs = outgoingArgs;
			return this;
		}

		public String getDescription() {
			return description;
		}

		public void setDescription(final String description) {
			this.description = description;
		}
		
		public ProvenanceAction withDescription(final String description) {
			this.description = description;
			return this;
		}

		// would prefer to make this abstract but Jackson doesn't like it
		// and want to keep this class as unaware of the backend implementation
		// as possible
		@JsonIgnore
		public List<String> getResolvedObjects() {
			throw new UnimplementedException();
		}

		@Override
		public String toString() {
			return "ProvenanceAction [time=" + time + ", service=" + service
					+ ", serviceVersion=" + serviceVersion + ", method="
					+ method + ", methodParameters=" + methodParameters
					+ ", script=" + script + ", scriptVersion=" + scriptVersion
					+ ", commandLine=" + commandLine + ", wsobjs=" + wsobjs
					+ ", incomingArgs=" + incomingArgs + ", outgoingArgs="
					+ outgoingArgs + ", description=" + description + "]";
		}
	}
}
