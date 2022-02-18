package us.kbase.workspace.test;

public class LongTextForTestUsage {

	public static final String LONG_TEXT_PART =
			"Passersby were amazed by the unusually large amounts of blood. ";
	public static final String LONG_TEXT = initLongText();
	public static final String TEXT100 = initText100();
	public static final String TEXT101 = TEXT100 + "f";
	public static final String TEXT255 = TEXT100 + TEXT100 + TEXT100.substring(0, 55);
	public static final String TEXT256 = TEXT255 + "f";
	public static final String TEXT1000 = initText1000();
	
	private static String initLongText() {
		String ret = "";
		for (int i = 0; i < 17; i++) {
			ret += LONG_TEXT_PART;
		}
		return ret;
	}
	
	private static String initText100() {
		String ret = "";
		for (int i = 0; i < 10; i++) {
			ret += "aaaaabbbbb";
		}
		return ret;
	}
	
	private static String initText1000() {
		String ret = "";
		for (int i = 0; i < 10; i++) {
			ret += TEXT100;
		}
		return ret;
	}
}
