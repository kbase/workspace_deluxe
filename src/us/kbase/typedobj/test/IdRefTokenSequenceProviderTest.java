package us.kbase.typedobj.test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.common.service.JsonTokenStream;
import us.kbase.typedobj.core.IdRefTokenSequenceProvider;
import us.kbase.typedobj.core.JsonTokenStreamWriter;
import us.kbase.typedobj.core.JsonTokenValidationSchema;

public class IdRefTokenSequenceProviderTest {
	
	//TODO TEST add more tests to cover IRTSP.
	// as of 19/4/11 this only tests that embedded maps are marked as sorted correctly.
	
	@Test
	public void sortedSingleMap() throws Exception {
		final Map<String, Object> data = new LinkedHashMap<>();
		data.put("first", "a");
		data.put("second", "a");
		data.put("third", "a");
		
		checkSort(data, true);
	}
	
	@Test
	public void unsortedSingleMap() throws Exception {
		final Map<String, Object> data = new LinkedHashMap<>();
		data.put("first", "a");
		data.put("third", "a");
		data.put("second", "a");
		
		checkSort(data, false);
	}
	
	@Test
	public void sortedEmbeddedMap() throws Exception {
		final Map<String, String> embedded = new LinkedHashMap<>();
		embedded.put("first", "a");
		embedded.put("second", "a");
		embedded.put("third", "a");
		
		final Map<String, Object> data = new LinkedHashMap<>();
		data.put("first", "a");
		data.put("second", embedded);
		data.put("third", "a");
		
		checkSort(data, true);
	}
	
	@Test
	public void sortedEmbeddedMapDifferentKeys() throws Exception {
		final Map<String, String> embedded = new LinkedHashMap<>();
		embedded.put("one", "a");
		embedded.put("two", "a");
		embedded.put("twuee", "a");
		
		final Map<String, Object> data = new LinkedHashMap<>();
		data.put("first", "a");
		data.put("second", embedded);
		data.put("third", "a");
		
		checkSort(data, true);
	}
	
	@Test
	public void unsortedEmbeddedMap() throws Exception {
		final Map<String, String> embedded = new LinkedHashMap<>();
		embedded.put("first", "a");
		embedded.put("third", "a");
		embedded.put("second", "a");
		
		final Map<String, Object> data = new LinkedHashMap<>();
		data.put("first", "a");
		data.put("second", embedded);
		data.put("third", "a");
		
		checkSort(data, false);
	}
	
	@Test
	public void sortedEmbeddedMapInList() throws Exception {
		final Map<String, String> embedded = new LinkedHashMap<>();
		embedded.put("first", "a");
		embedded.put("second", "a");
		embedded.put("third", "a");
		
		final Map<String, Object> data = new LinkedHashMap<>();
		data.put("first", "a");
		data.put("second", Arrays.asList("foo", embedded, "bar"));
		data.put("third", "a");
		
		checkSort(data, true);
	}
	
	@Test
	public void unsortedEmbeddedMapInList() throws Exception {
		final Map<String, String> embedded = new LinkedHashMap<>();
		embedded.put("first", "a");
		embedded.put("third", "a");
		embedded.put("second", "a");
		
		final Map<String, Object> data = new LinkedHashMap<>();
		data.put("first", "a");
		data.put("second", Arrays.asList("foo", embedded, "bar"));
		data.put("third", "a");
		
		checkSort(data, false);
	}
	
	@Test
	public void sortedDoubleEmbeddedMap() throws Exception {
		final Map<String, String> embedded1 = new LinkedHashMap<>();
		embedded1.put("first", "a");
		embedded1.put("second", "a");
		embedded1.put("zee", "a");
		
		final Map<String, Object> embedded2 = new LinkedHashMap<>();
		embedded2.put("first", "a");
		embedded2.put("second", embedded1);
		embedded2.put("twee", "a");
		
		final Map<String, Object> data = new LinkedHashMap<>();
		data.put("first", "a");
		data.put("second", embedded2);
		data.put("third", "a");
		
		checkSort(data, true);
	}

	private void checkSort(final Map<String, Object> data, final boolean sorted) throws Exception {
		final IdRefTokenSequenceProvider p = new IdRefTokenSequenceProvider(
				new JsonTokenStream(new ObjectMapper().writeValueAsString(data)),
				JsonTokenValidationSchema.parseJsonSchema(JsonSchemas.EMPTY_STRUCT),
				null);
		
		new JsonTokenStreamWriter().writeTokens(p, mock(JsonGenerator.class));
		
		assertThat("incorrect sorted", p.isSorted(), is(sorted));
	}

}
