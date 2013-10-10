package us.kbase.typedobj.core;

import static us.kbase.common.utils.StringUtils.checkString;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TypeDefName {
	
	private final static Pattern INVALID_TYPE_NAMES = 
			Pattern.compile("[^\\w]");
	
	private final String module;
	private final String name;
	
	public TypeDefName(String module, String name) {
		checkString(module, "Module");
		checkTypeName(module);
		checkString(name, "Name");
		checkTypeName(name);
		this.module = module;
		this.name = name;
	}
	
	/**
	 * accepts (and splits) fully qualified name, e.g. 'KB.Genome', 'FBA.Model'
	 */
	public TypeDefName(String fullname) {
		String [] tokens = fullname.split("\\.");
		if(tokens.length != 2) {
			throw new IllegalArgumentException(String.format(
					"Illegal fullname of a typed object: %s", fullname));
		}
		this.module = tokens[0];
		this.name = tokens[1];
		checkString(this.module, "Module");
		checkTypeName(this.module);
		checkString(this.name, "Name");
		checkTypeName(this.name);
	}
	
	private void checkTypeName(String name) {
		final Matcher m = INVALID_TYPE_NAMES.matcher(name);
		if (m.find()) {
			throw new IllegalArgumentException(String.format(
					"Illegal character in type id %s: %s", name, m.group()));
		}
	}
	
	public String getModule() {
		return module;
	}
	
	public String getName() {
		return name;
	}
	
	public String getTypeString() {
		return module + "." + name;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((module == null) ? 0 : module.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
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
		if (!(obj instanceof TypeDefName)) {
			return false;
		}
		TypeDefName other = (TypeDefName) obj;
		if (module == null) {
			if (other.module != null) {
				return false;
			}
		} else if (!module.equals(other.module)) {
			return false;
		}
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!name.equals(other.name)) {
			return false;
		}
		return true;
	}
	
	@Override
	public String toString() {
		return "TypeDefName [module=" + module + ", name=" + name + "]";
	}

}
