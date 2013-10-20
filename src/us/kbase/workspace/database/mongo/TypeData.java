package us.kbase.workspace.database.mongo;

import static us.kbase.common.utils.StringUtils.checkString;

import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;

import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.TypeDefId;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

public class TypeData {
	
	@JsonIgnore
	public static final String TYPE_COL_PREFIX = "type_";
	
	@JsonIgnore
	private String data = null;
	
	//these attributes are actually saved in mongo
	private String type = null;
	private String chksum;
	@JsonInclude(value=JsonInclude.Include.ALWAYS)
	private Map<String, Object> subdata;
	private long size;
	
	public TypeData(final String data, final AbsoluteTypeDefId type,
			final Map<String,Object> subdata) {
		//TODO might be better to generate subdata here
		checkString(data, "data");
		if (type == null) {
			throw new IllegalArgumentException("type may not be null");
		}
		this.data = data;
		this.type = type.getType().getTypeString() +
				AbsoluteTypeDefId.TYPE_VER_SEP + type.getMajorVersion();
		this.subdata = subdata;
		this.size = data.length();
		this.chksum = DigestUtils.md5Hex(data);
		
	}
	
	public String getTypeCollection() {
		return TYPE_COL_PREFIX + DigestUtils.md5Hex(this.type);
	}
	
	public static String getTypeCollection(final TypeDefId type) {
		if (type.getMajorVersion() == null) {
			throw new IllegalArgumentException(
					"Cannot get a type collection for a typedef without a major version");
		}
		final String t = type.getType().getTypeString() +
				AbsoluteTypeDefId.TYPE_VER_SEP + type.getMajorVersion();
		return TYPE_COL_PREFIX + DigestUtils.md5Hex(t);
	}

	public String getData() {
		return data;
	}
	
	public TypeDefId getType() {
		return TypeDefId.fromTypeString(type);
	}
	
	public String getChksum() {
		return chksum;
	}
	
	public long getSize() {
		return size;
	}

	@Override
	public String toString() {
		return "TypeData [data=" + data + ", type=" + type
				+ ", chksum=" + chksum + ", subdata=" + subdata + ", size="
				+ size + "]";
	}
}
