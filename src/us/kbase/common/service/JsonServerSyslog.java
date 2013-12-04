package us.kbase.common.service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.ini4j.Ini;
import org.productivity.java.syslog4j.SyslogConstants;
import org.productivity.java.syslog4j.SyslogIF;
import org.productivity.java.syslog4j.impl.unix.socket.UnixSocketSyslog;
import org.productivity.java.syslog4j.impl.unix.socket.UnixSocketSyslogConfig;

/**
 * Class is used by server side for logging into linux/macos system syslog.
 */
public class JsonServerSyslog {
	private final String serviceName;
	private final SyslogIF log;
	private final Config config;
	private File currentFile = null;
	private PrintWriter currentWriter = null;
	private SyslogOutput output = defaultSyslogOutput;
	private int logLevel = -1;

	public static final int LOG_LEVEL_ERR = SyslogConstants.LEVEL_ERROR;
	public static final int LOG_LEVEL_INFO = SyslogConstants.LEVEL_INFO;
	public static final int LOG_LEVEL_DEBUG = SyslogConstants.LEVEL_DEBUG;
	public static final int LOG_LEVEL_DEBUG2 = SyslogConstants.LEVEL_DEBUG + 1;
	public static final int LOG_LEVEL_DEBUG3 = SyslogConstants.LEVEL_DEBUG + 2;

	private static ThreadLocal<SimpleDateFormat> sdf = new ThreadLocal<SimpleDateFormat>();
	private static ThreadLocal<RpcInfo> rpcInfo = new ThreadLocal<RpcInfo>();

	private static String systemLogin = nn(System.getProperty("user.name"));
	private static String pid = getPID();
	
	private static final SyslogOutput defaultSyslogOutput = new SyslogOutput();
	
	private static final long startMillis = System.currentTimeMillis();
	private static final long startNanos = System.nanoTime();
	
	public JsonServerSyslog(String serviceName, String configFileParam) {
		this(serviceName, configFileParam, -1);
	}
	
	public JsonServerSyslog(String serviceName, String configFileParam, int defultLogLevel) {
		this.serviceName = serviceName;
		logLevel = defultLogLevel;
		UnixSocketSyslogConfig cfg = new UnixSocketSyslogConfig();
		if (System.getProperty("os.name").toLowerCase().startsWith("mac"))
			cfg.setPath("/var/run/syslog");
		cfg.setFacility(SyslogConstants.FACILITY_LOCAL1);
		cfg.removeAllMessageModifiers();
		cfg.setIdent(null);
		log = new UnixSocketSyslog();
		log.initialize(SyslogConstants.UNIX_SOCKET, cfg);
		this.config = new Config(configFileParam, serviceName);
	}

	public JsonServerSyslog(JsonServerSyslog otherLog) {
		this.serviceName = otherLog.serviceName;
		this.log = otherLog.log;
		this.config = otherLog.config;
	}

	public void changeOutput(SyslogOutput output) {
		if (output == null)
			throw new IllegalStateException("Syslog output shouldn't be null");
		this.output = output;
	}
	
	private static String nn(String value) {
		return value == null ? "-" : value;
	}
	
	private static String getPID() {
		String ret = ManagementFactory.getRuntimeMXBean().getName();
		if (ret.indexOf('@') > 0)
			ret = ret.substring(0, ret.indexOf('@'));
		return ret;
	}
	
	public void log(int level, String caller, String... messages) {
		config.checkForReloadingTime();
		if (level > getLogLevel())
			return;
		if (level > LOG_LEVEL_DEBUG)
			level = LOG_LEVEL_DEBUG;
		String micro = getCurrentMicro();
		for (String message : messages)
			output.logToSystem(log, level, getFullMessage(level, caller, message, micro));
		logToFile(level, caller, messages, micro);
		config.addPrintedLines(messages.length);
	}
	
	public void logErr(String message) {
		logErr(new Exception(message), findCaller());
	}

	public void logErr(Throwable err) {
		logErr(err, findCaller());
	}
	
	public void logErr(Throwable err, String caller) {
		List<String> messages = new ArrayList<String>();
		extractErrorLines(err, false, messages);
		log(LOG_LEVEL_ERR, caller, messages.toArray(new String[messages.size()]));
	}

