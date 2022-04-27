package us.kbase.workspace.database.provenance;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import us.kbase.typedobj.core.JsonDocumentLocation;
import us.kbase.workspace.database.ObjectIdentifier;

/**
 * A provenance action (PA) is an action taken while transforming one data
 * object to another. There may be several PAs taken in series. A PA is
 * typically running a script, running an API command, etc. All of the
 * fields are optional, but a) at least one must be provided, and b) more
 * information equates to better data provenance.
 * 
 * Workspace objects can be specified as references as part of the provenance in
 * {@link Builder#withWorkspaceObjects(List)} if they were used to create the data resulting
 * from this or a chain of PAs. Resolved references, known as Unique Permanent Addresses (UPAs),
 * may be available in {@link Builder#withResolvedObjects(List)}.
 * 
 * A workspace reference is of the form X/Y/Z, where X is the workspace name or immutable ID,
 * Y is the object name or immutable ID, and Z is the optional version of the object.
 * 
 * A resolved reference, or UPA, is a reference where X and Y are both immutable IDs and the
 * version is present.
 * 
 * A workspace reference may be a path through the reference graph in the workspace, where
 * objects in the path are separated by semicolons, e.g. X1/Y1/Z1;...;Xn/YnZn
 */
public class ProvenanceAction {
	
	/* TODO PROVENANCE:LATER consider grouping fields together and field requirements. For example:
	 * service, serviceVersion, method, and methodParameters should all be required to describe
	 * a service call. (serviceEndpointURL might be a useful addition)
	 * method and methodParameters describe a local method call. (needs version though)
	 * script, scriptVersion, and commandLine describe a script call.
	 * 
	 * Similarly, it doesn't make sense to specify more than one of a script, method, or service
	 * call. Should probably have a getBuilder method for each case that requires an input group,
	 * as well as a builder that requires none of the 3.
	 * 
	 * This is a bit of a pain due to 8+ years of provenance data in KBase prod (as well as
	 * systems currently saving provenance) that may or may not follow any new rules.
	 * 
	 * The first thing to do is dig through the DB and see if any rules are being de facto
	 * followed, and then plan from there what is reasonable to implement and what isn't.
	 * Needless to say, that's not going to happen for a while
	 */
	
	private final Instant time;
	private final String caller;
	private final String service;
	private final String serviceVersion;
	private final String method;
	private final List<Object> methodParameters;
	private final String script;
	private final String scriptVersion;
	private final String commandLine;
	private final List<String> wsobjs;
	private final List<String> incomingArgs;
	private final List<String> outgoingArgs;
	private final List<ExternalData> externalData;
	private final List<SubAction> subActions;
	private final Map<String, String> custom;
	private final String description;
	private final List<String> resolvedObjects;
	
	private ProvenanceAction(
			final Instant time,
			final String caller,
			final String service,
			final String serviceVersion,
			final String method,
			final List<Object> methodParameters,
			final String script,
			final String scriptVersion,
			final String commandLine,
			final List<String> wsobjs,
			final List<String> incomingArgs,
			final List<String> outgoingArgs,
			final List<ExternalData> externalData,
			final List<SubAction> subActions,
			final Map<String, String> custom,
			final String description,
			final List<String> resolvedObjects) {
		this.time = time;
		this.caller = caller;
		this.service = service;
		this.serviceVersion = serviceVersion;
		this.method = method;
		this.methodParameters = methodParameters;
		this.script = script;
		this.scriptVersion = scriptVersion;
		this.commandLine = commandLine;
		this.wsobjs = wsobjs;
		this.incomingArgs = incomingArgs;
		this.outgoingArgs = outgoingArgs;
		this.externalData = externalData;
		this.subActions = subActions;
		this.custom = custom;
		this.description = description;
		this.resolvedObjects = resolvedObjects;
	}

	/** Get the time this provenance action occurred.
	 * @return the time, if present.
	 */
	public Optional<Instant> getTime() {
		return Optional.ofNullable(time);
	}

	/** Get the caller that created this provenance action. This is often an external script,
	 * service, etc. 
	 * @return the caller, if present.
	 */
	public Optional<String> getCaller() {
		return Optional.ofNullable(caller);
	}

