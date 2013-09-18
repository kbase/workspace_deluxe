package us.kbase.validatejson.test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

import us.kbase.Tuple2;
import us.kbase.validatejson.ValidatejsonClient;
import us.kbase.validatejson.ValidationMessage;

public class MainTest {
    public static void main(String[] args) throws Exception {
    	runClient();
    }
    
    private static void runClient() throws Exception {
		ValidatejsonClient cl = new ValidatejsonClient("http://localhost:9999"); //140.221.85.98:8282");
		String jsonSchema = loadRes("schema.json");
		String jsonData = loadRes("data.json");
		Tuple2<List<ValidationMessage>, Integer> answer = cl.check(jsonData, jsonSchema);
		System.out.println("Messages:");
		for (ValidationMessage msg : answer.getE1()) {
			System.out.println("    " + msg.getLevel() + ": " + msg.getMessage());
		}
		System.out.println("--------------------------");
		System.out.println("Json data is " + (answer.getE2() == 1 ? "" : "not ") + "valid");
	}
    
    private static String loadRes(String name) throws Exception {
    	StringBuilder ret = new StringBuilder();
    	BufferedReader br = new BufferedReader(new InputStreamReader(MainTest.class.getResourceAsStream(name + ".properties")));
    	while (true) {
    		String l = br.readLine();
    		if (l == null)
    			break;
    		ret.append(l).append('\n');
    	}
    	br.close();
    	return ret.toString();
    }
}
