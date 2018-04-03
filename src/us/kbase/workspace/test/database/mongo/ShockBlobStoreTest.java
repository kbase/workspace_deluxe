package us.kbase.workspace.test.database.mongo;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoException;

import us.kbase.common.test.TestCommon;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockFileInformation;
import us.kbase.shock.client.ShockNode;
import us.kbase.shock.client.ShockNodeId;
import us.kbase.shock.client.ShockVersionStamp;
import us.kbase.shock.client.exceptions.InvalidShockUrlException;
import us.kbase.shock.client.exceptions.ShockHttpException;
import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.Restreamable;
import us.kbase.workspace.database.DependencyStatus;
import us.kbase.workspace.database.mongo.ShockBlobStore;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreCommunicationException;
import us.kbase.workspace.test.database.mongo.ShockBlobStoreIntegrationTest.StringRestreamable;

public class ShockBlobStoreTest {

	/* This is strictly for unit tests. */
	
	@Test
	public void constructFail() {
		final DBCollection col = mock(DBCollection.class);
		final BasicShockClient client = mock(BasicShockClient.class);
		
		failConstruct(null, client);
		failConstruct(col, null);
	}
	
	private void failConstruct(
			final DBCollection collection,
			final BasicShockClient client) {
		try {
			new ShockBlobStore(collection, client);
		} catch (NullPointerException npe) {
			assertThat("correct exception message", npe.getLocalizedMessage(),
					is("Arguments cannot be null"));
		}
	}
	
	@Test
	public void constructVerify() throws Exception {
		final DBCollection col = mock(DBCollection.class);
		final BasicShockClient client = mock(BasicShockClient.class);
		
		new ShockBlobStore(col, client);
		
		final DBObject dbo = new BasicDBObject();
		dbo.put("chksum", 1);
		final DBObject opts = new BasicDBObject();
		opts.put("unique", 1);
		
		verify(col).createIndex(dbo, opts);
	}
	
	@Test
	public void status() throws Exception {
		final DBCollection col = mock(DBCollection.class);
		final BasicShockClient client = mock(BasicShockClient.class);
		
		final ShockBlobStore sbs = new ShockBlobStore(col, client);
		
		when(client.getRemoteVersion()).thenReturn("my version");
		
		assertThat("incorrect status", sbs.status(),
				is(Arrays.asList(new DependencyStatus(true, "OK", "Shock", "my version"))));
	}
	
	@Test
	public void statusFailIOException() throws Exception {
		final DBCollection col = mock(DBCollection.class);
		final BasicShockClient client = mock(BasicShockClient.class);
		
		final ShockBlobStore sbs = new ShockBlobStore(col, client);
		
		when(client.getRemoteVersion()).thenThrow(new IOException("foo"));
		
		assertThat("incorrect status", sbs.status(),
				is(Arrays.asList(new DependencyStatus(
						false, "Cannot connect to Shock: foo", "Shock", "Unknown"))));
	}
	
	@Test
	public void statusFailURLException() throws Exception {
		final DBCollection col = mock(DBCollection.class);
		final BasicShockClient client = mock(BasicShockClient.class);
		
		final ShockBlobStore sbs = new ShockBlobStore(col, client);
		
		when(client.getRemoteVersion()).thenThrow(new InvalidShockUrlException("url"));
		
		assertThat("incorrect status", sbs.status(),
				is(Arrays.asList(new DependencyStatus(
						false, "Invalid Shock URL: url", "Shock", "Unknown"))));
	}
	
	@Test
	public void saveBlobFailInput() throws Exception {
		failSaveBlob(null, new StringRestreamable("foo"),
				new NullPointerException("Arguments cannot be null"));
		failSaveBlob(new MD5("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1"), null,
				new NullPointerException("Arguments cannot be null"));
	}
	