	/** Get the name of the service that performed the action described in this provenance action.
	 * @return the service name, if present.
	 */
	public Optional<String> getServiceName() {
		return Optional.ofNullable(service);
	}

	/** Get the version of the service that performed the action described in this
	 * provenance action.
	 * @return the service version, if present.
	 */
	public Optional<String> getServiceVersion() {
		return Optional.ofNullable(serviceVersion);
	}

	/** Get the method in the service that performed the action described in this
	 * provenance action.
	 * @return the service method, if present.
	 */
	public Optional<String> getMethod() {
		return Optional.ofNullable(method);
	}

	/** Get the parameters of the service method that performed the action described in this
	 * provenance action.
	 * @return the method parameters.
	 */
	public List<Object> getMethodParameters() {
		return Common.getList(methodParameters);
	}

	/** Get the name of the script that performed the action described in this
	 * provenance action.
	 * @return the script name, if present.
	 */
	public Optional<String> getScript() {
		return Optional.ofNullable(script);
	}

	/** Get the version of the script that performed the action described in this
	 * provenance action.
	 * @return the script version, if present.
	 */
	public Optional<String> getScriptVersion() {
		return Optional.ofNullable(scriptVersion);
	}

	/** Get the command line provided to the script that performed the action described in this
	 * provenance action.
	 * @return the command line, if present.
	 */
	public Optional<String> getCommandLine() {
		return Optional.ofNullable(commandLine);
	}

	/** Get the workspace objects that were used in the performance of this provenance action
	 * and are therefore part of any produced data's provenance. Resolved object UPAs may be
	 * available in {@link #getResolvedObjects()}.
	 * @return the workspace objects.
	 */
	public List<String> getWorkspaceObjects() {
		return Common.getList(wsobjs);
	}

	/** Get the incoming arguments to this provenance action from the prior provenance action.
	 * These arguments will typically be present in the method parameters or script command lines,
	 * and can be compared with the outgoing arguments from the prior provenance action in a
	 * chain of actions to determine how output from one provenance action is fed into the next.
	 * @return the incoming arguments.
	 */
	public List<String> getIncomingArgs() {
		return Common.getList(incomingArgs);
	}

	/** Get the outgoing arguments from this provenance action to the next provenance action.
	 * These arguments will typically be present in the method parameters or script command lines
	 * of the next provenance action, and can be compared with the incoming arguments in the next
	 * provenance action in a chain of actions to determine how output from one provenance action
	 * is fed into the next.
	 * @return the outgoing arguments.
	 */
	public List<String> getOutgoingArgs() {
		return Common.getList(outgoingArgs);
	}

	/** Get information about any external data used in this provenance action.
	 * @return the external data.
	 */
	public List<ExternalData> getExternalData() {
		return Common.getList(externalData);
	}

	/** Get information about any sub actions taken as part of this provenance action.
	 * @return the sub actions.
	 */
	public List<SubAction> getSubActions() {
		return Common.getList(subActions);
	}

	/** Get any custom provenance information for this action.
	 * @return the custom provenance information.
	 */
	public Map<String, String> getCustom() {
		return custom == null ? Collections.emptyMap() : custom;
	}
	
	/** Get the description of this provenance action.
	 * @return the description, if present.
	 */
	public Optional<String> getDescription() {
		return Optional.ofNullable(description);
	}

