package us.kbase.workspace.kbase;

import static us.kbase.workspace.version.WorkspaceVersion.VERSION;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import org.bson.Document;
import org.slf4j.LoggerFactory;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.MongoException;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import us.kbase.workspace.database.mongo.CollectionNames;
import us.kbase.workspace.database.mongo.SchemaUpdater;
import us.kbase.workspace.database.mongo.SchemaUpdater.SchemaUpdateException;
import us.kbase.workspace.kbase.InitWorkspaceServer.WorkspaceInitException;
import us.kbase.workspace.kbase.KBaseWorkspaceConfig.KBaseWorkspaceConfigException;

/** A CLI class to update the workspace schema. */
@Command(
		name = "update_workspace_database_schema",
		mixinStandardHelpOptions = true,
		version = VERSION,
		description = "Update the workspace database to the current version.\n" +
				"Please read the upgrade documentation carefully before running this script."
		)
public class SchemaUpdaterCLI implements Callable<Integer>{
	
	// don't support the KB_SERVICE_NAME env var garbage
	private final static String CONFIG_SECTION = "Workspace";
	
	@Spec
	private CommandSpec spec;
	
	@Parameters(
			index = "0",
			paramLabel = "<CONFIG_FILE>",
			description = "The configuration file (usually called deploy.cfg) for the workspace " +
					"service to modify.")
	private Path configFile;
	
	@Option(names = {"-c", "--complete"},
			description = "Complete the upgrade. WARNING: DO NOT complete the upgrade unless " +
				"no other processes are writing to the workspace database.")
	private boolean complete;
	
	@Option(names = {"-o", "--override-version-check"},
			description = "Allow the upgrade to continue even if the database schema version " +
				"indicates it is complete.")
	private boolean override;
	
	@Option(names = {"-s", "--print-stacktrace"},
			description = "On an error, print a stacktrace if available.")
	private boolean stacktrace;
	
	@Option(names = {"-t", "--test-index"}, description = "Test index creation")
	private boolean testIndex;
	
	static {
		// mongo sure is chatty
		((Logger) LoggerFactory.getLogger("org.mongodb.driver")).setLevel(Level.ERROR);
	}

	/** Provides a MongoClient given a configuration. */ 
	public static interface MongoProvider {
		
		/** Provide the MongoClient.
		 * @param cfg the configuration to use to build the client.
		 * @return the client.
		 * @throws WorkspaceInitException if the client couldn't be created.
		 */
		public MongoClient provide(final KBaseWorkspaceConfig cfg) throws WorkspaceInitException;
	}

	private final SchemaUpdater updater;
	private final MongoProvider mongoProvider;
	
	private SchemaUpdaterCLI(final SchemaUpdater updater, final MongoProvider mongoProvider) {
		this.updater = updater;
		this.mongoProvider = mongoProvider;
	}

	/** Run the CLI.
	 * @param args the CLI arguments.
	 */
	public static void main(final String[] args) {
		// WARNING - these lines are only tested manually. Retest if you make changes.
		final int exitCode = run(
				new SchemaUpdater(),
				cfg -> InitWorkspaceServer.buildMongo(cfg, cfg.getDBname()),
				System.out,
				System.err,
				args);
		System.exit(exitCode);
	}
	
	/** Run the updater.
	 * @param updater an updater instance.
	 * @param mongoProvider a provider of Mongo.
	 * @param out an output stream to which to write logs.
	 * @param err an output stream to which to write errors.
	 * @param args the CLI arguments.
	 * @return the exit code.
	 */
	public static int run(
			final SchemaUpdater updater,
			final MongoProvider mongoProvider,
			final OutputStream out,
			final OutputStream err,
			final String[] args) {
		final CommandLine cl = new CommandLine(new SchemaUpdaterCLI(updater, mongoProvider));
		cl.setOut(new PrintWriter(out));
		cl.setErr(new PrintWriter(err));
		final int exitCode = cl.execute(args);
		// sometimes no output unless these are flushed. Flushing the underlying streams no worky
		cl.getOut().flush();
		cl.getErr().flush();
		return exitCode;
	}
	
	@Override
	public Integer call() {
		final CommandLine cl = spec.commandLine();
		final KBaseWorkspaceConfig cfg;
		try {
			cfg = new KBaseWorkspaceConfig(configFile, CONFIG_SECTION);
		} catch (KBaseWorkspaceConfigException e) {
			printStackTrace(cl, e);
			throw new ParameterException(cl, e.getLocalizedMessage(), e);
		}
		if (cfg.hasErrors()) {
			for (final String e: cfg.getErrors()) {
				cl.getErr().println(e);
			}
			throw new ParameterException(cl, String.format(
					"Invalid specification file %s", configFile.toAbsolutePath()));
		}
		try (final MongoClient mc = mongoProvider.provide(cfg)) {
			final String col = CollectionNames.COL_WORKSPACE_VERS;
			if (testIndex) {
				cl.getOut().println("testing index creation w/ old & new mongo APIs");
				System.out.println("starting test code");
				final DB dbold = mc.getDB(cfg.getDBname());
				dbold.getCollection(col).createIndex(
						new BasicDBObject("meta", 1), new BasicDBObject("sparse", 1));
				System.out.println("created old index");
				final MongoDatabase dbnew = mc.getDatabase(cfg.getDBname());
				dbnew.getCollection(col).createIndex(
						new Document("meta", 1), new IndexOptions().sparse(true));
				System.out.println("created new index");
			}
			final MongoDatabase db = mc.getDatabase(cfg.getDBname());
			updater.update(db, s -> cl.getOut().println(s), complete, override);
		} catch (SchemaUpdateException | WorkspaceInitException | MongoException e) {
			printStackTrace(cl, e);
			throw new ParameterException(cl, e.getLocalizedMessage(), e);
		}
		return 0;
	}
	
	private void printStackTrace(final CommandLine cl, final Exception e) {
		if (stacktrace) {
			e.printStackTrace(cl.getErr());
		}
	}

}
