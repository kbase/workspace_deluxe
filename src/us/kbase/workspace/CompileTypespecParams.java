
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


/**
 * <p>Original spec-file type: CompileTypespecParams</p>
 * <pre>
 * Parameters for the compile_typespec function.
 *         Required parameters:
 *         One of:
 *         typespec spec - the new typespec to compile.
 *         modulename mod - the module to recompile.
 *         
 *         Optional parameters:
 *         boolean dryrun - Return, but do not save, the results of compiling the 
 *                 spec. Default true. Set to false for making permanent changes.
 *         list<typename> new_types - types in the spec to make available in the
 *                 workspace service. When compiling a spec for the first time, if
 *                 this argument is empty no types will be made available. Previously
 *                 available types remain so upon recompilation of a spec or
 *                 compilation of a new spec.
 *         list<typename> remove_types - no longer make these types available in
 *                 the workspace service for the new version of the spec. This does
 *                 not remove versions of types previously compiled.
 *         mapping<modulename, spec_version> dependencies - By default, the
 *                 latest released versions of spec dependencies will be included when
 *                 compiling a spec. Specific versions can be specified here.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "spec",
    "mod",
    "new_types",
    "remove_types",
    "dependencies",
    "dryrun"
})
public class CompileTypespecParams {

    @JsonProperty("spec")
    private java.lang.String spec;
    @JsonProperty("mod")
    private java.lang.String mod;
    @JsonProperty("new_types")
    private List<java.lang.String> newTypes = new ArrayList<java.lang.String>();
    @JsonProperty("remove_types")
    private List<java.lang.String> removeTypes = new ArrayList<java.lang.String>();
    @JsonProperty("dependencies")
    private Map<String, Integer> dependencies;
    @JsonProperty("dryrun")
    private java.lang.Integer dryrun;
    private Map<java.lang.String, Object> additionalProperties = new HashMap<java.lang.String, Object>();

    @JsonProperty("spec")
    public java.lang.String getSpec() {
        return spec;
    }

    @JsonProperty("spec")
    public void setSpec(java.lang.String spec) {
        this.spec = spec;
    }

    public CompileTypespecParams withSpec(java.lang.String spec) {
        this.spec = spec;
        return this;
    }

    @JsonProperty("mod")
    public java.lang.String getMod() {
        return mod;
    }

    @JsonProperty("mod")
    public void setMod(java.lang.String mod) {
        this.mod = mod;
    }

    public CompileTypespecParams withMod(java.lang.String mod) {
        this.mod = mod;
        return this;
    }

    @JsonProperty("new_types")
    public List<java.lang.String> getNewTypes() {
        return newTypes;
    }

    @JsonProperty("new_types")
    public void setNewTypes(List<java.lang.String> newTypes) {
        this.newTypes = newTypes;
    }

    public CompileTypespecParams withNewTypes(List<java.lang.String> newTypes) {
        this.newTypes = newTypes;
        return this;
    }

    @JsonProperty("remove_types")
    public List<java.lang.String> getRemoveTypes() {
        return removeTypes;
    }

    @JsonProperty("remove_types")
    public void setRemoveTypes(List<java.lang.String> removeTypes) {
        this.removeTypes = removeTypes;
    }

    public CompileTypespecParams withRemoveTypes(List<java.lang.String> removeTypes) {
        this.removeTypes = removeTypes;
        return this;
    }

    @JsonProperty("dependencies")
    public Map<String, Integer> getDependencies() {
        return dependencies;
    }

    @JsonProperty("dependencies")
    public void setDependencies(Map<String, Integer> dependencies) {
        this.dependencies = dependencies;
    }

    public CompileTypespecParams withDependencies(Map<String, Integer> dependencies) {
        this.dependencies = dependencies;
        return this;
    }

    @JsonProperty("dryrun")
    public java.lang.Integer getDryrun() {
        return dryrun;
    }

    @JsonProperty("dryrun")
    public void setDryrun(java.lang.Integer dryrun) {
        this.dryrun = dryrun;
    }

    public CompileTypespecParams withDryrun(java.lang.Integer dryrun) {
        this.dryrun = dryrun;
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
        return ((((((((((((((("CompileTypespecParams"+" [spec=")+ spec)+", mod=")+ mod)+", newTypes=")+ newTypes)+", removeTypes=")+ removeTypes)+", dependencies=")+ dependencies)+", dryrun=")+ dryrun)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
