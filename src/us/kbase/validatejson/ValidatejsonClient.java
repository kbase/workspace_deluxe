package us.kbase.validatejson;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.codehaus.jackson.type.TypeReference;
import us.kbase.JsonClientCaller;
import us.kbase.Tuple2;

public class ValidatejsonClient {
    private JsonClientCaller caller;

    public ValidatejsonClient(String url) throws MalformedURLException {
        caller = new JsonClientCaller(new URL(url));
    }

    public Tuple2<List<ValidationMessage>, Integer> check(String json, String jsonSchema) throws Exception {
        List<Object> args = new ArrayList<Object>();
        args.add(json);
        args.add(jsonSchema);
        TypeReference<Tuple2<List<ValidationMessage>, Integer>> retType = new TypeReference<Tuple2<List<ValidationMessage>, Integer>>() {};
        Tuple2<List<ValidationMessage>, Integer> res = caller.jsonrpcCall("ValidateJson.check", args, retType, true, false);
        return res;
    }
}
