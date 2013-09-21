package us.kbase.workspace.kbase;

import static us.kbase.workspace.util.Util.xorNameId;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.WorkspaceIdentifier;

public class KBaseIdentifierFactory {
	
	private static final Pattern KB_WS_ID = Pattern.compile("kb\\|ws\\.(\\d+)");
	private static final Pattern KB_OBJ_ID = Pattern.compile(
			"kb\\|ws\\.(\\d+)\\.obj\\.(\\d+)(?:\\.ver\\.(\\d+))?");

	public static WorkspaceIdentifier processWorkspaceIdentifier(
			final WorkspaceIdentity wsi) {
		ArgUtils.checkAddlArgs(wsi.getAdditionalProperties(), wsi.getClass());
		return processWorkspaceIdentifier(wsi.getWorkspace(), wsi.getId());
	}
	
	public static WorkspaceIdentifier processWorkspaceIdentifier(
			final String workspace, final Integer id) {
		xorNameId(workspace, id, "workspace");
		if (id != null) {
			return new WorkspaceIdentifier(id);
		}
		Matcher m = KB_WS_ID.matcher(workspace);
		if (m.find()) {
			return new WorkspaceIdentifier(new Integer(m.group(1)));
		}
		return new WorkspaceIdentifier(workspace);
	}
	
	private static void verifyRefOnly(final ObjectIdentity oi) {
		if (oi.getWorkspace() != null || oi.getWsid() != null 
				|| oi.getName() != null || oi.getObjid() != null ||
				oi.getVer() != null) {
			final List<String> err = new ArrayList<String>(4);
			if (oi.getWorkspace() != null) {
				err.add("workspace: " + oi.getWorkspace());
			}
			if (oi.getWsid() != null) {
				err.add("workspace id: " + oi.getWsid());
			}
			if (oi.getName() != null) {
				err.add("object name: " + oi.getName());
			}
			if (oi.getObjid() != null) {
				err.add("object id: " + oi.getObjid());
			}
			if (oi.getVer() != null) {
				err.add("version: " + oi.getVer());
			}
			throw new IllegalArgumentException(String.format(
					"Object reference %s provided; cannot provide any other means of identifying an object. %s",
					oi.getRef(), StringUtils.join(err, " ")));
		}
	}
	
	public static List<ObjectIdentifier> processObjectIdentifiers(
			List<ObjectIdentity> objectIDs) {
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
		ArgUtils.checkAddlArgs(oi.getAdditionalProperties(), oi.getClass());
		if (oi.getRef() != null) {
			verifyRefOnly(oi);
			final String ref = oi.getRef();
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
		final WorkspaceIdentifier wsi = processWorkspaceIdentifier(
				oi.getWorkspace(), oi.getWsid());
		return ObjectIdentifier.create(wsi, oi.getName(), oi.getObjid(),
				oi.getVer());
	}
}
