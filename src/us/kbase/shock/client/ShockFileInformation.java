package us.kbase.shock.client;

import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties({"virtual", "virtual_parts", "format"})
public class ShockFileInformation {
	
	private ShockFileInformation(){}
	
	@JsonProperty("checksum")
	private Map<String, String> checksum;
	private String name;
	private int size;
	
	//will be empty string if no file
	public String getName() {
		if (name == "") {
			return null;
		}
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

	@Override
	public String toString() {
		return "ShockFileInformation [checksum=" + checksum + ", name=" +
				getName() + ", size=" + size + "]";
	}

}
