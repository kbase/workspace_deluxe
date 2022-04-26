package us.kbase.workspace.kbase;

import static us.kbase.common.utils.ServiceUtils.checkAddlArgs;
import static us.kbase.workspace.kbase.KBasePermissions.PERM_NONE;
import static us.kbase.workspace.kbase.KBasePermissions.PERM_READ;
import static us.kbase.workspace.kbase.KBasePermissions.translatePermission;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import us.kbase.common.service.Tuple11;
import us.kbase.common.service.Tuple12;
import us.kbase.common.service.Tuple7;
import us.kbase.common.service.Tuple9;
import us.kbase.common.service.UObject;
import us.kbase.typedobj.idref.IdReferencePermissionHandlerSet;
import us.kbase.typedobj.idref.IdReferencePermissionHandlerSet.IdReferencePermissionHandlerException;
import us.kbase.typedobj.idref.IdReferenceType;
import us.kbase.workspace.ExternalDataUnit;
import us.kbase.workspace.ObjectData;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.Reference;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.provenance.ExternalData;
import us.kbase.workspace.database.provenance.Provenance;
import us.kbase.workspace.database.provenance.ProvenanceAction;
import us.kbase.workspace.database.provenance.SubAction;

public class ArgUtils {
	
	// TODO JAVADOC
	// TODO TEST unit tests
	
	private static Logger getLogger() {
		return LoggerFactory.getLogger(ArgUtils.class);
	}
	
