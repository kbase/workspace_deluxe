package us.kbase.workspace.workspaces;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class WorkspaceObjectCollection implements Iterable<WorkspaceObject> {
	
	private final WorkspaceIdentifier wsi;
	private final List<WorkspaceObject> objects = new LinkedList<WorkspaceObject>();
	
	public WorkspaceObjectCollection(WorkspaceIdentifier wsi) {
		if (wsi == null) {
			throw new NullPointerException("wsi cannot be null");
		}
		this.wsi = wsi;
	}
	
	public void addObject(WorkspaceObject object) {
		if (!wsi.equals(object.getObjectIdentifier().getWorkspaceIdentifier())) {
			throw new IllegalArgumentException(
					"All objects in the collection must have the same WorkspaceIdentifier");
		}
		objects.add(object);
	}

	public WorkspaceIdentifier getWorkspaceIdentifier() {
		return wsi;
	}

	public Iterator<WorkspaceObject> iterateObjects() {
		return new LinkedList<WorkspaceObject>(objects).iterator();
	}

	@Override
	public Iterator<WorkspaceObject> iterator() {
		return new LinkedList<WorkspaceObject>(objects).iterator();
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