	/** Get the resolved workspace object refs from {@link #getWorkspaceObjects()}. Each item in
	 * the standard list has a resolved entry in the resolved list, if the resolved list isn't
	 * empty.
	 * @return the resolved workspace objects or an empty list if there are no resolved objects
	 * present.
	 */
	public List<String> getResolvedObjects() {
		return Common.getList(resolvedObjects);
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((caller == null) ? 0 : caller.hashCode());
		result = prime * result + ((commandLine == null) ? 0 : commandLine.hashCode());
		result = prime * result + ((custom == null) ? 0 : custom.hashCode());
		result = prime * result + ((description == null) ? 0 : description.hashCode());
		result = prime * result + ((externalData == null) ? 0 : externalData.hashCode());
		result = prime * result + ((incomingArgs == null) ? 0 : incomingArgs.hashCode());
		result = prime * result + ((method == null) ? 0 : method.hashCode());
		result = prime * result + ((methodParameters == null) ? 0 : methodParameters.hashCode());
		result = prime * result + ((outgoingArgs == null) ? 0 : outgoingArgs.hashCode());
		result = prime * result + ((resolvedObjects == null) ? 0 : resolvedObjects.hashCode());
		result = prime * result + ((script == null) ? 0 : script.hashCode());
		result = prime * result + ((scriptVersion == null) ? 0 : scriptVersion.hashCode());
		result = prime * result + ((service == null) ? 0 : service.hashCode());
		result = prime * result + ((serviceVersion == null) ? 0 : serviceVersion.hashCode());
		result = prime * result + ((subActions == null) ? 0 : subActions.hashCode());
		result = prime * result + ((time == null) ? 0 : time.hashCode());
		result = prime * result + ((wsobjs == null) ? 0 : wsobjs.hashCode());
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
		ProvenanceAction other = (ProvenanceAction) obj;
		if (caller == null) {
			if (other.caller != null)
				return false;
		} else if (!caller.equals(other.caller))
			return false;
		if (commandLine == null) {
			if (other.commandLine != null)
				return false;
		} else if (!commandLine.equals(other.commandLine))
			return false;
		if (custom == null) {
			if (other.custom != null)
				return false;
		} else if (!custom.equals(other.custom))
			return false;
		if (description == null) {
			if (other.description != null)
				return false;
		} else if (!description.equals(other.description))
			return false;
		if (externalData == null) {
			if (other.externalData != null)
				return false;
		} else if (!externalData.equals(other.externalData))
			return false;
		if (incomingArgs == null) {
			if (other.incomingArgs != null)
				return false;
		} else if (!incomingArgs.equals(other.incomingArgs))
			return false;
		if (method == null) {
			if (other.method != null)
				return false;
		} else if (!method.equals(other.method))
			return false;
		if (methodParameters == null) {
			if (other.methodParameters != null)
				return false;
		} else if (!methodParameters.equals(other.methodParameters))
			return false;
		if (outgoingArgs == null) {
			if (other.outgoingArgs != null)
				return false;
		} else if (!outgoingArgs.equals(other.outgoingArgs))
			return false;
		if (resolvedObjects == null) {
			if (other.resolvedObjects != null)
				return false;
		} else if (!resolvedObjects.equals(other.resolvedObjects))
			return false;
		if (script == null) {
			if (other.script != null)
				return false;
		} else if (!script.equals(other.script))
			return false;
		if (scriptVersion == null) {
			if (other.scriptVersion != null)
				return false;
		} else if (!scriptVersion.equals(other.scriptVersion))
			return false;
		if (service == null) {
			if (other.service != null)
				return false;
		} else if (!service.equals(other.service))
			return false;
		if (serviceVersion == null) {
			if (other.serviceVersion != null)
				return false;
		} else if (!serviceVersion.equals(other.serviceVersion))
			return false;
		if (subActions == null) {
			if (other.subActions != null)
				return false;
		} else if (!subActions.equals(other.subActions))
			return false;
		if (time == null) {
			if (other.time != null)
				return false;
		} else if (!time.equals(other.time))
			return false;
		if (wsobjs == null) {
			if (other.wsobjs != null)
				return false;
		} else if (!wsobjs.equals(other.wsobjs))
			return false;
		return true;
	}
	
	/** Get a builder for a {@link ProvenanceAction}.
	 * @return the builder.
	 */
	public static Builder getBuilder() {
		return new Builder();
	}
	
	/** A builder for a {@link ProvenanceAction}. */
	public static class Builder {
		
		private Instant time = null;
		private String caller = null;
		private String service = null;
		private String serviceVersion = null;
		private String method = null;
		private List<Object> methodParameters = null;
		private String script = null;
		private String scriptVersion = null;
		private String commandLine = null;
		private List<String> wsobjs = null;
		private List<String> incomingArgs = null;
		private List<String> outgoingArgs = null;
		private List<ExternalData> externalData = null;
		private List<SubAction> subActions = null;
		private Map<String, String> custom = null;
		private String description = null;
		private List<String> resolvedObjects = null;
		