	private final static DateTimeFormatter DATE_PARSER = DateTimeFormatter
			.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS][.SS][.S][XXX][XX]"); // saucy datetimes here
	
	private final static DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
			.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ").withZone(ZoneOffset.UTC);
	
	/** Given a string or epoch millisecond timestamp, return an {@link Instant} created from
	 * the timestamp. Providing both timestamps is an error.
	 * @param timestamp a string typestamp in ISO8601 format, using the Z timezone designator.
	 * @param epochInMilliSec the Linux epoch time in milliseconds.
	 * @param error an error string to use if both timestamps are supplied.
	 * @return the new instant or null if neither argument is provided.
	 */
	public static Instant chooseInstant(
			final String timestamp,
			final Long epochInMilliSec,
			final String error) {
		if (timestamp != null && epochInMilliSec != null) {
			throw new IllegalArgumentException(error);
		}
		if (timestamp != null) {
			try {
				final TemporalAccessor date =  DATE_PARSER.parseBest(
						timestamp, ZonedDateTime::from, LocalDateTime::from);
				if (date instanceof LocalDateTime) {
					throw new IllegalArgumentException(String.format(
							"Date '%s' does not have time zone information", timestamp));
				}
				return Instant.from(date);
			} catch (DateTimeParseException e) {
				throw new IllegalArgumentException("Unparseable date: " + e.getMessage(), e);
			}
		}
		if (epochInMilliSec != null) {
			return Instant.ofEpochMilli(epochInMilliSec);
		}
		return null;
	}
	
	public static Provenance processProvenance(
			final WorkspaceUser user,
			final Instant time,
			final List<us.kbase.workspace.ProvenanceAction> actions) {
		
		final Provenance.Builder p = Provenance.getBuilder(user, time);
		if (actions == null) {
			return p.build();
		}
		final ListIterator<us.kbase.workspace.ProvenanceAction> li = actions.listIterator();
		while (li.hasNext()) {
			final us.kbase.workspace.ProvenanceAction a = li.next();
			try {
				if (a == null) {
					throw new NullPointerException("is null");
				}
				checkAddlArgs(a.getAdditionalProperties(), a.getClass());
				final Instant d = chooseInstant(a.getTime(), a.getEpoch(),
						"Cannot specify both time and epoch in provenance action");
				final ProvenanceAction.Builder pa = ProvenanceAction.getBuilder()
						.withTime(d)
						.withCaller(a.getCaller())
						.withServiceName(a.getService())
						.withServiceVersion(a.getServiceVer())
						.withMethod(a.getMethod())
						.withMethodParameters(translateMethodParametersToObject(
								a.getMethodParams()))
						.withScript(a.getScript())
						.withScriptVersion(a.getScriptVer())
						.withCommandLine(a.getScriptCommandLine())
						.withWorkspaceObjects(a.getInputWsObjects())
						.withIncomingArgs(a.getIntermediateIncoming())
						.withOutgoingArgs(a.getIntermediateOutgoing())
						.withExternalData(processExternalData(a.getExternalData()))
						.withSubActions(processSubActions(a.getSubactions()))
						.withCustom(a.getCustom())
						.withDescription(a.getDescription());
				if (!pa.isEmpty()) { // requiring non-empty PAs broke external code unfortunately
					p.withAction(pa.build());
				}
			} catch (IllegalArgumentException | NullPointerException e) {
				throw new IllegalArgumentException(String.format("Provenance action #%s: %s",
						li.nextIndex(), e.getLocalizedMessage()), e);
			}
		}
		return p.build();
	}
	
	private static List<SubAction> processSubActions(
			List<us.kbase.workspace.SubAction> subactions) {
		final List<SubAction> ret = new LinkedList<SubAction>();
		if (subactions == null) {
			return ret;
		}
		final ListIterator<us.kbase.workspace.SubAction> si = subactions.listIterator();
		while (si.hasNext()) {
			final us.kbase.workspace.SubAction sa = si.next();
			try {
				if (sa == null) {
					throw new NullPointerException("is null");
				}
				checkAddlArgs(sa.getAdditionalProperties(), sa.getClass());
				ret.add(SubAction.getBuilder()
						.withCodeURL(sa.getCodeUrl())
						.withCommit(sa.getCommit())
						.withEndpointURL(sa.getEndpointUrl())
						.withName(sa.getName())
						.withVersion(sa.getVer())
						.build()
						);
			} catch (IllegalArgumentException | NullPointerException e) {
				throw new IllegalArgumentException(String.format("Sub action #%s: %s",
						si.nextIndex(), e.getLocalizedMessage()), e);
			}
			
		}
		return ret;
	}

	private static List<ExternalData> processExternalData(
			final List<ExternalDataUnit> externalData) {
		final List<ExternalData> ret = new LinkedList<>();
		if (externalData == null) {
			return ret;
		}
		final ListIterator<ExternalDataUnit> ei = externalData.listIterator();
		while (ei.hasNext()) {
			final ExternalDataUnit edu = ei.next();
			try {
				if (edu == null) {
					throw new NullPointerException("is null");
				}
				final Instant d = chooseInstant(edu.getResourceReleaseDate(),
						edu.getResourceReleaseEpoch(),
						"Cannot specify both time and epoch in external data unit");
				checkAddlArgs(edu.getAdditionalProperties(), edu.getClass());
				ret.add(ExternalData.getBuilder()
						.withDataID(edu.getDataId())
						.withDataURL(edu.getDataUrl())
						.withDescription(edu.getDescription())
						.withResourceName(edu.getResourceName())
						.withResourceReleaseDate(d)
						.withResourceURL(edu.getResourceUrl())
						.withResourceVersion(edu.getResourceVersion())
						.build()
				);
			} catch (IllegalArgumentException | NullPointerException e) {
				throw new IllegalArgumentException(String.format("External data unit #%s: %s",
						ei.nextIndex(), e.getLocalizedMessage()), e);
			}
		}
		return ret;
	}

	// TODO CODE remove this eventually when everything uses Instants
	private static String formatDate(final Date date) {
		return formatDate(date.toInstant());
	}
	
	private static String formatDate(final Instant date) {
		return DATE_FORMATTER.format(date);
	}
	
	private static String formatDate(final Optional<Instant> date) {
		return date.map(d -> DATE_FORMATTER.format(d)).orElse(null);
	}
	
	private static List<Object> translateMethodParametersToObject(
			final List<UObject> methodParams) {
		if (methodParams == null) {
			return null;
		}
		final List<Object> params = new LinkedList<Object>();
		for (final UObject uo: methodParams) {
			params.add(uo.asInstance());
		}
		return params;
	}
	

	private static List<UObject> translateMethodParametersToUObject(
			final List<Object> methodParams) {
		return methodParams.stream().map(o -> new UObject(o)).collect(Collectors.toList());
	}
	
	public static List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>>
			wsInfoToTuple (final List<WorkspaceInformation> info) {
		final List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>> ret =
				new LinkedList<Tuple9<Long, String, String, String, Long, String,
						String, String, Map<String, String>>>();
		for (final WorkspaceInformation wi: info) {
			ret.add(wsInfoToTuple(wi));
		}
		return ret;
	}

	public static Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>
			wsInfoToTuple(final WorkspaceInformation info)  {
		return new Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>()
				.withE1(info.getId())
				.withE2(info.getName())
				.withE3(info.getOwner().getUser())
				.withE4(formatDate(info.getModDate()))
				.withE5(info.getMaximumObjectID())
				.withE6(translatePermission(info.getUserPermission())) 
				.withE7(translatePermission(info.isGloballyReadable()))
				.withE8(info.getLockState())
				.withE9(info.getUserMeta().getMetadata());
	}
	
	public static List<Tuple7<String, String, String, Long, String, String, Long>> wsInfoToMetaTuple(
			List<WorkspaceInformation> info) {
		final List<Tuple7<String, String, String, Long, String, String, Long>> ret =
				new LinkedList<Tuple7<String, String, String, Long, String,
				String, Long>>();
		for (final WorkspaceInformation wi: info) {
			ret.add(wsInfoToMetaTuple(wi));
		}
		return ret;
	}
	
	public static Tuple7<String, String, String, Long, String, String, Long>
				wsInfoToMetaTuple(final WorkspaceInformation info) {
		return new Tuple7<String, String, String, Long, String, String, Long>()
				.withE7(info.getId())
				.withE1(info.getName())
				.withE2(info.getOwner().getUser())
				.withE3(formatDate(info.getModDate()))
				.withE4(info.getMaximumObjectID())
				.withE5(translatePermission(info.getUserPermission()))
				.withE6(translatePermission(info.isGloballyReadable()));
	}
	
	public static Tuple11<Long, String, String, String, Long, String,
			Long, String, String, Long, Map<String, String>>
			objInfoToTuple(
					final ObjectInformation info,
					final boolean logObjects) {
		final List<ObjectInformation> m = new ArrayList<ObjectInformation>();
		m.add(info);
		return objInfoToTuple(m, logObjects).get(0);
	}

	public static List<List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>>>
			translateObjectInfoList(
					final List<Set<ObjectInformation>> lsoi,
					final boolean logObjects) {
		final List<List<Tuple11<Long, String, String, String, Long, String,
				Long, String, String, Long, Map<String, String>>>> ret = 
				new LinkedList<List<Tuple11<Long,String,String,String,Long,String,Long,String,String,Long,Map<String,String>>>>();
		for (Set<ObjectInformation> soi: lsoi) {
			ret.add(objInfoToTuple(new LinkedList<ObjectInformation>(soi),
					logObjects));
		}
		return ret;
	}
	
	public static List<Tuple11<Long, String, String, String, Long, String,
			Long, String, String, Long, Map<String, String>>>
			objInfoToTuple(
					final List<ObjectInformation> info,
					final boolean logObjects) {

		//oh the humanity
		final List<Tuple11<Long, String, String, String, Long, String,
			Long, String, String, Long, Map<String, String>>> ret = 
			new ArrayList<Tuple11<Long, String, String, String, Long,
			String, Long, String, String, Long, Map<String, String>>>();
		for (ObjectInformation m: info) {
			if (m == null) {
				ret.add(null);
			} else {
				if (logObjects) {
					getLogger().info("Object {}/{}/{} {}", m.getWorkspaceId(),
							m.getObjectId(), m.getVersion(),
							m.getTypeString());
				}
				ret.add(new Tuple11<Long, String, String, String, Long,
						String, Long, String, String, Long, Map<String, String>>()
						.withE1(m.getObjectId())
						.withE2(m.getObjectName())
						.withE3(m.getTypeString())
						.withE4(formatDate(m.getSavedDate()))
						.withE5(Long.valueOf(m.getVersion()))
						.withE6(m.getSavedBy().getUser())
						.withE7(m.getWorkspaceId())
						.withE8(m.getWorkspaceName())
						.withE9(m.getCheckSum())
						.withE10(m.getSize())
						.withE11(m.getUserMetaData() == null ? null :
							m.getUserMetaData().getMetadata()));
			}
		}
		return ret;
	}
	
	
	public static Tuple12<String, String, String, Long, String, String, String,
			String, String, String, Map<String, String>, Long>
			objInfoToMetaTuple(
					final ObjectInformation info,
					final boolean logObjects) {
		final List<ObjectInformation> m = new ArrayList<ObjectInformation>();
		m.add(info);
		return objInfoToMetaTuple(m, logObjects).get(0);
	}
	
	public static List<Tuple12<String, String, String, Long, String, String, String,
			String, String, String, Map<String, String>, Long>>
			objInfoToMetaTuple(
					final List<ObjectInformation> info,
					final boolean logObjects) {
		//oh the humanity
		final List<Tuple12<String, String, String, Long, String, String, String,
		String, String, String, Map<String, String>, Long>> ret = 
		new ArrayList<Tuple12<String, String, String, Long, String, String,
		String, String, String, String, Map<String, String>, Long>>();
		
		for (ObjectInformation m: info) {
			if (logObjects) {
				getLogger().info("Object {}/{}/{} {}", m.getWorkspaceId(),
						m.getObjectId(), m.getVersion(), m.getTypeString());
			}
			ret.add(new Tuple12<String, String, String, Long, String, String, String,
					String, String, String, Map<String, String>, Long>()
					.withE1(m.getObjectName())
					.withE2(m.getTypeString())
					.withE3(formatDate(m.getSavedDate()))
					.withE4(Long.valueOf(m.getVersion()))
					.withE5("") //command is deprecated
					.withE6(m.getSavedBy().getUser())
					.withE7(m.getSavedBy().getUser()) //owner is deprecated
					.withE8(m.getWorkspaceName())
					.withE9("")//ref is deprecated
					.withE10(m.getCheckSum())
					.withE11(m.getUserMetaData() == null ? null : 
						m.getUserMetaData().getMetadata())
					.withE12(m.getObjectId()));
		}
		return ret;
	}
	
	/** Translate object data returned from the workspace to JSONRPC API object data.
	 * @param objects the objects to convert.
	 * @param permHandler any permissions handlers to invoke based on the contents of the object.
	 * If not present, no permissions are updated.
	 * @param batchExternalUpdates if true, send all external system updates in a batch to each
	 * external system when possible rather than object by object. This can potentially
	 * speed up the updates, but the drawback is that if the external update fails for any object,
	 * all the objects that required updates for that system will be marked as having a failed
	 * update. Has no effect if the permissions handler is not present.
	 * @param logObjects if true, log the object ref and type.
	 * @return the translated objects.
	 */
	public static List<ObjectData> translateObjectData(
			final List<WorkspaceObjectData> objects, 
			final Optional<IdReferencePermissionHandlerSet> permHandler,
			final boolean batchExternalUpdates,
			final boolean logObjects) {
		final List<ObjectData> ret = new ArrayList<ObjectData>();
		Map<WorkspaceObjectData, PermError> errs = null;
		if (batchExternalUpdates) {
			errs = makeExternalIDsReadable(objects, permHandler);
		}
		for (final WorkspaceObjectData o: objects) {
			if (o == null) {
				ret.add(null);
				continue;
			}
			if (!batchExternalUpdates) {
				errs = makeExternalIDsReadable(Arrays.asList(o), permHandler);
			}
			final UObject data;
			try {
				data = o.hasData() ? o.getSerializedData().get().getUObject() : null;
			} catch (IOException e) {
				// impossible to test in integration tests, shouldn't occur
				throw new RuntimeException(
						"An unexpected error occurred: " + e.getLocalizedMessage(), e);
			}
			ret.add(new ObjectData()
					.withData(data)
					.withInfo(objInfoToTuple(o.getObjectInfo(), logObjects))
					.withPath(toObjectPath(o.getObjectInfo().getReferencePath()))
					.withProvenance(translateProvenanceActions(o.getProvenance().getActions()))
					.withCreator(o.getProvenance().getUser().getUser())
					.withOrigWsid(o.getProvenance().getWorkspaceID().orElse(null))
					.withCreated(formatDate(o.getProvenance().getDate()))
					.withEpoch(o.getProvenance().getDate().toEpochMilli())
					.withRefs(o.getReferences())
					.withCopied(o.getCopyReference().isPresent() ?
							o.getCopyReference().get().getId() : null)
					.withCopySourceInaccessible(o.isCopySourceInaccessible() ? 1L: 0L)
					.withExtractedIds(toRawExternalIDs(o.getExtractedIds()))
					.withHandleError(errs.get(o).error)
					.withHandleStacktrace(errs.get(o).stackTrace));
		}
		return ret;
	}
	
	private static Map<String, List<String>> toRawExternalIDs(
			final Map<IdReferenceType, List<String>> extractedIds) {
		return extractedIds.keySet().stream().collect(Collectors.toMap(
				k -> k.getType(),
				k -> extractedIds.get(k)));
	}

	public static List<List<String>> toObjectPaths(final List<ObjectInformation> ois) {
		final List<List<String>> ret = new LinkedList<>();
		for (final ObjectInformation oi: ois) {
			if (oi == null) {
				ret.add(null);
			} else {
				ret.add(toObjectPath(oi.getReferencePath()));
			}
		}
		return ret;
	}
	
	private static List<String> toObjectPath(final List<Reference> referencePath) {
		final List<String> ret = new LinkedList<>();
		for (final Reference r: referencePath) {
			ret.add(r.getId());
		}
		return ret;
	}

	@SuppressWarnings("deprecation")
	public static List<us.kbase.workspace.ObjectProvenanceInfo> translateObjectProvInfo(
			final List<WorkspaceObjectData> objects,
			final IdReferencePermissionHandlerSet permHandler,
			final boolean logObjects) {
		final List<us.kbase.workspace.ObjectProvenanceInfo> ret =
				new ArrayList<us.kbase.workspace.ObjectProvenanceInfo>();
		for (final WorkspaceObjectData o: objects) {
			final PermError error = makeExternalIDsReadable(
					Arrays.asList(o), Optional.of(permHandler)).get(o);
			ret.add(new us.kbase.workspace.ObjectProvenanceInfo()
					.withInfo(objInfoToTuple(o.getObjectInfo(), logObjects))
					.withProvenance(translateProvenanceActions(o.getProvenance().getActions()))
					.withCreator(o.getProvenance().getUser().getUser())
					.withOrigWsid(o.getProvenance().getWorkspaceID().orElse(null))
					.withCreated(formatDate(o.getProvenance().getDate()))
					.withEpoch(o.getProvenance().getDate().toEpochMilli())
					.withRefs(o.getReferences())
					.withCopied(o.getCopyReference().isPresent() ?
							o.getCopyReference().get().getId() : null)
					.withCopySourceInaccessible(o.isCopySourceInaccessible() ? 1L: 0L)
					.withExtractedIds(toRawExternalIDs(o.getExtractedIds()))
					.withHandleError(error.error)
					.withHandleStacktrace(error.stackTrace));
		}
		return ret;
	}
	
	private static class PermError {
		
		public String error;
		public String stackTrace;

		public PermError(String error, String stackTrace) {
			super();
			this.error = error;
			this.stackTrace = stackTrace;
		}
	}

	private static final PermError NULL_ERR = new PermError(null, null);
	
	private static Map<WorkspaceObjectData, PermError> makeExternalIDsReadable(
			final List<WorkspaceObjectData> objects,
			final Optional<IdReferencePermissionHandlerSet> permhandler) {
		/* External services are generally going to fail quickly if setting an ACL fails,
		 * since there's almost certainly something very wrong - this is all admin stuff and 
		 * so regular failures shouldn't be an issue. As such, we just assign any errors
		 * to all objects that were part of the call, as it's not clear which objects were
		 * processed and which failed.
		 * The alternative is to have all external services return exactly which IDs failed and
		 * which succeeded, which means failing slowly and trying all IDs, which in most cases
		 * is just going to waste time, because, again, there's something likely very wrong.
		 * 
		 * One exception is that nodes for handles can be deleted by the owner, unlike samples
		 * or bytestream nodes. However, in KBase this should never happen so we don't consider
		 * it here.
		 */
		final Map<WorkspaceObjectData, PermError> ret = objects.stream().filter(o -> o != null)
				.collect(Collectors.toMap(o -> o, o -> NULL_ERR));
		if (permhandler.isPresent()) {
			// This section could probably be more efficient, but there's very few types
			// and it's going to be dominated by the network connections so meh
			final Set<IdReferenceType> types = objects.stream().filter(o -> o != null)
					.flatMap(o -> o.getExtractedIds().keySet().stream())
					.collect(Collectors.toCollection(() -> new TreeSet<>()));
			for (final IdReferenceType t: types) {
				final Set<String> ids = new HashSet<>();
				final List<WorkspaceObjectData> objs = new LinkedList<>();
				for (final WorkspaceObjectData o: objects) {
					// getExtractedIds never returns an empty list
					if (o != null && o.getExtractedIds().get(t) != null) {
						ids.addAll(o.getExtractedIds().get(t));
						objs.add(o);
					}
				}
				try {
					/* Each object has a max of 100K refs and there's a max of 10K objects per
					 * get_objects2 call. That means that theoretically we could be sending 1B
					 * ids here. However, in practice most object have very few refs so don't
					 * worry about it for now. If needed later add a loop or throw an error if
					 * there's too many IDs.
					 */
					permhandler.get().addReadPermission(t, ids);
				} catch (IdReferencePermissionHandlerException e) {
					final PermError err = new PermError(
							e.getMessage(), ExceptionUtils.getStackTrace(e));
					objs.stream().forEach(o -> ret.put(o, err));
				}
			}
		}
		return ret;
	}

	private static List<us.kbase.workspace.ProvenanceAction> translateProvenanceActions(
			final List<ProvenanceAction> actions) {
		final List<us.kbase.workspace.ProvenanceAction> pas = new LinkedList<>();
		for (final ProvenanceAction a: actions) {
			final Optional<Instant> d = a.getTime();
			pas.add(new us.kbase.workspace.ProvenanceAction()
					.withTime(formatDate(d))
					.withEpoch(d.map(t -> t.toEpochMilli()).orElse(null))
					.withCaller(a.getCaller().orElse(null))
					.withService(a.getServiceName().orElse(null))
					.withServiceVer(a.getServiceVersion().orElse(null))
					.withMethod(a.getMethod().orElse(null))
					.withMethodParams(translateMethodParametersToUObject(
							a.getMethodParameters()))
					.withScript(a.getScript().orElse(null))
					.withScriptVer(a.getScriptVersion().orElse(null))
					.withScriptCommandLine(a.getCommandLine().orElse(null))
					.withInputWsObjects(a.getWorkspaceObjects())
					.withResolvedWsObjects(a.getResolvedObjects())
					.withIntermediateIncoming(a.getIncomingArgs())
					.withIntermediateOutgoing(a.getOutgoingArgs())
					.withExternalData(
							translateExternalDataUnits(a.getExternalData()))
					.withCustom(a.getCustom())
					.withSubactions(translateSubActions(a.getSubActions()))
					.withDescription(a.getDescription().orElse(null))
					);
		}
		return pas;
	}
	
	private static List<us.kbase.workspace.SubAction> translateSubActions(
			List<SubAction> subActions) {
		final List<us.kbase.workspace.SubAction> ret = new LinkedList<>();
		for (final SubAction sa: subActions) {
			ret.add(new us.kbase.workspace.SubAction()
					.withCodeUrl(sa.getCodeURL().map(u -> u.toString()).orElse(null))
					.withCommit(sa.getCommit().orElse(null))
					.withEndpointUrl(sa.getEndpointURL().map(u -> u.toString()).orElse(null))
					.withName(sa.getName().orElse(null))
					.withVer(sa.getVersion().orElse(null))
					);
		}
		return ret;
	}

	private static List<ExternalDataUnit> translateExternalDataUnits(
			List<ExternalData> externalData) {
		final List<ExternalDataUnit> ret = new LinkedList<>();
		for (final ExternalData ed: externalData) {
			final Optional<Instant> d = ed.getResourceReleaseDate();
			ret.add(new ExternalDataUnit()
					.withDataId(ed.getDataID().orElse(null))
					.withDataUrl(ed.getDataURL().map(u -> u.toString()).orElse(null))
					.withDescription(ed.getDescription().orElse(null))
					.withResourceName(ed.getResourceName().orElse(null))
					.withResourceReleaseDate(formatDate(d))
					.withResourceReleaseEpoch(d.map(t -> t.toEpochMilli()).orElse(null))
					.withResourceUrl(ed.getResourceURL().map(u -> u.toString()).orElse(null))
					.withResourceVersion(ed.getResourceVersion().orElse(null))
					);
		}
		return ret;
	}

	public static boolean longToBoolean(final Long b) {
		return longToBoolean(b, false);
	}
	
	public static boolean longToBoolean(final Long b, final boolean default_) {
		if (b == null) {
			return default_;
		}
		return b != 0;
	}
	
	public static int longToInt(
			final Long l,
			final String name,
			final int default_) {
		if (l == null) {
			return default_;
		}
		if (l > Integer.MAX_VALUE) {
				throw new IllegalArgumentException(
						name + " can be no greater than " + Integer.MAX_VALUE);
		}
		return Long.valueOf(l).intValue();
	}
	
	public static long checkLong(
			final Long l,
			final long default_) {
		if (l == null) {
			return default_;
		}
		return l;
	}
	
	public static Permission getGlobalWSPerm(final String globalRead) {
		Permission p = Permission.NONE;
		if (globalRead != null) {
			if (!globalRead.equals(PERM_READ) && 
					!globalRead.equals(PERM_NONE)) {
				throw new IllegalArgumentException(String.format(
						"globalread must be %s or %s", PERM_NONE, PERM_READ));
			}
			p = translatePermission(globalRead);
		}
		return p;
	}
}
