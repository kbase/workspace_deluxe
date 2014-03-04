package us.kbase.typedobj.core;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class ObjectPaths implements Iterable<String> {
	
	private final List<String> paths;
	
	public ObjectPaths(final List<String> paths) {
		if (paths == null) {
			this.paths = Collections.unmodifiableList(
					new LinkedList<String>());
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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((paths == null) ? 0 : paths.hashCode());
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
		ObjectPaths other = (ObjectPaths) obj;
		if (paths == null) {
			if (other.paths != null)
				return false;
		} else if (!paths.equals(other.paths))
			return false;
		return true;
	}
}
