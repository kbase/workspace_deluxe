
package us.kbase.workspace;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Generated;
import org.codehaus.jackson.annotate.JsonAnyGetter;
import org.codehaus.jackson.annotate.JsonAnySetter;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.annotate.JsonPropertyOrder;
import org.codehaus.jackson.map.annotate.JsonSerialize;


/**
 * <p>Original spec-file type: userperm</p>
 * <pre>
 * A permission linked with a user.
 * username user - the username
 * permission perm - the permission
 * </pre>
 * 
 */
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "user",
    "perm"
})
public class Userperm {

    @JsonProperty("user")
    private String user;
    @JsonProperty("perm")
    private String perm;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("user")
    public String getUser() {
        return user;
    }

    @JsonProperty("user")
    public void setUser(String user) {
        this.user = user;
    }

    public Userperm withUser(String user) {
        this.user = user;
        return this;
    }

    @JsonProperty("perm")
    public String getPerm() {
        return perm;
    }

    @JsonProperty("perm")
    public void setPerm(String perm) {
        this.perm = perm;
    }

    public Userperm withPerm(String perm) {
        this.perm = perm;
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
