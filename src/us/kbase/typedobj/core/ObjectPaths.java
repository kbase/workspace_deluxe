package us.kbase.typedobj.core;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Class is responsible for storing "JSON Pointer" paths pointing to particular places in 
 * JSON data (that is constructed based on arrays and maps nested in each other in any
 * combinations). Path elements are separated by '/' character. In order to be able to
 * use '/' itself as part of mapping keys RFC6901 approach is used 
 * (http://tools.ietf.org/html/rfc6901).
 * @author rsutormin
 */
public class ObjectPaths implements Iterable<String> {
	
	private final List<String> paths;
	private final boolean strictMaps;
	private final boolean strictArrays;
	
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

    /**
     * Construct set of JSON Pointer paths with default values for strictMaps (false)
     * and strictArrays (true).
     * @param paths JSON Pointer paths
     */
	public ObjectPaths(final List<String> paths) {
	    this(paths, STRICT_MAPS_DEFAULT, STRICT_ARRAYS_DEFAULT);
	}

	/**
	 * Construct set of JSON Pointer paths.
	 * @param paths JSON Pointer paths
	 * @param strictMaps restriction mode for maps
	 * @param strictArrays restriction mode for arrays
	 */
	public ObjectPaths(final List<String> paths, boolean strictMaps, boolean strictArrays) {
	    if (paths == null) {
			this.paths = Collections.unmodifiableList(
					new LinkedList<String>());
		} else {
			this.paths = Collections.unmodifiableList(
					new LinkedList<String>(paths));
		}
	    this.strictMaps = strictMaps;
	    this.strictArrays = strictArrays;
	}
	
	/**
	 * Give restriction mode for maps
	 * @return restriction mode for maps
	 */
	public boolean isStrictMaps() {
        return strictMaps;
    }
	
	/**
	 * Give restriction mode for arrays
	 * @return restriction mode for arrays
	 */
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
	
	/**
	 * Parse path according to JsonPointer rules (http://tools.ietf.org/html/rfc6901).
	 * @param index number of path in set of paths
	 * @return parsed path
	 * @throws JsonPointerParseException in case there are '~' characters not followed by '0' or '1'
	 */
	public String[] getPath(int index) throws JsonPointerParseException {
	    String p = paths.get(index);
	    if (p.startsWith("/"))
	        p = p.substring(1);
	    if (p.endsWith("/"))
	        p = p.substring(0, p.length() - 1);
	    String[] ret = p.split("/");
	    for (int pos = 0; pos < ret.length; pos++) {
	        if (ret[pos].indexOf('~') >= 0) {
	            StringBuilder item = new StringBuilder(ret[pos]);
	            int n = 0;
	            int origN = 0;
	            while (n < item.length()) {
	                if (item.charAt(n) == '~') {
	                    char next = n + 1 < item.length() ? item.charAt(n + 1) : (char)0;
	                    if (next == '1') {
	                        item.setCharAt(n, '/');
	                    } else if (next != '0') {
	                        String errorBlock = ret[pos];
	                        errorBlock = errorBlock.substring(0, origN) + "[->]" + errorBlock.substring(origN);
	                        throw new JsonPointerParseException("Wrong usage of ~ in json pointer path: " + 
	                                paths.get(index) + " (" + errorBlock + ")");
	                    }
	                    item.deleteCharAt(n + 1);
	                    origN++;
	                }
	                n++;
	                origN++;
	            }
	            ret[pos] = item.toString();
	        }
	    }
		return ret;
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
