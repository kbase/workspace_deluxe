package us.kbase.workspace.test;

import java.util.LinkedHashMap;
import java.util.Map;

import us.kbase.common.service.JsonTokenStream;

public class JsonTokenStreamOCStat {
	private static Map<JsonTokenStream, Exception> openEvents = new LinkedHashMap<JsonTokenStream, Exception>();
	
	public static void register() {
		JsonTokenStream.setDebugOpenCloseListener(new JsonTokenStream.DebugOpenCloseListener() {
			@Override
			public void onStreamOpen(JsonTokenStream instance) {
				if (openEvents.containsKey(instance))
					throw new IllegalStateException("Stream was already open", openEvents.get(instance));
				openEvents.put(instance, new Exception("Stream wasn't closed"));
			}
			@Override
			public void onStreamClosed(JsonTokenStream instance) {
				if (!openEvents.containsKey(instance))
					throw new IllegalStateException("Stream wasn't open before");
				openEvents.remove(instance);
			}
		});
	}
	
	public static void showStat() {
		for (Exception ex : openEvents.values())
			ex.printStackTrace();
		boolean notEmpty = !openEvents.isEmpty();
		openEvents.clear();
		if (notEmpty)
			throw new IllegalStateException("Some JsonTokenStream-related open-close errors occurred (see error log)");
	}
}