	private void extractErrorLines(Throwable err, boolean isCause, List<String> messages) {
		StackTraceElement[] st = err.getStackTrace();
		int firstPos = 0;
		String packageName = "us.kbase";
		String className = JsonServerServlet.class.getName();
		String className2 = JsonServerSyslog.class.getName();
		for (; firstPos < st.length; firstPos++) {
			if (st[firstPos].getClassName().equals(className) || st[firstPos].getClassName().equals(className2))
				continue;
			break;
		}
		if (firstPos == st.length)
			firstPos = 0;
		int lastPos = st.length - 1;
		for (; lastPos > firstPos; lastPos--) {
			if (st[lastPos].getClassName().startsWith(packageName) && 
					(!st[lastPos].getClassName().equals(className)) &&
					(!st[lastPos].getClassName().equals(className2)))
				break;
		}
		String errorPrefix = err.getClass().equals(Exception.class) ? "Error: " :
			(err.getClass().getName() + ": ");
		if (isCause)
			errorPrefix = "Cause of error above: " + errorPrefix;
		messages.add(errorPrefix + err.getMessage());
		messages.add("Traceback (most recent call last):");
		for (int pos = lastPos; pos >= firstPos; pos--) {
			messages.add("Class \"" + st[pos].getClassName() + "\", file \"" + st[pos].getFileName() + 
					"\", line " + st[pos].getLineNumber() + ", in " + st[pos].getMethodName());
		}
		if (err.getCause() != null)
			extractErrorLines(err.getCause(), true, messages);
	}
	
	public void logInfo(String message) {
		log(LOG_LEVEL_INFO, findCaller(), message);
	}
	
	public void logDebug(String message) {
		log(LOG_LEVEL_DEBUG, findCaller(), message);
	}
	
	public void logDebug(String message, int debugLevelFrom1to3) {
		if (debugLevelFrom1to3 < 1 || debugLevelFrom1to3 > 3)
			throw new IllegalStateException("Wrong debug log level, it should be between 1 and 3");
		log(LOG_LEVEL_DEBUG + (debugLevelFrom1to3 - 1), findCaller(), message);
	}

	public static String findCaller() {
		StackTraceElement[] st = Thread.currentThread().getStackTrace();
		String packageName = "us.kbase";
		String className = JsonServerServlet.class.getName();
		String className2 = JsonServerSyslog.class.getName();
		for (int pos = 0; pos < st.length; pos++) {
			if (st[pos].getClassName().equals(className) || st[pos].getClassName().equals(className2) ||
					!st[pos].getClassName().startsWith(packageName))
				continue;
			return st[pos].getClassName();
		}
		throw new IllegalStateException();
	}

	static RpcInfo getCurrentRpcInfo() {
		RpcInfo ret = rpcInfo.get();
		if (ret == null) {
			ret = new RpcInfo();
			rpcInfo.set(ret);
		}
		return ret;
	}

	private String getFullMessage(int level, String caller, String message, String micro) {
		String levelText = level == LOG_LEVEL_ERR ? "ERR" : (level == LOG_LEVEL_INFO ? "INFO" : "DEBUG");
		RpcInfo info = getCurrentRpcInfo();
		return "[" + serviceName + "] [" + levelText + "] [" + micro + "] [" + systemLogin + "] " +
				"[" + caller + "] [" + pid + "] [" + nn(info.getIp()) + "] [" + nn(info.getUser()) + "] " +
				"[" + nn(info.getModule()) + "] [" + nn(info.getMethod()) + "] [" + nn(info.getId()) + "]: " + message;
	}
	
	private void logToFile(int level, String caller, String[] messages, String micro) {
		File f = config.getExternalLogFile();
		if (f == null)
			return;
		try {
			if (currentWriter != null && !f.equals(currentFile)) {
				currentWriter.close();
				currentWriter = null;
			}
			if (currentWriter == null) {
				currentFile = f;
			}
			for (String message : messages) {
				message = getDateFormat().format(new Date()) + " java " + getFullMessage(level, caller, message, micro);
				currentWriter = output.logToFile(currentFile, currentWriter, level, message);
			}
			if (currentWriter != null)
				currentWriter.flush();
		} catch (Exception ex) {
			output.logToSystem(log, LOG_LEVEL_ERR, getFullMessage(LOG_LEVEL_ERR, getClass().getName(), 
					"Can not write into log file: " + f, micro));
		}
	}
	
