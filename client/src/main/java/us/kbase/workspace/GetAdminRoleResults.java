
package us.kbase.workspace;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Generated;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * <p>Original spec-file type: GetAdminRoleResults</p>
 * <pre>
 * The results of the get_admin_role call.
 *         adminrole - the users's administration role, one of `none`, `read`, or `full`.
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "adminrole"
})
public class GetAdminRoleResults {

    @JsonProperty("adminrole")
    private String adminrole;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("adminrole")
    public String getAdminrole() {
        return adminrole;
    }

    @JsonProperty("adminrole")
    public void setAdminrole(String adminrole) {
        this.adminrole = adminrole;
    }

    public GetAdminRoleResults withAdminrole(String adminrole) {
        this.adminrole = adminrole;
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

    @Override
    public String toString() {
        return ((((("GetAdminRoleResults"+" [adminrole=")+ adminrole)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
