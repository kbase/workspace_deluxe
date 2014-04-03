import junit.framework.JUnit4TestAdapter;
import junit.framework.TestSuite;

import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

import us.kbase.typedobj.db.test.TypeRegisteringTest;
import us.kbase.typedobj.test.BasicValidationTest;
import us.kbase.typedobj.test.IdProcessingTest;
import us.kbase.typedobj.test.ObjectExtractionByPathTest;
import us.kbase.typedobj.test.TypeDefsTest;
import us.kbase.typedobj.test.WsSubsetExtractionTest;
import us.kbase.workspace.test.database.mongo.GridFSBackendTest;
import us.kbase.workspace.test.database.mongo.MongoInternalsTest;
import us.kbase.workspace.test.database.mongo.ShockBackendTest;
import us.kbase.workspace.test.kbase.JSONRPCLayerLongTest;
import us.kbase.workspace.test.kbase.JSONRPCLayerTest;
import us.kbase.workspace.test.workspace.WorkspaceLongTest;
import us.kbase.workspace.test.workspace.WorkspaceTest;

@RunWith(AllTests.class)
public class AllTestsRunner {
	public static TestSuite suite() {
        TestSuite suite = new TestSuite();

        suite.addTest(new JUnit4TestAdapter(TypeDefsTest.class));
        suite.addTest(new JUnit4TestAdapter(BasicValidationTest.class));
        suite.addTest(new JUnit4TestAdapter(IdProcessingTest.class));
        suite.addTest(new JUnit4TestAdapter(WsSubsetExtractionTest.class));
        suite.addTest(new JUnit4TestAdapter(ObjectExtractionByPathTest.class));
        suite.addTest(new JUnit4TestAdapter(TypeRegisteringTest.class));
        suite.addTest(new JUnit4TestAdapter(ShockBackendTest.class));
        suite.addTest(new JUnit4TestAdapter(GridFSBackendTest.class));
        suite.addTest(new JUnit4TestAdapter(MongoInternalsTest.class));
        suite.addTest(new JUnit4TestAdapter(WorkspaceTest.class));
        suite.addTest(new JUnit4TestAdapter(JSONRPCLayerTest.class));
        suite.addTest(new JUnit4TestAdapter(WorkspaceLongTest.class));
        suite.addTest(new JUnit4TestAdapter(JSONRPCLayerLongTest.class));

        return suite;
     }
}
