package us.kbase.typedobj.core.validatornew;

import java.util.List;
import java.util.Map;

import us.kbase.common.service.JsonTokenStream;
import us.kbase.common.service.UObject;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class NewUObjectTest {
	
	public static void main(String[] args) throws Exception {
		//String text = "[{\"key1\": [1, 2.0, [{\"3\": \"4\"}]]},{\"key2\": {\"key3\": [\"1\", 2, 3.0]}}]";
		//String text = "[{\"key1\": [1, 2.0, [{\"3\": \"4\"}]],\"key2\": {\"key3\": [\"1\", 2, 3.0]}}]";
		String text = "{\"params\":[\"0\", {\"1\":2},{\"1\":3},[5,6,7],[8,9], 1, 2.0, \"3\"]}";
		JsonTokenStream jts = new JsonTokenStream(text);
		ObjectMapper mapper = UObject.getMapper();
		//TypeReference<List<Map<String, NewUObject>>> type = new TypeReference<List<Map<String,NewUObject>>>() {};
		//List<Map<String, NewUObject>> obj = mapper.readValue(jts, type);
		TypeReference<Map<String, List<UObject>>> type = new TypeReference<Map<String, List<UObject>>>() {};
		Map<String, List<UObject>> obj = mapper.readValue(jts, type);
		jts.close();
		System.out.println(obj);
	}
}