		private Builder() {}
		
		/** Set the time this provenance action occurred. Null removes any current time in the
		 * builder.
		 * @param time the time.
		 * @return this builder.
		 */
		public Builder withTime(final Instant time) {
			this.time = time;
			return this;
		}
		
		/** Set the caller that created this provenance action. This is often an external script,
		 * service, etc. Null or the empty string removes any caller in the builder.
		 * @param caller the caller.
		 * @return this builder.
		 */
		public Builder withCaller(final String caller) {
			this.caller = Common.processString(caller);
			return this;
		}
		
		/** Set the name of the service that performed the action described in this provenance
		 * action. Null or the empty string removes any name in the builder.
		 * @param service the service name.
		 * @return this builder.
		 */
		public Builder withServiceName(final String service) {
			this.service = Common.processString(service);
			return this;
		}
		
		/** Set the version of the service that performed the action described in this provenance
		 * action. Null or the empty string removes any version in the builder.
		 * @param serviceVersion the service version.
		 * @return this builder.
		 */
		public Builder withServiceVersion(final String serviceVersion) {
			this.serviceVersion = Common.processString(serviceVersion);
			return this;
		}
		
		/** Set the method in the service that performed the action described in this provenance
		 * action. Null or the empty string removes any method in the builder.
		 * @param method the service method.
		 * @return this builder.
		 */
		public Builder withMethod(final String method) {
			this.method = Common.processString(method);
			return this;
		}
		
		/** Set the parameters of the service method that performed the action described in this
		 * provenance action. The parameters may only consist of nulls, numbers, strings, booleans,
		 * {@link Collection}s, and {@link Map}s. {@link Collection}s are converted to
		 * {@link List}s with the ordering as returned by the {@link Collection} iterator.
		 * Any workspace object references in the parameters should also be entered in
		 * {@link #withWorkspaceObjects(List)}. Null or an empty list removes any
		 * parameters in the builder.
		 * @param parameters the method parameters.
		 * @return this builder.
		 */
		public Builder withMethodParameters(final List<Object> parameters) {
			if (parameters == null || parameters.isEmpty()) {
				this.methodParameters = null;
			} else {
				@SuppressWarnings("unchecked")
				final List<Object> copy = (List<Object>) immutableDeepCopy(
						parameters, new JsonDocumentLocation());
				this.methodParameters = copy;
			}
			return this;
		}
		
		private Object immutableDeepCopy(final Object obj, final JsonDocumentLocation loc) {
			if (
					obj == null 
					|| obj instanceof Integer
					|| obj instanceof Long
					|| obj instanceof Float
					|| obj instanceof Double
					|| obj instanceof String
					|| obj instanceof Boolean) {
				return obj;
			} else if (obj instanceof Collection) {
				@SuppressWarnings("unchecked")
				final Collection<Object> inc = (Collection<Object>) obj;
				final List<Object> ret = new LinkedList<>();
				int index = 0;
				for (final Object o: inc) {
					loc.addArrayLocation(index++);
					ret.add(immutableDeepCopy(o, loc));
					loc.removeLast();
				}
				return Collections.unmodifiableList(ret);
			} else if (obj instanceof Map) {
				@SuppressWarnings("unchecked")
				final Map<Object, Object> inc = (Map<Object, Object>) obj;
				final Map<String, Object> ret = new HashMap<>();
				for (final Entry<Object, Object> e: inc.entrySet()) {
					if (!(e.getKey() instanceof String)) {
						throw new IllegalArgumentException(String.format(
								"Non string key in map at %s in method parameters",
								loc.getFullLocationAsString()));
					}
					loc.addMapLocation((String) e.getKey());
					ret.put((String) e.getKey(), immutableDeepCopy(e.getValue(), loc));
					loc.removeLast();
				}
				return Collections.unmodifiableMap(ret);
			} else {
				throw new IllegalArgumentException(String.format(
						"Illegal type at %s in method parameters: %s",
						loc.getFullLocationAsString(), obj.getClass().getSimpleName()));
			}
		}