	public int getLogLevel() {
		return logLevel < 0 ? config.maxLogLevel : logLevel;
	}
	
	public void setLogLevel(int level) {
		logLevel = level;
	}
	
	public void clearLogLevel() {
		logLevel = -1;
	}
	
	private static SimpleDateFormat getDateFormat() {
		SimpleDateFormat ret = sdf.get();
		if (ret == null) {
			ret = new SimpleDateFormat("MMM d HH:mm:ss");
			sdf.set(ret);
		}
		return ret;
	}
	
	private static String getCurrentMicro() {
		long microSeconds = startMillis * 1000 + (System.nanoTime() - startNanos) / 1000;
		return (microSeconds / 1000000) + "." + (microSeconds % 1000000);
		//String ret = "" + System.currentTimeMillis() + "000000";
		//return ret.substring(0, ret.length() - 9) + "." + ret.substring(ret.length() - 9, ret.length() - 3);
	}

	private class Config {
		private String configPathParam;
		private String sectionName;
		private int printedLines = 0;
		private int maxLogLevel = LOG_LEVEL_INFO;
		private File externalLogFile = null;
		private long lastLoadingTime = -1;

		public Config(String serviceConfigPathParam, String sectionName) {
			this.configPathParam = serviceConfigPathParam;
			this.sectionName = sectionName;
			load();
		}
		
		public void addPrintedLines(int lines) {
			printedLines += lines;
			if (printedLines >= 100) {
				printedLines = 0;
				load();
			}
		}
		
		public File getExternalLogFile() {
			return externalLogFile;
		}
		
		public void checkForReloadingTime() {
			if (System.currentTimeMillis() - lastLoadingTime >= 5 * 60 * 1000)
				load();
		}
		
		private void load() {
			lastLoadingTime = System.currentTimeMillis();
			maxLogLevel = LOG_LEVEL_INFO;
			externalLogFile = null;
			File file;
			//System.out.println("Configuration file reloading...");
			String configPath = System.getProperty(configPathParam) == null ?
					System.getenv(configPathParam) : System.getProperty(configPathParam);
			if (configPath == null) {
				return;
			} else {
				file = new File(configPath);
			}
			try {
				Ini ini = new Ini(file);
				Map<String, String> section = ini.get(sectionName);
				if (section == null)
					return;
				String filePath = section.get("mlog_log_file");
				if (filePath != null)
					externalLogFile = new File(filePath);
				String logLevelText = section.get("mlog_log_level");
				if (logLevelText != null)
					maxLogLevel = Integer.parseInt(logLevelText);
			} catch (IOException ignore) {
				output.logToSystem(log, LOG_LEVEL_ERR, getFullMessage(LOG_LEVEL_ERR, getClass().getName(), 
						"Error reading configuration file: " + file, getCurrentMicro()));
			}
		}
	}
	
	public static class SyslogOutput {
		public void logToSystem(SyslogIF log, int level, String message) {
			try {
				log.log(level, message);
				//log.flush();
			} catch (Throwable ex) {
				System.out.println("JsonServerSyslog: Error writing to syslog (" + ex.getMessage() + "), see user defined log-file instead of syslog.");
			}
		}
		
		public PrintWriter logToFile(File f, PrintWriter pw, int level, String message) throws Exception {
			try {
				if (pw == null)
					pw = new PrintWriter(new FileWriter(f, true));
				pw.println(message);
			} catch (Throwable ignore) {}
			return pw;
		}
	}
	
	public static class RpcInfo {
		private String id;
		private String module;
		private String method;
		private String user;
		private String ip;
		
		RpcInfo reset() {
			id = null;
			module = null;
			method = null;
			user = null;
			return this;
		}
		
		String getId() {
			return id;
		}
		
		void setId(String id) {
			this.id = id;
		}
		
		String getModule() {
			return module;
		}
		
		void setModule(String module) {
			this.module = module;
		}
		
		String getMethod() {
			return method;
		}
		
		void setMethod(String method) {
			this.method = method;
		}
		
		String getUser() {
			return user;
		}
		
		void setUser(String user) {
			this.user = user;
		}
		
		String getIp() {
			return ip;
		}
		
		void setIp(String ip) {
			this.ip = ip;
		}
	}
}
