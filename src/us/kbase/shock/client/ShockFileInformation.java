package us.kbase.shock.client;

import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties({"virtual", "virtual_parts"})
public class ShockFileInformation {
	
	private ShockFileInformation(){}
	
	@JsonProperty("checksum")
	private Map<String, String> checksum;
	private String format;
	private String name;
	private int size;
	
	public String getFormat() {
		return format;
	}
	
	public String getName() {
		return name;
	}
	
	public int getSize() {
		return size;
	}
	
	@JsonIgnore
	public Set<String> getChecksumTypes() {
		return checksum.keySet();
	}
	
	@JsonIgnore
	public String getChecksum(String type) {
		if (!checksum.containsKey(type)) {
			throw new IllegalArgumentException("No such checksum type: "
					+ type);
		}
		return checksum.get(type);
	}
}
