package us.kbase.workspace.workspaces;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import us.kbase.workspace.database.WorkspaceIdentifier;

public class WorkspaceObjectCollection implements Iterable<WorkspaceSaveObject> {
	
	private final WorkspaceIdentifier wsi;
	private final List<WorkspaceSaveObject> objects = new LinkedList<WorkspaceSaveObject>();
	
	public WorkspaceObjectCollection(WorkspaceIdentifier wsi) {
		if (wsi == null) {
			throw new NullPointerException("wsi cannot be null");
		}
		this.wsi = wsi;
	}
	
	public void addObject(WorkspaceSaveObject object) {
		if (!wsi.equals(object.getWorkspaceIdentifier())) {
			throw new IllegalArgumentException(
					"All objects in the collection must have the same WorkspaceIdentifier");
		}
		objects.add(object);
	}

	public WorkspaceIdentifier getWorkspaceIdentifier() {
		return wsi;
	}

	public Iterator<WorkspaceSaveObject> iterateObjects() {
		return new LinkedList<WorkspaceSaveObject>(objects).iterator();
	}

	@Override
	public Iterator<WorkspaceSaveObject> iterator() {
		return new LinkedList<WorkspaceSaveObject>(objects).iterator();
	}
	
	public int size() {
		return objects.size();
	}

	@Override
	public String toString() {
		return "WorkspaceObjectCollection [wsi=" + wsi + ", objects=" + objects
				+ "]";
	}

}
