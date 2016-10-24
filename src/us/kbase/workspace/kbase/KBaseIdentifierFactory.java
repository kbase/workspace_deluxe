package us.kbase.workspace.kbase;

import static us.kbase.common.utils.ServiceUtils.checkAddlArgs;
import static us.kbase.workspace.database.Util.xorNameId;
import static us.kbase.workspace.kbase.ArgUtils.longToBoolean;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import us.kbase.typedobj.core.ObjectPaths;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.ObjectSpecification;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.database.ObjIDWithChainAndSubset;
import us.kbase.workspace.database.ObjectIDWithRefChain;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.WorkspaceIdentifier;

public class KBaseIdentifierFactory {
	
	//TODO TEST unit tests
	//TODO JAVADOC
	//TODO NOW rename to IdentifierUtils
	
	private static final Pattern KB_WS_ID = Pattern.compile("kb\\|ws\\.(\\d+)");
	private static final Pattern KB_OBJ_ID = Pattern.compile(
			"kb\\|ws\\.(\\d+)\\.obj\\.(\\d+)(?:\\.ver\\.(\\d+))?");

	public static WorkspaceIdentifier processWorkspaceIdentifier(
			final WorkspaceIdentity wsi) {
		if (wsi == null) {
			throw new NullPointerException(
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
		final List<String> err = new LinkedList<String>();
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
		if (!err.isEmpty()) {
			throw new IllegalArgumentException(String.format(
					"Object reference %s provided; cannot provide any other "+
							"means of identifying an object. %s",
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
			throw new IllegalArgumentException(
					"No object identifiers provided");
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
			throw new NullPointerException(
					"ObjectIdentity cannot be null");
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
			throw new NullPointerException(
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

	@SuppressWarnings("deprecation")
	public static List<ObjectIdentifier> processSubObjectIdentifiers(
			List<us.kbase.workspace.SubObjectIdentity> subObjectIds) {
		final List<ObjectIdentifier> objs =
				new LinkedList<ObjectIdentifier>();
		int objcount = 1;
		for (final us.kbase.workspace.SubObjectIdentity soi: subObjectIds) {
			final ObjectIdentifier oi;
			try {
				oi = processObjectIdentifier(
					new ObjectIdentity()
						.withWorkspace(soi.getWorkspace())
						.withWsid(soi.getWsid())
						.withName(soi.getName())
						.withObjid(soi.getObjid())
						.withVer(soi.getVer())
						.withRef(soi.getRef()));
				checkAddlArgs(soi.getAdditionalProperties(), soi.getClass());
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException(
						"Error on SubObjectIdentity #"
						+ objcount + ": " + e.getLocalizedMessage(), e);
			}
					
			ObjectPaths paths = new ObjectPaths(soi.getIncluded(),
					longToBoolean(soi.getStrictMaps(),
							ObjectPaths.STRICT_MAPS_DEFAULT),
					longToBoolean(soi.getStrictArrays(),
							ObjectPaths.STRICT_ARRAYS_DEFAULT));
			objs.add(new ObjIDWithChainAndSubset(oi, null, paths));
			objcount++;
		}
		return objs;
	}
	
	public static List<ObjectIdentifier> processObjectSpecifications(
			final List<ObjectSpecification> objects) {
		
		if (objects == null) {
			throw new NullPointerException(
					"The object specification list cannot be null");
		}
		final List<ObjectIdentifier> objs =
				new LinkedList<ObjectIdentifier>();
		int objcount = 1;
		for (final ObjectSpecification o: objects) {
			if (o == null) {
				throw new NullPointerException(
						"Objects in the object specification list cannot "+
						"be null");
			}
			final ObjectIdentifier oi;
			try {
				mutateObjSpecByRefString(o);
				oi = processObjectIdentifier(
					new ObjectIdentity()
						.withWorkspace(o.getWorkspace())
						.withWsid(o.getWsid())
						.withName(o.getName())
						.withObjid(o.getObjid())
						.withVer(o.getVer())
						.withRef(o.getRef()));
				checkAddlArgs(o.getAdditionalProperties(), o.getClass());
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException(
						"Error on ObjectSpecification #"
						+ objcount + ": " + e.getLocalizedMessage(), e);
			}
			objs.add(buildObjectIdentifier(o, oi, objcount));
			objcount++;
		}
		return objs;
	}

	private static ObjectPaths getObjectPaths(final ObjectSpecification o) {
		if (o.getIncluded() != null && !o.getIncluded().isEmpty()) {
			return new ObjectPaths(o.getIncluded(),
					longToBoolean(o.getStrictMaps(),
							ObjectPaths.STRICT_MAPS_DEFAULT),
					longToBoolean(o.getStrictArrays(),
							ObjectPaths.STRICT_ARRAYS_DEFAULT));
		}
		return null;
	}
	
	private static ObjectIdentifier buildObjectIdentifier(
			final ObjectSpecification o,
			ObjectIdentifier oi,
			final int objcount) {
		final ObjectPaths paths = getObjectPaths(o);
		final List<ObjectIdentifier> refchain;
		try {
			final RefChainResult res = processRefChains(o);
			if (res == null) {
				refchain = null;
			} else if (res.isToPath) {
				refchain = new LinkedList<>();
				if (res.path.size() > 1) {
					refchain.addAll(res.path.subList(1, res.path.size() - 1));
				}
				refchain.add(oi);
				oi = res.path.get(0);
			} else {
				refchain = res.path;
			}
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(
					"Error on ObjectSpecification #"
					+ objcount + ": " + e.getLocalizedMessage(), e);
		}
		if (paths == null && refchain == null) {
			return oi;
		} else if (paths == null) {
			return new ObjectIDWithRefChain(oi, refchain);
		} else {
			return new ObjIDWithChainAndSubset(oi, refchain, paths);
		}
	}

	private static void mutateObjSpecByRefString(
			final ObjectSpecification o) {
		if (o.getRef() == null || o.getRef().trim().isEmpty()) {
			return;
		}
		final String[] reflist = o.getRef().trim().split(";");
		o.setRef(reflist[0].trim());
		final List<String> refs = new LinkedList<>();
		for (int i = 1; i < reflist.length; i++) {
			refs.add(reflist[i].trim());
		}
		if (!refs.isEmpty()) {
			if (countRefPathsInObjectSpec(o) > 0) {
				throw new IllegalArgumentException(
						"Only one of the 5 options for specifying an "+
						"object reference path is allowed");
			}
			o.setObjRefPath(refs);
		}
	}
		
	private static int countRefPathsInObjectSpec(final ObjectSpecification o) {
		int count = 0;
		// there's probably some obnoxiously smarter way to do this
		if (o.getObjPath() != null && !o.getObjPath().isEmpty()) {
			count++;
		}
		if (o.getObjRefPath() != null && !o.getObjRefPath().isEmpty()) {
			count++;
		}
		if (o.getToObjPath() != null && !o.getToObjPath().isEmpty()) {
			count++;
		}
		if (o.getToObjRefPath() != null && !o.getToObjRefPath().isEmpty()) {
			count++;
		}
		return count;
	}

	private static class RefChainResult {
		
		private boolean isToPath;
		private List<ObjectIdentifier> path;
		
		public RefChainResult(
				final List<ObjectIdentifier> path,
				final boolean isToPath) {
			this.isToPath = isToPath;
			this.path = path;
		}
	}
	
	private static RefChainResult processRefChains(
			final ObjectSpecification o) {
		if (countRefPathsInObjectSpec(o) > 1) {
			throw new IllegalArgumentException("Only one of the 5 options " +
					"for specifying an object reference path is allowed");
		}
		if (o.getObjPath() != null && !o.getObjPath().isEmpty()) {
			return new RefChainResult(processObjectPath(
					o.getObjPath(), false), false);
		} else if (o.getToObjPath() != null && !o.getToObjPath().isEmpty()) {
			return new RefChainResult(processObjectPath(
					o.getToObjPath(), true), true);
		}
		if (o.getObjRefPath() != null && !o.getObjRefPath().isEmpty()) {
			return new RefChainResult(processRefPath(
					o.getObjRefPath(), false), false);
		} else if (o.getToObjRefPath() != null &&
				!o.getToObjRefPath().isEmpty()) {
			return new RefChainResult(processRefPath(
					o.getToObjRefPath(), true), true);
		}
		return null;
	}

	private static List<ObjectIdentifier> processRefPath(
			final List<String> objRefPath,
			final boolean isToPath) {
		int refcount = isToPath ? 1 : 2;
		final List<ObjectIdentifier> ret = new LinkedList<>();
		for (final String r: objRefPath) {
			try {
				ret.add(processObjectReference(r));
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException(String.format(
						"Invalid object reference (%s) at position #%s: %s",
						r, refcount, e.getLocalizedMessage()), e);
			}
			refcount++;
		}
		return ret;
	}

	private static List<ObjectIdentifier> processObjectPath(
			final List<ObjectIdentity> objPath,
			final boolean isToPath) {
		int refcount = isToPath ? 1 : 2;
		final List<ObjectIdentifier> ret = new LinkedList<>();
		for (final ObjectIdentity oi: objPath) {
			try {
				ret.add(processObjectIdentifier(oi));
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException(String.format(
						"Invalid object id at position #%s: %s",
						refcount, e.getLocalizedMessage()), e);
			}
			refcount++;
		}
		return ret;
	}
}
