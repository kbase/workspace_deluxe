package us.kbase.shock.client;

import java.util.regex.Pattern;

public class ShockVersionStamp {
	
	private static final Pattern MD5 = Pattern.compile("[\\da-f]{32}");

	public final String version;

	public ShockVersionStamp(String version) throws IllegalArgumentException {
		if (!MD5.matcher(version).matches()) {
			throw new IllegalArgumentException("version must be an md5 string");
		}
		this.version = version;
	}
		
	public String getVersion() {
		return version;
	}
	
	@Override
	public String toString() {
		return "ShockVersionStamp [version=" + version + "]";
	}
}
