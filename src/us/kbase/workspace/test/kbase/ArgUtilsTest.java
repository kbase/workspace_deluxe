package us.kbase.workspace.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.workspace.kbase.ArgUtils.chooseInstant;
import static us.kbase.workspace.kbase.ArgUtils.processProvenance;
import static us.kbase.common.test.TestCommon.inst;
import static us.kbase.common.test.TestCommon.list;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import us.kbase.common.service.UObject;
import us.kbase.common.test.TestCommon;
import us.kbase.workspace.ExternalDataUnit;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.provenance.ExternalData;
import us.kbase.workspace.database.provenance.Provenance;
import us.kbase.workspace.database.provenance.ProvenanceAction;
import us.kbase.workspace.database.provenance.SubAction;

public class ArgUtilsTest {
	
	private static final WorkspaceUser U1 = new WorkspaceUser("u1");
	private static final WorkspaceUser U2 = new WorkspaceUser("u2");
	
	// TODO TEST add more ArgUtils unit tests
	
	@Test
	public void chooseInstantSuccess() throws Exception {
		checkChooseInstant(null, null, null);
		
		checkChooseInstant(null, -1000L, inst(-1000));
		checkChooseInstant(null, 0L, inst(0));
		checkChooseInstant(null, 1000L, inst(1000));
		checkChooseInstant(null, 897086L, inst(897086));
		
		checkChooseInstant("2013-04-26T23:52:06-1111", null, inst(1367060586000L));
		checkChooseInstant("2013-04-26T23:52:06-0000", null, inst(1367020326000L));
		checkChooseInstant("2013-04-26T23:52:06-00:00", null, inst(1367020326000L));
		checkChooseInstant("2013-04-26T23:52:06Z", null, inst(1367020326000L));
		checkChooseInstant("2013-04-26T23:52:06.Z", null, inst(1367020326000L));
		checkChooseInstant("2013-04-26T23:52:06.-0500", null, inst(1367038326000L));
		checkChooseInstant("2013-04-26T23:52:06.7Z", null, inst(1367020326700L));
		checkChooseInstant("2013-04-26T23:52:06.78Z", null, inst(1367020326780L));
		checkChooseInstant("2013-04-26T23:52:06.789Z", null, inst(1367020326789L));
		checkChooseInstant("2013-04-26T23:52:06.789-0000", null, inst(1367020326789L));
		checkChooseInstant("2013-04-26T23:52:06.789-0600", null, inst(1367041926789L));
		checkChooseInstant("2013-04-26T23:52:06.789-06:00", null, inst(1367041926789L));
		// this is a weird case. Changing either of the time zone offsets to -7 causes a failure as
		// expected, as does switching the order of the offsets.
		// Presumably this is due to the format string accepting time zone offsets
		// both with or without a colon. Not sure how to fix and not particularly concerned since
		// the time is unambiguous and no one is likely to format time stamps like this.
		checkChooseInstant("2013-04-26T23:52:06.789-08:00-0800", null, inst(1367049126789L));
		// another weird case. Obvs there isn't 31 days in April but the smart resolver
		// automatically switches it to 30. I set the resolver to strict mode but then everything
		// started failing with completely unhelpful error messages. So f this for now.
		checkChooseInstant("2013-04-31T23:52:06-0800", null, inst(1367394726000L));
	}
	
	private void checkChooseInstant(
			final String timestamp,
			final Long epochMillis,
			final Instant expected)
			throws Exception {
		final Instant got = chooseInstant(timestamp, epochMillis, "foo");
		assertThat("incorrect instant, got epoch of " + (got == null ? null : got.toEpochMilli()),
				got, is(expected));
	}

