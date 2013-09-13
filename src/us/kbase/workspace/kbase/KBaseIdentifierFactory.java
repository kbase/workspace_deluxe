package us.kbase.workspace.kbase;

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

	private static WorkspaceIdentifier createWSID(String wsname) {
		Matcher m = KB_WS_ID.matcher(wsname);
		if (m.find()) {
			return new WorkspaceIdentifier(new Integer(m.group(1)));
		}
		return new WorkspaceIdentifier(wsname);
	}
	
	private static WorkspaceIdentifier createWSID(int id) {
		return new WorkspaceIdentifier(id);
	}
	
	public static WorkspaceIdentifier processWorkspaceIdentifier(
			final WorkspaceIdentity wsi) {
		ArgUtils.checkAddlArgs(wsi.getAdditionalProperties(), wsi.getClass());
		return processWorkspaceIdentifier(wsi.getWorkspace(), wsi.getId());
	}
	
	public static WorkspaceIdentifier processWorkspaceIdentifier(
			final String workspace, final Integer id) {
		if (!(workspace == null ^ id == null)) {
			throw new IllegalArgumentException(String.format(
					"Must provide one and only one of workspace (was: %s) or id (was: %s)",
					workspace, id));
		}
		if (id != null) {
			return createWSID(id);
		}
		return createWSID(workspace);
	}
	
	private static void verifyRefOnly(final ObjectIdentity oi) {
		if (oi.getWorkspace() != null || oi.getWsid() != null 
				|| oi.getName() != null || oi.getObjid() != null ||
				oi.getVer() != null) {
			final List<Object> err = new ArrayList<Object>(4);
			if (oi.getWorkspace() != null) {
				err.add(oi.getWorkspace());
			}
			if (oi.getWsid() != null) {
				err.add(oi.getWsid());
			}
			if (oi.getName() != null) {
				err.add(oi.getName());
			}
			if (oi.getObjid() != null) {
				err.add(oi.getObjid());
			}
			if (oi.getVer() != null) {
				err.add(oi.getVer());
			}
			throw new IllegalArgumentException(String.format(
					"Object reference %s provided; cannot provide any other means of identifying an object: %s",
					oi.getRef(), StringUtils.join(err, " ")));
		}
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
		return processObjectIdentifier(wsi, oi.getName(), oi.getObjid(),
				oi.getVer());
		
	}
	
	public static ObjectIdentifier processObjectIdentifier(
			final WorkspaceIdentifier wsi, final String name,
			final Integer id) {
		return processObjectIdentifier(wsi, name, id, null);
	}
	
	public static ObjectIdentifier processObjectIdentifier(
			final WorkspaceIdentifier wsi, final String name, final Integer id,
			final Integer ver) {
		if (!(name == null ^ id == null)) {
			throw new IllegalArgumentException(String.format(
					"Must provide one and only one of an object name or id: %s/%s",
					name, id));
		}
		if (name != null) {
			if (ver == null) {
				return new ObjectIdentifier(wsi, name);
			}
			return new ObjectIdentifier(wsi, name, ver);
		}
		if (ver == null) {
			return new ObjectIdentifier(wsi, id);
		}
		return new ObjectIdentifier(wsi, id, ver);
	}
}
