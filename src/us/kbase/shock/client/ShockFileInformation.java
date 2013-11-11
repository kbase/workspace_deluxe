package us.kbase.shock.client;

import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Information about a shock node's file. This class is never instantiated
 * manually.
 * @author gaprice@lbl.gov
 *
 */
@JsonIgnoreProperties({"virtual", "virtual_parts", "format"})
public class ShockFileInformation {
	
	private ShockFileInformation(){}
	
	@JsonProperty("checksum")
	private Map<String, String> checksum;
	private String name;
	private long size;
	
	//will be empty string if no file
	/**
	 * Get the file name.
	 * @return the name of the file, or <code>null</code> if the shock node has no file.
	 */
	public String getName() {
		if (name == "") {
			return null;
		}
		return name;
	}
	
	/**
	 * Get the file size.
	 * @return the file size in bytes, or 0 if the shock node has no file.
	 */
	public long getSize() {
		return size;
	}
	
	/** 
	 * <p>Get the types of checksums available.</p>
	 * <code>"md5"</code> and <code>"sha1"</code> are currently included.
	 * @return the types of checksums available.
	 */
	@JsonIgnore
	public Set<String> getChecksumTypes() {
		return checksum.keySet();
	}
	
	/**
	 * Get the checksum of the file.
	 * @param type the type of checksum to get. <code>"md5"</code> and
	 * <code>"sha1"</code> are currently available.
	 * @return the file's checksum.
	 * @throws IllegalArgumentException if the checksum <code>type</code> is
	 * not available.
	 */
	@JsonIgnore
	public String getChecksum(String type) throws IllegalArgumentException {
		if (!checksum.containsKey(type)) {
			throw new IllegalArgumentException("No such checksum type: "
					+ type);
		}
		return checksum.get(type);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "ShockFileInformation [checksum=" + checksum + ", name=" +
				getName() + ", size=" + size + "]";
	}

}
