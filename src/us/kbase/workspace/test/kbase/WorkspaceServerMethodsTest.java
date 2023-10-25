package us.kbase.workspace.test.kbase;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import us.kbase.common.test.TestCommon;
import us.kbase.common.test.TestCommon.LogEvent;
import us.kbase.workspace.AlterAdminObjectMetadataParams;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.ObjectMetadataUpdate;
import us.kbase.workspace.database.MetadataUpdate;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.ResolvedObjectID;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.WorkspaceUserMetadata;
import us.kbase.workspace.kbase.WorkspaceServerMethods;

public class WorkspaceServerMethodsTest {
	
	private static List<ILoggingEvent> logEvents;
	
	@BeforeClass
	public static void beforeClass() {
		logEvents = TestCommon.setUpSLF4JTestLoggerAppender("us.kbase.workspace");
	}
	
	@Before
	public void before() {
		logEvents.clear();
	}
	
	@Test
	public void setAdminObjectMetadata() throws Exception {
		final Workspace ws = mock(Workspace.class);
		
		final WorkspaceServerMethods wsm = new WorkspaceServerMethods(ws, null, null);
		final MetadataUpdate mu1 = new MetadataUpdate(null, Arrays.asList("foo"));
		final MetadataUpdate mu2 = new MetadataUpdate(new WorkspaceUserMetadata(
				ImmutableMap.of("baz", "bar")), null);
		final MetadataUpdate mu3 = new MetadataUpdate(new WorkspaceUserMetadata(
				ImmutableMap.of("one", "hump")), Arrays.asList("dromedary"));

		when(ws.setAdminObjectMetadata(ImmutableMap.of(
				ObjectIdentifier.getBuilderFromRefPath("myws/96").build(), mu1,
				ObjectIdentifier.getBuilderFromRefPath("8/myobj/1").build(), mu2,
				ObjectIdentifier.getBuilderFromRefPath("myws/myobj").build(), mu3
				))).thenReturn(ImmutableMap.of(
						ObjectIdentifier.getBuilderFromRefPath("myws/96").build(),
							new ResolvedObjectID(new ResolvedWorkspaceID(3, "myws", false, false),
									96, 2, "myobj2", false),
						ObjectIdentifier.getBuilderFromRefPath("8/myobj/1").build(),
							new ResolvedObjectID(new ResolvedWorkspaceID(8, "myws2", false, false),
									3, 1, "myobj", false),
						ObjectIdentifier.getBuilderFromRefPath("myws/myobj").build(),
							new ResolvedObjectID(new ResolvedWorkspaceID(3, "myws", false, false),
									254, 108, "myobj", false)
		));
		wsm.setAdminObjectMetadata(new AlterAdminObjectMetadataParams()
				.withUpdates(Arrays.asList(
						new ObjectMetadataUpdate()  // test wsname, objid
								.withOi(new ObjectIdentity().withWorkspace("myws").withObjid(96L))
								.withRemove(Arrays.asList("foo")),
						new ObjectMetadataUpdate()  // test wsid, objname, ver
							.withOi(new ObjectIdentity().withWsid(8L).withName("myobj")
									.withVer(1L))
							.withNew(ImmutableMap.of("baz", "bar")),
						new ObjectMetadataUpdate()  // test ref
							.withOi(new ObjectIdentity().withRef("myws/myobj"))
							.withNew(ImmutableMap.of("one", "hump"))
							.withRemove(Arrays.asList("dromedary"))
		)));
		
		TestCommon.assertLogEventsCorrect(
				logEvents,
				new LogEvent(Level.INFO, "Object 3/96/2", WorkspaceServerMethods.class),
				new LogEvent(Level.INFO, "Object 8/3/1", WorkspaceServerMethods.class),
				new LogEvent(Level.INFO, "Object 3/254/108", WorkspaceServerMethods.class)
		);
	}

	@Test
	public void setAdminObjectMetadataFailBadTopLevelParams() throws Exception {
		final WorkspaceServerMethods wsm = new WorkspaceServerMethods(
				mock(Workspace.class), null, null);
		
		setAdminObjectMetadataFail(wsm, null, new NullPointerException("params"));
		final AlterAdminObjectMetadataParams p = new AlterAdminObjectMetadataParams();
		p.setAdditionalProperties("foo", "bar");
		setAdminObjectMetadataFail(wsm, p, new IllegalArgumentException(
				"Unexpected arguments in AlterAdminObjectMetadataParams: foo"));
		setAdminObjectMetadataFail(wsm, new AlterAdminObjectMetadataParams(),
				new IllegalArgumentException("updates list cannot be empty"));
		setAdminObjectMetadataFail(wsm, new AlterAdminObjectMetadataParams().withUpdates(null),
				new IllegalArgumentException("updates list cannot be empty"));
		setAdminObjectMetadataFail(wsm, new AlterAdminObjectMetadataParams()
				.withUpdates(Collections.emptyList()),
				new IllegalArgumentException("updates list cannot be empty"));
	}
	