	@Test
	public void chooseInstantFail() throws Exception {
		failChooseInstant("ts", 1L, iae("can't provide both error"));

		failChooseInstant("2O13-00-26T23:52:06-0800", 0, true);

		failChooseInstant("2013--01-26T23:52:06-0800", 5, true);
		failChooseInstant("2013-00-26T23:52:06-0800",
				"Invalid value for MonthOfYear (valid values 1 - 12): 0");
		failChooseInstant("2013-13-26T23:52:06-0800",
				"Invalid value for MonthOfYear (valid values 1 - 12): 13");
		failChooseInstant("2013-O1-26T23:52:06-0800", 5, true);
		
		failChooseInstant("2013-04-00T23:52:06-0800",
				"Invalid value for DayOfMonth (valid values 1 - 28/31): 0");
		failChooseInstant("2013-04-32T23:52:06-0800",
				"Invalid value for DayOfMonth (valid values 1 - 28/31): 32");
		failChooseInstant("2013-01-O1T23:52:06-0800", 8, true);
		
		failChooseInstant("2013-04-01T24:52:06-0800",
				"Invalid value for HourOfDay (valid values 0 - 23): 24");
		failChooseInstant("2013-01-01TO2:52:06-0800", 11, true);
		
		failChooseInstant("2013-04-01T23:60:06-0800",
				"Invalid value for MinuteOfHour (valid values 0 - 59): 60");
		failChooseInstant("2013-01-01T02:O4:06-0800", 14, true);
		
		failChooseInstant("2013-04-01T23:59:60-0800",
				"Invalid value for SecondOfMinute (valid values 0 - 59): 60");
		failChooseInstant("2013-01-01T02:07:O9-0800", 17, true);
		
		failChooseInstant("2013-04-26T23:52:0", 17, true);
		failChooseInstant("2013-04-26T23:52:06", null, iae(
				"Date '2013-04-26T23:52:06' does not have time zone information"));
		failChooseInstant("2013-04-26T23:52Z", 16, true);
		failChooseInstant("2013-04-26T23:52-0800", 16, true);
		failChooseInstant("2013-04-26T23:52:06.7893Z", 23, false);
		failChooseInstant("2013-04-26T23:52:06-", 19, false);
		failChooseInstant("2013-04-26T23:52:06-GMT+1", 19, false);
		failChooseInstant("2013-04-26T23:52:06-1", 19, false);
		failChooseInstant("2013-04-26T23:52:06-11", 19, false);
		failChooseInstant("2013-04-26T23:52:06-11:", 19, false);
		failChooseInstant("2013-04-26T23:52:06-111", 19, false);
		failChooseInstant("2013-04-26T23:52:06-11:1", 19, false);
		failChooseInstant("2013-04-26T23:52:06-8000", 19, false);
		failChooseInstant("2013-04-26T23:52:06-0800-08:00", 24, false);
		failChooseInstant("2013-04-26T23:52:06-08:00-0700", 25, false);
		failChooseInstant("2013-04-26T23:52:06-07:00-0800", 25, false);
	}
	
	private void failChooseInstant(final String timestamp, final String errsuffix)
			throws Exception {
		failChooseInstant(timestamp, null, iae(generrPrefix(timestamp) + ": " + errsuffix));
	}
	
	private void failChooseInstant(final String timestamp, final int index, final boolean prior)
			throws Exception {
		failChooseInstant(timestamp, null, iae(generr(timestamp, index, prior)));
	}
	
	private IllegalArgumentException iae(final String message) {
		return new IllegalArgumentException(message);
	}
	
	private String generrPrefix(final String timestamp) {
		return String.format("Unparseable date: Text '%s' could not be parsed", timestamp);
	}
	
	private String generr(final String timestamp, final int index, final boolean prior) {
		final String template;
		if (prior) {
			template = generrPrefix(timestamp) + " at index %s";
					
		} else {
			template = generrPrefix(timestamp) + ", unparsed text found at index %s";
		}
		return String.format(template, index);
	}
	
