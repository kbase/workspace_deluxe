package us.kbase.typedobj.core;

import java.util.ArrayList;
import java.util.List;

/** Provides the current location in a JSON document.
 * @author gaprice@lbl.gov
 *
 */
public class JsonDocumentLocation {
	
	//TODO unit tests
	
	public static final char DEFAULT_PATHSEP = '/';
	private final String pathSep;
	private static final JsonLocation MAP_START = new JsonMapStart();
	private static final JsonLocation ARRAY_START = new JsonArrayStart();
	
	
	private final List<JsonLocation> loc = new ArrayList<JsonLocation>();
	
	public JsonDocumentLocation() {
		this(DEFAULT_PATHSEP);
	}
	
	public JsonDocumentLocation(final char pathSep) {
		this.pathSep = String.valueOf(pathSep);
	}
	
	public JsonDocumentLocation(final JsonDocumentLocation jdl) {
		this(DEFAULT_PATHSEP, jdl);
	}
	
	public JsonDocumentLocation(final char pathSep,
			final JsonDocumentLocation jdl) {
		this.pathSep = String.valueOf(pathSep);
		loc.addAll(jdl.loc);
	}
	
	public int getDepth() {
		return loc.size();
	}
	
	public void addLocation(final JsonLocation jl) {
		if (jl == null) {
			throw new NullPointerException("loc cannot be null");
		}
		loc.add(jl);
	}
	
	public JsonLocation addMapStart() {
		loc.add(MAP_START);
		return MAP_START;
	}
	
	public JsonLocation addArrayStart() {
		loc.add(ARRAY_START);
		return ARRAY_START;
	}
	
	public JsonMapLocation addMapLocation(final String loc) {
		final JsonMapLocation ret = new JsonMapLocation(loc);
		this.loc.add(ret);
		return ret;
	}
	
	public JsonArrayLocation addArrayLocation(final long loc) {
		final JsonArrayLocation ret = new JsonArrayLocation(loc);
		this.loc.add(ret);
		return ret;
	}
	
	public JsonLocation replaceLast(final JsonLocation jl) {
		final JsonLocation l = removeLast();
		addLocation(jl);
		return l;
	}
	
	public JsonLocation replaceLast(final String loc) {
		final JsonLocation l = removeLast();
		addMapLocation(loc);
		return l;
	}
	
	public JsonLocation replaceLast(final long loc) {
		final JsonLocation l = removeLast();
		addArrayLocation(loc);
		return l;
	}
	
	public JsonArrayLocation incrementArrayLocation() {
		final JsonLocation l = getLast();
		if (!l.isArrayLocation()) {
			throw new NoSuchLocationException(
					"Last position is not in an array");
		}
		if (l.isStartLocation()) {
			replaceLast(0);
		} else {
			replaceLast(((JsonArrayLocation)l).getLocationAsLong() + 1);
		}
		return (JsonArrayLocation) getLast();
	}
	
	public JsonLocation removeLast() {
		if (loc.isEmpty()) {
			throw new EndOfPathException("At the path root");
		}
		final JsonLocation l = loc.get(loc.size() - 1);
		loc.remove(loc.size() -1);
		return l;
	}
	
	public JsonLocation getLocation(final int index) {
		if (index < 0) {
			throw new IndexOutOfBoundsException("index must be 0 or greater");
		}
		if (index >= getDepth()) {
			throw new IndexOutOfBoundsException("index " + index +
					"greater or equal to path depth " + getDepth());
		}
		return loc.get(index);
	}
	
	public JsonLocation getLast() {
		if (loc.isEmpty()) {
			throw new EndOfPathException("At the path root");
		}
		return loc.get(loc.size() - 1);
	}
	
	public String getFullLocationAsString() {
		if (loc.isEmpty()) {
			return pathSep;
		}
		final StringBuilder sb = new StringBuilder();
		for (final JsonLocation l: loc) {
			sb.append(l.getLocationInFullPath());
		}
		return sb.toString();
	}
	
