package us.kbase.test.typedobj.db;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import us.kbase.kidl.KbFuncdef;
import us.kbase.kidl.KbParameter;
import us.kbase.kidl.KbScalar;
import us.kbase.kidl.KbStruct;
import us.kbase.kidl.KbStructItem;
import us.kbase.kidl.KbTypedef;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.db.FileTypeStorage;
import us.kbase.typedobj.db.MongoTypeStorage;
import us.kbase.typedobj.db.RefInfo;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.db.TypeStorage;
import us.kbase.typedobj.exceptions.NoSuchFuncException;
import us.kbase.typedobj.exceptions.NoSuchTypeException;

public class HighLoadParallelTester {
	private static TypeStorage storage = null;
	private static TypeDefinitionDB db = null;
	private static boolean useMongo = true;
	private static boolean useTestStorage = false;
	private static String adminUser = "admin";
	private static int modulePerThreadCount = 3;
	private static int threadCount = 4;
	private static Random rnd = new Random(1234567890L);

	public static void main(String[] args) throws Exception {
		File dir = new File("temp_files");
		if (!dir.exists())
			dir.mkdir();
		if (useMongo) {
			storage = new MongoTypeStorage(TypeRegisteringTest.createMongoDbConnection());
		} else {
			storage = new FileTypeStorage(dir.getAbsolutePath());
		}
		if (useTestStorage)
			storage = TestTypeStorageFactory.createTypeStorageWrapper(storage);
		storage.removeAllData();
		db = new TypeDefinitionDB(storage);
		for (int i = 0; i < threadCount * modulePerThreadCount; i++) {
			String moduleName = "TestModule" + (i + 1);
			String specDocument = "module " + moduleName + "{\n};\n";
			db.requestModuleRegistration(moduleName, adminUser);
			db.approveModuleRegistrationRequest(adminUser, moduleName, true);
			db.registerModule(specDocument, adminUser);
		}
		for (int i = 0; i < threadCount; i++) {
			new TypeDbUser(i).start();
		}
	}
	
	private static int rndInt(int threadNum, RndStage stage, int maxBorderExcluding) {
		return rnd.nextInt(maxBorderExcluding);
	}
	
	private static String getStorageObjects() throws Exception {
		Map<String, Long> ret = storage.listObjects();
		for (String key : new ArrayList<String>(ret.keySet())) 
			if (ret.get(key) == 0)
				ret.remove(key);
		return "" + ret;
	}

	private static class TypeDbUser extends Thread {
		private final int threadNum;
		
		public TypeDbUser(int threadNum) {
			this.threadNum = threadNum;
		}
		
