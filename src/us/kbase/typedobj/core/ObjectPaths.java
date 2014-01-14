package us.kbase.typedobj.core;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class ObjectPaths implements Iterable<String> {
	
	private final List<String> paths;
	
	public ObjectPaths(final List<String> paths) {
		if (paths == null) {
			this.paths = new LinkedList<String>();
		} else {
			this.paths = Collections.unmodifiableList(
					new LinkedList<String>(paths));
		}
	}

	@Override
	public Iterator<String> iterator() {
		return paths.iterator();
	}
	
	public int size() {
		return paths.size();
	}
	
	public boolean isEmpty() {
		return paths.isEmpty();
	}
	
	public List<String> getPaths() {
		return new LinkedList<String>(paths);
	}

	@Override
	public String toString() {
		return "ObjectPaths [paths=" + paths + "]";
	}
}
