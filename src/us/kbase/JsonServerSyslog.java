package us.kbase;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import org.ini4j.Ini;
import org.productivity.java.syslog4j.SyslogConstants;
import org.productivity.java.syslog4j.impl.unix.socket.UnixSocketSyslog;
import org.productivity.java.syslog4j.impl.unix.socket.UnixSocketSyslogConfig;

public class JsonServerSyslog {
	private final String serviceName;
	private final UnixSocketSyslog log;
	private final Config config;

	private int logLevel = -1;

	public static final int LOG_LEVEL_ERR = SyslogConstants.LEVEL_ERROR;
	public static final int LOG_LEVEL_INFO = SyslogConstants.LEVEL_INFO;
	public static final int LOG_LEVEL_DEBUG = SyslogConstants.LEVEL_DEBUG;
	public static final int LOG_LEVEL_DEBUG2 = SyslogConstants.LEVEL_DEBUG + 1;
	public static final int LOG_LEVEL_DEBUG3 = SyslogConstants.LEVEL_DEBUG + 2;

	private static ThreadLocal<SimpleDateFormat> sdf = new ThreadLocal<SimpleDateFormat>();

	private static String systemLogin = nn(System.getProperty("user.name"));
	private static String pid = getPID();
	
	public JsonServerSyslog(String serviceName, String configFile) {
		this.serviceName = serviceName;
		UnixSocketSyslogConfig cfg = new UnixSocketSyslogConfig();
		if (System.getProperty("os.name").toLowerCase().startsWith("mac"))
			cfg.setPath("/var/run/syslog");
		cfg.setFacility(SyslogConstants.FACILITY_LOCAL1);
		cfg.removeAllMessageModifiers();
		cfg.setIdent(null);
		log = new UnixSocketSyslog();
		log.initialize(SyslogConstants.UNIX_SOCKET, cfg);
		this.config = new Config(configFile, serviceName);
	}

	public JsonServerSyslog(JsonServerSyslog otherLog) {
		this.serviceName = otherLog.serviceName;
		this.log = otherLog.log;
		this.config = otherLog.config;
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
		for (String message : messages)
			log.log(level, getFullMessage(level, caller, message));
		//log.flush();
		logToFile(level, caller, messages);
		config.addPrintedLines(messages.length);
	}
	
	private String getFullMessage(int level, String caller, String message) {
		String levelText = level == LOG_LEVEL_ERR ? "ERR" : (level == LOG_LEVEL_INFO ? "INFO" : "DEBUG");
		JsonServerServlet.RpcInfo info = JsonServerServlet.getCurrentRpcInfo();
		return "[" + serviceName + "] [" + levelText + "] [" + getCurrentMicro() + "] [" + systemLogin + "] " +
				"[" + caller + "] [" + pid + "] [" + nn(info.getUser()) + "] [" + nn(info.getModule()) + "] " +
				"[" + nn(info.getMethod()) + "] [" + nn(info.getId()) + "]: " + message;
	}
	
	private void logToFile(int level, String caller, String[] messages) {
		File f = config.getExternalLogFile();
		if (f == null)
			return;
		try {
			PrintWriter pw = new PrintWriter(new FileWriter(f, true));
			for (String message : messages) {
				message = getDateFormat().format(new Date()) + " java " + getFullMessage(level, caller, message);
				pw.println(message);
			}
			pw.close();
		} catch (Exception ex) {
			log.log(LOG_LEVEL_ERR, getFullMessage(LOG_LEVEL_ERR, getClass().getName(), "Can not write into log file: " + f));
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
		String ret = "" + System.currentTimeMillis() + "000000";
		return ret.substring(0, ret.length() - 9) + "." + ret.substring(ret.length() - 9, ret.length() - 3);
	}

	private class Config {
		private String configPath;
		private String sectionName;
		private int printedLines = 0;
		private int maxLogLevel = LOG_LEVEL_INFO;
		private File externalLogFile = null;
		private long lastLoadingTime = -1;

		public Config(String serviceConfigPath, String sectionName) {
			this.configPath = serviceConfigPath;
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
			System.out.println("Configuration file reloading...");
			if (configPath == null) {
				file = new File("/etc/mlog/mlog.conf");
				if (!file.exists())
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
				log.log(LOG_LEVEL_ERR, getFullMessage(LOG_LEVEL_ERR, getClass().getName(), "Error reading configuration file: " + file));
			}
		}
	}
}
