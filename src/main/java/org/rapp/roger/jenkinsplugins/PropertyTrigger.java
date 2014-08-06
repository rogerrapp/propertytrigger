package org.rapp.roger.jenkinsplugins;

import hudson.Extension;
import hudson.model.BuildableItem;
import hudson.model.Item;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import antlr.ANTLRException;

import com.perforce.p4java.core.file.FileSpecBuilder;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.server.IOptionsServer;
import com.perforce.p4java.server.ServerFactory;

/**
 * @author Roger Rapp
 * 
 */
public class PropertyTrigger extends Trigger<BuildableItem> {

	@SuppressWarnings("unused")
	private static final long serialVersionUID = -1995396998287936179L;
	private String propslist;
	private transient IOptionsServer p4server = null;
	private String p4User = null;;
	private String p4Passwd = null;
	private String p4Port = null;
	private String p4client = null;
	private String cause = "unset";
	private File rootDir;
	private static Logger LOGGER = Logger.getLogger(PropertyTrigger.class
			.getSimpleName());


	// private boolean fastDebug = true;

	private Hashtable<String, Properties> propsHash;

	public String getPropslist() {
		return propslist;
	}

	protected File getRootDir() {
		return this.rootDir;
	}

	@DataBoundSetter
	public void setPropslist(String propslist) {
		this.propslist = propslist;
	}

	public String getP4User() {
		return this.p4User;
	}

	@DataBoundSetter
	public void setP4User(String s) {
		this.p4User = s;
	}

	public String getP4Passwd() {
		return this.p4Passwd;
	}

	@DataBoundSetter
	public void setP4Passwd(String s) {
		this.p4Passwd = s;
	}

	public String getP4Port() {
		return this.p4Port;
	}

	@DataBoundSetter
	public void setP4Port(String s) {
		this.p4Port = s;
	}

	@DataBoundConstructor
	public PropertyTrigger(String cronTabSpec) throws ANTLRException {
		super(cronTabSpec);
	}

	@SuppressWarnings("unused")
	private PropertyTrigger() {
		this.p4server = null;
		this.cause = null;
	}
	
	protected String getCause() {
		return this.cause;
	}

	public File getLogFile() {
		if (job == null) {
			return new File(getClass().getSimpleName() + "-polling.log");
		}

		return new File(job.getRootDir(), getClass().getSimpleName()
				+ "-polling.log");
	}

	protected String getName() {
		return getClass().getSimpleName();
	}

	protected boolean requiresWorkspaceForPolling() {
		return false;
	}

	@Extension
	public static class PCTriggerDescriptor extends TriggerDescriptor {

		public PCTriggerDescriptor() {
			super();
			load();
		}

		public PCTriggerDescriptor(Class<? extends Trigger<?>> clazz) {
			super(clazz);
			load();
		}

		@Override
		public boolean isApplicable(Item item) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "[Property in P4 Trigger] - Monitor properties.";
		}

