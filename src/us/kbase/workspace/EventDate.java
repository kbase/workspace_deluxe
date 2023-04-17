
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
 * <p>Original spec-file type: EventDate</p>
 * <pre>
 * EventDate
 *                 Represents an event in the lifecycle of a resource and the date it occurred on.
 *                 See https://support.datacite.org/docs/datacite-metadata-schema-v44-recommended-and-optional-properties#8-date
 *                 for more information on the events (below).
 *                 Both event and date are required fields.
 *                 date - the date associated with the event. The date may be in the format
 *                         YYYY, YYYY-MM, or YYYY-MM-DD.
 *                         Examples:
 *                                 - '2001'
 *                                 - 2021-05
 *                                 - 1998-02-15
 *                 event - the event that occurred.
 *                         Valid 'event' values:
 *                                 - accepted
 *                                 - available
 *                                 - copyrighted
 *                                 - collected
 *                                 - created
 *                                 - issued
 *                                 - submitted
 *                                 - updated
 *                                 - valid
 *                                 - withdrawn
 *                                 - other
 * </pre>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "date",
    "event"
})
public class EventDate {

    @JsonProperty("date")
    private String date;
    @JsonProperty("event")
    private String event;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("date")
    public String getDate() {
        return date;
    }

    @JsonProperty("date")
    public void setDate(String date) {
        this.date = date;
    }

    public EventDate withDate(String date) {
        this.date = date;
        return this;
    }

    @JsonProperty("event")
    public String getEvent() {
        return event;
    }

    @JsonProperty("event")
    public void setEvent(String event) {
        this.event = event;
    }

    public EventDate withEvent(String event) {
        this.event = event;
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
        return ((((((("EventDate"+" [date=")+ date)+", event=")+ event)+", additionalProperties=")+ additionalProperties)+"]");
    }

}
