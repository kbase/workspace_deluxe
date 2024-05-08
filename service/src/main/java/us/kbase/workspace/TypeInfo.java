
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
 * <p>Original spec-file type: TypeInfo</p>
 * <pre>
 * Information about a type
 *                 type_string type_def - resolved type definition id.
 *                 string description - the description of the type from spec file.
 *                 string spec_def - reconstruction of type definition from spec file.
 *                 jsonschema json_schema - JSON schema of this type.
 *                 string parsing_structure - json document describing parsing structure of type
 *                         in spec file including involved sub-types.
 *                 list<spec_version> module_vers - versions of spec-files containing
 *                         given type version.
 *                 list<spec_version> released_module_vers - versions of released spec-files
 *                         containing given type version.
 *                 list<type_string> type_vers - all versions of type with given type name.
 *                 list<type_string> released_type_vers - all released versions of type with
 *                         given type name.
 *                 list<func_string> using_func_defs - list of functions (with versions)
 *                         referring to this type version.
 *                 list<type_string> using_type_defs - list of types (with versions)
 *                         referring to this type version.
 *                 list<type_string> used_type_defs - list of types (with versions)
 *                         referred from this type version.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "type_def",
    "description",
    "spec_def",
    "json_schema",
    "parsing_structure",
    "module_vers",
    "released_module_vers",
    "type_vers",
    "released_type_vers",
    "using_func_defs",
    "using_type_defs",
    "used_type_defs"
})
public class TypeInfo {

    @JsonProperty("type_def")
    private java.lang.String typeDef;
    @JsonProperty("description")
    private java.lang.String description;
    @JsonProperty("spec_def")
    private java.lang.String specDef;
    @JsonProperty("json_schema")
    private java.lang.String jsonSchema;
    @JsonProperty("parsing_structure")
    private java.lang.String parsingStructure;
    @JsonProperty("module_vers")
    private List<Long> moduleVers;
    @JsonProperty("released_module_vers")
    private List<Long> releasedModuleVers;
    @JsonProperty("type_vers")
    private List<String> typeVers;
    @JsonProperty("released_type_vers")
    private List<String> releasedTypeVers;
    @JsonProperty("using_func_defs")
    private List<String> usingFuncDefs;
    @JsonProperty("using_type_defs")
    private List<String> usingTypeDefs;
    @JsonProperty("used_type_defs")
    private List<String> usedTypeDefs;
    private Map<java.lang.String, Object> additionalProperties = new HashMap<java.lang.String, Object>();

    @JsonProperty("type_def")
    public java.lang.String getTypeDef() {
        return typeDef;
    }

    @JsonProperty("type_def")
    public void setTypeDef(java.lang.String typeDef) {
        this.typeDef = typeDef;
    }

    public TypeInfo withTypeDef(java.lang.String typeDef) {
        this.typeDef = typeDef;
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

    public TypeInfo withDescription(java.lang.String description) {
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

    public TypeInfo withSpecDef(java.lang.String specDef) {
        this.specDef = specDef;
        return this;
    }

    @JsonProperty("json_schema")
    public java.lang.String getJsonSchema() {
        return jsonSchema;
    }

    @JsonProperty("json_schema")
    public void setJsonSchema(java.lang.String jsonSchema) {
        this.jsonSchema = jsonSchema;
    }

    public TypeInfo withJsonSchema(java.lang.String jsonSchema) {
        this.jsonSchema = jsonSchema;
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

    public TypeInfo withParsingStructure(java.lang.String parsingStructure) {
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

    public TypeInfo withModuleVers(List<Long> moduleVers) {
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

    public TypeInfo withReleasedModuleVers(List<Long> releasedModuleVers) {
        this.releasedModuleVers = releasedModuleVers;
        return this;
    }

    @JsonProperty("type_vers")
    public List<String> getTypeVers() {
        return typeVers;
    }

    @JsonProperty("type_vers")
    public void setTypeVers(List<String> typeVers) {
        this.typeVers = typeVers;
    }

    public TypeInfo withTypeVers(List<String> typeVers) {
        this.typeVers = typeVers;
        return this;
    }

    @JsonProperty("released_type_vers")
    public List<String> getReleasedTypeVers() {
        return releasedTypeVers;
    }

    @JsonProperty("released_type_vers")
    public void setReleasedTypeVers(List<String> releasedTypeVers) {
        this.releasedTypeVers = releasedTypeVers;
    }

    public TypeInfo withReleasedTypeVers(List<String> releasedTypeVers) {
        this.releasedTypeVers = releasedTypeVers;
        return this;
    }

    @JsonProperty("using_func_defs")
    public List<String> getUsingFuncDefs() {
        return usingFuncDefs;
    }

    @JsonProperty("using_func_defs")
    public void setUsingFuncDefs(List<String> usingFuncDefs) {
        this.usingFuncDefs = usingFuncDefs;
    }

    public TypeInfo withUsingFuncDefs(List<String> usingFuncDefs) {
        this.usingFuncDefs = usingFuncDefs;
        return this;
    }

    @JsonProperty("using_type_defs")
    public List<String> getUsingTypeDefs() {
        return usingTypeDefs;
    }

    @JsonProperty("using_type_defs")
    public void setUsingTypeDefs(List<String> usingTypeDefs) {
        this.usingTypeDefs = usingTypeDefs;
    }

    public TypeInfo withUsingTypeDefs(List<String> usingTypeDefs) {
        this.usingTypeDefs = usingTypeDefs;
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

    public TypeInfo withUsedTypeDefs(List<String> usedTypeDefs) {
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
        return ((((((((((((((((((((((((((("TypeInfo"+" [typeDef=")+ typeDef)+", description=")+ description)+", specDef=")+ specDef)+", jsonSchema=")+ jsonSchema)+", parsingStructure=")+ parsingStructure)+", moduleVers=")+ moduleVers)+", releasedModuleVers=")+ releasedModuleVers)+", typeVers=")+ typeVers)+", releasedTypeVers=")+ releasedTypeVers)+", usingFuncDefs=")+ usingFuncDefs)+", usingTypeDefs=")+ usingTypeDefs)+", usedTypeDefs=")+ usedTypeDefs)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
