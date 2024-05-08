package us.kbase.test.workspace.database.provenance;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.test.common.TestCommon.inst;
import static us.kbase.test.common.TestCommon.list;
import static us.kbase.test.common.TestCommon.opt;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.test.common.MapBuilder;
import us.kbase.test.common.TestCommon;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.provenance.ExternalData;
import us.kbase.workspace.database.provenance.ProvenanceAction;
import us.kbase.workspace.database.provenance.SubAction;

public class ProvenanceActionTest {
	
	private static class ProvExpected {
		
		// set all fields to default returns from getters
		private Optional<Instant> time = Optional.empty();
		private Optional<String> caller = Optional.empty();
		private Optional<String> service = Optional.empty();
		private Optional<String> serviceVersion = Optional.empty();
		private Optional<String> method = Optional.empty();
		private List<Object> methodParameters = Collections.emptyList();
		private Optional<String> script = Optional.empty();
		private Optional<String> scriptVersion = Optional.empty();
		private Optional<String> commandLine = Optional.empty();
		private List<String> wsobjs = Collections.emptyList();
		private List<String> incomingArgs = Collections.emptyList();
		private List<String> outgoingArgs = Collections.emptyList();
		private List<ExternalData> externalData = Collections.emptyList();
		private List<SubAction> subActions = Collections.emptyList();
		private Map<String, String> custom = Collections.emptyMap();
		private Optional<String> description = Optional.empty();
		private List<String> resolvedObjects = Collections.emptyList();
		
		public ProvExpected withTime(Optional<Instant> time) {
			this.time = time;
			return this;
		}
		
		public ProvExpected withCaller(Optional<String> caller) {
			this.caller = caller;
			return this;
		}
		
		public ProvExpected withService(Optional<String> service) {
			this.service = service;
			return this;
		}
		
		public ProvExpected withServiceVersion(Optional<String> serviceVersion) {
			this.serviceVersion = serviceVersion;
			return this;
		}
		
		public ProvExpected withMethod(Optional<String> method) {
			this.method = method;
			return this;
		}
		
		public ProvExpected withMethodParameters(List<Object> methodParameters) {
			this.methodParameters = methodParameters;
			return this;
		}
		
		public ProvExpected withScript(Optional<String> script) {
			this.script = script;
			return this;
		}
		
		public ProvExpected withScriptVersion(Optional<String> scriptVersion) {
			this.scriptVersion = scriptVersion;
			return this;
		}
		
		public ProvExpected withCommandLine(Optional<String> commandLine) {
			this.commandLine = commandLine;
			return this;
		}
		
		public ProvExpected withWsobjs(List<String> wsobjs) {
			this.wsobjs = wsobjs;
			return this;
		}
		
		public ProvExpected withIncomingArgs(List<String> incomingArgs) {
			this.incomingArgs = incomingArgs;
			return this;
		}
		
		public ProvExpected withOutgoingArgs(List<String> outgoingArgs) {
			this.outgoingArgs = outgoingArgs;
			return this;
		}
		
		public ProvExpected withExternalData(List<ExternalData> externalData) {
			this.externalData = externalData;
			return this;
		}
		
		public ProvExpected withSubActions(List<SubAction> subActions) {
			this.subActions = subActions;
			return this;
		}
		
		public ProvExpected withCustom(Map<String, String> custom) {
			this.custom = custom;
			return this;
		}
		
		public ProvExpected withDescription(Optional<String> description) {
			this.description = description;
			return this;
		}
		
		public ProvExpected withResolvedObjects(List<String> resolvedObjects) {
			this.resolvedObjects = resolvedObjects;
			return this;
		}
		
