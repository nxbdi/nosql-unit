package com.lordofthejars.nosqlunit.neo4j;

import static com.lordofthejars.nosqlunit.core.IOUtils.deleteDir;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.neo4j.server.configuration.Configurator;

import com.lordofthejars.nosqlunit.core.AbstractLifecycleManager;
import com.lordofthejars.nosqlunit.core.CommandLineExecutor;
import com.lordofthejars.nosqlunit.core.OperatingSystem;
import com.lordofthejars.nosqlunit.core.OperatingSystemResolver;
import com.lordofthejars.nosqlunit.core.OsNameSystemPropertyOperatingSystemResolver;

public class ManagedNeoServer extends AbstractLifecycleManager {

	private static final String START_COMMAND = "start";
	private static final String STOP_COMMAND = "stop";
	private static final String STATUS_COMMAND = "status";
	private static final String SERVER_NOT_AVAILABLE_MESSAGE = "Neo4j Server is not running";

	private static final String LOCALHOST = "127.0.0.1";

	private static final int NUM_RETRIES_TO_CHECK_SERVER_UP = 3;

	protected static final String DEFAULT_NEO4J_TARGET_PATH = "target" + File.separatorChar + "neo4j-temp";
	protected static final String NEO4J_BINARY_DIRECTORY = "bin";

	protected static final String NEO4J_EXECUTABLE_X = "neo4j";
	protected static final String NEO4J_EXECUTABLE_W = "Neo4j.bat";

	private String targetPath = DEFAULT_NEO4J_TARGET_PATH;

	private String neo4jPath = System.getProperty("NEO4J_HOME");

	private int port = Configurator.DEFAULT_WEBSERVER_PORT;

	private CommandLineExecutor commandLineExecutor = new CommandLineExecutor();
	private OperatingSystemResolver operatingSystemResolver = new OsNameSystemPropertyOperatingSystemResolver();

	private ManagedNeoServer() {
		super();
	}

	/**
	 * Builder to start neo4j server accordingly to your setup
	 */
	public static class Neo4jServerRuleBuilder {

		private ManagedNeoServer managedNeoServer;

		private Neo4jServerRuleBuilder() {
			this.managedNeoServer = new ManagedNeoServer();
		}

		public static Neo4jServerRuleBuilder newManagedNeo4jServerRule() {
			return new Neo4jServerRuleBuilder();
		}

		public Neo4jServerRuleBuilder neo4jPath(String neo4jPath) {
			this.managedNeoServer.setNeo4jPath(neo4jPath);
			return this;
		}

		public Neo4jServerRuleBuilder targetPath(String targetPath) {
			this.managedNeoServer.setTargetPath(targetPath);
			return this;
		}

		public Neo4jServerRuleBuilder port(int port) {
			this.managedNeoServer.setPort(port);
			return this;
		}

		public ManagedNeoServer build() {
			if (this.managedNeoServer.getNeo4jPath() == null) {
				throw new IllegalArgumentException("No Path to Neo4j is provided.");
			}
			return this.managedNeoServer;
		}

	}

	@Override
	protected String getHost() {
		return LOCALHOST;
	}

	@Override
	protected int getPort() {
		return port;
	}

	@Override
	protected void doStart() throws Throwable {
		File targetPathDirectory = ensureTargetPathDoesNotExitsAndReturnCompositePath();

		if (targetPathDirectory.mkdirs()) {
			startNeo4jAsADaemon();

			boolean isServerUp = assertThatConnectionIsPossible();

			if (!isServerUp) {
				throw new IllegalStateException("Couldn't establish a connection with " + this.neo4jPath
						+ " server at /127.0.0.1:" + port);
			}
		} else {	
			throw new IllegalStateException("Target Path " + targetPathDirectory
					+ " could not be created.");
		}
	}

	@Override
	protected void doStop() {
		try {
			stopNeo4j();
		} catch (InterruptedException e) {
			throw new IllegalArgumentException(e);
		} finally {
			// until neo4j 1.8 data directory cannot be configured.
			ensureTargetPathDoesNotExitsAndReturnCompositePath();
		}
	}


