package us.kbase.test.workspace.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import org.junit.Test;

import us.kbase.workspace.database.Reference;
import us.kbase.workspace.database.refsearch.ReferenceTreeNode;

public class ReferenceTreeNodeTest {

	@Test
	public void constructRootNode() throws Exception {
		final ReferenceTreeNode node = new ReferenceTreeNode(new Reference(4, 3, 10), null);
		assertThat("incorrect reference", node.getReference(), is(new Reference(4, 3, 10)));
		assertThat("expected null parent", node.getParent(), is((ReferenceTreeNode) null));
		assertThat("incorrect toString", node.toString(),
				is("ReferenceTreeNode [ref = 4/3/10, parent ref = null]"));
	}
	
	@Test
	public void constructChildNode() throws Exception {
		final ReferenceTreeNode node = new ReferenceTreeNode(new Reference(4, 3, 11),
				new ReferenceTreeNode(new Reference(2, 4, 6), null));
		assertThat("incorrect reference", node.getReference(), is(new Reference(4, 3, 11)));
		assertThat("incorrect parent reference", node.getParent().getReference(),
				is(new Reference(2, 4, 6)));
		assertThat("expected null parent of parent", node.getParent().getParent(),
				is((ReferenceTreeNode) null));
		assertThat("incorrect toString", node.toString(),
				is("ReferenceTreeNode [ref = 4/3/11, parent ref = 2/4/6]"));
	}
	
	@Test
	public void failConstruct() throws Exception {
		try {
			new ReferenceTreeNode(null, null);
			fail("created ref tree node without ref");
		} catch (NullPointerException e) {
			assertThat("incorrect exception message", e.getMessage(), is("ref"));
		}
	}
}
