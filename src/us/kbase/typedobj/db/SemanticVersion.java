package us.kbase.typedobj.db;

public class SemanticVersion implements Comparable<SemanticVersion> {
	private int major;
	private int minor;
	private Integer hashCode;
	
	public SemanticVersion(String text) throws NumberFormatException {
		int dotPos = text.indexOf('.');
		if (dotPos < 0)
			throw new NumberFormatException("Wrong version number format, use <major>.<minor>");
		this.major = Integer.parseInt(text.substring(0, dotPos));
		this.minor = Integer.parseInt(text.substring(dotPos + 1));
	}
	
	public SemanticVersion(int major, int minor) {
		this.major = major;
		this.minor = minor;
	}
	
	public int getMajor() {
		return major;
	}
	
	public int getMinor() {
		return minor;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof SemanticVersion))
			return false;
		SemanticVersion o = (SemanticVersion)obj;
		return major == o.major && minor == o.minor;
	}
	
	@Override
	public int compareTo(SemanticVersion o) {
		if (major == o.major)
			return Integer.valueOf(minor).compareTo(o.minor);
		return Integer.valueOf(major).compareTo(o.major);
	}
	
	@Override
	public String toString() {
		return major + "." + minor;
	}
	
	@Override
	public int hashCode() {
		if (hashCode == null)
			hashCode = toString().hashCode();
		return hashCode;
	}
}
