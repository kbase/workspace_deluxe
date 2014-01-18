package us.kbase.typedobj.core.validatornew;

public class ProcessStat {
	public int objectCount = 0;
	public int arrayCount = 0;
	public int stringCount = 0;
	public int integerCount = 0;
	public int floatCount = 0;
	@Override
	public String toString() {
		return "ProcessStat [objectCount=" + objectCount + ", arrayCount="
				+ arrayCount + ", stringCount=" + stringCount
				+ ", integerCount=" + integerCount + ", floatCount="
				+ floatCount + "]";
	}
}