		@Override
		public String getHelpFile() {
			String ret = super.getHelpFile();
			return ret;
		}

	}
	

	public void build(Cause cause) {
		String c = getCause();
		LOGGER.info(getClass().getSimpleName() + " Job: "+job.getDisplayName()+" Trigger Cause:\n" + c);
		job.scheduleBuild(cause);
	}

	public void run() {

		if (job == null) {
			return;
		}
		if (propsHash == null) {
			return;
		}
		LOGGER.finest("Trigger run in JOB: " + job.getDisplayName());

		/*
		 * if (fastDebug) { File f = new File("/tmp/fastdebug"); if (f.exists())
		 * { LOGGER.info("Trigger build.."); build(new
		 * PropertyTriggerCause("Fast debug")); } return; }
		 */

		for (String key : propsHash.keySet()) {
			Properties oldProps = propsHash.get(key);
			if (oldProps == null) {
				LOGGER.warning("No saved properties for: " + key);
			}
			Properties newProps;
			try {
				newProps = getNewPropertiesFromP4(key);
				if (newProps.size() == 0) {
					continue;
				}
				List<String> diffs = diffProps(key, oldProps, newProps);
				if (diffs.size() > 0) {
					setCause(diffs);
					build(new PropertyTriggerCause(diffs));
					propsHash.put(key, newProps);
					File saveFile = getSaveFile(key);
					newProps.store(new FileOutputStream(saveFile), getClass()
							.getSimpleName());

				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		super.run();
	}

	private void setCause(List<String> diffs) {
		StringBuffer buf = new StringBuffer();
		for (String diff : diffs) {
			buf.append(diff + "\n");
		}
		this.cause = buf.toString();
	}

	private List<String> diffProps(String streamKey, Properties oldProps,
			Properties newProps) {
		ArrayList<String> ret = new ArrayList<String>();
		for (Object key : newProps.keySet()) {
			if (oldProps.containsKey(key)) {
				String newVal = newProps.getProperty((String) key);
				String oldVal = oldProps.getProperty((String) key);

				if (newVal == null) {
					newVal = "";
				}
				if (oldVal == null) {
					oldVal = "";
				}
				if (!oldVal.trim().equals(newVal.trim())) {
					ret.add(streamKey + " Property: " + key + " old: " + oldVal
							+ " new: " + newVal);
				}

			} else {
				ret.add(streamKey + " Property: " + key + " Value: "
						+ newProps.getProperty((String) key) + " didn't exist.");
			}
		}
		return ret;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void start(BuildableItem buildableItem, boolean newInstance) {
		propsHash = new Hashtable<String, Properties>();

		if (((AbstractProject) buildableItem).isDisabled()) {
			return;
		}

		LOGGER.info("Starting trigger " + getClass().getName());
		this.rootDir = buildableItem.getRootDir();

		try {
			// if (!fastDebug) {
			readPropsListConfig();
			// }
		} catch (Exception e) {
			e.printStackTrace();
		}

		super.start(buildableItem, newInstance);
	}

	private void readPropsListConfig() throws Exception {
		String rawList = getPropslist();
		if (rawList == null) {
			return;
		}
		StringTokenizer stz = new StringTokenizer(rawList, "\n");

		while (stz.hasMoreTokens()) {
			String entry = stz.nextToken();
			if (entry.contains("#")) {
				entry = entry.substring(0, entry.indexOf('#')).trim();
			}
			if (entry.length() == 0) {
				continue;
			}

			StringTokenizer entrytz = new StringTokenizer(entry, ";");
			if (entrytz.countTokens() != 2) {
				LOGGER.severe("ERROR: [" + getClass().getSimpleName()
						+ "], Misconfiguration for '" + entry
						+ "' Should be of format p4pathOrFilePath;property");
				continue;
			}
			String path = entrytz.nextToken().trim();
			String prop = entrytz.nextToken().trim();

			addEntryToPropsHash(path, prop);
		}
		storePropertyValues();
	}

	private File getSaveFile(String key) {
		key = key.replace("//", "");
		key = key.replace("/", ".");
		File ret = new File(this.rootDir.getAbsolutePath() + File.separator
				+ key);
		return ret;
	}

	private Properties getNewPropertiesFromP4(String key) throws Exception {
		List<IFileSpec> fs = FileSpecBuilder.makeFileSpecList(key);
		Properties allProps = new Properties();
		if (this.p4server == null) {
			LOGGER.severe("No Perforce path matches: " + key
					+ ", should start with '//'");
			return allProps;
		}
		allProps.load(this.p4server.getFileContents(fs, false, false));
		Properties newProps = new Properties();
		Properties p = this.propsHash.get(key);

		for (Object pkey : p.keySet()) {
			String skey = (String) pkey;
			String val = (String) allProps.getProperty(skey);
			if (val != null) {
				newProps.put(pkey, val);
			} else {
				LOGGER.warning("WARNING [" + getName() + "] : Subscribing on "
						+ pkey + " which doesn't exist.");
			}
		}
		return newProps;
	}

	private void storePropertyValues() throws Exception {
		Set<String> keys = propsHash.keySet();
		Properties newProperties;

		for (String key : keys) {
			if (key.startsWith("//")) { // It's a P4 path
				try {
					this.p4server = getP4Server();
					File savefile = getSaveFile(key);
					newProperties = getNewPropertiesFromP4(key);
					propsHash.put(key, newProperties);
					newProperties.store(new FileOutputStream(savefile),
							getClass().getSimpleName());
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				// TODO
				// LOGGER.warning("TODO Reading from plain file not implemented yet.");
				LOGGER.severe("TODO Reading from plain file not implemented yet.");
			}
		}

	}

	private void addEntryToPropsHash(String path, String prop) {
		Properties p;
		if (!propsHash.containsKey(path)) {
			p = new Properties();
		} else {
			p = propsHash.get(path);
		}
		p.put(prop, "unset");
		propsHash.put(path, p);
	}

	private void checkP4Credentials() throws Exception {

		if (this.p4User == null) {
			if ((System.getProperty("P4USER") == null)
					&& (System.getenv("P4USER") == null)) {
				this.p4server = null;
				throw new Exception(
						"P4USER environment or System property not set.");
			}
		}

		if (this.p4Passwd == null) {
			if ((System.getProperty("P4PASSWD") == null)
					&& (System.getenv("P4PASSWD") == null)) {
				this.p4server = null;
				throw new Exception(
						"P4PASSWD environment or System property not set.");
			}
		}

		if ((this.p4User != null) && (this.p4Passwd != null)) {
			return;
		}

		if (System.getenv("P4USER") != null) {
			this.p4User = System.getenv("P4USER");
		}
		if (System.getProperty("P4USER") != null) {
			this.p4User = System.getProperty("P4USER");
		}

		if (System.getenv("P4PASSWD") != null) {
			this.p4Passwd = System.getenv("P4PASSWD");
		}
		if (System.getProperty("P4PASSWD") != null) {
			this.p4Passwd = System.getProperty("P4PASSWD");
		}

		if (System.getenv("P4CLIENT") != null) {
			this.p4client = System.getenv("P4CLIENT");
		}
		if (System.getProperty("P4CLIENT") != null) {
			this.p4client = System.getProperty("P4CLIENT");
		}

	}

	private void p4Connect(IOptionsServer server) throws Exception {
		checkP4Credentials();
		server.connect();
		server.setUserName(this.p4User);
		this.p4server.login(this.p4Passwd, false);
	}

	private IOptionsServer getP4Server() throws Exception {

		if (this.p4server != null) {
			if (this.p4server.isConnected()) {
				return this.p4server;
			}
			p4Connect(this.p4server);
			return this.p4server;
		}

		Properties props = new Properties();
		String p4port = "perforce:1666";
		String eP4Port = System.getenv("P4PORT");

		if (eP4Port != null) {
			p4port = eP4Port;
		}

		this.p4server = (IOptionsServer) ServerFactory.getServer("p4java://"
				+ p4port, props);
		p4Connect(p4server);

		if (this.p4client != null) {
			this.p4server.setCurrentClient(this.p4server
					.getClient(this.p4client));
		}
		return this.p4server;
	}

}
