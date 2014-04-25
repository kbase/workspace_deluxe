import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import junit.framework.JUnit4TestAdapter;
import junit.framework.TestSuite;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

@RunWith(AllTests.class)
public class AllWorkspaceTestsRunner {
	private static boolean skipLongTests = false;
	
	public static TestSuite suite() throws Exception {
		TestSuite suite = new TestSuite();
		for (Class<?> classObj : collectTestClasses()) {
			suite.addTest(new JUnit4TestAdapter(classObj));
		}
		return suite;
	}

	private static List<Class<?>> collectTestClasses() throws Exception {
		List<Class<?>> ret = new ArrayList<Class<?>>();
		collectTestClasses(new File("src"), "", ret);
		return ret;
	}

	private static void collectTestClasses(File packageDir, String packagePrefix, List<Class<?>> ret) throws Exception {
		for (File f : packageDir.listFiles()) {
			if (f.isDirectory()) {
				String subPackage = addSuffixFromFile(packagePrefix, f.getName());
				collectTestClasses(f, subPackage, ret);
			} else if (f.isFile() && f.getName().endsWith(".java")) {
				String nameWithoutExt = f.getName().substring(0, f.getName().length() - 5);
				String className = addSuffixFromFile(packagePrefix, nameWithoutExt);
				Class<?> classObj = Class.forName(className);
				if (isTestPackage(packagePrefix)) {
					if (!isJUnitTestClass(classObj))
						continue;
					if (nameWithoutExt.endsWith("Test")) {
						if (skipLongTests && nameWithoutExt.endsWith("LongTest"))
							continue;
						ret.add(classObj);
					}
				}
			}
		}
	}
	
	private static String addSuffixFromFile(String packagePrefix, String fileName) {
		String subPackage = packagePrefix;
		if (subPackage.length() > 0)
			subPackage += ".";
		subPackage += fileName;
		return subPackage;
	}
	
	public static boolean isJUnitTestClass(Class<?> classObj) {
		for (Method m : classObj.getMethods())
			if (m.getAnnotation(Test.class) != null)
				return true;
		return false;
	}

	private static boolean isTestPackage(String packageName) throws Exception {
		String[] parts = packageName.split(Pattern.quote("."));
		for (String part : parts)
			if (part.equals("test"))
				return true;
		return false;
	}
}