	private void failSaveBlob(final MD5 md5, final Restreamable stream, final Exception expected) {
		final DBCollection col = mock(DBCollection.class);
		final BasicShockClient client = mock(BasicShockClient.class);
		try {
			new ShockBlobStore(col, client).saveBlob(md5, stream, true);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void saveBlobNoop() throws Exception {
		final DBCollection col = mock(DBCollection.class);
		final BasicShockClient client = mock(BasicShockClient.class);
		
		final ShockBlobStore sbs = new ShockBlobStore(col, client);
		
		when(col.findOne(new BasicDBObject("chksum", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1")))
				.thenReturn(new BasicDBObject("node", "foo"));
		
		sbs.saveBlob(
				new MD5("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1"), new StringRestreamable("foo"), true);
		
		verifyZeroInteractions(client);
	}
	
	@Test
	public void saveBlob() throws Exception {
		final String md5 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1";
		final DBCollection col = mock(DBCollection.class);
		final BasicShockClient client = mock(BasicShockClient.class);
		final Restreamable res = mock(Restreamable.class);
		// normally you shouldn't mock value classes but these can't be instantiated
		// because I'm a dummy
		final ShockNode sn = mock(ShockNode.class);
		final ShockFileInformation sfi = mock(ShockFileInformation.class);
		
		final ShockBlobStore sbs = new ShockBlobStore(col, client);
		
		when(col.findOne(new BasicDBObject("chksum", md5))).thenReturn(null);
		
		final InputStream stream = new ByteArrayInputStream("foo".getBytes());
		
		when(res.getInputStream()).thenReturn(stream);
		
		when(client.addNode(stream, "workspace_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1", "JSON"))
				.thenReturn(sn);
		
		when(sn.getId()).thenReturn(new ShockNodeId("ca4a4b5a-b676-4090-9a7d-9690189e29be"));
		when(sn.getVersion()).thenReturn(
				new ShockVersionStamp("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa2"));
		when(sn.getFileInformation()).thenReturn(sfi);
		when(sfi.getChecksum("md5")).thenReturn(md5);
		
		sbs.saveBlob(new MD5(md5), res, true);
		
		verify(col).update(new BasicDBObject("chksum", md5),
				new BasicDBObject("chksum", md5)
					.append("node", "ca4a4b5a-b676-4090-9a7d-9690189e29be")
					.append("ver", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa2")
					.append("sorted", true),
				true, false);
	}
	
	@Test
	public void saveBlob4AttemptsAndSortedFalse() throws Exception {
		final String md5 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1";
		final DBCollection col = mock(DBCollection.class);
		final BasicShockClient client = mock(BasicShockClient.class);
		final Restreamable res = mock(Restreamable.class);
		// normally you shouldn't mock value classes but these can't be instantiated
		// because I'm a dummy
		final ShockNode sn1 = mock(ShockNode.class);
		final ShockNode sn2 = mock(ShockNode.class);
		final ShockNode sn3 = mock(ShockNode.class);
		final ShockNode sn4 = mock(ShockNode.class);
		final ShockNode sn5 = mock(ShockNode.class);
		final ShockFileInformation sfi1 = mock(ShockFileInformation.class);
		final ShockFileInformation sfi2 = mock(ShockFileInformation.class);
		
		final ShockBlobStore sbs = new ShockBlobStore(col, client);
		
		when(col.findOne(new BasicDBObject("chksum", md5))).thenReturn(null);
		
		final InputStream stream = new ByteArrayInputStream("foo".getBytes());
		
		when(res.getInputStream()).thenReturn(stream);
		
		when(client.addNode(stream, "workspace_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1", "JSON"))
				.thenReturn(sn1, sn2, sn3, sn4, sn5);
		
		when(sfi1.getChecksum("md5")).thenReturn("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa3");
		when(sfi2.getChecksum("md5")).thenReturn(md5);
		
		when(sn1.getId()).thenReturn(new ShockNodeId("ca4a4b5a-b676-4090-9a7d-9690189e29be"));
		when(sn1.getFileInformation()).thenReturn(sfi1);
		when(sn2.getId()).thenReturn(new ShockNodeId("5da17b70-bfc1-41d3-8180-a0f2610d9609"));
		when(sn2.getFileInformation()).thenReturn(sfi1);
		when(sn3.getId()).thenReturn(new ShockNodeId("d73e0326-900f-44db-a359-4f297e6270a8"));
		when(sn3.getFileInformation()).thenReturn(sfi1);
		when(sn4.getId()).thenReturn(new ShockNodeId("3d82bbee-1c4b-44f6-982f-d4e5db8533b4"));
		when(sn4.getFileInformation()).thenReturn(sfi1);
		
		when(sn5.getId()).thenReturn(new ShockNodeId("b6ce18d4-fc39-45c0-9918-d4d5800a8f43"));
		when(sn5.getFileInformation()).thenReturn(sfi2);
		when(sn5.getVersion()).thenReturn(
				new ShockVersionStamp("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa2"));
		
		sbs.saveBlob(new MD5(md5), res, false);
		
		verify(client).deleteNode(new ShockNodeId("ca4a4b5a-b676-4090-9a7d-9690189e29be"));
		verify(client).deleteNode(new ShockNodeId("5da17b70-bfc1-41d3-8180-a0f2610d9609"));
		verify(client).deleteNode(new ShockNodeId("d73e0326-900f-44db-a359-4f297e6270a8"));
		verify(client).deleteNode(new ShockNodeId("3d82bbee-1c4b-44f6-982f-d4e5db8533b4"));
		
		verify(col).update(new BasicDBObject("chksum", md5),
				new BasicDBObject("chksum", md5)
					.append("node", "b6ce18d4-fc39-45c0-9918-d4d5800a8f43")
					.append("ver", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa2")
					.append("sorted", false),
				true, false);
	}
	
	@Test
	public void saveBlobFailOn5Attempts() throws Exception {
		final String md5 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1";
		final DBCollection col = mock(DBCollection.class);
		final BasicShockClient client = mock(BasicShockClient.class);
		final Restreamable res = mock(Restreamable.class);
		// normally you shouldn't mock value classes but these can't be instantiated
		// because I'm a dummy
		final ShockNode sn1 = mock(ShockNode.class);
		final ShockNode sn2 = mock(ShockNode.class);
		final ShockNode sn3 = mock(ShockNode.class);
		final ShockNode sn4 = mock(ShockNode.class);
		final ShockNode sn5 = mock(ShockNode.class);
		final ShockFileInformation sfi1 = mock(ShockFileInformation.class);
		
		final ShockBlobStore sbs = new ShockBlobStore(col, client);
		
		when(col.findOne(new BasicDBObject("chksum", md5))).thenReturn(null);
		
		final InputStream stream = new ByteArrayInputStream("foo".getBytes());
		
		when(res.getInputStream()).thenReturn(stream);
		
		when(client.addNode(stream, "workspace_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1", "JSON"))
				.thenReturn(sn1, sn2, sn3, sn4, sn5);
		
		when(sfi1.getChecksum("md5")).thenReturn("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa3");
		
		when(sn1.getId()).thenReturn(new ShockNodeId("ca4a4b5a-b676-4090-9a7d-9690189e29be"));
		when(sn1.getFileInformation()).thenReturn(sfi1);
		when(sn2.getId()).thenReturn(new ShockNodeId("5da17b70-bfc1-41d3-8180-a0f2610d9609"));
		when(sn2.getFileInformation()).thenReturn(sfi1);
		when(sn3.getId()).thenReturn(new ShockNodeId("d73e0326-900f-44db-a359-4f297e6270a8"));
		when(sn3.getFileInformation()).thenReturn(sfi1);
		when(sn4.getId()).thenReturn(new ShockNodeId("3d82bbee-1c4b-44f6-982f-d4e5db8533b4"));
		when(sn4.getFileInformation()).thenReturn(sfi1);
		when(sn5.getId()).thenReturn(new ShockNodeId("b6ce18d4-fc39-45c0-9918-d4d5800a8f43"));
		when(sn5.getFileInformation()).thenReturn(sfi1);
		
		failSaveBlob(sbs, new MD5(md5), res, false,
				new BlobStoreCommunicationException(
						"Blob save failed with non-matching md5 five times. " +
						"Workspace: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1, " +
						"Shock: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa3"));
		
		verify(client).deleteNode(new ShockNodeId("ca4a4b5a-b676-4090-9a7d-9690189e29be"));
		verify(client).deleteNode(new ShockNodeId("5da17b70-bfc1-41d3-8180-a0f2610d9609"));
		verify(client).deleteNode(new ShockNodeId("d73e0326-900f-44db-a359-4f297e6270a8"));
		verify(client).deleteNode(new ShockNodeId("3d82bbee-1c4b-44f6-982f-d4e5db8533b4"));
		verify(client).deleteNode(new ShockNodeId("b6ce18d4-fc39-45c0-9918-d4d5800a8f43"));
	}

	private void failSaveBlob(
			final ShockBlobStore sbs,
			final MD5 md5,
			final Restreamable res,
			final boolean sorted,
			final Exception expected) {
		try {
			sbs.saveBlob(md5, res, sorted);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void saveBlobFailMongoOnGet() {
		final String md5 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1";
		final DBCollection col = mock(DBCollection.class);
		final BasicShockClient client = mock(BasicShockClient.class);
		final Restreamable res = mock(Restreamable.class);
		
		final ShockBlobStore sbs = new ShockBlobStore(col, client);
		
		when(col.findOne(new BasicDBObject("chksum", md5))).thenThrow(new MongoException("foo"));
		
		failSaveBlob(sbs, new MD5(md5), res, true, new BlobStoreCommunicationException(
				"Could not read from the mongo database"));
	}
	
	@Test
	public void saveBlobFailOnSaveNode() throws Exception {
		saveBlobFailOnSaveNode(new JsonMappingException("foo"),
				new RuntimeException("Attribute serialization failed: foo"));
		saveBlobFailOnSaveNode(new IOException("bar"), new BlobStoreCommunicationException(
				"Could not connect to the shock backend: bar"));
		saveBlobFailOnSaveNode(new ShockHttpException(1, "baz"),
				new BlobStoreCommunicationException("Failed to create shock node: baz"));
	}

	private void saveBlobFailOnSaveNode(final Exception thrown, final Exception expected)
			throws Exception {
		final String md5 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1";
		final DBCollection col = mock(DBCollection.class);
		final BasicShockClient client = mock(BasicShockClient.class);
		final Restreamable res = mock(Restreamable.class);
		
		final ShockBlobStore sbs = new ShockBlobStore(col, client);
		
		when(col.findOne(new BasicDBObject("chksum", md5))).thenReturn(null);
		
		final InputStream stream = new ByteArrayInputStream("foo".getBytes());
		
		when(res.getInputStream()).thenReturn(stream);
		
		when(client.addNode(stream, "workspace_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1", "JSON"))
				.thenThrow(thrown);
		
		failSaveBlob(sbs, new MD5(md5), res, true, expected);
	}
	
	@Test
	public void saveBlobFailOnMongoWrite() throws Exception {
		final String md5 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1";
		final DBCollection col = mock(DBCollection.class);
		final BasicShockClient client = mock(BasicShockClient.class);
		final Restreamable res = mock(Restreamable.class);
		// normally you shouldn't mock value classes but these can't be instantiated
		// because I'm a dummy
		final ShockNode sn = mock(ShockNode.class);
		final ShockFileInformation sfi = mock(ShockFileInformation.class);
		
		final ShockBlobStore sbs = new ShockBlobStore(col, client);
		
		when(col.findOne(new BasicDBObject("chksum", md5))).thenReturn(null);
		
		final InputStream stream = new ByteArrayInputStream("foo".getBytes());
		
		when(res.getInputStream()).thenReturn(stream);
		
		when(client.addNode(stream, "workspace_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1", "JSON"))
				.thenReturn(sn);
		
		when(sn.getId()).thenReturn(new ShockNodeId("ca4a4b5a-b676-4090-9a7d-9690189e29be"));
		when(sn.getVersion()).thenReturn(
				new ShockVersionStamp("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa2"));
		when(sn.getFileInformation()).thenReturn(sfi);
		when(sfi.getChecksum("md5")).thenReturn(md5);
		
		when(col.update(new BasicDBObject("chksum", md5),
				new BasicDBObject("chksum", md5)
					.append("node", "ca4a4b5a-b676-4090-9a7d-9690189e29be")
					.append("ver", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa2")
					.append("sorted", true),
				true, false))
				.thenThrow(new MongoException("baz"));
		
		failSaveBlob(sbs, new MD5(md5), res, true, new BlobStoreCommunicationException(
				"Could not write to the mongo database"));
	}
	
	@Test
	public void saveBlobFailOnDeleteNode() throws Exception {
		saveBlobFailOnDeleteNode(new IOException("foo"), new BlobStoreCommunicationException(
				"Could not connect to the Shock backend: foo"));
		saveBlobFailOnDeleteNode(new ShockHttpException(1, "bar"),
				new BlobStoreCommunicationException("Failed to delete Shock node: bar"));
	}

	private void saveBlobFailOnDeleteNode(final Exception thrown, final Exception expected)
			throws Exception {
		final String md5 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1";
		final DBCollection col = mock(DBCollection.class);
		final BasicShockClient client = mock(BasicShockClient.class);
		final Restreamable res = mock(Restreamable.class);
		// normally you shouldn't mock value classes but these can't be instantiated
		// because I'm a dummy
		final ShockNode sn1 = mock(ShockNode.class);
		final ShockFileInformation sfi1 = mock(ShockFileInformation.class);
		
		final ShockBlobStore sbs = new ShockBlobStore(col, client);
		
		when(col.findOne(new BasicDBObject("chksum", md5))).thenReturn(null);
		
		final InputStream stream = new ByteArrayInputStream("foo".getBytes());
		
		when(res.getInputStream()).thenReturn(stream);
		
		when(client.addNode(stream, "workspace_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1", "JSON"))
				.thenReturn(sn1);
		
		when(sfi1.getChecksum("md5")).thenReturn("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa3");
		
		when(sn1.getId()).thenReturn(new ShockNodeId("ca4a4b5a-b676-4090-9a7d-9690189e29be"));
		when(sn1.getFileInformation()).thenReturn(sfi1);
		
		doThrow(thrown)
				.when(client).deleteNode(new ShockNodeId("ca4a4b5a-b676-4090-9a7d-9690189e29be"));
		
		failSaveBlob(sbs, new MD5(md5), res, true, expected);
	}
	
	@Test
	public void removeBlobNoop() throws Exception {
		final String md5 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1";
		final DBCollection col = mock(DBCollection.class);
		final BasicShockClient client = mock(BasicShockClient.class);
		
		final ShockBlobStore sbs = new ShockBlobStore(col, client);
		
		final DBObject dbo = new BasicDBObject();
		dbo.put("chksum", 1);
		final DBObject opts = new BasicDBObject();
		opts.put("unique", 1);
		// need to verify so verifyNoMoreInteractions() works
		verify(col).createIndex(dbo, opts);
		
		when(col.findOne(new BasicDBObject("chksum", md5))).thenReturn(null);
		
		sbs.removeBlob(new MD5(md5));
		// same as the when() above
		verify(col).findOne(new BasicDBObject("chksum", md5));
		
		verifyZeroInteractions(client);
		verifyNoMoreInteractions(col);
	}
	
	@Test
	public void removeBlob() throws Exception {
		final String md5 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1";
		final DBCollection col = mock(DBCollection.class);
		final BasicShockClient client = mock(BasicShockClient.class);
		
		final ShockBlobStore sbs = new ShockBlobStore(col, client);
		
		when(col.findOne(new BasicDBObject("chksum", md5))).thenReturn(
				new BasicDBObject("node", "ca4a4b5a-b676-4090-9a7d-9690189e29be"));
		
		sbs.removeBlob(new MD5(md5));
		
		verify(col).remove(new BasicDBObject("chksum", md5));
		verify(client).deleteNode(new ShockNodeId("ca4a4b5a-b676-4090-9a7d-9690189e29be"));
	}
	
	@Test
	public void removeBlobFailReadMongo() throws Exception {
		final String md5 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1";
		final DBCollection col = mock(DBCollection.class);
		final BasicShockClient client = mock(BasicShockClient.class);
		
		final ShockBlobStore sbs = new ShockBlobStore(col, client);
		
		when(col.findOne(new BasicDBObject("chksum", md5))).thenThrow(new MongoException("foo"));
		
		failRemoveBlob(sbs, new MD5(md5), new BlobStoreCommunicationException(
				"Could not read from the mongo database"));
	}

	private void failRemoveBlob(
			final ShockBlobStore sbs,
			final MD5 md5,
			final Exception expected) {
		try {
			sbs.removeBlob(md5);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void removeBlobFailOnDeleteNode() throws Exception {
		removeBlobFailOnDeleteNode(new IOException("foo"), new BlobStoreCommunicationException(
				"Could not connect to the Shock backend: foo"));
		removeBlobFailOnDeleteNode(new ShockHttpException(1, "bar"),
				new BlobStoreCommunicationException("Failed to delete Shock node: bar"));
	}

	private void removeBlobFailOnDeleteNode(final Exception thrown, final Exception expected)
			throws Exception {
		final String md5 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1";
		final DBCollection col = mock(DBCollection.class);
		final BasicShockClient client = mock(BasicShockClient.class);
		
		final ShockBlobStore sbs = new ShockBlobStore(col, client);
		
		when(col.findOne(new BasicDBObject("chksum", md5))).thenReturn(
				new BasicDBObject("node", "ca4a4b5a-b676-4090-9a7d-9690189e29be"));
		
		doThrow(thrown)
				.when(client).deleteNode(new ShockNodeId("ca4a4b5a-b676-4090-9a7d-9690189e29be"));
		
		failRemoveBlob(sbs, new MD5(md5), expected);
	}
	
	// TODO TEST getBlob, removeAllBlobs, getExternalIdentifier tests
}

