package us.kbase.workspace.kbase;

import static us.kbase.common.utils.ServiceUtils.checkAddlArgs;
import static us.kbase.workspace.kbase.KBasePermissions.PERM_NONE;
import static us.kbase.workspace.kbase.KBasePermissions.PERM_READ;
import static us.kbase.workspace.kbase.KBasePermissions.translatePermission;

import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;

import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.Tuple11;
import us.kbase.common.service.Tuple12;
import us.kbase.common.service.Tuple7;
import us.kbase.common.service.Tuple9;
import us.kbase.common.service.UObject;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.handlemngr.HandleMngrClient;
import us.kbase.auth.AuthToken;
import us.kbase.workspace.ExternalDataUnit;
import us.kbase.workspace.ObjectData;
import us.kbase.workspace.ProvenanceAction;
import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.Provenance.ExternalData;
import us.kbase.workspace.database.Provenance.SubAction;
import us.kbase.workspace.database.Reference;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.WorkspaceUser;

/**
 * @author gaprice@lbl.gov
 *
 */
public class ArgUtils {
	
	private static Logger getLogger() {
		return LoggerFactory.getLogger(ArgUtils.class);
	}
	
	private final static DateTimeFormatter DATE_PARSER =
			new DateTimeFormatterBuilder()
				.append(DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss"))
				.appendOptional(DateTimeFormat.forPattern(".SSS").getParser())
				.append(DateTimeFormat.forPattern("Z"))
				.toFormatter();
	
	private final static DateTimeFormatter DATE_FORMATTER =
			DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssZ").withZoneUTC();
	
	public static Date chooseDate(
			final String timestamp,
			final Long epochInMilliSec,
			final String error)
			throws ParseException {
		if (timestamp != null && epochInMilliSec != null) {
			throw new IllegalArgumentException(error);
		}
		if (timestamp != null) {
			return parseDate(timestamp);
		}
		if (epochInMilliSec != null) {
			return new Date(epochInMilliSec);
		}
		return null;
	}
	
	public static Provenance processProvenance(final WorkspaceUser user,
			final List<ProvenanceAction> actions) throws ParseException {
		
		final Provenance p = new Provenance(user);
		if (actions == null) {
			return p;
		}
		for (final ProvenanceAction a: actions) {
			checkAddlArgs(a.getAdditionalProperties(), a.getClass());
			final Date d = chooseDate(a.getTime(), a.getEpoch(),
					"Cannot specify both time and epoch in provenance " +
							"action");
			p.addAction(new Provenance.ProvenanceAction()
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
					.withDescription(a.getDescription())
			);
		}
		return p;
	}
	
	private static List<SubAction> processSubActions(
			List<us.kbase.workspace.SubAction> subactions) {
		final List<SubAction> ret = new LinkedList<SubAction>();
		if (subactions == null) {
			return ret;
		}
		for (final us.kbase.workspace.SubAction sa: subactions) {
			checkAddlArgs(sa.getAdditionalProperties(), sa.getClass());
			ret.add(new SubAction()
					.withCodeUrl(sa.getCodeUrl())
					.withCommit(sa.getCommit())
					.withEndpointUrl(sa.getEndpointUrl())
					.withName(sa.getName())
					.withVer(sa.getVer())
					);
		}
		return ret;
	}

	private static List<ExternalData> processExternalData(
			final List<ExternalDataUnit> externalData) throws ParseException {
		final List<ExternalData> ret = new LinkedList<ExternalData>();
		if (externalData == null) {
			return ret;
		}
		for (final ExternalDataUnit edu: externalData) {
			final Date d = chooseDate(edu.getResourceReleaseDate(),
					edu.getResourceReleaseEpoch(),
					"Cannot specify both time and epoch in external " +
							"data unit");
			checkAddlArgs(edu.getAdditionalProperties(), edu.getClass());
			ret.add(new ExternalData()
					.withDataId(edu.getDataId())
					.withDataUrl(edu.getDataUrl())
					.withDescription(edu.getDescription())
					.withResourceName(edu.getResourceName())
					.withResourceReleaseDate(d)
					.withResourceUrl(edu.getResourceUrl())
					.withResourceVersion(edu.getResourceVersion())
			);
		}
		return ret;
	}

	private static Date parseDate(final String date) throws ParseException {
		if (date == null) {
			return null;
		}
		try {
			return DATE_PARSER.parseDateTime(date).toDate();
		} catch (IllegalArgumentException iae) {
			throw new IllegalArgumentException("Unparseable date: " +
					iae.getMessage());
		}
	}
	
	private static String formatDate(final Date date) {
		if (date == null) {
			return null;
		}
		return DATE_FORMATTER.print(new DateTime(date));
	}
	
	private static String formatDate(final Instant date) {
		if (date == null) {
			return null;
		}
		return formatDate(Date.from(date));
		
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
		if (methodParams == null) {
			return null;
		}
		final List<UObject> params = new LinkedList<UObject>();
		for (final Object uo: methodParams) {
			params.add(new UObject(uo));
		}
		return params;
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
						.withE5(new Long(m.getVersion()))
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
					.withE4(new Long(m.getVersion()))
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
	
	public static List<ObjectData> translateObjectData(
			final List<WorkspaceObjectData> objects, 
			final WorkspaceUser user,
			final URL handleManagerURl,
			final AuthToken handleManagertoken,
			final boolean logObjects)
			throws JsonParseException, IOException {
		final List<ObjectData> ret = new ArrayList<ObjectData>();
		for (final WorkspaceObjectData o: objects) {
			if (o == null) {
				ret.add(null);
				continue;
			}
			final HandleError error = makeHandlesReadable(
					o, user, handleManagerURl, handleManagertoken);
			final ByteArrayFileCache resource = o.getSerializedData();
			ret.add(new ObjectData()
					.withData(resource == null ? null : resource.getUObject())
					.withInfo(objInfoToTuple(o.getObjectInfo(), logObjects))
					.withPath(toObjectPath(o.getObjectInfo().getReferencePath()))
					.withProvenance(translateProvenanceActions(
							o.getProvenance().getActions()))
					.withCreator(o.getProvenance().getUser().getUser())
					.withOrigWsid(o.getProvenance().getWorkspaceID())
					.withCreated(formatDate(
							o.getProvenance().getDate()))
					.withEpoch(o.getProvenance().getDate().getTime())
					.withRefs(o.getReferences())
					.withCopied(o.getCopyReference() == null ? null :
						o.getCopyReference().getId())
					.withCopySourceInaccessible(
							o.isCopySourceInaccessible() ? 1L: 0L)
					.withExtractedIds(o.getExtractedIds())
					.withHandleError(error.error)
					.withHandleStacktrace(error.stackTrace));
		}
		return ret;
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
			final WorkspaceUser user,
			final URL handleManagerURl,
			final AuthToken handleManagertoken,
			final boolean logObjects) {
		final List<us.kbase.workspace.ObjectProvenanceInfo> ret =
				new ArrayList<us.kbase.workspace.ObjectProvenanceInfo>();
		for (final WorkspaceObjectData o: objects) {
			final HandleError error = makeHandlesReadable(
					o, user, handleManagerURl, handleManagertoken);
			ret.add(new us.kbase.workspace.ObjectProvenanceInfo()
					.withInfo(objInfoToTuple(o.getObjectInfo(), logObjects))
					.withProvenance(translateProvenanceActions(
							o.getProvenance().getActions()))
					.withCreator(o.getProvenance().getUser().getUser())
					.withOrigWsid(o.getProvenance().getWorkspaceID())
					.withCreated(formatDate(
							o.getProvenance().getDate()))
					.withEpoch(o.getProvenance().getDate().getTime())
					.withRefs(o.getReferences())
					.withCopied(o.getCopyReference() == null ? null :
						o.getCopyReference().getId())
					.withCopySourceInaccessible(
						o.isCopySourceInaccessible() ? 1L: 0L)
					.withExtractedIds(o.getExtractedIds())
					.withHandleError(error.error)
					.withHandleStacktrace(error.stackTrace));
		}
		return ret;
	}
	
	private static class HandleError {
		
		public String error;
		public String stackTrace;

		public HandleError(String error, String stackTrace) {
			super();
			this.error = error;
			this.stackTrace = stackTrace;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("HandleError [error=");
			builder.append(error);
			builder.append(", stackTrace=");
			builder.append(stackTrace);
			builder.append("]");
			return builder.toString();
		}
		
	}

	private static HandleError makeHandlesReadable(
			final WorkspaceObjectData o,
			final WorkspaceUser user,
			final URL handleManagerURL,
			final AuthToken handleManagertoken) {
		final List<String> handles = o.getExtractedIds().get(
				HandleIdHandlerFactory.type.getType());
		if (handles == null || handles.isEmpty()) {
			return new HandleError(null, null);
		}
		final HandleMngrClient hmc;
		try {
			hmc = new HandleMngrClient(handleManagerURL, handleManagertoken);
			if (handleManagerURL.getProtocol().equals("http")) {
				hmc.setIsInsecureHttpConnectionAllowed(true);
			}
		} catch (UnauthorizedException e) {
			return new HandleError(
					"Unable to contact the Handle Manager - " +
							"the Workspace credentials were rejected: " +
							e.getMessage(),
					ExceptionUtils.getStackTrace(e));
		} catch (IOException e) {
			return new HandleError(
					"Unable to contact the Handle Manager - IO exception " +
							"attempting to validate credentials with the " +
							"Auth Service: " + e.getMessage(),
					ExceptionUtils.getStackTrace(e));
		}
		try {
			if (user == null) {
				hmc.setPublicRead(handles);
			} else {
				hmc.addReadAcl(handles, user.getUser());
			}
		} catch (IOException e) {
			return new HandleError(
					"There was an IO problem while attempting to set " +
							"Handle ACLs: " + e.getMessage(),
					ExceptionUtils.getStackTrace(e));
		} catch (UnauthorizedException e) {
			return new HandleError(
					"Unable to contact the Handle Manager - " +
							"the Workspace credentials were rejected: " +
							e.getMessage(),
					ExceptionUtils.getStackTrace(e));
		} catch (ServerException e) {
			return new HandleError(
					"The Handle Manager reported a problem while attempting " +
							"to set Handle ACLs: " + e.getMessage(),
					ExceptionUtils.getStackTrace(e));
		} catch (JsonClientException e) {
			return new HandleError(
					"There was an unexpected problem while contacting the " +
							"Handle Manager to set Handle ACLs: " +
							e.getMessage(),
					ExceptionUtils.getStackTrace(e));
		}
		return new HandleError(null, null);
	}

	private static List<ProvenanceAction> translateProvenanceActions(
			final List<Provenance.ProvenanceAction> actions) {
		final List<ProvenanceAction> pas = new LinkedList<ProvenanceAction>();
		for (final Provenance.ProvenanceAction a: actions) {
			final Date d = a.getTime();
			pas.add(new ProvenanceAction()
					.withTime(formatDate(d))
					.withEpoch(d == null ? null : d.getTime())
					.withCaller(a.getCaller())
					.withService(a.getServiceName())
					.withServiceVer(a.getServiceVersion())
					.withMethod(a.getMethod())
					.withMethodParams(translateMethodParametersToUObject(
							a.getMethodParameters()))
					.withScript(a.getScript())
					.withScriptVer(a.getScriptVersion())
					.withScriptCommandLine(a.getCommandLine())
					.withInputWsObjects(a.getWorkspaceObjects())
					.withResolvedWsObjects(a.getResolvedObjects())
					.withIntermediateIncoming(a.getIncomingArgs())
					.withIntermediateOutgoing(a.getOutgoingArgs())
					.withExternalData(
							translateExternalDataUnits(a.getExternalData()))
					.withCustom(a.getCustom())
					.withSubactions(translateSubActions(a.getSubActions()))
					.withDescription(a.getDescription())
					);
		}
		return pas;
	}
	
	private static List<us.kbase.workspace.SubAction> translateSubActions(
			List<SubAction> subActions) {
		final List<us.kbase.workspace.SubAction> ret =
				new LinkedList<us.kbase.workspace.SubAction>();
		if (subActions == null) {
			return ret;
		}
		for (final SubAction sa: subActions) {
			ret.add(new us.kbase.workspace.SubAction()
					.withCodeUrl(sa.getCodeUrl())
					.withCommit(sa.getCommit())
					.withEndpointUrl(sa.getEndpointUrl())
					.withName(sa.getName())
					.withVer(sa.getVer())
					);
		}
		return ret;
	}

	private static List<ExternalDataUnit> translateExternalDataUnits(
			List<ExternalData> externalData) {
		final List<ExternalDataUnit> ret = new LinkedList<ExternalDataUnit>();
		if (externalData == null) {
			return ret; //this should never happen, but just in case
		}
		for (final ExternalData ed: externalData) {
			final Date d = ed.getResourceReleaseDate();
			ret.add(new ExternalDataUnit()
					.withDataId(ed.getDataId())
					.withDataUrl(ed.getDataUrl())
					.withDescription(ed.getDescription())
					.withResourceName(ed.getResourceName())
					.withResourceReleaseDate(formatDate(d))
					.withResourceReleaseEpoch(d == null ? null : d.getTime())
					.withResourceUrl(ed.getResourceUrl())
					.withResourceVersion(ed.getResourceVersion())
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
		return new Long(l).intValue();
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