	@Test
	public void setAdminObjectMetadataFailBadObjectIDs() throws Exception {
		// test a selection of bad object identifiers, not meant to be exhaustive.
		final ObjectIdentity good = new ObjectIdentity().withWsid(1L).withObjid(1L);
		
		setAdminObjectMetadataFailBadObjectID(new IllegalArgumentException(
				"Error processing update index 1: ObjectIdentity cannot be null"), good, null);
		setAdminObjectMetadataFailBadObjectID(new IllegalArgumentException(
				"Error processing update index 0: Must provide one and only one of workspace name "
				+ "(was: null) or id (was: null)"),
				new ObjectIdentity(), good);
		setAdminObjectMetadataFailBadObjectID(new IllegalArgumentException(
				"Error processing update index 2: Must provide one and only one of object name "
				+ "(was: n) or id (was: 1)"),
				good, good, new ObjectIdentity().withWsid(1L).withName("n").withObjid(1L));
		setAdminObjectMetadataFailBadObjectID(new IllegalArgumentException(
				"Error processing update index 3: Illegal number of separators '/' in "
				+ "object reference '1/2/3/4'"),
				good, good, good, new ObjectIdentity().withRef("1/2/3/4"));
	}
	
	private void setAdminObjectMetadataFailBadObjectID(
			final Exception expected,
			final ObjectIdentity... oi) {
		final WorkspaceServerMethods wsm = new WorkspaceServerMethods(
				mock(Workspace.class), null, null);
		setAdminObjectMetadataFail(
				wsm,
				new AlterAdminObjectMetadataParams().withUpdates(Stream.of(oi)
						.map(o -> new ObjectMetadataUpdate()
								.withOi(o).withRemove(Arrays.asList("foo")))
						.collect(Collectors.toList())),
				expected);
	}
	
	@Test
	public void setAdminObjectMetadataFailBadMetadata() throws Exception {
		// test a selection of problems, again not exhaustive for all possible code paths
		// through dependencies
		final ObjectMetadataUpdate good = omu(Arrays.asList("foo"));
		
		setAdminObjectMetadataFailMetadata(new IllegalArgumentException(
				"Error processing update index 1: ObjectMetadataUpdate cannot be null"),
				good, null, good);
		final ObjectMetadataUpdate addl = new ObjectMetadataUpdate();
		addl.setAdditionalProperties("yay", "boo");
		setAdminObjectMetadataFailMetadata(new IllegalArgumentException(
				"Error processing update index 2: Unexpected arguments in "
				+ "ObjectMetadataUpdate: yay"),
				good, good, addl);
		setAdminObjectMetadataFailMetadata(new IllegalArgumentException(
				"Error processing update index 1: null metadata keys are not allowed in the "
				+ "remove parameter"),
				good, omu(Arrays.asList("foo", null)));
		setAdminObjectMetadataFailMetadata(new IllegalArgumentException(
				"Error processing update index 0: A metadata update is required"),
				omu(), good);
		final Map<String, String> nully = new HashMap<>();
		nully.put("foo", null);
		setAdminObjectMetadataFailMetadata(new IllegalArgumentException(
				"Error processing update index 2: Null value for metadata key foo"),
				good, good, omu(nully), good);
		
		
	}
	
	private ObjectMetadataUpdate omu() {
		return new ObjectMetadataUpdate().withOi(new ObjectIdentity().withRef("1/1"));
	}
	
	private ObjectMetadataUpdate omu(final Map<String, String> newm) {
		return new ObjectMetadataUpdate()
				.withOi(new ObjectIdentity().withRef("1/1"))
				.withNew(newm);
	}
	
	private ObjectMetadataUpdate omu(final List<String> remove) {
		return new ObjectMetadataUpdate()
				.withOi(new ObjectIdentity().withRef("1/1"))
				.withRemove(remove);
	}
	
	private void setAdminObjectMetadataFailMetadata(
			final Exception expected,
			final ObjectMetadataUpdate ... omu) {
		final WorkspaceServerMethods wsm = new WorkspaceServerMethods(
				mock(Workspace.class), null, null);
		setAdminObjectMetadataFail(
				wsm,
				new AlterAdminObjectMetadataParams().withUpdates(Arrays.asList(omu)),
				expected);
	}

	private void setAdminObjectMetadataFail(
			final WorkspaceServerMethods wsm,
			final AlterAdminObjectMetadataParams params,
			final Exception expected) {
		try {
			wsm.setAdminObjectMetadata(params);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	

}
