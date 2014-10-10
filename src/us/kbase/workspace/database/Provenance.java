package us.kbase.workspace.database;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import us.kbase.common.exceptions.UnimplementedException;

//TODO unit tests

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
		StringBuilder builder = new StringBuilder();
		builder.append("Provenance [user=");
		builder.append(user);
		builder.append(", date=");
		builder.append(date);
		builder.append(", actions=");
		builder.append(actions);
		builder.append("]");
		return builder.toString();
	}

	public static class ExternalData {
		
		private String resourceName;
		private String resourceUrl;
		private String resourceVersion;
		private Date resourceReleaseDate;
		private String dataUrl;
		private String dataId;
		private String description;
		
		public ExternalData() {}

		public String getResourceName() {
			return resourceName;
		}

		public void setResourceName(String resourceName) {
			this.resourceName = resourceName;
		}
		
		public ExternalData withResourceName(String resourceName) {
			this.resourceName = resourceName;
			return this;
		}

		public String getResourceUrl() {
			return resourceUrl;
		}

		public void setResourceUrl(String resourceUrl) {
			this.resourceUrl = resourceUrl;
		}

		public ExternalData withResourceUrl(String resourceUrl) {
			this.resourceUrl = resourceUrl;
			return this;
		}

		public String getResourceVersion() {
			return resourceVersion;
		}

		public void setResourceVersion(String resourceVersion) {
			this.resourceVersion = resourceVersion;
		}
		
		public ExternalData withResourceVersion(String resourceVersion) {
			this.resourceVersion = resourceVersion;
			return this;
		}

		public Date getResourceReleaseDate() {
			return resourceReleaseDate;
		}
		

		public void setResourceReleaseDate(Date resourceReleaseDate) {
			this.resourceReleaseDate = resourceReleaseDate;
		}

		public ExternalData withResourceReleaseDate(Date resourceReleaseDate) {
			this.resourceReleaseDate = resourceReleaseDate;
			return this;
		}

		public String getDataUrl() {
			return dataUrl;
		}

		public void setDataUrl(String dataUrl) {
			this.dataUrl = dataUrl;
		}

		public ExternalData withDataUrl(String dataUrl) {
			this.dataUrl = dataUrl;
			return this;
		}

		public String getDataId() {
			return dataId;
		}
		
		public void setDataId(String dataId) {
			this.dataId = dataId;
		}

		public ExternalData withDataId(String dataId) {
			this.dataId = dataId;
			return this;
		}

		public String getDescription() {
			return description;
		}
		
		public void setDescription(String description) {
			this.description = description;
		}

		public ExternalData withDescription(String description) {
			this.description = description;
			return this;
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("ExternalData [resourceName=");
			builder.append(resourceName);
			builder.append(", resourceUrl=");
			builder.append(resourceUrl);
			builder.append(", resourceVersion=");
			builder.append(resourceVersion);
			builder.append(", resourceReleaseDate=");
			builder.append(resourceReleaseDate);
			builder.append(", dataUrl=");
			builder.append(dataUrl);
			builder.append(", dataId=");
			builder.append(dataId);
			builder.append(", description=");
			builder.append(description);
			builder.append("]");
			return builder.toString();
		}
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
		protected List<ExternalData> externalData =
				new LinkedList<ExternalData>();
		
		public ProvenanceAction() {}
		
		//copy constructor - shallow copy
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
			externalData = action.externalData;
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
		
		public List<ExternalData> getExternalData() {
			return externalData;
		}

		public void setExternalData(final List<ExternalData> externalData) {
			this.externalData = externalData;
		}
		
		public ProvenanceAction withExternalData(
				final List<ExternalData> externalData) {
			this.externalData = externalData;
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
			StringBuilder builder = new StringBuilder();
			builder.append("ProvenanceAction [time=");
			builder.append(time);
			builder.append(", service=");
			builder.append(service);
			builder.append(", serviceVersion=");
			builder.append(serviceVersion);
			builder.append(", method=");
			builder.append(method);
			builder.append(", methodParameters=");
			builder.append(methodParameters);
			builder.append(", script=");
			builder.append(script);
			builder.append(", scriptVersion=");
			builder.append(scriptVersion);
			builder.append(", commandLine=");
			builder.append(commandLine);
			builder.append(", wsobjs=");
			builder.append(wsobjs);
			builder.append(", incomingArgs=");
			builder.append(incomingArgs);
			builder.append(", outgoingArgs=");
			builder.append(outgoingArgs);
			builder.append(", description=");
			builder.append(description);
			builder.append(", externalData=");
			builder.append(externalData);
			builder.append("]");
			return builder.toString();
		}
	}
}