	private void failChooseInstant(
			final String timestamp,
			final Long epochMillis,
			final Exception expected)
			throws Exception {
		try {
			chooseInstant(timestamp, epochMillis, "can't provide both error");
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	final us.kbase.workspace.ProvenanceAction newPA() {
		return new us.kbase.workspace.ProvenanceAction();
	}
	
	final us.kbase.workspace.SubAction newSA() {
		return new us.kbase.workspace.SubAction();
	}
	
	@Test
	public void processProvenanceEmpty() throws Exception {
		final Provenance expected = Provenance.getBuilder(U1, inst(10000)).build();
		assertThat("incorrect provenance",
				processProvenance(U1, inst(10000), null), is(expected));
		assertThat("incorrect provenance",
				processProvenance(U1, inst(10000), list()), is(expected));
	}

	@Test
	public void processProvenanceMinimal() throws Exception {
		assertThat("incorrect provenance",
				processProvenance(U2, inst(10000), list(newPA().withCaller("c"))),
				is(Provenance.getBuilder(U2, inst(10000)).withAction(
						ProvenanceAction.getBuilder().withCaller("c").build())
						.build()));
	}
	
	@Test
	public void processProvenanceWithEmptyActions() throws Exception {
		assertThat("incorrect provenance",
				processProvenance(
						U2, inst(10000), list(newPA(), newPA().withCaller("c"), newPA())),
				is(Provenance.getBuilder(U2, inst(10000)).withAction(
						ProvenanceAction.getBuilder().withCaller("c").build())
						.build()));
	}
	
	@Test
	public void processProvenanceMaximal() throws Exception {
		final List<us.kbase.workspace.ProvenanceAction> inc = list(
				newPA()
						.withCaller("c")
						.withCustom(ImmutableMap.of("foo", "bar"))
						.withDescription("d")
						.withEpoch(90000L)
						.withExternalData(list(
								new ExternalDataUnit()
										.withDataId("did")
										.withDataUrl("https://data.com")
										.withDescription("d")
										.withResourceName("JGI")
										.withResourceReleaseEpoch(70000L)
										.withResourceUrl("https://otherdata.com")
										.withResourceVersion("v2"),
								new ExternalDataUnit().withDataId("d")
								))
						.withInputWsObjects(list("foo/bar", "1/1/1"))
						.withIntermediateIncoming(list("a", "b"))
						.withIntermediateOutgoing(list("c", "d"))
						.withMethod("meth")
						.withMethodParams(list(new UObject("foo"), new UObject(7L)))
						.withScript("cooking_with_sudafed")
						.withScriptCommandLine("--dump-in-bathtub --location=doublewide --teeth=7")
						.withScriptVer("git er done")
						.withService("methamphetaminedelivery")
						.withServiceVer("some large number")
						.withSubactions(list(
								newSA()
										.withCodeUrl("file://c:/stuff/methcode")
										.withCommit("version control is for wussies")
										.withEndpointUrl("https://uswest.methdeliv.amazon.com")
										.withName("meth delivery sub service")
										.withVer("what did I just say about version control"),
								newSA().withVer("1")
								)),
				newPA() // test time strings vs epoch
						.withTime("2021-04-23T12:01:01.456-0700")
						.withExternalData(list(new ExternalDataUnit()
								.withResourceReleaseDate("2020-03-22T11:00:00.345-0600")
						)),
				newPA().withCaller("c"), // test null external data list
				// test empty lists 
				newPA().withCaller("d").withExternalData(list()).withSubactions(list())
				);
		final Provenance expected = Provenance.getBuilder(U2, inst(20000))
				.withAction(ProvenanceAction.getBuilder()
						.withCaller("c")
						.withCustom(ImmutableMap.of("foo", "bar"))
						.withDescription("d")
						.withTime(inst(90000))
						.withExternalData(list(
								ExternalData.getBuilder()
										.withDataID("did")
										.withDataURL("https://data.com")
										.withDescription("d")
										.withResourceName("JGI")
										.withResourceReleaseDate(inst(70000L))
										.withResourceURL("https://otherdata.com")
										.withResourceVersion("v2")
										.build(),
								ExternalData.getBuilder().withDataID("d").build()
								))
						.withWorkspaceObjects(list("foo/bar", "1/1/1"))
						.withIncomingArgs(list("a", "b"))
						.withOutgoingArgs(list("c", "d"))
						.withMethod("meth")
						.withMethodParameters(list("foo", 7L))
						.withScript("cooking_with_sudafed")
						.withCommandLine("--dump-in-bathtub --location=doublewide --teeth=7")
						.withScriptVersion("git er done")
						.withServiceName("methamphetaminedelivery")
						.withServiceVersion("some large number")
						.withSubActions(list(
								SubAction.getBuilder()
										.withCodeURL("file://c:/stuff/methcode")
										.withCommit("version control is for wussies")
										.withEndpointURL("https://uswest.methdeliv.amazon.com")
										.withName("meth delivery sub service")
										.withVersion("what did I just say about version control")
										.build(),
								SubAction.getBuilder().withVersion("1").build()
								))
						.build())
				.withAction(ProvenanceAction.getBuilder()
						.withTime(inst(1619204461456L))
						.withExternalData(list(ExternalData.getBuilder()
								.withResourceReleaseDate(inst(1584896400345L))
								.build()))
						.build())
				.withAction(ProvenanceAction.getBuilder().withCaller("c").build())
				.withAction(ProvenanceAction.getBuilder().withCaller("d").build())
				.build();
		
		final Provenance got = processProvenance(U2, inst(20000), inc);
		assertThat("incorrect provenance", got, is(expected));
	}
	
	// ######## Provenance action build failures ########
	
	@Test
	public void processProvenanceFailNulls() throws Exception {
		final us.kbase.workspace.ProvenanceAction pa = newPA();
		failProcessProvenance(null, inst(0), list(pa), new NullPointerException("user"));
		failProcessProvenance(U2, null, list(pa), new NullPointerException("date"));
	}
	
	@Test
	public void processProvenanceFailNullPA() throws Exception {
		final us.kbase.workspace.ProvenanceAction pa = newPA().withCaller("c");
		failProcessProvenance(U2, inst(0), list(pa, null), new IllegalArgumentException(
				"Provenance action #2: is null"));
	}
	
	@Test
	public void processProvenanceFailAddlArgs() throws Exception {
		final us.kbase.workspace.ProvenanceAction pa = newPA().withCaller("c");
		pa.setAdditionalProperties("foo", 42);
		failProcessProvenance(U2, inst(0), list(pa, newPA().withCaller("d")),
				new IllegalArgumentException("Provenance action #1: "
						+ "Unexpected arguments in ProvenanceAction: foo"));
	}
	
	@Test
	public void processProvenanceFailTwoTimeSpecs() throws Exception {
		final us.kbase.workspace.ProvenanceAction pa = newPA().withCaller("c");
		final us.kbase.workspace.ProvenanceAction p2 = newPA()
				.withEpoch(1L).withTime("2222-01-01T12:00:00-0700");
		failProcessProvenance(U2, inst(0), list(pa, pa, p2),
				new IllegalArgumentException("Provenance action #3: Cannot specify both time "
						+ "and epoch in provenance action"));
	}
	
	// there's lots of ways a PA build can fail, we just test a couple of them here
	
	@Test
	public void processProvenanceFailBuildNullCustom() throws Exception {
		final us.kbase.workspace.ProvenanceAction pa = newPA().withCaller("c");
		final Map<String, String> custom = new HashMap<>();
		custom.put(null, "foo");
		failProcessProvenance(U2, inst(0), list(pa, pa, newPA().withCustom(custom)),
				new IllegalArgumentException(
						"Provenance action #3: Null key in custom provenance"));
	}
	
	@Test
	public void processProvenanceFailBuildBadRef() throws Exception {
		final us.kbase.workspace.ProvenanceAction pa = newPA().withCaller("c");
		failProcessProvenance(U2, inst(0), list(pa, pa, pa, newPA()
				.withInputWsObjects(list("foo/bar", "1/1/1/1"))),
				new IllegalArgumentException("Provenance action #4: Invalid workspace object "
						+ "provenenance reference at position 2: Illegal number of separators "
						+ "'/' in object reference '1/1/1/1'"));
	}
	
	// #### Sub action build failures ####
	
	@Test
	public void processProvenanceFailNullSA() throws Exception {
		final us.kbase.workspace.ProvenanceAction pa = newPA().withCaller("c");
		final us.kbase.workspace.ProvenanceAction pa2 = newPA().withSubactions(
				list(newSA().withVer("v"), newSA().withVer("v"), null));
		failProcessProvenance(U2, inst(0), list(pa2, pa), new IllegalArgumentException(
				"Provenance action #1: Sub action #3: is null"));
	}
	
	@Test
	public void processProvenanceFailAddlArgsSA() throws Exception {
		final us.kbase.workspace.SubAction ex = newSA().withCommit("c");
		ex.setAdditionalProperties("yay", "yo");
		final us.kbase.workspace.SubAction ex2 = newSA().withCommit("c");
		final us.kbase.workspace.ProvenanceAction pa = newPA().withSubactions(list(ex, ex2));
		failProcessProvenance(U2, inst(0), list(newPA().withCaller("d"), pa),
				new IllegalArgumentException("Provenance action #2: Sub action #1: Unexpected "
						+ "arguments in SubAction: yay"));
	}
	
	// there's lots of ways a SA build can fail, we just test a few of them here
	
	@Test
	public void processProvenanceFailBuildEmptySA() throws Exception {
		final us.kbase.workspace.ProvenanceAction pa = newPA().withCaller("c");
		final us.kbase.workspace.ProvenanceAction p2 = newPA()
				.withSubactions(list(newSA().withCommit("c"), newSA()));
		failProcessProvenance(U2, inst(0), list(pa, pa, p2),
				new IllegalArgumentException("Provenance action #3: Sub action #2: At least one "
						+ "field in a provenance sub action must be provided"));
	}
	
	@Test
	public void processProvenanceFailBuildSABadCodeURL() throws Exception {
		final us.kbase.workspace.ProvenanceAction pa = newPA().withCaller("c");
		final us.kbase.workspace.ProvenanceAction p2 = newPA().withSubactions(
				list(newSA().withCommit("c"), newSA().withCodeUrl("what's a url vern")));
		failProcessProvenance(U2, inst(0), list(pa, pa, pa, p2),
				new IllegalArgumentException("Provenance action #4: Sub action #2: Illegal code"
						+ " url 'what's a url vern': no protocol: what's a url vern"));
	}
	
	@Test
	public void processProvenanceFailBuildSABadEndpointURL() throws Exception {
		final us.kbase.workspace.ProvenanceAction pa = newPA().withSubactions(
				list(newSA().withEndpointUrl("httpsssss://moresesismoresecret.com")));
		failProcessProvenance(U2, inst(0), list(pa),
				new IllegalArgumentException("Provenance action #1: Sub action #1: Illegal "
						+ "endpoint url 'httpsssss://moresesismoresecret.com': unknown "
						+ "protocol: httpsssss"));
	}
	
	// #### External data unit build failures ####
	
	@Test
	public void processProvenanceFailNullEDU() throws Exception {
		final us.kbase.workspace.ProvenanceAction pa = newPA().withCaller("c");
		final us.kbase.workspace.ProvenanceAction pa2 = newPA().withExternalData(
				list(new ExternalDataUnit().withDataId("d"), null));
		failProcessProvenance(U2, inst(0), list(pa2, pa), new IllegalArgumentException(
				"Provenance action #1: External data unit #2: is null"));
	}
	
	@Test
	public void processProvenanceFailTwoTimeSpecsEDU() throws Exception {
		final us.kbase.workspace.ProvenanceAction pa = newPA().withCaller("c");
		final us.kbase.workspace.ProvenanceAction p2 = newPA().withExternalData(list(
				new ExternalDataUnit()
						.withResourceReleaseEpoch(1L)
						.withResourceReleaseDate("2222-01-01T12:00:00-0700")));
		failProcessProvenance(U2, inst(0), list(pa, p2),
				new IllegalArgumentException("Provenance action #2: External data unit #1: "
						+ "Cannot specify both time and epoch in external data unit"));
	}
	
	@Test
	public void processProvenanceFailAddlArgsEDU() throws Exception {
		final ExternalDataUnit edu = new ExternalDataUnit().withDataId("d");
		edu.setAdditionalProperties("thing", "thang");
		final ExternalDataUnit edu2 = new ExternalDataUnit().withDataId("e");
		final us.kbase.workspace.ProvenanceAction pa2 = newPA().withExternalData(list(edu, edu2));
		final us.kbase.workspace.ProvenanceAction pa1 = newPA().withCaller("d");
		failProcessProvenance(U2, inst(0), list(pa1, pa1, pa1, pa1, pa2),
				new IllegalArgumentException("Provenance action #5: External data unit #1: "
						+ "Unexpected arguments in ExternalDataUnit: thing"));
	}
	
	// there's lots of ways an EDU build can fail, we just test a few of them here
	
	@Test
	public void processProvenanceFailBuildEmptyEDU() throws Exception {
		final us.kbase.workspace.ProvenanceAction pa = newPA().withCaller("c");
		final ExternalDataUnit edu = new ExternalDataUnit().withDataId("d");
		final us.kbase.workspace.ProvenanceAction p2 = newPA()
				.withExternalData(list(edu, edu, new ExternalDataUnit()));
		failProcessProvenance(U2, inst(0), list(p2, pa),
				new IllegalArgumentException("Provenance action #1: External data unit #3: "
						+ "At least one field in an external data unit must be provided"));
	}
	
	@Test
	public void processProvenanceFailBuildEDUBadDataURL() throws Exception {
		final us.kbase.workspace.ProvenanceAction pa = newPA().withCaller("c");
		final ExternalDataUnit edu = new ExternalDataUnit().withDataId("d");
		final us.kbase.workspace.ProvenanceAction p2 = newPA()
				.withExternalData(list(edu, new ExternalDataUnit().withDataUrl("well dang")));
		failProcessProvenance(U2, inst(0), list(pa, p2),
				new IllegalArgumentException("Provenance action #2: External data unit #2: "
						+ "Illegal data url 'well dang': no protocol: well dang"));
	}
	
	@Test
	public void processProvenanceFailBuildEDUBadResourceURL() throws Exception {
		final us.kbase.workspace.ProvenanceAction pa = newPA().withCaller("c");
		final ExternalDataUnit edu = new ExternalDataUnit().withDataId("d");
		final us.kbase.workspace.ProvenanceAction p2 = newPA()
				.withExternalData(list(edu, edu, new ExternalDataUnit()
						.withResourceUrl("download more humans")));
		failProcessProvenance(U2, inst(0), list(pa, pa, pa, p2), new IllegalArgumentException(
				"Provenance action #4: External data unit #3: Illegal resource url "
				+ "'download more humans': no protocol: download more humans"));
	}
	
	private void failProcessProvenance(
			final WorkspaceUser u,
			final Instant t,
			final List<us.kbase.workspace.ProvenanceAction> pas,
			final Exception expected) {
		try {
			processProvenance(u, t, pas);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
}
