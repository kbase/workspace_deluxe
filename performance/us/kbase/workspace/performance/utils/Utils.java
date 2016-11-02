package us.kbase.workspace.performance.utils;

public class Utils {

	public static double printElapse(final String name, final long start) {
		final double elapsed = (System.nanoTime() - start) / 1000000000.0;
		System.out.println(name + " elapsed " + elapsed);
		return elapsed;
	}
	
	
	public static String makeString(final int size) {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < size; i++) {
			sb.append("A");
		}
		return sb.toString();
	}

}
