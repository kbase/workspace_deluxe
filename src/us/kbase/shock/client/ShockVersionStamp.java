package us.kbase.shock.client;

import java.util.regex.Pattern;

/**
 * Represents a shock node's version stamp.
 * @author gaprice@lbl.gov
 *
 */
public class ShockVersionStamp {
	
	private static final Pattern MD5 = Pattern.compile("[\\da-f]{32}");

	private final String version;

	
	/** 
	 * Construct a shock version stamp.
	 * @param version the version stamp.
	 * @throws IllegalArgumentException if <code>version</code> is not a 
	 * valid version stamp.
	 */
	public ShockVersionStamp(String version) throws IllegalArgumentException {
		if (!MD5.matcher(version).matches()) {
			throw new IllegalArgumentException("version must be an md5 string");
		}
		this.version = version;
	}
		
	/**
	 * Get the version stamp.
	 * @return the version stamp.
	 */
	public String getVersion() {
		return version;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "ShockVersionStamp [version=" + version + "]";
	}
}
