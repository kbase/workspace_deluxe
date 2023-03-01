
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
 * <p>Original spec-file type: Title</p>
 * <pre>
 * Title
 * Represents the title or name of a resource.
 * The 'title_string' field is required; if no value is supplied for 'title_type', it
 * defaults to 'title'.
 * If the title is in a language other than English, the 'title_type' should be set to
 * 'translated_title', and the appropriate BCP-47 tag supplied in the 'title_language'
 * field.
 * Note that the workspace checks that the 'title_language' field adheres to IETF
 * BCP-47 syntax rules, but it does not check the validity of the tag.
 * title_string - a string used as a title for a resource.
 *         Examples:
 *                 - Amaranthus hypochondriacus genome
 *                 - ?????????? ???????????????? ????????????????????????????????
 * title_language (optional) - language that the title is in, as a IETF BCP-47 tag.
 *         Examples:
 *                 - fr
 *                 - jp-JP
 *                 - zh-Hant-CN
 *                 - en-Latn-GB
 *                 - mn-Cyrl
 * title_type (optional) - the type of title described.
 *         Valid 'title_type' values:
 *                 - title
 *                 - subtitle
 *                 - alternative_title
 *                 - translated_title
 *                 - other
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "title_string",
    "title_language",
    "title_type"
})
public class Title {

    @JsonProperty("title_string")
    private String titleString;
    @JsonProperty("title_language")
    private String titleLanguage;
    @JsonProperty("title_type")
    private String titleType;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("title_string")
    public String getTitleString() {
        return titleString;
    }

    @JsonProperty("title_string")
    public void setTitleString(String titleString) {
        this.titleString = titleString;
    }

    public Title withTitleString(String titleString) {
        this.titleString = titleString;
        return this;
    }

    @JsonProperty("title_language")
    public String getTitleLanguage() {
        return titleLanguage;
    }

    @JsonProperty("title_language")
    public void setTitleLanguage(String titleLanguage) {
        this.titleLanguage = titleLanguage;
    }

    public Title withTitleLanguage(String titleLanguage) {
        this.titleLanguage = titleLanguage;
        return this;
    }

    @JsonProperty("title_type")
    public String getTitleType() {
        return titleType;
    }

    @JsonProperty("title_type")
    public void setTitleType(String titleType) {
        this.titleType = titleType;
    }

    public Title withTitleType(String titleType) {
        this.titleType = titleType;
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
        return ((((((((("Title"+" [titleString=")+ titleString)+", titleLanguage=")+ titleLanguage)+", titleType=")+ titleType)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
