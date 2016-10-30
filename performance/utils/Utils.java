package utils;

public class Utils {

	public static void printElapse(final String name, final long start) {
		System.out.println(name + " elapsed " + ((System.nanoTime() - start) / 1000000000.0));
	}

}
