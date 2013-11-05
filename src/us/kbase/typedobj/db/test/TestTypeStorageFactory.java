package us.kbase.typedobj.db.test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import us.kbase.typedobj.db.TypeStorage;

public class TestTypeStorageFactory {

	public static TestTypeStorage createTypeStorageWrapper(final TypeStorage inner) {
		final List<TypeStorageListener> lsts = new ArrayList<TypeStorageListener>();
		TestTypeStorage ret = (TestTypeStorage) Proxy.newProxyInstance(
				TestTypeStorage.class.getClassLoader(),
				new Class<?>[] {TestTypeStorage.class}, 
				new InvocationHandler() {
					
					@Override
					public Object invoke(Object proxy, Method method, Object[] args)
							throws Throwable {
						String name = method.getName();
						if (name.equals("addTypeStorageListener")) {
							TypeStorageListener lst = (TypeStorageListener)args[0];
							lsts.add(lst);
							return null;
						} else if (name.equals("getTypeStorageListeners")) {
							return lsts;
						} else if (name.equals("removeTypeStorageListener")) {
							TypeStorageListener lst = (TypeStorageListener)args[0];
							lsts.remove(lst);
							return null;
						} else if (name.equals("removeAllTypeStorageListeners")) {
							lsts.clear();
							return null;
						} else if (name.equals("getInnerStorage")) {
							return inner;
						} else {
							for (TypeStorageListener lst : lsts)
								lst.onMethodStart(name, args);
							Method m2 = inner.getClass().getMethod(name, method.getParameterTypes());
							try {
								Object ret = m2.invoke(inner, args);
								for (TypeStorageListener lst : lsts)
									lst.onMethodEnd(name, args, ret);
								return ret;
							} catch (InvocationTargetException ex) {
								if (ex.getCause() != null)
									throw ex.getCause();
								throw ex;
							}
						}
					}
				});
		return ret;
	}
}
