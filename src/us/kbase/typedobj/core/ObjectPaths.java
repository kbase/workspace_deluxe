package us.kbase.typedobj.core;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class ObjectPaths implements Iterable<String> {
	
	private final List<String> paths;
	private boolean strictMaps = STRICT_MAPS_DEFAULT;
	private boolean strictArrays = STRICT_ARRAYS_DEFAULT;
	
   /** sets default behavior for extraction.  If strict is true, then errors are thrown if a field
     * in map is requested but does not exist in the data object. If strict is false, then
     * if the field in map is missing, nothing is returned.  This is useful for optional
     * fields, but may be prone to error if a user had a typo in the path...
     */
    public static boolean STRICT_MAPS_DEFAULT = false;
    /** sets default behavior for extraction.  If strict is true, then errors are thrown if an
     * array element is requested but does not exist in the data object. If strict is false, then
     * if an array element is missing, nothing is returned.  This is useful for optional
     * fields, but may be prone to error if a user had a typo in the path...
     */
    public static boolean STRICT_ARRAYS_DEFAULT = true;

	public ObjectPaths(final List<String> paths) {
		if (paths == null) {
			this.paths = Collections.unmodifiableList(
					new LinkedList<String>());
		} else {
			this.paths = Collections.unmodifiableList(
					new LinkedList<String>(paths));
		}
	}
	
	public ObjectPaths withStringMaps(boolean strictMaps) {
	    this.strictMaps = strictMaps;
	    return this;
	}

	public ObjectPaths withStringArrays(boolean strictArrays) {
	    this.strictArrays = strictArrays;
	    return this;
	}

	public boolean isStrictMaps() {
        return strictMaps;
    }
	
	public boolean isStrictArrays() {
        return strictArrays;
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
        return "ObjectPaths [paths=" + paths + ", strictMaps=" + strictMaps
                + ", strictArrays=" + strictArrays + "]";
    }

	@Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((paths == null) ? 0 : paths.hashCode());
        result = prime * result + (strictArrays ? 1231 : 1237);
        result = prime * result + (strictMaps ? 1231 : 1237);
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
        if (strictArrays != other.strictArrays)
            return false;
        if (strictMaps != other.strictMaps)
            return false;
        return true;
    }
}