	private boolean assertThatConnectionIsPossible() throws InterruptedException {

		for (int i = 0; i < NUM_RETRIES_TO_CHECK_SERVER_UP; i++) {

			List<String> status = statusNeo4j();

			if (status.size() > 0) {
				String currentStatus = status.get(0);

				if (!SERVER_NOT_AVAILABLE_MESSAGE.equalsIgnoreCase(currentStatus)) {
					return true;
				}
			}

			TimeUnit.SECONDS.sleep(3);
		}

		return false;
	}

	private List<String> statusNeo4j() throws InterruptedException {

		Process pwd;
		try {
			pwd = startProcess(STATUS_COMMAND);
			List<String> lines = getConsoleOutput(pwd);
			pwd.waitFor();
			if (pwd.exitValue() != 0) {
				throw new IllegalStateException("Neo4j status [" + neo4jPath + "port " + port
						+ "] could not be started. Next console message was thrown: " + lines);
			}
			return lines;
		} catch (IOException e) {
			throw new IllegalStateException("Neo4j status [" + neo4jPath + "port " + port
					+ "] could not be started. Next console message was thrown: " + e.getMessage());
		}
	}

	private List<String> stopNeo4j() throws InterruptedException {

		Process pwd;
		try {
			pwd = startProcess(STOP_COMMAND);
			List<String> lines = getConsoleOutput(pwd);
			pwd.waitFor();
			if (pwd.exitValue() != 0) {
				throw new IllegalStateException("Neo4j stop [" + neo4jPath + "port " + port
						+ "] could not be started. Next console message was thrown: " + lines);
			}
			return lines;
		} catch (IOException e) {
			throw new IllegalStateException("Neo4j stop [" + neo4jPath + "port " + port
					+ "] could not be started. Next console message was thrown: " + e.getMessage());
		}
	}

	private List<String> startNeo4jAsADaemon() throws InterruptedException {

		Process pwd;
		try {
			pwd = startProcess(START_COMMAND);
			List<String> lines = getConsoleOutput(pwd);
			pwd.waitFor();
			if (pwd.exitValue() != 0) {
				throw new IllegalStateException("Neo4j start [" + neo4jPath + "port " + port
						+ "] could not be started. Next console message was thrown: " + lines);
			}
			return lines;
		} catch (IOException e) {
			throw new IllegalStateException("Neo4j start [" + neo4jPath + "port " + port
					+ "] could not be started. Next console message was thrown: " + e.getMessage());
		}
	}

	private Process startProcess(String command) throws IOException {
		return this.commandLineExecutor.startProcessInDirectoryAndArguments(targetPath,
				buildOperationSystemProgramAndArguments(command));
	}

	private List<String> buildOperationSystemProgramAndArguments(String command) {

		List<String> programAndArguments = new ArrayList<String>();

		programAndArguments.add(getExecutablePath());
		programAndArguments.add(command);

		return programAndArguments;

	}

	private String getExecutablePath() {
		return this.neo4jPath + File.separatorChar + NEO4J_BINARY_DIRECTORY + File.separatorChar + neo4jExecutable();
	}

	private String neo4jExecutable() {
		OperatingSystem operatingSystem = this.operatingSystemResolver.currentOperatingSystem();

		switch (operatingSystem.getFamily()) {
		case WINDOWS:
			return NEO4J_EXECUTABLE_W;
		default:
			return NEO4J_EXECUTABLE_X;
		}

	}

	private List<String> getConsoleOutput(Process pwd) throws IOException {
		return this.commandLineExecutor.getConsoleOutput(pwd);
	}


	private File ensureTargetPathDoesNotExitsAndReturnCompositePath() {
		File dbPath = new File(targetPath);
		if (dbPath.exists()) {
			deleteDir(dbPath);
		}
		return dbPath;
	}

	protected void setCommandLineExecutor(CommandLineExecutor commandLineExecutor) {
		this.commandLineExecutor = commandLineExecutor;
	}

	protected void setOperatingSystemResolver(OperatingSystemResolver operatingSystemResolver) {
		this.operatingSystemResolver = operatingSystemResolver;
	}

	private void setTargetPath(String targetPath) {
		this.targetPath = targetPath;
	}

	private void setNeo4jPath(String neo4jPath) {
		this.neo4jPath = neo4jPath;
	}

	private String getNeo4jPath() {
		return neo4jPath;
	}

	private void setPort(int port) {
		this.port = port;
	}

}
