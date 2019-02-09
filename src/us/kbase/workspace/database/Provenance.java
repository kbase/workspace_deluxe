package us.kbase.workspace.database;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

//TODO TEST unit tests
//TODO JAVADOC
//TODO MEM this should keep track of its size & punt if it gets too large
//TODO consider checking the syntax of urls
//TODO CODE make this immutable and use builders.
//TODO NOW don't allow nulls for lists/maps
//TODO NOW this whole class needs a massive refactor

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
		if (wsid < 1) {
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

		public ExternalData withResourceName(String resourceName) {
			this.resourceName = resourceName;
			return this;
		}

		public String getResourceUrl() {
			return resourceUrl;
		}

		public ExternalData withResourceUrl(String resourceUrl) {
			this.resourceUrl = resourceUrl;
			return this;
		}

		public String getResourceVersion() {
			return resourceVersion;
		}

		public ExternalData withResourceVersion(String resourceVersion) {
			this.resourceVersion = resourceVersion;
			return this;
		}

		public Date getResourceReleaseDate() {
			return resourceReleaseDate;
		}
		

		public ExternalData withResourceReleaseDate(Date resourceReleaseDate) {
			this.resourceReleaseDate = resourceReleaseDate;
			return this;
		}

		public String getDataUrl() {
			return dataUrl;
		}

		public ExternalData withDataUrl(String dataUrl) {
			this.dataUrl = dataUrl;
			return this;
		}

		public String getDataId() {
			return dataId;
		}
		
		public ExternalData withDataId(String dataId) {
			this.dataId = dataId;
			return this;
		}

		public String getDescription() {
			return description;
		}
		
		public ExternalData withDescription(String description) {
			this.description = description;
			return this;
		}
		
	}
	
	public static class SubAction {
		private String name;
		private String ver;
		private String codeUrl;
		private String commit;
		private String endpointUrl;
		
		public SubAction() {}

		public String getName() {
			return name;
		}

		public String getVer() {
			return ver;
		}

		public String getCodeUrl() {
			return codeUrl;
		}

		public String getCommit() {
			return commit;
		}

		public String getEndpointUrl() {
			return endpointUrl;
		}

		public SubAction withName(String name) {
			this.name = name;
			return this;
		}

		public SubAction withVer(String ver) {
			this.ver = ver;
			return this;
		}

		public SubAction withCodeUrl(String codeUrl) {
			this.codeUrl = codeUrl;
			return this;
		}

		public SubAction withCommit(String commit) {
			this.commit = commit;
			return this;
		}

		public SubAction withEndpointUrl(String endpointUrl) {
			this.endpointUrl = endpointUrl;
			return this;
		}

	}
	
	public static class ProvenanceAction {
		
		protected Date time;
		protected String caller;
		protected String service;
		protected String serviceVersion;
		protected String method;
		protected List<Object> methodParameters;
		protected String script;
		protected String scriptVersion;
		protected String commandLine;
		protected List<String> wsobjs = new LinkedList<>();
		protected List<String> incomingArgs;
		protected List<String> outgoingArgs;
		protected List<ExternalData> externalData = new LinkedList<>();
		protected List<SubAction> subActions = new LinkedList<>();
		protected Map<String, String> custom = new HashMap<>();
		protected String description;
		private List<String> resolvedObjects = new LinkedList<>();
		
		public ProvenanceAction() {}
		
		//copy constructor - shallow copy
		public ProvenanceAction(final ProvenanceAction action) {
			time = action.time;
			caller = action.caller;
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
			externalData = action.externalData;
			subActions = action.subActions;
			custom = action.custom;
			description = action.description;
		}
		
		public Date getTime() {
			return time;
		}

		public ProvenanceAction withTime(final Date time) {
			this.time = time;
			return this;
		}
		
		public String getCaller() {
			return caller;
		}

		public ProvenanceAction withCaller(final String caller) {
			this.caller = caller;
			return this;
		}
		
		public String getServiceName() {
			return service;
		}

		public ProvenanceAction withServiceName(final String service) {
			this.service = service;
			return this;
		}

		public String getServiceVersion() {
			return serviceVersion;
		}

		public ProvenanceAction withServiceVersion(final String serviceVersion) {
			this.serviceVersion = serviceVersion;
			return this;
		}

		public String getMethod() {
			return method;
		}

		public ProvenanceAction withMethod(final String method) {
			this.method = method;
			return this;
		}

		public List<Object> getMethodParameters() {
			return methodParameters;
		}

		public ProvenanceAction withMethodParameters(final List<Object> methodParameters) {
			this.methodParameters = methodParameters;
			return this;
		}

		public String getScript() {
			return script;
		}

		public ProvenanceAction withScript(final String script) {
			this.script = script;
			return this;
		}

		public String getScriptVersion() {
			return scriptVersion;
		}

		public ProvenanceAction withScriptVersion(final String scriptVersion) {
			this.scriptVersion = scriptVersion;
			return this;
		}
		
		public String getCommandLine() {
			return commandLine;
		}

		public ProvenanceAction withCommandLine(final String commandLine) {
			this.commandLine = commandLine;
			return this;
		}
		
		public List<String> getWorkspaceObjects() {
			return wsobjs;
		}
		
		public ProvenanceAction withWorkspaceObjects(final List<String> wsobjs) {
			if (wsobjs != null) {
				this.wsobjs = new LinkedList<String>(new HashSet<String>(wsobjs));
			}
			return this;
		}
		
		public List<String> getIncomingArgs() {
			return incomingArgs;
		}

		public ProvenanceAction withIncomingArgs(final List<String> incomingArgs) {
			this.incomingArgs = incomingArgs;
			return this;
		}
		
		public List<String> getOutgoingArgs() {
			return outgoingArgs;
		}

		public ProvenanceAction withOutgoingArgs(final List<String> outgoingArgs) {
			this.outgoingArgs = outgoingArgs;
			return this;
		}

		public String getDescription() {
			return description;
		}

		public ProvenanceAction withDescription(final String description) {
			this.description = description;
			return this;
		}
		
		public List<ExternalData> getExternalData() {
			return externalData;
		}

		public ProvenanceAction withExternalData(final List<ExternalData> externalData) {
			this.externalData = externalData;
			return this;
		}
		
		public List<SubAction> getSubActions() {
			return subActions;
		}

		public ProvenanceAction withSubActions(final List<SubAction> subActions) {
			this.subActions = subActions;
			return this;
		}
		
		public Map<String, String> getCustom() {
			return custom;
		}

		public ProvenanceAction withCustom(final Map<String, String> custom) {
			if (custom != null) {
				this.custom = custom;
			}
			return this;
		}

		public List<String> getResolvedObjects() {
			return resolvedObjects;
		}
		
		public ProvenanceAction withResolvedObjects(final List<String> resolvedObjects) {
			if (resolvedObjects != null) {
				this.resolvedObjects = resolvedObjects;
			}
			return this;
		}
	}
}