		private ProvExpected assertCorrect(final ProvenanceAction action) {
			assertThat("incorrect caller", action.getCaller(), is(caller));
			assertThat("incorrect command line", action.getCommandLine(), is(commandLine));
			assertThat("incorrect custom", action.getCustom(), is(custom));
			assertThat("incorrect desc", action.getDescription(), is(description));
			assertThat("incorrect ext data", action.getExternalData(), is(externalData));
			assertThat("incorrect inc args", action.getIncomingArgs(), is(incomingArgs));
			assertThat("incorrect method", action.getMethod(), is(method));
			assertThat("incorrect params", action.getMethodParameters(), is(methodParameters));
			assertThat("incorrect out args", action.getOutgoingArgs(), is(outgoingArgs));
			assertThat("incorrect resolved objs",
					action.getResolvedObjects(), is(resolvedObjects));
			assertThat("incorrect script", action.getScript(), is(script));
			assertThat("incorrect script ver", action.getScriptVersion(), is(scriptVersion));
			assertThat("incorrect service", action.getServiceName(), is(service));
			assertThat("incorrect service ver", action.getServiceVersion(), is(serviceVersion));
			assertThat("incorrect subactions", action.getSubActions(), is(subActions));
			assertThat("incorrect time", action.getTime(), is(time));
			assertThat("incorrect ws objs", action.getWorkspaceObjects(), is(wsobjs));
			return this;
		}
	}

	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(ProvenanceAction.class).usingGetClass().verify();
	}
	
	@Test
	public void isEmpty() throws Exception {
		// Test with each field individually to test the at least one field set requirement
		// Except resolved objects, which must be paired with workspace objects
		
		assertThat("incorrect isEmpty",
				ProvenanceAction.getBuilder().isEmpty(), is(true));
		
		assertThat("incorrect isEmpty",
				ProvenanceAction.getBuilder().withCaller("caller").isEmpty(), is(false));

		
		assertThat("incorrect isEmpty",
				ProvenanceAction.getBuilder().withCommandLine("--dostuff").isEmpty(), is(false));

		assertThat("incorrect isEmpty",
				ProvenanceAction.getBuilder().withCustom(ImmutableMap.of("foo", "bar")).isEmpty(),
				is(false));

		assertThat("incorrect isEmpty",
				ProvenanceAction.getBuilder().withDescription("desc").isEmpty(), is(false));

		assertThat("incorrect isEmpty",
				ProvenanceAction.getBuilder().withExternalData(list(
						ExternalData.getBuilder().withDataID("dataid").build())
						).isEmpty(), is(false));

		assertThat("incorrect isEmpty",
				ProvenanceAction.getBuilder().withIncomingArgs(list("foo", "bar")).isEmpty(),
				is(false));

		assertThat("incorrect isEmpty",
				ProvenanceAction.getBuilder().withMethod("meth").isEmpty(), is(false));

		assertThat("incorrect isEmpty",
				ProvenanceAction.getBuilder().withMethodParameters(list(list("foo"))).isEmpty(),
				is(false));

		assertThat("incorrect isEmpty",
				ProvenanceAction.getBuilder().withOutgoingArgs(list("yay")).isEmpty(), is(false));

		assertThat("incorrect isEmpty",
				ProvenanceAction.getBuilder()
						.withWorkspaceObjects(list("foo/bar;baz/bat", "65/3/81"))
						.withResolvedObjects(list("1/1/1", "65/3/81"))
						.isEmpty(),
				is(false));

		assertThat("incorrect isEmpty",
				ProvenanceAction.getBuilder().withScript("script").isEmpty(), is(false));

		assertThat("incorrect isEmpty",
				ProvenanceAction.getBuilder().withScriptVersion("sver").isEmpty(), is(false));

		assertThat("incorrect isEmpty",
				ProvenanceAction.getBuilder().withServiceName("serv").isEmpty(), is(false));

		assertThat("incorrect isEmpty",
				ProvenanceAction.getBuilder().withServiceVersion("servver").isEmpty(), is(false));

		assertThat("incorrect isEmpty",
				ProvenanceAction.getBuilder().withSubActions(list(
						SubAction.getBuilder().withName("n").build()
						)).isEmpty(),
				is(false));

		assertThat("incorrect isEmpty",
				ProvenanceAction.getBuilder().withTime(inst(100000)).isEmpty(), is(false));

		assertThat("incorrect isEmpty",
				ProvenanceAction.getBuilder().withWorkspaceObjects(list("foo/bar", "1/w/1"))
						.isEmpty(),
				is(false));
	}
	
	@Test
	public void buildMinimal() throws Exception {
		// test with each field individually to test the at least one field set requirement
		// Except resolved objects, which must be paired with workspace objects
		
		new ProvExpected().withCaller(opt("caller")).assertCorrect(
				ProvenanceAction.getBuilder().withCaller("caller").build());

		new ProvExpected().withCommandLine(opt("--dostuff")).assertCorrect(
				ProvenanceAction.getBuilder().withCommandLine("--dostuff").build());

		new ProvExpected().withCustom(ImmutableMap.of("foo", "bar")).assertCorrect(
				ProvenanceAction.getBuilder().withCustom(ImmutableMap.of("foo", "bar")).build());

		new ProvExpected().withDescription(opt("desc")).assertCorrect(
				ProvenanceAction.getBuilder().withDescription("desc").build());

		new ProvExpected().withExternalData(list(
				ExternalData.getBuilder().withDataID("dataid").build()))
				.assertCorrect(ProvenanceAction.getBuilder().withExternalData(list(
						ExternalData.getBuilder().withDataID("dataid").build())
						).build());

		new ProvExpected().withIncomingArgs(list("foo", "bar")).assertCorrect(
				ProvenanceAction.getBuilder().withIncomingArgs(list("foo", "bar")).build());

		new ProvExpected().withMethod(opt("meth")).assertCorrect(
				ProvenanceAction.getBuilder().withMethod("meth").build());

		new ProvExpected().withMethodParameters(list(list("foo"))).assertCorrect(
				ProvenanceAction.getBuilder().withMethodParameters(list(list("foo"))).build());

		new ProvExpected().withOutgoingArgs(list("yay")).assertCorrect(
				ProvenanceAction.getBuilder().withOutgoingArgs(list("yay")).build());

		new ProvExpected()
				.withResolvedObjects(list("1/1/1", "65/3/81"))
				.withWsobjs(list("foo/bar;baz/bat", "65/3/81"))
				.assertCorrect(
				ProvenanceAction.getBuilder()
						.withWorkspaceObjects(list("foo/bar;baz/bat", "65/3/81"))
						.withResolvedObjects(list("1/1/1", "65/3/81"))
						.build());

		new ProvExpected().withScript(opt("script")).assertCorrect(
				ProvenanceAction.getBuilder().withScript("script").build());

		new ProvExpected().withScriptVersion(opt("sver")).assertCorrect(
				ProvenanceAction.getBuilder().withScriptVersion("sver").build());

		new ProvExpected().withService(opt("serv")).assertCorrect(
				ProvenanceAction.getBuilder().withServiceName("serv").build());

		new ProvExpected().withServiceVersion(opt("servver")).assertCorrect(
				ProvenanceAction.getBuilder().withServiceVersion("servver").build());

		new ProvExpected().withSubActions(list(SubAction.getBuilder().withName("n").build()))
				.assertCorrect(ProvenanceAction.getBuilder().withSubActions(list(
						SubAction.getBuilder().withName("n").build()
						)).build());

		new ProvExpected().withTime(opt(inst(100000))).assertCorrect(
				ProvenanceAction.getBuilder().withTime(inst(100000)).build());

		new ProvExpected().withWsobjs(list("foo/bar", "1/w/1")).assertCorrect(
				ProvenanceAction.getBuilder().withWorkspaceObjects(list("foo/bar", "1/w/1"))
						.build());
	}
	
	@Test
	public void buildMinimalWithNulls() throws Exception {
		final ProvenanceAction pa = ProvenanceAction.getBuilder()
				.withCaller("caller")
				.withCommandLine(null)
				.withCustom(null)
				.withDescription(null)
				.withExternalData(null)
				.withIncomingArgs(null)
				.withMethod(null)
				.withMethodParameters(null)
				.withOutgoingArgs(null)
				.withResolvedObjects(null)
				.withScript(null)
				.withScriptVersion(null)
				.withServiceName(null)
				.withServiceVersion(null)
				.withSubActions(null)
				.withTime(null)
				.withWorkspaceObjects(null)
				.build();
		
		new ProvExpected().withCaller(opt("caller")).assertCorrect(pa);
		
		// need to test caller field as well
		final ProvenanceAction pa2 = ProvenanceAction.getBuilder()
				.withCaller(null).withCommandLine("--boop").build();
		
		new ProvExpected().withCommandLine(opt("--boop")).assertCorrect(pa2);
	}
	@Test
	public void buildMinimalWithEmpties() throws Exception {
		final ProvenanceAction pa = ProvenanceAction.getBuilder()
				.withCaller("caller")
				.withCommandLine("  \t  ")
				.withCustom(Collections.emptyMap())
				.withDescription("  \t  ")
				.withExternalData(Collections.emptyList())
				.withIncomingArgs(Collections.emptyList())
				.withMethod("  \t  ")
				.withMethodParameters(Collections.emptyList())
				.withOutgoingArgs(Collections.emptyList())
				.withResolvedObjects(Collections.emptyList())
				.withScript("  \t  ")
				.withScriptVersion("  \t  ")
				.withServiceName("  \t  ")
				.withServiceVersion("  \t  ")
				.withSubActions(Collections.emptyList())
				.withWorkspaceObjects(Collections.emptyList())
				.build();
		
		new ProvExpected().withCaller(opt("caller")).assertCorrect(pa);
		
		// need to test caller field as well
		final ProvenanceAction pa2 = ProvenanceAction.getBuilder()
				.withCaller("   \t   ").withCommandLine("--boop").build();
		
		new ProvExpected().withCommandLine(opt("--boop")).assertCorrect(pa2);
	}
	
	@Test
	public void buildMaximal() throws Exception {
		// tests string trimming where appropriate
		final ProvenanceAction pa = ProvenanceAction.getBuilder()
				.withCaller("   \t  caller   \t    ")
				.withCommandLine("  -rf /   ")
				.withCustom(ImmutableMap.of("baz", "bat", "whee\n", "  whoo"))
				.withDescription("\t thingamajig")
				.withExternalData(list(ExternalData.getBuilder()
						.withDescription("another thingamajig").build()))
				.withIncomingArgs(list("  A   ", "B", "C"))
				.withMethod("   \t transcendental prognostication  \t   ")
				.withMethodParameters(list(
						ImmutableMap.of("whoo", "   large quantities   "),
						null,
						list("  foobar\t\n")))
				.withOutgoingArgs(list("D", "\tE  ", "F\n"))
				.withWorkspaceObjects(list("  foo/bar/1;  7/8/9\t", "8/9"))
				.withResolvedObjects(list("   \t  1/1/1", "76/8/45   "))
				.withScript("  rm  ")
				.withScriptVersion("  v798.1.1  ")
				.withServiceName("\tLOL Wombat\n")
				.withServiceVersion("\n90\n")
				.withSubActions(list(SubAction.getBuilder().withName("subby sub sub").build()))
				.withTime(inst(20000))
				.build();
		
		new ProvExpected()
				.withCaller(opt("caller"))
				.withCommandLine(opt("-rf /"))
				.withCustom(ImmutableMap.of("baz", "bat", "whee\n", "  whoo"))
				.withDescription(opt("thingamajig"))
				.withExternalData(list(ExternalData.getBuilder()
						.withDescription("another thingamajig").build()))
				.withIncomingArgs(list("  A   ", "B", "C"))
				.withMethod(opt("transcendental prognostication"))
				.withMethodParameters(list(
						ImmutableMap.of("whoo", "   large quantities   "),
						null,
						list("  foobar\t\n")))
				.withOutgoingArgs(list("D", "\tE  ", "F\n"))
				.withResolvedObjects(list("1/1/1", "76/8/45"))
				.withScript(opt("rm"))
				.withScriptVersion(opt("v798.1.1"))
				.withService(opt("LOL Wombat"))
				.withServiceVersion(opt("90"))
				.withSubActions(list(SubAction.getBuilder().withName("subby sub sub").build()))
				.withTime(opt(inst(20000)))
				.withWsobjs(list("foo/bar/1;  7/8/9", "8/9"))
				.assertCorrect(pa);
	}
	
	@Test
	public void buildOverwriteWithNulls() throws Exception {
		final ProvenanceAction pa = ProvenanceAction.getBuilder()
				.withCaller("   \t  caller   \t    ")
				.withCommandLine("  -rf /   ")
				.withCustom(ImmutableMap.of("baz", "bat", "whee\n", "  whoo"))
				.withDescription("\t thingamajig")
				.withExternalData(list(ExternalData.getBuilder()
						.withDescription("another thingamajig").build()))
				.withIncomingArgs(list("  A   ", "B", "C"))
				.withMethod("   \t transcendental prognostication  \t   ")
				.withMethodParameters(list(
						ImmutableMap.of("whoo", "   large quantities   "),
						null,
						list("  foobar\t\n")))
				.withOutgoingArgs(list("D", "\tE  ", "F\n"))
				.withWorkspaceObjects(list("  foo/bar/1\t", "8/9"))
				.withResolvedObjects(list("   \t  1/1/1", "76/8/45   "))
				.withScript("  rm  ")
				.withScriptVersion("  v798.1.1  ")
				.withServiceName("\tLOL Wombat\n")
				.withServiceVersion("\n90\n")
				.withSubActions(list(SubAction.getBuilder().withName("subby sub sub").build()))
				.withTime(inst(20000))
				.withCommandLine(null)
				.withCustom(null)
				.withDescription(null)
				.withExternalData(null)
				.withIncomingArgs(null)
				.withMethod(null)
				.withMethodParameters(null)
				.withOutgoingArgs(null)
				.withResolvedObjects(null)
				.withScript(null)
				.withScriptVersion(null)
				.withServiceName(null)
				.withServiceVersion(null)
				.withSubActions(null)
				.withTime(null)
				.withWorkspaceObjects(null)
				.build();
		
		new ProvExpected().withCaller(opt("caller")).assertCorrect(pa);
		
		// need to test caller field as well
		final ProvenanceAction pa2 = ProvenanceAction.getBuilder()
				.withCaller("foo").withCaller(null).withCommandLine("--boop").build();
		
		new ProvExpected().withCommandLine(opt("--boop")).assertCorrect(pa2);
	}
	
	@Test
	public void buildOverwriteWithEmpties() throws Exception {
		final ProvenanceAction pa = ProvenanceAction.getBuilder()
				.withCaller("   \t  caller   \t    ")
				.withCommandLine("  -rf /   ")
				.withCustom(ImmutableMap.of("baz", "bat", "whee\n", "  whoo"))
				.withDescription("\t thingamajig")
				.withExternalData(list(ExternalData.getBuilder()
						.withDescription("another thingamajig").build()))
				.withIncomingArgs(list("  A   ", "B", "C"))
				.withMethod("   \t transcendental prognostication  \t   ")
				.withMethodParameters(list(
						ImmutableMap.of("whoo", "   large quantities   "),
						null,
						list("  foobar\t\n")))
				.withOutgoingArgs(list("D", "\tE  ", "F\n"))
				.withWorkspaceObjects(list("  foo/bar/1\t", "8/9", "baz/baz/1"))
				.withResolvedObjects(list("   \t  1/1/1", "76/8/45   ", "6/6/1"))
				.withScript("  rm  ")
				.withScriptVersion("  v798.1.1  ")
				.withServiceName("\tLOL Wombat\n")
				.withServiceVersion("\n90\n")
				.withSubActions(list(SubAction.getBuilder().withName("subby sub sub").build()))
				.withTime(inst(20000))
				.withCommandLine("   \t   ")
				.withCustom(Collections.emptyMap())
				.withDescription("   \t   ")
				.withExternalData(list())
				.withIncomingArgs(list())
				.withMethod("   \t   ")
				.withMethodParameters(list())
				.withOutgoingArgs(list())
				.withResolvedObjects(list())
				.withScript("   \t   ")
				.withScriptVersion("   \t   ")
				.withServiceName("   \t   ")
				.withServiceVersion("   \t   ")
				.withSubActions(list())
				.withTime(null)
				.withWorkspaceObjects(list())
				.build();
		
		new ProvExpected().withCaller(opt("caller")).assertCorrect(pa);
		
		// need to test caller field as well
		final ProvenanceAction pa2 = ProvenanceAction.getBuilder()
				.withCaller("foo").withCaller(null).withCommandLine("--boop").build();
		
		new ProvExpected().withCommandLine(opt("--boop")).assertCorrect(pa2);
	}
	
	private MapBuilder<String, Object> mapb() {
		return MapBuilder.<String, Object>newHashMap();
	}
	
	@Test
	public void buildWithMethodParameters() throws Exception {
		// Method parameter's code is a bit more complex than the others due to the deep
		// copy so we do some more exhaustive testing here
		// We make two copies of the input to be sure that identity equality is false and
		// switch non List Collections to List
		final List<Object> input = list(
				null,
				"string",
				8,
				94L,
				true,
				8.9F,
				90.4D,
				list(null, "thing", 5, 96L, false, 94.5F, 82.3D, mapb()
						.with("  null  ", null)
						.with("string", "X")
						.with("int", 96)
						.with("long", 894L)
						.with("boolean", true)
						.with("float", 90.5F)
						.with("double", 42.3D)
						.with("list", list(null, "   string  ", 8, 94L, true, 8.9F, 90.4D))
						.build()
						),
				new LinkedHashSet<>(list(null, "thing2", 6, 97L, true, 94.6F, 82.4D, mapb()
						.with("  null  ", null)
						.with("string", "X")
						.with("int", 96)
						.with("long", 894L)
						.with("boolean", true)
						.with("float", 90.5F)
						.with("double", 42.3D)
						.build()
						)),
				mapb()
					.with("  null  ", null)
					.with("string", "X")
					.with("int", 96)
					.with("long", 894L)
					.with("boolean", true)
					.with("float", 90.5F)
					.with("double", 42.3D)
					.with("list", list(null, "   string  ", 8, 94L, true, 8.9F, 90.4D, mapb()
							.with("string", "X")
							.with("int", 96)
							.with("long", 894L)
							.with("boolean", true)
							.with("float", 90.5F)
							.with("double", 42.3D)
							.with("set", new LinkedHashSet<>(list("foo", false)))
							.build()
							)
					).build()
				);
		final List<Object> expected = list(
				null,
				"string",
				8,
				94L,
				true,
				8.9F,
				90.4D,
				list(null, "thing", 5, 96L, false, 94.5F, 82.3D, mapb()
						.with("  null  ", null)
						.with("string", "X")
						.with("int", 96)
						.with("long", 894L)
						.with("boolean", true)
						.with("float", 90.5F)
						.with("double", 42.3D)
						.with("list", list(null, "   string  ", 8, 94L, true, 8.9F, 90.4D))
						.build()
						),
				list(null, "thing2", 6, 97L, true, 94.6F, 82.4D, mapb()
						.with("  null  ", null)
						.with("string", "X")
						.with("int", 96)
						.with("long", 894L)
						.with("boolean", true)
						.with("float", 90.5F)
						.with("double", 42.3D)
						.build()
						),
				mapb()
					.with("  null  ", null)
					.with("string", "X")
					.with("int", 96)
					.with("long", 894L)
					.with("boolean", true)
					.with("float", 90.5F)
					.with("double", 42.3D)
					.with("list", list(null, "   string  ", 8, 94L, true, 8.9F, 90.4D, mapb()
							.with("string", "X")
							.with("int", 96)
							.with("long", 894L)
							.with("boolean", true)
							.with("float", 90.5F)
							.with("double", 42.3D)
							.with("set", list("foo", false))
							.build()
							)
					).build()
				);
		new ProvExpected().withMethodParameters(expected).assertCorrect(
				ProvenanceAction.getBuilder().withMethodParameters(input).build());
	}
	
	@Test
	public void buildWithWorkspaceObjectsSetAndUnset() throws Exception {
		// Test setting and unsetting resolved and workspace objects.
		// The builder should never allow a different number of references in the resolved
		// & standard objects, except that having no resolved objects is always ok.
		final ProvenanceAction.Builder b = ProvenanceAction.getBuilder()
				.withCaller("c")
				.withWorkspaceObjects(list("foo/bar", "1/1/1", "baz/1/7"))
				// WSO must be set before RO, and RO must be the same size
				.withResolvedObjects(list("7/6/2", "1/1/1", "9/1/7"));
		
		final ProvExpected e = new ProvExpected()
				.withCaller(opt("c"))
				.withWsobjs(list("foo/bar", "1/1/1", "baz/1/7"))
				.withResolvedObjects(list("7/6/2", "1/1/1", "9/1/7"));
		e.assertCorrect(b.build());
		
		// unsetting resolved objects should always work
		e.withResolvedObjects(list()).assertCorrect(b.withResolvedObjects(null).build());
		
		// resetting resolved objects should work at long as the workspace objects are the same
		// size
		e.withResolvedObjects(list("8/9/2", "1/1/1", "6/8/7")).assertCorrect(
				b.withResolvedObjects(list("8/9/2", "1/1/1", "6/8/7")).build());

		// unsetting resolved objects should always work
		e.withResolvedObjects(list()).assertCorrect(b.withResolvedObjects(list()).build());
		
		// unsetting workspace objects should unset resolved objects
		e.withResolvedObjects(list("8/9/2", "1/1/1", "6/8/7")).assertCorrect(
				b.withResolvedObjects(list("8/9/2", "1/1/1", "6/8/7")).build());
		
		e.withWsobjs(list()).withResolvedObjects(list()).assertCorrect(
				b.withWorkspaceObjects(null).build());
		
		e.withWsobjs(list("foo/bar", "1/1/1", "baz/1/7"))
				.withResolvedObjects(list("8/9/2", "1/1/1", "6/8/7")).assertCorrect(
						b.withWorkspaceObjects(list("foo/bar", "1/1/1", "baz/1/7"))
							.withResolvedObjects(list("8/9/2", "1/1/1", "6/8/7")).build());
		
		e.withWsobjs(list()).withResolvedObjects(list()).assertCorrect(
				b.withWorkspaceObjects(list()).build());
		
		// Setting workspace objects should work as long as any current resolved objects
		// are the same size
		e.withWsobjs(list("foo/bar", "1/1/1")).withResolvedObjects(list("8/9/2", "1/1/1"))
				.assertCorrect(b.withWorkspaceObjects(list("foo/bar", "1/1/1"))
									.withResolvedObjects(list("8/9/2", "1/1/1")).build());
	
		e.withWsobjs(list("8/bar", "one/one/1")).withResolvedObjects(list("8/9/2", "1/1/1"))
				.assertCorrect(b.withWorkspaceObjects(list("8/bar", "one/one/1")).build());
	}
	
	@Test
	public void immutableMethodParameters() throws Exception {
		final List<String> toMutate = new LinkedList<>(list("a", "b", "c"));
		final List<Object> params = list(
				"foo", null, ImmutableMap.of("bar", "baz", "bat", toMutate));
		final ProvenanceAction pa = ProvenanceAction.getBuilder()
				.withMethodParameters(params).build();
		
		// check mutating input doesn't mutate PA
		toMutate.remove("b");
		assertThat("no mutate", toMutate, is(list("a", "c")));
		assertThat("incorrect params", pa.getMethodParameters(), is(list(
				"foo", null, ImmutableMap.of("bar", "baz", "bat", list("a", "b", "c")))));
		
		// check mutating return doesn't mutate PA
		try {
			pa.getMethodParameters().add("foo");
			fail("expected exception");
		} catch (UnsupportedOperationException e) {
			// test passed
		}
		@SuppressWarnings("unchecked")
		final Map<String, Object> pos2 = (Map<String, Object>) pa.getMethodParameters().get(2);
		try {
			pos2.put("foo", "bar");
			fail("expected exception");
		} catch (UnsupportedOperationException e) {
			// test passed
		}
		@SuppressWarnings("unchecked")
		final List<String> keyBat = (List<String>) pos2.get("bat");
		try {
			keyBat.add("foo");
			fail("expected exception");
		} catch (UnsupportedOperationException e) {
			// test passed
		}
	}
	
	// all these immutable tests are so similar but trying to DRY them up makes a real mess
	// maybe there's a better way to handle it but I'm not spending any more time on it
	
	@Test
	public void immutableWorkspaceObjects() throws Exception {
		final List<String> toMutate = new LinkedList<>(list("foo/bar", "baz/bat/1"));
		final ProvenanceAction pa = ProvenanceAction.getBuilder()
				.withWorkspaceObjects(toMutate).build();
		
		// check mutating doesn't mutate PA
		toMutate.add("badref");
		assertThat("no mutate", toMutate, is(list("foo/bar", "baz/bat/1", "badref")));
		assertThat("incorrect ws objs",
				pa.getWorkspaceObjects(), is(list("foo/bar", "baz/bat/1")));
		
		// check mutating return doesn't mutate PA
		try {
			pa.getWorkspaceObjects().add("foo");
			fail("expected exception");
		} catch (UnsupportedOperationException e) {
			// test passed
		}
	}
	
	@Test
	public void immutableIncomingParams() throws Exception {
		final List<String> toMutate = new LinkedList<>(list("1", "2"));
		final ProvenanceAction pa = ProvenanceAction.getBuilder()
				.withIncomingArgs(toMutate).build();
		
		// check mutating doesn't mutate PA
		toMutate.add("3");
		assertThat("no mutate", toMutate, is(list("1", "2", "3")));
		assertThat("incorrect inc args", pa.getIncomingArgs(), is(list("1", "2")));

		// check mutating return doesn't mutate PA
		try {
			pa.getIncomingArgs().add("foo");
			fail("expected exception");
		} catch (UnsupportedOperationException e) {
			// test passed
		}
	}
	
	@Test
	public void immutableOutgoingParams() throws Exception {
		final List<String> toMutate = new LinkedList<>(list("1", "2"));
		final ProvenanceAction pa = ProvenanceAction.getBuilder()
				.withOutgoingArgs(toMutate).build();
		
		// check mutating doesn't mutate PA
		toMutate.add("3");
		assertThat("no mutate", toMutate, is(list("1", "2", "3")));
		assertThat("incorrect out args", pa.getOutgoingArgs(), is(list("1", "2")));

		// check mutating return doesn't mutate PA
		try {
			pa.getOutgoingArgs().add("foo");
			fail("expected exception");
		} catch (UnsupportedOperationException e) {
			// test passed
		}
	}
	
	@Test
	public void immutableExternalData() throws Exception {
		final List<ExternalData> toMutate = new LinkedList<>(list(
				ExternalData.getBuilder().withDataID("d").build()));
		final ProvenanceAction pa = ProvenanceAction.getBuilder()
				.withExternalData(toMutate).build();
		
		// check mutating doesn't mutate PA
		toMutate.add(ExternalData.getBuilder().withDataID("e").build());
		assertThat("no mutate", toMutate, is(list(
				ExternalData.getBuilder().withDataID("d").build(),
				ExternalData.getBuilder().withDataID("e").build()
				)));
		assertThat("incorrect ext data", pa.getExternalData(), is(list(
				ExternalData.getBuilder().withDataID("d").build())));

		// check mutating return doesn't mutate PA
		try {
			pa.getExternalData().remove(0);
			fail("expected exception");
		} catch (UnsupportedOperationException e) {
			// test passed
		}
	}
	
	@Test
	public void immutableSubActions() throws Exception {
		final List<SubAction> toMutate = new LinkedList<>(list(
				SubAction.getBuilder().withName("f").build()));
		final ProvenanceAction pa = ProvenanceAction.getBuilder()
				.withSubActions(toMutate).build();
		
		// check mutating doesn't mutate PA
		toMutate.add(SubAction.getBuilder().withName("g").build());
		assertThat("no mutate", toMutate, is(list(
				SubAction.getBuilder().withName("f").build(),
				SubAction.getBuilder().withName("g").build()
				)));
		assertThat("incorrect sub actions", pa.getSubActions(), is(list(
				SubAction.getBuilder().withName("f").build())));

		// check mutating return doesn't mutate PA
		try {
			pa.getSubActions().remove(0);
			fail("expected exception");
		} catch (UnsupportedOperationException e) {
			// test passed
		}
	}
	
	@Test
	public void immutableCustom() throws Exception {
		final Map<String, String> toMutate = new HashMap<>(ImmutableMap.of(
				"foo", "bar", "baz", "bat"));
		final ProvenanceAction pa = ProvenanceAction.getBuilder()
				.withCustom(toMutate).build();
		
		// check mutating doesn't mutate PA
		toMutate.put("whee", "whoo");
		assertThat("no mutate", toMutate, is(ImmutableMap.of(
				"foo", "bar", "baz", "bat", "whee", "whoo")));
		assertThat("incorrect custom", pa.getCustom(), is(ImmutableMap.of(
				"foo", "bar", "baz", "bat")));

		// check mutating return doesn't mutate PA
		try {
			pa.getCustom().remove("foo");
			fail("expected exception");
		} catch (UnsupportedOperationException e) {
			// test passed
		}
	}
	
	@Test
	public void immutableResolvedObjects() throws Exception {
		final List<String> toMutate = new LinkedList<>(list("6/8/22", "1/1/1"));
		final ProvenanceAction pa = ProvenanceAction.getBuilder()
				.withWorkspaceObjects(list("1/1/1", "1/1/1"))
				.withResolvedObjects(toMutate).build();
		
		// check mutating doesn't mutate PA
		toMutate.add("badref");
		assertThat("no mutate", toMutate, is(list("6/8/22", "1/1/1", "badref")));
		assertThat("incorrect resolved objs",
				pa.getResolvedObjects(), is(list("6/8/22", "1/1/1")));
		
		// check mutating return doesn't mutate PA
		try {
			pa.getResolvedObjects().add("foo");
			fail("expected exception");
		} catch (UnsupportedOperationException e) {
			// test passed
		}
	}
	
	@Test
	public void withMethodParametersFailBadMapKey() throws Exception {
		final Map<String, Object> nullMap = new HashMap<>();
		nullMap.put("a", "b");
		nullMap.put("b", "c");
		nullMap.put(null, "bar");
		nullMap.put("z", "x");
		failWithMethodParameters(
				list(
					// make location enter & exit list -> map -> list
					list("one", ImmutableMap.of("foo", "bar"), list("two")),
					null,
					// make the location enter & exit a map -> list -> map
					ImmutableMap.of("baz", "bat", "whee", list("foo", ImmutableMap.of("a", "b"))),
					ImmutableMap.of("bar", "baz", "whee", "whoo", "foo", list(nullMap)),
					null),
				"Non string key in map at /3/foo/0 in method parameters");
		failWithMethodParameters(
				list(list("one", ImmutableMap.of(1, "bar"), list("two"))),
				"Non string key in map at /0/1 in method parameters");
	}
	
	@Test
	public void withMethodParametersFailCantSerialize() throws Exception {
		failWithMethodParameters(
				list(
					// make location enter & exit list -> map -> list
					list("one", ImmutableMap.of("foo", "bar"), list("two")),
					null,
					1L,
					// make the location enter & exit a map -> list -> map
					ImmutableMap.of("baz", "bat", "whee", list("foo", ImmutableMap.of("a", "b"))),
					ImmutableMap.of(
							"bar", "baz",
							"whee", "whoo",
							"wugga", list(null, null, new WorkspaceUser("foo"), 1)),
					null),
				"Illegal type at /4/wugga/2 in method parameters: WorkspaceUser");
		
		failWithMethodParameters(list(
					list("one", ImmutableMap.of("foo", new ByteArrayOutputStream()), list("two"))),
				"Illegal type at /0/1/foo in method parameters: ByteArrayOutputStream");
	}
	
	private void failWithMethodParameters(final List<Object> input, final String exception) {
		try {
			ProvenanceAction.getBuilder().withMethodParameters(input);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(exception));
		}
	}
	
	@Test
	public void withWorkspaceObjectsFailBadRef() throws Exception {
		// we just test a few cases here, since the ObjectIdentifier tests are exhaustive
		// and we use that under the hood.
		final String prefix = "Invalid workspace object provenenance reference at position ";
		failWithWorkspaceObjects(
				list(null, "1/1/1"),
				prefix + "1: refpath cannot be null or the empty string");
		failWithWorkspaceObjects(
				list("foo/bar", "1/1/1/1"),
				prefix + "2: Illegal number of separators '/' in object reference '1/1/1/1'");
		failWithWorkspaceObjects(
				list("foo/bar", "1/1/1", "foo/bar/baz"),
				prefix + "3: Unable to parse version portion of object reference 'foo/bar/baz' "
				+ "to an integer");
	}
	
	@Test
	public void withWorkspaceObjectsFailUnequalResolvedObjects() throws Exception {
		final ProvenanceAction.Builder b = ProvenanceAction.getBuilder()
				.withWorkspaceObjects(list("foo/bar"))
				.withResolvedObjects(list("1/1/1"));
		
		failWithWorkspaceObjects(b, list("foo/bar", "baz/bat/1"),
				"The workspace objects list must be the same size as the resolved objects list");
		// check builder unmodified
		new ProvExpected().withWsobjs(list("foo/bar")).withResolvedObjects(list("1/1/1"))
				.assertCorrect(b.build());
	}


	private void failWithWorkspaceObjects(final List<String> input, final String exception) {
		failWithWorkspaceObjects(ProvenanceAction.getBuilder(), input, exception);
	}

	private void failWithWorkspaceObjects(
			final ProvenanceAction.Builder b,
			final List<String> input,
			final String exception) {
		try {
			b.withWorkspaceObjects(input);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(exception));
		}
	}
	
	@Test
	public void withResolvedObjectsFail() throws Exception {
		// we just test a few cases here, since the ObjectIdentifier tests are exhaustive
		// and we use that under the hood.
		final String prefix =
				"Invalid resolved workspace object provenenance reference at position ";
		failWithResolvedObjects(
				list(null, "1/1/1"),
				prefix + "1: refpath cannot be null or the empty string");
		failWithResolvedObjects(
				list("1/1/1", "1/1/1/1", "foo/bar"),
				prefix + "2: Illegal number of separators '/' in object reference '1/1/1/1'");
		failWithResolvedObjects(
				list("foo/bar", "1/1/1/1"),
				prefix + "1: Reference foo/bar is not absolute");
		failWithResolvedObjects(
				list("1/1/1", "1/1/foo"),
				prefix + "2: Unable to parse version portion of object reference '1/1/foo' "
				+ "to an integer");
		failWithResolvedObjects(
				list("1/1/1", "1/1/1", "1/1"),
				prefix + "3: Reference 1/1 is not absolute");
	}
	
	@Test
	public void withResolvedObjectsFailUnequalWorkspaceObjects() throws Exception {
		final String err = "The resolved workspace objects list must be the same size as "
				+ "the standard objects list";
		
		final ProvenanceAction.Builder b = ProvenanceAction.getBuilder().withCaller("c");
		final ProvExpected e = new ProvExpected().withCaller(opt("c"));
		failWithResolvedObjects(b, list("1/1/1", "2/2/2"), err);
		e.assertCorrect(b.build()); // check builder unmodified
		failWithResolvedObjects(
				b.withWorkspaceObjects(list("1/1/1", "2/2/2")), list("1/1/1"), err);
		e.withWsobjs(list("1/1/1", "2/2/2")).assertCorrect(b.build()); // check builder unmodified
	}

	private void failWithResolvedObjects(final List<String> input, final String exception) {
		failWithResolvedObjects(ProvenanceAction.getBuilder(), input, exception);
	}
	
	private void failWithResolvedObjects(
			final ProvenanceAction.Builder b,
			final List<String> input,
			final String exception) {
		try {
			b.withResolvedObjects(input);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(exception));
		}
	}
	
	@Test
	public void withIncomingArgsFail() throws Exception {
		failWithIncomingArgs(
				list("foo", null, "baz"),
				"Null or whitespace only string in collection incoming args");
		failWithIncomingArgs(
				list("foo", "baz", "   \t    "),
				"Null or whitespace only string in collection incoming args");
	}
	
	private void failWithIncomingArgs(final List<String> input, final String exception) {
		try {
			ProvenanceAction.getBuilder().withIncomingArgs(input);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(exception));
		}
	}
	
	@Test
	public void withOutgoingArgsFail() throws Exception {
		failWithOutgoingArgs(
				list("foo", null, "baz"),
				"Null or whitespace only string in collection outgoing args");
		failWithOutgoingArgs(
				list("foo", "baz", "   \t    "),
				"Null or whitespace only string in collection outgoing args");
	}
	
	private void failWithOutgoingArgs(final List<String> input, final String exception) {
		try {
			ProvenanceAction.getBuilder().withOutgoingArgs(input);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(exception));
		}
	}
	
	@Test
	public void withExternalDataFail() throws Exception {
		try {
			ProvenanceAction.getBuilder().withExternalData(list(
					ExternalData.getBuilder().withDataID("d").build(),
					null
					));
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException(
					"Null item in external data"));
		}
	}
	
	@Test
	public void withSubActionsFail() throws Exception {
		try {
			ProvenanceAction.getBuilder().withSubActions(list(
					SubAction.getBuilder().withName("a").build(),
					null
					));
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException(
					"Null item in subactions"));
		}
	}
	
	@Test
	public void withCustomFail() throws Exception {
		final Map<String, String> nullMap = new HashMap<>();
		nullMap.put("a", "b");
		nullMap.put(null, "c");
		nullMap.put("d", "e");
		try {
			ProvenanceAction.getBuilder().withCustom(nullMap);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
					"Null key in custom provenance"));
		}
	}
	
	@Test
	public void buildFail() throws Exception {
		failBuild(ProvenanceAction.getBuilder());
		failBuild(ProvenanceAction.getBuilder()
				.withCaller(null)
				.withCommandLine(null)
				.withCustom(null)
				.withDescription(null)
				.withExternalData(null)
				.withIncomingArgs(null)
				.withMethod(null)
				.withMethodParameters(null)
				.withOutgoingArgs(null)
				.withResolvedObjects(null)
				.withScript(null)
				.withScriptVersion(null)
				.withServiceName(null)
				.withServiceVersion(null)
				.withSubActions(null)
				.withTime(null)
				.withWorkspaceObjects(null)
				);
	}

	public void failBuild(final ProvenanceAction.Builder b) {
		try {
			b.build();
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
					"At least one field in a provenance action must be provided"));
		}
	}
}
