
package us.kbase.workspace;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Generated;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * <p>Original spec-file type: FuncInfo</p>
 * <pre>
 * Information about a function
 *         func_string func_def - resolved func definition id.
 *         string description - the description of the function from spec file.
 *         string spec_def - reconstruction of function definition from spec file.
 *         string parsing_structure - json document describing parsing structure of function 
 *                 in spec file including types of arguments.
 *         list<spec_version> module_vers - versions of spec files containing
 *                 given func version.
 *         list<spec_version> released_module_vers - released versions of spec files 
 *                 containing given func version.
 *         list<func_string> func_vers - all versions of function with given type
 *                 name.
 *         list<func_string> released_func_vers - all released versions of function 
 *                 with given type name.
 *         list<type_string> used_type_defs - list of types (with versions) 
 *                 referred to from this function version.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "func_def",
    "description",
    "spec_def",
    "parsing_structure",
    "module_vers",
    "released_module_vers",
    "func_vers",
    "released_func_vers",
    "used_type_defs"
})
public class FuncInfo {

    @JsonProperty("func_def")
    private java.lang.String funcDef;
    @JsonProperty("description")
    private java.lang.String description;
    @JsonProperty("spec_def")
    private java.lang.String specDef;
    @JsonProperty("parsing_structure")
    private java.lang.String parsingStructure;
    @JsonProperty("module_vers")
    private List<Long> moduleVers;
    @JsonProperty("released_module_vers")
    private List<Long> releasedModuleVers;
    @JsonProperty("func_vers")
    private List<String> funcVers;
    @JsonProperty("released_func_vers")
    private List<String> releasedFuncVers;
    @JsonProperty("used_type_defs")
    private List<String> usedTypeDefs;
    private Map<java.lang.String, Object> additionalProperties = new HashMap<java.lang.String, Object>();

    @JsonProperty("func_def")
    public java.lang.String getFuncDef() {
        return funcDef;
    }

    @JsonProperty("func_def")
    public void setFuncDef(java.lang.String funcDef) {
        this.funcDef = funcDef;
    }

    public FuncInfo withFuncDef(java.lang.String funcDef) {
        this.funcDef = funcDef;
        return this;
    }

    @JsonProperty("description")
    public java.lang.String getDescription() {
        return description;
    }

    @JsonProperty("description")
    public void setDescription(java.lang.String description) {
        this.description = description;
    }

    public FuncInfo withDescription(java.lang.String description) {
        this.description = description;
        return this;
    }

    @JsonProperty("spec_def")
    public java.lang.String getSpecDef() {
        return specDef;
    }

    @JsonProperty("spec_def")
    public void setSpecDef(java.lang.String specDef) {
        this.specDef = specDef;
    }

    public FuncInfo withSpecDef(java.lang.String specDef) {
        this.specDef = specDef;
        return this;
    }

    @JsonProperty("parsing_structure")
    public java.lang.String getParsingStructure() {
        return parsingStructure;
    }

    @JsonProperty("parsing_structure")
    public void setParsingStructure(java.lang.String parsingStructure) {
        this.parsingStructure = parsingStructure;
    }

    public FuncInfo withParsingStructure(java.lang.String parsingStructure) {
        this.parsingStructure = parsingStructure;
        return this;
    }

    @JsonProperty("module_vers")
    public List<Long> getModuleVers() {
        return moduleVers;
    }

    @JsonProperty("module_vers")
    public void setModuleVers(List<Long> moduleVers) {
        this.moduleVers = moduleVers;
    }

    public FuncInfo withModuleVers(List<Long> moduleVers) {
        this.moduleVers = moduleVers;
        return this;
    }

    @JsonProperty("released_module_vers")
    public List<Long> getReleasedModuleVers() {
        return releasedModuleVers;
    }

    @JsonProperty("released_module_vers")
    public void setReleasedModuleVers(List<Long> releasedModuleVers) {
        this.releasedModuleVers = releasedModuleVers;
    }

    public FuncInfo withReleasedModuleVers(List<Long> releasedModuleVers) {
        this.releasedModuleVers = releasedModuleVers;
        return this;
    }

    @JsonProperty("func_vers")
    public List<String> getFuncVers() {
        return funcVers;
    }

    @JsonProperty("func_vers")
    public void setFuncVers(List<String> funcVers) {
        this.funcVers = funcVers;
    }

    public FuncInfo withFuncVers(List<String> funcVers) {
        this.funcVers = funcVers;
        return this;
    }

    @JsonProperty("released_func_vers")
    public List<String> getReleasedFuncVers() {
        return releasedFuncVers;
    }

    @JsonProperty("released_func_vers")
    public void setReleasedFuncVers(List<String> releasedFuncVers) {
        this.releasedFuncVers = releasedFuncVers;
    }

    public FuncInfo withReleasedFuncVers(List<String> releasedFuncVers) {
        this.releasedFuncVers = releasedFuncVers;
        return this;
    }

    @JsonProperty("used_type_defs")
    public List<String> getUsedTypeDefs() {
        return usedTypeDefs;
    }

    @JsonProperty("used_type_defs")
    public void setUsedTypeDefs(List<String> usedTypeDefs) {
        this.usedTypeDefs = usedTypeDefs;
    }

    public FuncInfo withUsedTypeDefs(List<String> usedTypeDefs) {
        this.usedTypeDefs = usedTypeDefs;
        return this;
    }

    @JsonAnyGetter
    public Map<java.lang.String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperties(java.lang.String name, Object value) {
        this.additionalProperties.put(name, value);
    }

    @Override
    public java.lang.String toString() {
        return ((((((((((((((((((((("FuncInfo"+" [funcDef=")+ funcDef)+", description=")+ description)+", specDef=")+ specDef)+", parsingStructure=")+ parsingStructure)+", moduleVers=")+ moduleVers)+", releasedModuleVers=")+ releasedModuleVers)+", funcVers=")+ funcVers)+", releasedFuncVers=")+ releasedFuncVers)+", usedTypeDefs=")+ usedTypeDefs)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
