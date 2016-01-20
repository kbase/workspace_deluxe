package us.kbase.workspace.kbase;

import static us.kbase.common.utils.ServiceUtils.checkAddlArgs;
import static us.kbase.workspace.database.Util.xorNameId;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import us.kbase.typedobj.core.ObjectPaths;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.SubObjectIdentity;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.SubObjectIdentifier;
import us.kbase.workspace.database.WorkspaceIdentifier;

public class KBaseIdentifierFactory {
	
	//TODO unit tests
	
	private static final Pattern KB_WS_ID = Pattern.compile("kb\\|ws\\.(\\d+)");
	private static final Pattern KB_OBJ_ID = Pattern.compile(
			"kb\\|ws\\.(\\d+)\\.obj\\.(\\d+)(?:\\.ver\\.(\\d+))?");

	public static WorkspaceIdentifier processWorkspaceIdentifier(
			final WorkspaceIdentity wsi) {
		if (wsi == null) {
			throw new IllegalArgumentException(
					"WorkspaceIdentifier cannot be null");
		}
		checkAddlArgs(wsi.getAdditionalProperties(), wsi.getClass());
		return processWorkspaceIdentifier(wsi.getWorkspace(), wsi.getId());
	}
	
	public static WorkspaceIdentifier processWorkspaceIdentifier(
			final String workspace, final Long id) {
		xorNameId(workspace, id, "workspace");
		if (id != null) {
			return new WorkspaceIdentifier(id);
		}
		Matcher m = KB_WS_ID.matcher(workspace);
		if (m.find()) {
			return new WorkspaceIdentifier(new Long(m.group(1)));
		}
		return new WorkspaceIdentifier(workspace);
	}
	
	private static void verifyRefOnly(final ObjectIdentity oi) {
		if (oi.getWorkspace() != null || oi.getWsid() != null 
				|| oi.getName() != null || oi.getObjid() != null ||
				oi.getVer() != null) {
			final List<String> err = new ArrayList<String>(4);
			if (oi.getWorkspace() != null) {
				err.add("Workspace: " + oi.getWorkspace());
			}
			if (oi.getWsid() != null) {
				err.add("Workspace id: " + oi.getWsid());
			}
			if (oi.getName() != null) {
				err.add("Object name: " + oi.getName());
			}
			if (oi.getObjid() != null) {
				err.add("Object id: " + oi.getObjid());
			}
			if (oi.getVer() != null) {
				err.add("Version: " + oi.getVer());
			}
			throw new IllegalArgumentException(String.format(
					"Object reference %s provided; cannot provide any other means of identifying an object. %s",
					oi.getRef(), StringUtils.join(err, " ")));
		}
	}
	
	public static List<ObjectIdentifier> processObjectIdentifiers(
			List<ObjectIdentity> objectIDs) {
		if (objectIDs == null) {
			throw new IllegalArgumentException(
					"The object identifier list cannot be null");
		}
		if (objectIDs.isEmpty()) {
			throw new IllegalArgumentException("No object identifiers provided");
		}
		final List<ObjectIdentifier> loi = new ArrayList<ObjectIdentifier>();
		int objcount = 1;
		for (ObjectIdentity oi: objectIDs) {
			try {
				loi.add(processObjectIdentifier(oi));
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Error on ObjectIdentity #"
						+ objcount + ": " + e.getLocalizedMessage(), e);
			}
			objcount++;
		}
		return loi;
	}
	
	public static ObjectIdentifier processObjectIdentifier(
			final ObjectIdentity oi) {
		if (oi == null) {
			throw new IllegalArgumentException("ObjectIdentities cannot be null");
		}
		checkAddlArgs(oi.getAdditionalProperties(), oi.getClass());
		if (oi.getRef() != null) {
			verifyRefOnly(oi);
			return processObjectReference(oi.getRef());
		}
		return processObjectIdentifier(oi.getWorkspace(), oi.getWsid(),
				oi.getName(), oi.getObjid(), oi.getVer());
	}
		
	public static ObjectIdentifier processObjectIdentifier(
			final String workspace, final Long wsid, final String objname,
			final Long objid, final Long ver) {
		final Integer intver;
		if (ver != null) {
			if (ver.longValue() > Integer.MAX_VALUE) {
				throw new IllegalArgumentException("Maximum object version is "
						+ Integer.MAX_VALUE);
			}
			intver = (int) ver.longValue();
		} else {
			intver = null;
		}
		final WorkspaceIdentifier wsi = processWorkspaceIdentifier(
				workspace, wsid);
		return ObjectIdentifier.create(wsi, objname, objid, intver);
	}

	public static ObjectIdentifier processObjectReference(final String ref) {
		if (ref == null) {
			throw new IllegalArgumentException(
					"Reference string cannot be null");
		}
		final Matcher m = KB_OBJ_ID.matcher(ref);
		if (m.matches()) {
			final WorkspaceIdentifier wsi = new WorkspaceIdentifier(
					Integer.parseInt(m.group(1)));
			final int obj = Integer.parseInt(m.group(2));
			if (m.group(3) == null) {
				return new ObjectIdentifier(wsi, obj);
			}
			return new ObjectIdentifier(wsi, obj,
					Integer.parseInt(m.group(3)));
		}
		return ObjectIdentifier.parseObjectReference(ref);
	}

	public static List<SubObjectIdentifier> processSubObjectIdentifiers(
			List<SubObjectIdentity> subObjectIds) {
		final List<SubObjectIdentifier> objs =
				new LinkedList<SubObjectIdentifier>();
		int objcount = 1;
		for (final SubObjectIdentity soi: subObjectIds) {
			final ObjectIdentifier oi;
			try {
				checkAddlArgs(soi.getAdditionalProperties(), soi.getClass());
				oi = processObjectIdentifier(
					new ObjectIdentity()
					.withWorkspace(soi.getWorkspace())
					.withWsid(soi.getWsid())
					.withName(soi.getName())
					.withObjid(soi.getObjid())
					.withVer(soi.getVer())
					.withRef(soi.getRef()));
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Error on SubObjectIdentity #"
						+ objcount + ": " + e.getLocalizedMessage(), e);
			}
					
			boolean strictMaps = ObjectPaths.STRICT_MAPS_DEFAULT;
            boolean strictArrays = ObjectPaths.STRICT_ARRAYS_DEFAULT;
            if (soi.getStrictMaps() != null)
                strictMaps = soi.getStrictMaps() != 0L;
            if (soi.getStrictArrays() != null)
                strictArrays = soi.getStrictArrays() != 0L;
			ObjectPaths paths = new ObjectPaths(soi.getIncluded(), strictMaps, strictArrays);
			objs.add(new SubObjectIdentifier(oi, paths));
			objcount++;
		}
		return objs;
	}
}
