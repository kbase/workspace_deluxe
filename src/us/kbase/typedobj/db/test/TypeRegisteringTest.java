package us.kbase.typedobj.db.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.auth.AuthUser;
import us.kbase.typedobj.db.FileTypeStorage;
import us.kbase.typedobj.db.RefInfo;
import us.kbase.typedobj.db.SimpleTypeDefinitionDB;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.db.TypeStorage;

public class TypeRegisteringTest {
	private static TypeStorage storage = null;
	private static TypeDefinitionDB db = null;

	@BeforeClass
	public static void prepareBeforeClass() throws Exception {
		File dir = new File("temp_files");
		if (!dir.exists())
			dir.mkdir();
		storage = new FileTypeStorage(dir.getAbsolutePath());
		db = new SimpleTypeDefinitionDB(storage, dir);
	}
	
	@Before
	public void cleanupBefore() throws Exception {
		db.removeAllRefs();
		for (String moduleName : db.getAllRegisteredModules())
			db.removeModule(moduleName);
	}
	
	@After
	public void cleanupAfter() throws Exception {
		//cleanupBefore();
	}
	
	@Test
	public void testSimple() throws Exception {
		AuthUser user = getAdmin();
		String taxonomySpec = loadSpec("simple", "Taxonomy");
		db.registerModule(taxonomySpec, Arrays.asList("taxon"), user);
		db.releaseType("Taxonomy", "taxon");
		String sequenceSpec = loadSpec("simple", "Sequence");
		db.registerModule(sequenceSpec, Arrays.asList("sequence_id", "sequence_pos"), user);
		db.releaseType("Sequence", "sequence_id");
		db.releaseType("Sequence", "sequence_pos");
		String annotationSpec = loadSpec("simple", "Annotation");
		db.registerModule(annotationSpec, Arrays.asList("genome", "gene"), user);
		db.releaseType("Annotation", "genome");
		db.releaseType("Annotation", "gene");
		String regulationSpec = loadSpec("simple", "Regulation");
		db.registerModule(regulationSpec, Arrays.asList("regulator", "binding_site"), user);
		db.releaseType("Regulation", "regulator");
		db.releaseType("Regulation", "binding_site");
		checkTypeDep("Annotation", "gene", "Sequence", "sequence_pos", null, true);
		checkTypeDep("Regulation", "binding_site", "Regulation", "regulator", "1.0", true);
	}
	
	private AuthUser getAdmin() throws Exception {
		Constructor<AuthUser> c = AuthUser.class.getDeclaredConstructor();
		c.setAccessible(true);
		AuthUser user = c.newInstance();
		c.setAccessible(false);
		for (Field f : user.getClass().getDeclaredFields()) {
			//System.out.println("Field: " + f.getName() + ": " + f.getType());
			f.setAccessible(true);
			if (f.getName().equals("userId"))
				f.set(user, "admin");
			if (f.getName().equals("systemAdmin"))
				f.set(user, true);
			f.setAccessible(false);
		}
		return user;
	}
	
	private String loadSpec(String testName, String specName) throws Exception {
		return loadSpec(testName, specName, null);
	}
	
	private String loadSpec(String testName, String specName, String version) throws Exception {
		String resName = testName + "." + specName + (version == null ? "" : ("." +version)) + ".spec.properties";
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		InputStream is = getClass().getResourceAsStream(resName);
		if (is == null)
			throw new IllegalStateException("Resource not found: " + resName);
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		while (true) {
			String line = br.readLine();
			if (line == null)
				break;
			pw.println(line);
		}
		br.close();
		pw.close();
		return sw.toString();
	}
	
	private void checkTypeDep(String depModule, String depType, 
			String refModule, String refType, String refVer, boolean res) throws Exception {
		String depVersion = db.getLatestTypeVersion(depModule, depType);
		Set<RefInfo> refs = db.getTypeRefsByDep(depModule, depType, depVersion);
		RefInfo ret = null;
		for (RefInfo ri : refs) {
			if (ri.getRefModule().equals(refModule) && ri.getRefName().equals(refType)) {
				ret = ri;
				break;
			}
		}
		Assert.assertEquals(res, ret != null);
		if (ret != null && refVer != null) {
			Assert.assertEquals(refVer, ret.getRefVersion());
		}
	}
}