	@Override
	public String toString() {
		return "JsonDocumentLocation [pathSep=" + pathSep + ", loc=" + loc
				+ "]";
	}

	public interface JsonLocation {
		public String getLocationAsString();
		public boolean isStartLocation();
		public String getLocationInFullPath();
		public Object getLocation();
		public boolean isMapLocation();
		public boolean isArrayLocation();
	}
	
	public class JsonArrayLocation implements JsonLocation {
		
		private final long location;
		
		private JsonArrayLocation(final long location) {
			if (location < 0) {
				throw new ArrayIndexOutOfBoundsException(
						"JSON arrays cannot have negative indexes");
			}
			this.location = location;
		}

		@Override
		public String getLocationAsString() {
			return location + "";
		}

		@Override
		public String getLocationInFullPath() {
			return pathSep + location;// + "array";
		}
		
		@Override
		public Object getLocation() {
			return location;
		}
		
		public long getLocationAsLong() {
			return location;
		}

		@Override
		public boolean isMapLocation() {
			return false;
		}

		@Override
		public boolean isArrayLocation() {
			return true;
		}
		
		@Override
		public boolean isStartLocation() {
			return false;
		}

		@Override
		public String toString() {
			return "JsonArrayLocation [location=" + location + "]";
		}
	}
	
	public static class JsonArrayStart implements JsonLocation {
		
		private JsonArrayStart() {}

		@Override
		public String getLocationAsString() {
			throw new NoSuchLocationException(
					"Array start is not a real location");
		}

		@Override
		public String getLocationInFullPath() {
			return "";
		}
		
		@Override
		public Object getLocation() {
			throw new NoSuchLocationException(
					"Array start is not a real location");
		}
		
		public long getLocationAsLong() {
			throw new NoSuchLocationException(
					"Array start is not a real location");
		}

		@Override
		public boolean isMapLocation() {
			return false;
		}

		@Override
		public boolean isArrayLocation() {
			return true;
		}
		
		@Override
		public boolean isStartLocation() {
			return true;
		}

		@Override
		public String toString() {
			return "JsonArrayStart []";
		}
	}
	
	public class JsonMapLocation implements JsonLocation {
		
		private final String location;
		
		private JsonMapLocation(final String location) {
			if (location == null || location.isEmpty()) {
				throw new IllegalArgumentException(
						"Map locations cannot be null or the empty string");
			}
			this.location = location;
		}

		@Override
		public String getLocationAsString() {
			return location;
		}
		
		@Override
		public String getLocationInFullPath() {
			return pathSep + location;
		}

		@Override
		public Object getLocation() {
			return location;
		}
		

		@Override
		public boolean isMapLocation() {
			return true;
		}

		@Override
		public boolean isArrayLocation() {
			return false;
		}
		
		@Override
		public boolean isStartLocation() {
			return false;
		}

		@Override
		public String toString() {
			return "JsonMapLocation [location=" + location + "]";
		}
	}
	
	public static class JsonMapStart implements JsonLocation {
		
		private JsonMapStart() {}

		@Override
		public String getLocationAsString() {
			throw new NoSuchLocationException(
					"Map start is not a real location");
		}

		@Override
		public String getLocationInFullPath() {
			return "";
		}
		
		@Override
		public Object getLocation() {
			throw new NoSuchLocationException(
					"Map start is not a real location");
		}

		@Override
		public boolean isMapLocation() {
			return true;
		}

		@Override
		public boolean isArrayLocation() {
			return false;
		}
		
		@Override
		public boolean isStartLocation() {
			return true;
		}

		@Override
		public String toString() {
			return "JsonMapStart []";
		}
	}
	
	@SuppressWarnings("serial")
	public static class EndOfPathException extends RuntimeException {

		public EndOfPathException(String message) {
			super(message);
		}
	}
	
	@SuppressWarnings("serial")
	public static class NoSuchLocationException extends RuntimeException{

		public NoSuchLocationException(String message) {
			super(message);
		}
	}
}
