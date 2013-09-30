
package us.kbase.workspace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Generated;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import us.kbase.common.service.UObject;


/**
 * <p>Original spec-file type: ProvenanceAction</p>
 * <pre>
 * A provenance action.
 *         A provenance action is an action taken while transforming one data
 *         object to another. There may be several provenance actions taken in
 *         series. An action is typically running a script, running an api
 *         command, etc. All of the following are optional, but more information
 *         provided equates to better data provenance.
 *         
 *         timestamp time - the time the action was started.
 *         string service - the name of the service that performed this action.
 *         int service_ver - the version of the service that performed this action.
 *         string method - the method of the service that performed this action.
 *         list<UnspecifiedObject> method_params - the parameters of the method
 *                 that performed this action. If the object is a workspace object,
 *                 put the object id in the input_ws_object list and refer to it here
 *                 by the %N syntax described below.
 *         string script - the name of the script that performed this action.
 *         int script_ver - the version of the script that performed this action.
 *         string script_command_line - the command line provided to the script
 *                 that performed this action. If workspace objects were provided in
 *                 the command line, put the object id in the input_ws_object list
 *                 and refer to it here by the %N syntax described below.
 *         list<ObjectIdentifier> input_ws_objects - the workspace objects that
 *                 were used as input to this action. Refer to these objects
 *                 elsewhere in the action via the syntax %N, where N is the index
 *                 of the object in this list.
 *         list<string> intermediate_incoming - if the previous action produced 
 *                 output that 1) was not stored in a referrable way, and 2) is
 *                 used as input for this action, provide it with an arbitrary and
 *                 unique ID here, in the order of the input arguments to this action.
 *                 These IDs can be used in the method_params argument.
 *         list<string> intermediate_outgoing - if this action produced output
 *                 that 1) was not stored in a referrable way, and 2) is
 *                 used as input for the next action, provide it with an arbitrary and
 *                 unique ID here, in the order of the output values from this action.
 *                 These IDs can be used in the intermediate_incoming argument in the
 *                 next action.
 *         string description - a free text description of this action, limited to
 *                 1000 characters. Longer descriptions will be silently truncated.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "time",
    "service",
    "service_ver",
    "method",
    "method_params",
    "script",
    "script_ver",
    "script_command_line",
    "description",
    "input_ws_objects",
    "intermediate_incoming",
    "intermediate_outgoing"
})
public class ProvenanceAction {

    @JsonProperty("time")
    private String time;
    @JsonProperty("service")
    private String service;
    @JsonProperty("service_ver")
    private Integer serviceVer;
    @JsonProperty("method")
    private String method;
    @JsonProperty("method_params")
    private List<UObject> methodParams = new ArrayList<UObject>();
    @JsonProperty("script")
    private String script;
    @JsonProperty("script_ver")
    private Integer scriptVer;
    @JsonProperty("script_command_line")
    private String scriptCommandLine;
    @JsonProperty("description")
    private String description;
    @JsonProperty("input_ws_objects")
    private List<ObjectIdentity> inputWsObjects = new ArrayList<ObjectIdentity>();
    @JsonProperty("intermediate_incoming")
    private List<String> intermediateIncoming = new ArrayList<String>();
    @JsonProperty("intermediate_outgoing")
    private List<String> intermediateOutgoing = new ArrayList<String>();
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("time")
    public String getTime() {
        return time;
    }

    @JsonProperty("time")
    public void setTime(String time) {
        this.time = time;
    }

    public ProvenanceAction withTime(String time) {
        this.time = time;
        return this;
    }

    @JsonProperty("service")
    public String getService() {
        return service;
    }

    @JsonProperty("service")
    public void setService(String service) {
        this.service = service;
    }

    public ProvenanceAction withService(String service) {
        this.service = service;
        return this;
    }

    @JsonProperty("service_ver")
    public Integer getServiceVer() {
        return serviceVer;
    }

    @JsonProperty("service_ver")
    public void setServiceVer(Integer serviceVer) {
        this.serviceVer = serviceVer;
    }

    public ProvenanceAction withServiceVer(Integer serviceVer) {
        this.serviceVer = serviceVer;
        return this;
    }

    @JsonProperty("method")
    public String getMethod() {
        return method;
    }

    @JsonProperty("method")
    public void setMethod(String method) {
        this.method = method;
    }

    public ProvenanceAction withMethod(String method) {
        this.method = method;
        return this;
    }

    @JsonProperty("method_params")
    public List<UObject> getMethodParams() {
        return methodParams;
    }

    @JsonProperty("method_params")
    public void setMethodParams(List<UObject> methodParams) {
        this.methodParams = methodParams;
    }

    public ProvenanceAction withMethodParams(List<UObject> methodParams) {
        this.methodParams = methodParams;
        return this;
    }

    @JsonProperty("script")
    public String getScript() {
        return script;
    }

    @JsonProperty("script")
    public void setScript(String script) {
        this.script = script;
    }

    public ProvenanceAction withScript(String script) {
        this.script = script;
        return this;
    }

    @JsonProperty("script_ver")
    public Integer getScriptVer() {
        return scriptVer;
    }

    @JsonProperty("script_ver")
    public void setScriptVer(Integer scriptVer) {
        this.scriptVer = scriptVer;
    }

    public ProvenanceAction withScriptVer(Integer scriptVer) {
        this.scriptVer = scriptVer;
        return this;
    }

    @JsonProperty("script_command_line")
    public String getScriptCommandLine() {
        return scriptCommandLine;
    }

    @JsonProperty("script_command_line")
    public void setScriptCommandLine(String scriptCommandLine) {
        this.scriptCommandLine = scriptCommandLine;
    }

    public ProvenanceAction withScriptCommandLine(String scriptCommandLine) {
        this.scriptCommandLine = scriptCommandLine;
        return this;
    }

    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
    }

    public ProvenanceAction withDescription(String description) {
        this.description = description;
        return this;
    }

    @JsonProperty("input_ws_objects")
    public List<ObjectIdentity> getInputWsObjects() {
        return inputWsObjects;
    }

    @JsonProperty("input_ws_objects")
    public void setInputWsObjects(List<ObjectIdentity> inputWsObjects) {
        this.inputWsObjects = inputWsObjects;
    }

    public ProvenanceAction withInputWsObjects(List<ObjectIdentity> inputWsObjects) {
        this.inputWsObjects = inputWsObjects;
        return this;
    }

    @JsonProperty("intermediate_incoming")
    public List<String> getIntermediateIncoming() {
        return intermediateIncoming;
    }

    @JsonProperty("intermediate_incoming")
    public void setIntermediateIncoming(List<String> intermediateIncoming) {
        this.intermediateIncoming = intermediateIncoming;
    }

    public ProvenanceAction withIntermediateIncoming(List<String> intermediateIncoming) {
        this.intermediateIncoming = intermediateIncoming;
        return this;
    }

    @JsonProperty("intermediate_outgoing")
    public List<String> getIntermediateOutgoing() {
        return intermediateOutgoing;
    }

    @JsonProperty("intermediate_outgoing")
    public void setIntermediateOutgoing(List<String> intermediateOutgoing) {
        this.intermediateOutgoing = intermediateOutgoing;
    }

    public ProvenanceAction withIntermediateOutgoing(List<String> intermediateOutgoing) {
        this.intermediateOutgoing = intermediateOutgoing;
        return this;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperties(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}
