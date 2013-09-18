package us.kbase.validatejson;

import java.util.ArrayList;
import java.util.List;
import us.kbase.JsonServerMethod;
import us.kbase.JsonServerServlet;
import us.kbase.Tuple2;

//BEGIN_HEADER
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Iterator;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JacksonUtils;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.github.fge.jsonschema.report.ProcessingMessage;
import com.github.fge.jsonschema.report.ProcessingReport;
//END_HEADER

public class ValidatejsonServer extends JsonServerServlet {
    private static final long serialVersionUID = 1L;

    //BEGIN_CLASS_HEADER
	private static JsonNode loadJson(String text) throws Exception {
		InputStream is = new ByteArrayInputStream(text.getBytes());
		JsonNode ret = JacksonUtils.getReader().readTree(is);
		is.close();
		return ret;
	}
    //END_CLASS_HEADER

    public ValidatejsonServer() throws Exception {
    	super("ValidateJson");
        //BEGIN_CONSTRUCTOR
        //END_CONSTRUCTOR
    }

    @JsonServerMethod(rpc = "ValidateJson.check", tuple = true)
    public Tuple2<List<ValidationMessage>, Integer> check(String json, String jsonSchema) throws Exception {
        List<ValidationMessage> ret1 = null;
        Integer ret2 = null;
        //BEGIN check
		JsonNode schemaNode = loadJson(jsonSchema);
		final JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
		final JsonSchema schema = factory.getJsonSchema(schemaNode);
		JsonNode dataNode = loadJson(json);		
		ProcessingReport report = schema.validate(dataNode);
		ret1 = new ArrayList<ValidationMessage>();
		for (Iterator<ProcessingMessage> it = report.iterator(); it.hasNext();) {
			ProcessingMessage pm = it.next();
			String level = pm.getLogLevel().name();
			String message = pm.getMessage();
			ret1.add(new ValidationMessage().withLevel(level).withMessage(message));
		}
		ret2 = report.isSuccess() ? 1: 0;
        //END check
        Tuple2<List<ValidationMessage>, Integer> ret = new Tuple2<List<ValidationMessage>, Integer>();
        ret.setE1(ret1);
        ret.setE2(ret2);
        return ret;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: <program> <server_port>");
            return;
        }
        new ValidatejsonServer().startupServer(Integer.parseInt(args[0]));
    }
}