		@Override
		public void run() {
			int lastObjectNum = 0;
			try {
				long time = System.currentTimeMillis();
				for (int iter = 0; iter < 400; iter++) {
					List<String> allModuleNames = db.getAllRegisteredModules();
					for (String module : allModuleNames) {
						for (String type : db.getAllRegisteredTypes(module)) {
							try {
								TypeDefName tdn = new TypeDefName(module, type);
								db.getJsonSchema(tdn);
								db.getTypeParsingDocument(tdn);
								db.getFuncRefsByRef(new TypeDefId(tdn));
							} catch (NoSuchTypeException ignore) {}
						}
						for (String func : db.getAllRegisteredFuncs(module)) {
							try {
								db.getFuncParsingDocument(module, func);
								db.getFuncRefsByDep(module, func, db.getLatestFuncVersion(module, func));
							} catch (NoSuchFuncException ignore) {}
						}
					}
					String moduleName = allModuleNames.get(threadNum * modulePerThreadCount + 
							rndInt(threadNum, RndStage.moduleSelection, modulePerThreadCount));
					Map<String, TypeDescr> typesToSave = new LinkedHashMap<String, TypeDescr>();
					for (String type : db.getAllRegisteredTypes(moduleName)) {
						KbTypedef typePrs = db.getTypeParsingDocument(new TypeDefName(moduleName, type));
						KbStruct str = (KbStruct)typePrs.getAliasType();
						TypeDescr descr = new TypeDescr();
						for (KbStructItem item : str.getItems()) {
							KbScalar iType = (KbScalar)item.getItemType();
							String iTypeName = iType.getScalarType().toString();
							descr.propTypes.add(iTypeName.substring(0, iTypeName.length() - 4));
						}
						typesToSave.put(type, descr);
					}
					Map<String, FuncDescr> funcsToSave = new LinkedHashMap<String, FuncDescr>();
					for (String func : db.getAllRegisteredFuncs(moduleName)) {
						KbFuncdef funcPrs = db.getFuncParsingDocument(moduleName, func);
						FuncDescr descr = new FuncDescr();
						for (KbParameter param : funcPrs.getParameters()) {
							KbTypedef type = (KbTypedef)param.getType();
							descr.paramTypes.add(type.getName());
						}
						funcsToSave.put(func, descr);
					}
					boolean processTypes = rndInt(threadNum, RndStage.typeOrFunc, 2) == 0;
					List<String> newTypes = new ArrayList<String>();
					if (processTypes) {
						List<String> typesCouldBeDeleted = new ArrayList<String>();
						for (String type : db.getAllRegisteredTypes(moduleName)) {
							Set<RefInfo> deps = db.getFuncRefsByRef(db.resolveTypeDefId(
									new TypeDefId(new TypeDefName(moduleName, type))));
							if (deps.size() == 0)
								typesCouldBeDeleted.add(type);
						}
						int maxBorder = 1;
						if (db.getAllRegisteredTypes(moduleName).size() > 0) {
							maxBorder++;
							if (typesCouldBeDeleted.size() > 0)
								maxBorder++;
						}
						int action = rndInt(threadNum, RndStage.createChangeOrDelete, maxBorder);
						if (action == 0) {  // Create
							lastObjectNum++;
							String newTypeName = "test_type_" + lastObjectNum;
							typesToSave.put(newTypeName, new TypeDescr("int"));
							newTypes.add(newTypeName);
						} else if (action == 1) {  // Change
							List<String> types = db.getAllRegisteredTypes(moduleName);
							String type = types.get(rndInt(threadNum, RndStage.pickForChange, types.size()));
							TypeDescr descr = typesToSave.get(type);
							maxBorder = 1;
							if (descr.propTypes.size() > 1)
								maxBorder++;
							boolean changeUp = rndInt(threadNum, RndStage.changeUpOrDown, maxBorder) == 0;
							if (changeUp) {
								descr.propTypes.add("int");
							} else {
								descr.propTypes.remove(descr.propTypes.size() - 1);
							}
						} else {  // Delete
							String typeToDelete = typesCouldBeDeleted.get(rndInt(threadNum, 
									RndStage.pickForChange, typesCouldBeDeleted.size()));
							typesToSave.remove(typeToDelete);
						}
					} else {  // Process functions
						int maxBorder = 1;
						if (db.getAllRegisteredFuncs(moduleName).size() > 0) {
							maxBorder += 2;
						}
						int action = rndInt(threadNum, RndStage.createChangeOrDelete, maxBorder);
						if (action == 0) {  // Create
							lastObjectNum++;
							String newFuncName = "test_func_" + lastObjectNum;
							funcsToSave.put(newFuncName, new FuncDescr());
						} else if (action == 1) {  // Change
							List<String> funcs = db.getAllRegisteredFuncs(moduleName);
							String func = funcs.get(rndInt(threadNum, RndStage.pickForChange, funcs.size()));
							FuncDescr descr = funcsToSave.get(func);
							maxBorder = 1;
							if (descr.paramTypes.size() > 0)
								maxBorder++;
							boolean changeUp = rndInt(threadNum, RndStage.changeUpOrDown, maxBorder) == 0;
							if (changeUp) {
								List<String> types = db.getAllRegisteredTypes(moduleName);
								if (types.size() > 0)
									descr.paramTypes.add(types.get(types.size() - 1));
							} else {
								descr.paramTypes.remove(descr.paramTypes.size() - 1);
							}
						} else {  // Delete
							List<String> funcs = db.getAllRegisteredFuncs(moduleName);
							String funcToDelete = funcs.get(rndInt(threadNum, 
									RndStage.pickForChange, funcs.size()));
							funcsToSave.remove(funcToDelete);
						}
					}
					StringBuilder specSb = new StringBuilder();
					specSb.append("module ").append(moduleName).append("{\n");
					for (String type : typesToSave.keySet()) {
						TypeDescr descr = typesToSave.get(type);
						specSb.append("    typedef structure {\n");
						for (int i = 0; i < descr.propTypes.size(); i++) {
							specSb.append("        ").append(descr.propTypes.get(i)).append(" ");
							specSb.append("prop").append(i + 1).append(";\n");
						}
						specSb.append("    } ").append(type).append(";\n\n");
					}
					for (String func : funcsToSave.keySet()) {
						FuncDescr descr = funcsToSave.get(func);
						specSb.append("    funcdef ").append(func).append("(");
						for (int i = 0; i < descr.paramTypes.size(); i++) {
							if (i > 0)
								specSb.append(", ");
							specSb.append(descr.paramTypes.get(i)).append(" ").append("param").append(i + 1);
						}
						specSb.append(") returns ();\n\n");
					}
					specSb.append("};\n");
					db.registerModule(specSb.toString(), newTypes, adminUser);
					db.releaseModule(moduleName, adminUser, true);
					System.out.println("After change in module " + moduleName + ": " + getStorageObjects());
					if ((iter + 1) % 100 == 0) {
						long timeDiff = System.currentTimeMillis() - time;
						long iterTime = timeDiff / (iter + 1);
						System.err.println("O------------------------------------------------------------");
						System.err.println("| Thread " + threadNum + ": iter=" + (iter + 1) + ", time=" + timeDiff + " ms.," +
								" iterTime=" + iterTime + " ms.");
						System.err.println("O------------------------------------------------------------");
					}
				}
			} catch (Throwable ex) {
				try {
					Thread.sleep(20000);
				} catch (InterruptedException ignore) {}
				ex.printStackTrace();
				System.exit(1);
			}
		}
	}
	
	private enum RndStage {
		moduleSelection, typeOrFunc, createChangeOrDelete, pickForChange, changeUpOrDown
	}
	
	private static class TypeDescr {
		List<String> propTypes = new ArrayList<String>();
		public TypeDescr(String... types) {
			propTypes.addAll(Arrays.asList(types));
		}
	}

	private static class FuncDescr {
		List<String> paramTypes = new ArrayList<String>();
		public FuncDescr(String... types) {
			paramTypes.addAll(Arrays.asList(types));
		}
	}
}
