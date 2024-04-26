package us.kbase.test.workspace.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.test.common.TestCommon.EI;
import static us.kbase.test.common.TestCommon.opt;

import java.util.Collections;
import java.util.Map;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.test.common.TestCommon;
import us.kbase.workspace.database.DynamicConfig;
import us.kbase.workspace.database.DynamicConfig.DynamicConfigUpdate;

public class DynamicConfigTest {
	
	private static final String BACKEND_SCALING_TXT = "backend-file-retrieval-scaling";

	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(DynamicConfig.class).usingGetClass().verify();
		EqualsVerifier.forClass(DynamicConfigUpdate.class).usingGetClass().verify();
	}
	
	@Test
	public void buildConfigEmpty() throws Exception {
		final DynamicConfig dc = DynamicConfig.getBuilder().build();
		
		assertThat("incorrect scaling", dc.getBackendScaling(), is(EI));
		assertThat("incorrect map", dc.toMap(), is(Collections.emptyMap()));
	}
	
	@Test
	public void buildConfigEmptyFromMap() throws Exception {
		final DynamicConfig dc = DynamicConfig.getBuilder()
				.withMap(Collections.emptyMap()).build();
		
		assertThat("incorrect scaling", dc.getBackendScaling(), is(EI));
		assertThat("incorrect map", dc.toMap(), is(Collections.emptyMap()));
	}
	
	@Test
	public void buildConfigMinimal() throws Exception {
		final DynamicConfig dc = DynamicConfig.getBuilder().withBackendScaling(1).build();
		
		assertThat("incorrect scaling", dc.getBackendScaling(), is(opt(1)));
		assertThat("incorrect map", dc.toMap(), is(ImmutableMap.of(BACKEND_SCALING_TXT, 1)));
		
		final DynamicConfig dc2 = DynamicConfig.getBuilder().withBackendScaling(1000).build();
		
		assertThat("incorrect scaling", dc2.getBackendScaling(), is(opt(1000)));
		assertThat("incorrect map", dc2.toMap(), is(ImmutableMap.of(BACKEND_SCALING_TXT, 1000)));
	}
	
	@Test
	public void buildConfigMinimalFromMap() throws Exception {
		final DynamicConfig dc = DynamicConfig.getBuilder()
				.withMap(ImmutableMap.of(BACKEND_SCALING_TXT, 1)).build();
		
		assertThat("incorrect scaling", dc.getBackendScaling(), is(opt(1)));
		assertThat("incorrect map", dc.toMap(), is(ImmutableMap.of(BACKEND_SCALING_TXT, 1)));
		
		final DynamicConfig dc2 = DynamicConfig.getBuilder()
				.withMap(ImmutableMap.of(BACKEND_SCALING_TXT, 100000)).build();
		
		assertThat("incorrect scaling", dc2.getBackendScaling(), is(opt(100000)));
		assertThat("incorrect map", dc2.toMap(), is(ImmutableMap.of(BACKEND_SCALING_TXT, 100000)));
	}
	
	@Test
	public void buildConfigOverrideMap() throws Exception {
		final DynamicConfig dc = DynamicConfig.getBuilder()
				.withMap(ImmutableMap.of(BACKEND_SCALING_TXT, 1))
				.withBackendScaling(10)
				.build();
		
		assertThat("incorrect scaling", dc.getBackendScaling(), is(opt(10)));
		assertThat("incorrect map", dc.toMap(), is(ImmutableMap.of(BACKEND_SCALING_TXT, 10)));
	}
	
	@Test
	public void buildConfigOverrideMapWithNoop() throws Exception {
		final DynamicConfig dc = DynamicConfig.getBuilder()
				.withMap(ImmutableMap.of(BACKEND_SCALING_TXT, 10))
				.withMap(Collections.emptyMap())
				.build();
		
		assertThat("incorrect scaling", dc.getBackendScaling(), is(opt(10)));
		assertThat("incorrect map", dc.toMap(), is(ImmutableMap.of(BACKEND_SCALING_TXT, 10)));
	}
	
	@Test
	public void buildConfigOverrideSetters() throws Exception {
		final DynamicConfig dc = DynamicConfig.getBuilder()
				.withBackendScaling(10)
				.withMap(ImmutableMap.of(BACKEND_SCALING_TXT, 42))
				.build();
		
		assertThat("incorrect scaling", dc.getBackendScaling(), is(opt(42)));
		assertThat("incorrect map", dc.toMap(), is(ImmutableMap.of(BACKEND_SCALING_TXT, 42)));
	}
	
	@Test
	public void configWithBackendScalingFail() throws Exception {
		final String err = "backend scaling must be > 0";
		failConfigWithBackendScaling(0, new IllegalArgumentException(err));
		failConfigWithBackendScaling(-1000, new IllegalArgumentException(err));
	}

	public void failConfigWithBackendScaling(final int backendScaling, final Exception expected) {
		try {
			DynamicConfig.getBuilder().withBackendScaling(backendScaling);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void configWithMapFail() throws Exception {
		final String err = "backend-file-retrieval-scaling must be an integer > 0";
		failConfigWithMap(
				ImmutableMap.of(BACKEND_SCALING_TXT, 0), new IllegalArgumentException(err));
		failConfigWithMap(
				ImmutableMap.of(BACKEND_SCALING_TXT, -1000), new IllegalArgumentException(err));
		failConfigWithMap(
				ImmutableMap.of(BACKEND_SCALING_TXT, "six"), new IllegalArgumentException(err));
		failConfigWithMap(ImmutableMap.of(BACKEND_SCALING_TXT, 1, "ayyy", "fonz", "baar", "foo"),
				new IllegalArgumentException("Unexpected key in configuration map: ayyy"));
		failConfigWithMap(ImmutableMap.of("ribaldry", 1, "ayyyy", "fonz", "baar", "foo"),
				new IllegalArgumentException("Unexpected key in configuration map: ayyyy"));
	}

	private void failConfigWithMap(final Map<String, Object> map, final Exception expected) {
		try {
			DynamicConfig.getBuilder().withMap(map);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void getDefaultUpdate() throws Exception {
		final DynamicConfigUpdate dcu = DynamicConfigUpdate.getDefault();
		
		assertThat("incorrect set", dcu.toSet(), is(ImmutableMap.of(BACKEND_SCALING_TXT, 1)));
		assertThat("incorrect map", dcu.toRemove(), is(Collections.emptySet()));
	}
	
	@Test
	public void buildUpdateEmpty() throws Exception {
		final DynamicConfigUpdate dcu = DynamicConfigUpdate.getBuilder().build();
		
		assertThat("incorrect set", dcu.toSet(), is(Collections.emptyMap()));
		assertThat("incorrect map", dcu.toRemove(), is(Collections.emptySet()));
	}
	
	@Test
	public void buildUpdateEmptyFromMap() throws Exception {
		final DynamicConfigUpdate dcu = DynamicConfigUpdate.getBuilder()
				.withMap(Collections.emptyMap()).build();
		
		assertThat("incorrect set", dcu.toSet(), is(Collections.emptyMap()));
		assertThat("incorrect map", dcu.toRemove(), is(Collections.emptySet()));
	}
	
	@Test
	public void buildUpdateMinimal() throws Exception {
		final DynamicConfigUpdate dcu = DynamicConfigUpdate.getBuilder()
				.withBackendScaling(1).build();
		
		assertThat("incorrect set", dcu.toSet(), is(ImmutableMap.of(BACKEND_SCALING_TXT, 1)));
		assertThat("incorrect map", dcu.toRemove(), is(Collections.emptySet()));
		
		final DynamicConfigUpdate dcu2 = DynamicConfigUpdate.getBuilder()
				.withBackendScaling(1000).build();
		
		assertThat("incorrect set", dcu2.toSet(), is(ImmutableMap.of(BACKEND_SCALING_TXT, 1000)));
		assertThat("incorrect map", dcu2.toRemove(), is(Collections.emptySet()));
	}
	
	@Test
	public void buildUpdateMinimalFromMap() throws Exception {
		final DynamicConfigUpdate dcu = DynamicConfigUpdate.getBuilder()
				.withMap(ImmutableMap.of(BACKEND_SCALING_TXT, 1)).build();
		
		assertThat("incorrect set", dcu.toSet(), is(ImmutableMap.of(BACKEND_SCALING_TXT, 1)));
		assertThat("incorrect map", dcu.toRemove(), is(Collections.emptySet()));
		
		final DynamicConfigUpdate dcu2 = DynamicConfigUpdate.getBuilder()
				.withMap(ImmutableMap.of(BACKEND_SCALING_TXT, 100000)).build();
		
		assertThat("incorrect set", dcu2.toSet(),
				is(ImmutableMap.of(BACKEND_SCALING_TXT, 100000)));
		assertThat("incorrect map", dcu2.toRemove(), is(Collections.emptySet()));
	}
	
	@Test
	public void buildUpdateOverrideMap() throws Exception {
		final DynamicConfigUpdate dcu = DynamicConfigUpdate.getBuilder()
				.withMap(ImmutableMap.of(BACKEND_SCALING_TXT, 1))
				.withBackendScaling(10)
				.build();
		
		assertThat("incorrect set", dcu.toSet(), is(ImmutableMap.of(BACKEND_SCALING_TXT, 10)));
		assertThat("incorrect map", dcu.toRemove(), is(Collections.emptySet()));
	}
	
	@Test
	public void buildUpdateOverrideMapWithNoop() throws Exception {
		final DynamicConfigUpdate dcu = DynamicConfigUpdate.getBuilder()
				.withMap(ImmutableMap.of(BACKEND_SCALING_TXT, 10))
				.withMap(Collections.emptyMap())
				.build();
		
		assertThat("incorrect set", dcu.toSet(), is(ImmutableMap.of(BACKEND_SCALING_TXT, 10)));
		assertThat("incorrect map", dcu.toRemove(), is(Collections.emptySet()));
	}
	
	@Test
	public void buildUpdateOverrideSetters() throws Exception {
		final DynamicConfigUpdate dcu = DynamicConfigUpdate.getBuilder()
				.withBackendScaling(10)
				.withMap(ImmutableMap.of(BACKEND_SCALING_TXT, 42))
				.build();
		
		assertThat("incorrect set", dcu.toSet(), is(ImmutableMap.of(BACKEND_SCALING_TXT, 42)));
		assertThat("incorrect map", dcu.toRemove(), is(Collections.emptySet()));
	}
	
	@Test
	public void updateWithBackendScalingFail() throws Exception {
		final String err = "backend scaling must be > 0";
		failUpdateWithBackendScaling(0, new IllegalArgumentException(err));
		failUpdateWithBackendScaling(-1000, new IllegalArgumentException(err));
	}

	public void failUpdateWithBackendScaling(final int backendScaling, final Exception expected) {
		try {
			DynamicConfigUpdate.getBuilder().withBackendScaling(backendScaling);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void updateWithMapFail() throws Exception {
		final String err = "backend-file-retrieval-scaling must be an integer > 0";
		failUpdateWithMap(
				ImmutableMap.of(BACKEND_SCALING_TXT, 0), new IllegalArgumentException(err));
		failUpdateWithMap(
				ImmutableMap.of(BACKEND_SCALING_TXT, -1000), new IllegalArgumentException(err));
		failUpdateWithMap(
				ImmutableMap.of(BACKEND_SCALING_TXT, "six"), new IllegalArgumentException(err));
		failUpdateWithMap(ImmutableMap.of(BACKEND_SCALING_TXT, 1, "ayyy", "fonz", "baar", "foo"),
				new IllegalArgumentException("Unexpected key in configuration map: ayyy"));
		failUpdateWithMap(ImmutableMap.of("ribaldry", 1, "ayyyy", "fonz", "baar", "foo"),
				new IllegalArgumentException("Unexpected key in configuration map: ayyyy"));
	}

	private void failUpdateWithMap(final Map<String, Object> map, final Exception expected) {
		try {
			DynamicConfigUpdate.getBuilder().withMap(map);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}

}
