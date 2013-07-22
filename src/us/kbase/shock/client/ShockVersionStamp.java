package us.kbase.shock.client;

import java.util.regex.Pattern;

public class ShockVersionStamp {
	
	private static final Pattern md5 = Pattern.compile("[\\da-f]{32}");

	public final String version;

	public ShockVersionStamp(String version) throws IllegalArgumentException {
		if (!md5.matcher(version).matches()) {
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

	public static void main(String[] args) {
		ShockVersionStamp s = new ShockVersionStamp("76a295479a82ddacee098be507bd31cf");
		System.out.println(s.getVersion());
	}
}
