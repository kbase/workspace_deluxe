package us.kbase.typedobj.core;

import java.util.regex.Pattern;

public class MD5 {
	
	private static final Pattern MD5pat = Pattern.compile("[\\da-f]{32}");
	
	private final String md5;
	
	public MD5(String md5) {
		checkMD5(md5);
		this.md5 = md5.toString();
	}
	
	public MD5(StringBuilder md5) {
		checkMD5(md5);
		this.md5 = md5.toString();
	}

	private void checkMD5(CharSequence md5) {
		if (!MD5pat.matcher(md5).matches()) {
			throw new IllegalArgumentException(md5 + " is not a valid MD5 string");
		}
	}

	public String getMD5() {
		return md5;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((md5 == null) ? 0 : md5.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof MD5)) {
			return false;
		}
		MD5 other = (MD5) obj;
		if (md5 == null) {
			if (other.md5 != null) {
				return false;
			}
		} else if (!md5.equals(other.md5)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return "MD5 [md5=" + md5 + "]";
	}
	
	

}