		/** Set the name of the script that performed the action described in this
		 * provenance action. Null or the empty string removes any name in the builder.
		 * @param script the script name.
		 * @return this builder.
		 */
		public Builder withScript(final String script) {
			this.script = Common.processString(script);
			return this;
		}
		
		/** Set the version of the script that performed the action described in this
		 * provenance action. Null or the empty string removes any version in the builder.
		 * @param scriptVersion the script version.
		 * @return this builder.
		 */
		public Builder withScriptVersion(final String scriptVersion) {
			this.scriptVersion = Common.processString(scriptVersion);
			return this;
		}
		
		/** Set the command line for the script that performed the action described in this
		 * provenance action. Any workspace object references in the command line should also be
		 * entered in {@link #withWorkspaceObjects(List)}. Null or the empty string removes any
		 * command line in the builder.
		 * @param commandLine the script version.
		 * @return this builder.
		 */
		public Builder withCommandLine(final String commandLine) {
			this.commandLine = Common.processString(commandLine);
			return this;
		}
		
		/** Set references for the workspace objects that were used as part of this provenance
		 * action. Reference paths are allowed. Resolved references (UPAs) can also be added in
		 * {@link #withResolvedObjects(List)}. Null or an empty list removes any references
		 * in the builder and also removes the resolved references. If resolved references
		 * are already present, the input list size must be the same as the resolved references
		 * list size. If the list size must change, remove the resolved references first by
		 * passing null to this method or {@link #withResolvedObjects(List)}.
		 * @param references the workspace object references.
		 * @return this builder.
		 */
		public Builder withWorkspaceObjects(final List<String> references) {
			final List<String> wso = processReferences(references, false);
			if (wso == null) {
				this.resolvedObjects = null;
			// it's always ok to unset workspace objects, but it's not ok to set it to a
			// different size than existing resolved objects.
			} else if (
					this.resolvedObjects != null &&
					wso.size() != this.resolvedObjects.size()) {
				throw new IllegalArgumentException("The workspace objects "
						+ "list must be the same size as the resolved objects list");
			}
			this.wsobjs = wso;
			return this;
		}
		
		/** Set the incoming arguments to this provenance action from the prior provenance action.
		 * These arguments will typically be present in the method parameters or script command
		 * lines, and can be compared with the outgoing arguments from the prior provenance action
		 * in a chain of actions to determine how output from one provenance action is fed into the
		 * next. If the input is a workspace argument, there usually is no need to add it here as
		 * well as in the workspace objects. Nulls or empty lists remove any incoming arguments
		 * in the builder.
		 * @param args the incoming arguments.
		 * @return this builder.
		 */
		public Builder withIncomingArgs(final List<String> args) {
			this.incomingArgs = Common.processSimpleStringList(args, "incoming args");
			return this;
		}
		
		/** Set the outgoing arguments from this provenance action to the next provenance action.
		 * These arguments will typically be present in the method parameters or script command
		 * lines of the next provenance action, and can be compared with the incoming arguments in
		 * the next provenance action in a chain of actions to determine how output from one
		 * provenance action is fed into the next. If the output is a workspace argument, there
		 * usually is no need to add it here as well as in the workspace objects. Nulls or
		 * empty lists remove any outgoing arguments in the builder.
		 * @param args the outgoing arguments.
		 * @return this builder.
		 */
		public Builder withOutgoingArgs(final List<String> args) {
			this.outgoingArgs = Common.processSimpleStringList(args, "outgoing args");
			return this;
		}
		
		/** Set information about the external data that was used in this provenance action.
		 * Nulls or empty lists remove any external data in the builder.
		 * @param external the external data.
		 * @return this builder.
		 */
		public Builder withExternalData(final List<ExternalData> external) {
			this.externalData = Common.processSimpleList(external, "external data");
			return this;
		}
		
		/** Set information about any sub actions that were taken as part of this provenance
		 * action. Nulls or empty lists remove any sub actions in the builder.
		 * @param subactions the sub actions.
		 * @return this builder.
		 */
		public Builder withSubActions(final List<SubAction> subactions) {
			this.subActions = Common.processSimpleList(subactions, "subactions");
			return this;
		}
		
		/** Set custom key-value provenance for this provenance action. Null or empty maps
		 * remove any custom provenance in the builder.
		 * @param custom the custom provenance.
		 * @return this builder.
		 */
		public Builder withCustom(final Map<String, String> custom) {
			if (custom == null || custom.isEmpty()) {
				this.custom = null;
			} else if (custom.containsKey(null)) {
				throw new IllegalArgumentException("Null key in custom provenance");
			} else {
				this.custom = Collections.unmodifiableMap(new HashMap<>(custom));
			}
			return this;
		}
		
		/** Set a free-text description for this provenance action. Null or the empty string
		 * removes any description in the builder.
		 * @param description the description.
		 * @return this builder.
		 */
		public Builder withDescription(final String description) {
			this.description = Common.processString(description);
			return this;
		}
		
		/** Set the equivalent resolved workspace object references (e.g. UPAs) for the objects
		 * provided in {@link #withWorkspaceObjects(List)}. Reference paths are not allowed.
		 * Null or the empty list removes any resolved references. If a populated list is provided,
		 * it must be the same size as that previously set by {@link #withWorkspaceObjects(List)}.
		 * Each UPA in the list is the resolved version of the reference at the same index in the
		 * workspace objects list.
		 * @param references the resolved references.
		 * @return this builder.
		 */
		public Builder withResolvedObjects(final List<String> references) {
			final List<String> resobjs = processReferences(references, true);
			// it's always ok to unset resolvedObjects, but it's not ok to set it to a different
			// size than the actual objects
			// TODO CODE could also check that versions are the same if present for each ref
			// don't worry about it for now, handled in other workspace code
			if (
					resobjs != null && (
							this.wsobjs == null ||
							this.wsobjs.size() != resobjs.size())) {
				throw new IllegalArgumentException("The resolved workspace objects "
						+ "list must be the same size as the standard objects list");
			}
			this.resolvedObjects = resobjs;
			return this;
		}
		
		private List<String> processReferences(
				final List<String> references,
				final boolean absolute) {
			if (references == null || references.isEmpty()) {
				return null;
			} else {
				final List<String> ret = new LinkedList<>();
				final ListIterator<String> iter = references.listIterator();
				while (iter.hasNext()) {
					final String next = iter.next();
					try {
						ObjectIdentifier.validateReferencePath(next, absolute);
					} catch (IllegalArgumentException e) {
						throw new IllegalArgumentException(String.format(
								"Invalid %sworkspace object provenenance reference at "
										+ "position %s: %s",
										absolute ? "resolved " : "",
										iter.nextIndex(),
										e.getLocalizedMessage(),
								e));
					}
					ret.add(next.trim());
				}
				return Collections.unmodifiableList(ret);
			}
		}
		
		/** Check the builder has any fields set.
		 * @return true if no fields are set, false otherwise.
		 */
		public boolean isEmpty() {
			return
					time == null &&
					caller == null &&
					service == null &&
					serviceVersion == null &&
					method == null &&
					methodParameters == null &&
					script == null &&
					scriptVersion == null &&
					commandLine == null &&
					wsobjs == null &&
					// if wsobjs is null, resolvedObjects must be null, so no need to test
					incomingArgs == null &&
					outgoingArgs == null &&
					externalData == null &&
					subActions == null &&
					custom == null &&
					description == null;
		}
		
		/** Build the {@link ProvenanceAction}. At least one field must be set before the build
		 * attempt.
		 * @return the provenance action.
		 */
		public ProvenanceAction build() {
			if (isEmpty()) {
				throw new IllegalArgumentException(
						"At least one field in a provenance action must be provided");
			}
			return new ProvenanceAction(time, caller, service, serviceVersion, method,
					methodParameters, script, scriptVersion, commandLine, wsobjs, incomingArgs,
					outgoingArgs, externalData, subActions, custom, description, resolvedObjects);
		}
	}

}
