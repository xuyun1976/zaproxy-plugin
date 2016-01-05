/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 ludovicRoucoux
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package fr.novia.zaproxyplugin;

import fr.novia.zaproxyplugin.report.ZAPreport;
import fr.novia.zaproxyplugin.report.ZAPreportCollection;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.model.AbstractDescribableImpl;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.JDK;
import hudson.model.Node;
import hudson.remoting.VirtualChannel;
import hudson.slaves.SlaveComputer;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.tools.ant.BuildException;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.DataBoundConstructor;
import org.zaproxy.clientapi.core.ApiResponse;
import org.zaproxy.clientapi.core.ApiResponseElement;
import org.zaproxy.clientapi.core.ApiResponseList;
import org.zaproxy.clientapi.core.ApiResponseSet;
import org.zaproxy.clientapi.core.ClientApi;
import org.zaproxy.clientapi.core.ClientApiException;
//import org.parosproxy.paros.CommandLine;

/**
 * Contains methods to start and execute ZAProxy.
 * Members variables are bind to the config.jelly placed to fr/novia/zaproxyplugin/ZAProxy
 * 
 * @author ludovic.roucoux
 *
 */
public class ZAProxy extends AbstractDescribableImpl<ZAProxy> implements Serializable  {

	private static final long serialVersionUID = 3381268691497579059L;

	private static final String API_KEY = "ZAPROXY-PLUGIN";
	private static final String REPORT_PREFIX = "ZAP";
	private static final int MILLISECONDS_IN_SECOND = 1000;
	public static final String FILE_POLICY_EXTENSION = ".policy";
	public static final String FILE_SESSION_EXTENSION = ".session";
	public static final String NAME_POLICIES_DIR_ZAP = "policies";
	
	public static final String CMD_LINE_DIR = "-dir";
	public static final String CMD_LINE_HOST = "-host";
	public static final String CMD_LINE_PORT = "-port";
	public static final String CMD_LINE_DAEMON = "-daemon";
	private static final String CMD_LINE_CONFIG = "-config";
	private static final String CMD_LINE_API_KEY = "api.key";
	
	private static final int TimeoutInSec = 60;
	
	// TODO Do import when zap-2.4.0.jar will contain the correct API version
//	public static final String CMD_LINE_DIR = CommandLine.DIR;
//	public static final String CMD_LINE_CONFIG = CommandLine.CONFIG;
//	public static final String CMD_LINE_HOST = CommandLine.HOST;
//	public static final String CMD_LINE_PORT = CommandLine.PORT;
//	public static final String CMD_LINE_DAEMON = CommandLine.DAEMON;
	
	private static final String ZAP_PROG_NAME_BAT = "zap.bat";
	private static final String ZAP_PROG_NAME_SH = "zap.sh";

	private static final String SCRIPT_NAME = "common-auth-json";
	
	/** Host configured when ZAProxy is used as proxy */
	private String zapProxyHost;
	
	/** Port configured when ZAProxy is used as proxy */
	private int zapProxyPort;
	
	/** Environment variable about ZAProxy path */
	private String zapProxyHome;
	
	/** Path to the ZAProxy program */
	private String zapProgram;
	
	private boolean zapProxyLocalLaunch;
	
	/** Time total to wait for zap initialization. After this time, the program is stopped */
	
	/** URL to attack by ZAProxy */
	private final String targetURL;
	private final String includeURLs;
	private final String excludeURLs;
	
	/** Realize a url spider or not by ZAProxy */
	private final boolean spiderURL;
	
	private final boolean ajaxSpiderURL;
	
	private final boolean authentication;

	/** logged in indication*/
	private final String loggedInIndicator;

	/** logged out indication*/
	private final String loggedOutIndicator;
	
	/** Id of the newly created context*/
	private String contextId;

	/** Id of the newly created user*/
	private String userId;

	/** Realize a url scan or not by ZAProxy */
	private final boolean scanURL;
	
	private final String clickElements;
	
	private final ArrayList<AuthenticationStep> authenticationSteps;
	private final ArrayList<AjaxSpiderFieldValue> ajaxSpiderFieldValues;
	
	@DataBoundConstructor
	public ZAProxy(String targetURL, String includeURLs,  String excludeURLs, boolean spiderURL, boolean scanURL, boolean authentication, List<AuthenticationStep> authenticationSteps, String loggedInIndicator, String loggedOutIndicator, boolean ajaxSpiderURL, String clickElements, List<AjaxSpiderFieldValue> ajaxSpiderFieldValues) {
		this.targetURL = targetURL;
		this.includeURLs = includeURLs;
		this.excludeURLs = excludeURLs;
		this.spiderURL = spiderURL;
		this.scanURL = scanURL;
		this.ajaxSpiderURL = ajaxSpiderURL;
		this.authentication = authentication;
		this.loggedInIndicator = loggedInIndicator;
		this.loggedOutIndicator = loggedOutIndicator;
		this.clickElements = clickElements;

		this.authenticationSteps = authenticationSteps != null ? new ArrayList<AuthenticationStep>(authenticationSteps) : new ArrayList<AuthenticationStep>();
		this.ajaxSpiderFieldValues = ajaxSpiderFieldValues != null ? new ArrayList<AjaxSpiderFieldValue>(ajaxSpiderFieldValues) : new ArrayList<AjaxSpiderFieldValue>();		
	}
	
	@Override
	public String toString() {
		String s = "";
		s += "zapProxyHome ["+zapProxyHome+"]\n";
		s += "targetURL ["+targetURL+"]\n";
		s += "spiderURL ["+spiderURL+"]\n";
		s += "authentication ["+authentication+"]\n";
		s += "includeURLs ["+includeURLs+"]\n";
		s += "excludeURLs ["+excludeURLs+"]\n";
		s += "loggedInIndicator ["+loggedInIndicator+"]\n";
		s += "loggedOutIndicator ["+loggedOutIndicator+"]\n";
		s += "ajaxSpiderURL ["+ajaxSpiderURL+"]\n";
		s += "clickElements ["+clickElements+"]\n";
		s += "scanURL ["+scanURL+"]\n";
		s += "zapProxyHost ["+zapProxyHost+"]\n";
		s += "zapProxyPort ["+zapProxyPort+"]\n";
		
		return s;
	}
	
	// Overridden for better type safety.
	// If your plugin doesn't really define any property on Descriptor,
	// you don't have to do this.
	@Override
	public ZAProxyDescriptorImpl getDescriptor() {
		return (ZAProxyDescriptorImpl)super.getDescriptor();
	}
	
	/*
	 * Getters allows to load members variables into UI.
	 */
	public String getZapProxyHome() {
		return zapProxyHome;
	}

	public void setZapProxyHome(String zapProxyHome) {
		this.zapProxyHome = zapProxyHome;
	}

	public String getTargetURL() {
		return targetURL;
	}

	public String getIncludeURLs() {
		return includeURLs;
	}

	public String getExcludeURLs() {
		return excludeURLs;
	}

	public boolean getSpiderURL() {
		return spiderURL;
	}

	public boolean getScanURL() {
		return scanURL;
	}

	public void setZapProxyHost(String zapProxyHost) {
		this.zapProxyHost = zapProxyHost;
	}

	public void setZapProxyPort(int zapProxyPort) {
		this.zapProxyPort = zapProxyPort;
	}

	public void setZapProxyLocalLaunch(boolean zapProxyLocalLaunch) {
		this.zapProxyLocalLaunch = zapProxyLocalLaunch;
	}

	public ArrayList<AuthenticationStep> getAuthenticationSteps() {
		return authenticationSteps;
	}

	public boolean isAjaxSpiderURL() {
		return ajaxSpiderURL;
	}

	public String getClickElements() {
		return clickElements;
	}

	public ArrayList<AjaxSpiderFieldValue> getAjaxSpiderFieldValues() {
		return ajaxSpiderFieldValues;
	}

	public boolean getAuthentication() {
		return authentication;
	}

	public String getLoggedInIndicator() {
		return loggedInIndicator;
	}

	public String getLoggedOutIndicator() {
		return loggedOutIndicator;
	}

	/**
	 * Return the ZAProxy program name with separator prefix (\zap.bat or /zap.sh) depending of the build node and the OS.
	 * 
	 * @param build
	 * @return the ZAProxy program name with separator prefix (\zap.bat or /zap.sh)
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private String getZAPProgramNameWithSeparator(AbstractBuild<?, ?> build) throws IOException, InterruptedException {
		Node node = build.getBuiltOn();
		String zapProgramName = "";
		
		// Append zap program following Master/Slave and Windows/Unix
		if( "".equals(node.getNodeName())) { // Master
			if( File.pathSeparatorChar == ':' ) { // UNIX
				zapProgramName = "/" + ZAP_PROG_NAME_SH;
			} else { // Windows (pathSeparatorChar == ';')
				zapProgramName = "\\" + ZAP_PROG_NAME_BAT;
			}
		} 
		else { // Slave
			if( "Unix".equals(((SlaveComputer)node.toComputer()).getOSDescription()) ) {
				zapProgramName = "/" + ZAP_PROG_NAME_SH;
			} else {
				zapProgramName = "\\" + ZAP_PROG_NAME_BAT;
			}
		}
		return zapProgramName;
	}
	
	/**
	 * Verify parameters of the build setup are correct (null, empty, negative ...)
	 * 
	 * @param build
	 * @param listener the listener to display log during the job execution in jenkins
	 * @throws InterruptedException 
	 * @throws IOException 
	 * @throws Exception throw an exception if a parameter is invalid.
	 */
	private void checkParams(AbstractBuild<?, ?> build, BuildListener listener) 
			throws IllegalArgumentException, IOException, InterruptedException {
		zapProgram = build.getEnvironment(listener).get(zapProxyHome);
		
		if(zapProgram == null || zapProgram.isEmpty()) {
			throw new IllegalArgumentException("zapProgram is missing");
		} else
			listener.getLogger().println("zapProgram = " + zapProgram);
		
//		if(targetURL == null || targetURL.isEmpty()) {
//			throw new IllegalArgumentException("targetURL is missing");
//		} else
//			listener.getLogger().println("targetURL = " + targetURL);

		if(zapProxyHost == null || zapProxyHost.isEmpty()) {
			throw new IllegalArgumentException("zapProxy Host is missing");
		} else
			listener.getLogger().println("zapProxyHost = " + zapProxyHost);

		if(zapProxyPort < 0) {
			throw new IllegalArgumentException("zapProxy Port is less than 0");
		} else
			listener.getLogger().println("zapProxyPort = " + zapProxyPort);
		
	}
		
	/**
	 * Start ZAProxy using command line. It uses host and port configured in Jenkins admin mode and
	 * ZAProxy program is launched in daemon mode (i.e without UI).
	 * ZAProxy is started on the build's machine (so master machine ou slave machine) thanks to 
	 * {@link FilePath} object and {@link Launcher} object.
	 * 
	 * @param build
	 * @param listener the listener to display log during the job execution in jenkins
	 * @param launcher the object to launch a process locally or remotely
	 * @throws InterruptedException 
	 * @throws IOException 
	 * @throws IllegalArgumentException 
	 */
	public void startZAP(AbstractBuild<?, ?> build, BuildListener listener, Launcher launcher, boolean startZAPGUI) 
			throws IllegalArgumentException, IOException, InterruptedException {
		
		if (!ZAPUtil.isLocalIP(zapProxyHost))
			return;
		
		checkParams(build, listener);
		
		FilePath ws = build.getWorkspace();
		if (ws == null) {
			Node node = build.getBuiltOn();
			if (node == null) {
				throw new NullPointerException("no such build node: " + build.getBuiltOnStr());
			}
			throw new NullPointerException("no workspace from node " + node + " which is computer " + node.toComputer() + " and has channel " + node.getChannel());
		}
		
		// Contains the absolute path to ZAP program
		FilePath zapPathWithProgName = new FilePath(ws.getChannel(), zapProgram + getZAPProgramNameWithSeparator(build));
		listener.getLogger().println("Start ZAProxy [" + zapPathWithProgName.getRemote() + "]");
		
		// Command to start ZAProxy with parameters
		List<String> cmd = new ArrayList<String>();
		cmd.add(zapPathWithProgName.getRemote());
		if (!startZAPGUI)
			cmd.add(CMD_LINE_DAEMON);
		cmd.add(CMD_LINE_HOST);
		cmd.add(zapProxyHost);
		cmd.add(CMD_LINE_PORT);
		cmd.add(String.valueOf(zapProxyPort));
		cmd.add(CMD_LINE_CONFIG);
		cmd.add(CMD_LINE_API_KEY + "=" + API_KEY);
		
		EnvVars envVars = build.getEnvironment(listener);
		// on Windows environment variables are converted to all upper case,
		// but no such conversions are done on Unix, so to make this cross-platform,
		// convert variables to all upper cases.
		for(Map.Entry<String,String> e : build.getBuildVariables().entrySet())
			envVars.put(e.getKey(),e.getValue());
		
		FilePath workDir = new FilePath(ws.getChannel(), zapProgram);
		
		// JDK choice
		computeJdkToUse(build, listener, envVars);
		
		// Launch ZAP process on remote machine (on master if no remote machine)
		launcher.launch().cmds(cmd).envs(envVars).stdout(listener).pwd(workDir).start();
		
		// Call waitForSuccessfulConnectionToZap(int, BuildListener) remotely
		build.getWorkspace().act(new WaitZAProxyInitCallable(this, listener));
	}
	
	/**
	 * Set the JDK to use to start ZAP.
	 * 
	 * @param build
	 * @param listener the listener to display log during the job execution in jenkins
	 * @param env list of environment variables. Used to set the path to the JDK
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private void computeJdkToUse(AbstractBuild<?, ?> build,
			BuildListener listener, EnvVars env) throws IOException, InterruptedException {
		JDK jdkToUse = build.getProject().getJDK();
		if (jdkToUse != null) {
			Computer computer = Computer.currentComputer();
			// just in case we are not in a build
			if (computer != null) {
				jdkToUse = jdkToUse.forNode(computer.getNode(), listener);
			}
			jdkToUse.buildEnvVars(env);
		}
	}
	
	/**
	 * Wait for ZAProxy initialization, so it's ready to use at the end of this method
	 * (otherwise, catch exception). This method is launched on the remote machine (if there is one)
	 *   
	 * @param timeout the time in sec to try to connect at zap proxy. 
	 * @param listener the listener to display log during the job execution in jenkins
	 * @see <a href="https://groups.google.com/forum/#!topic/zaproxy-develop/gZxYp8Og960">
	 * 		https://groups.google.com/forum/#!topic/zaproxy-develop/gZxYp8Og960</a>
	 */
	private void waitForSuccessfulConnectionToZap(int timeout, BuildListener listener) {
		int timeoutInMs = getMilliseconds(timeout);
		int connectionTimeoutInMs = timeoutInMs;
		int pollingIntervalInMs = getMilliseconds(1);
		boolean connectionSuccessful = false;
		long startTime = System.currentTimeMillis();
		Socket socket = null;
		do {
			try {
				socket = new Socket();
				socket.connect(new InetSocketAddress(zapProxyHost, zapProxyPort), connectionTimeoutInMs);
				connectionSuccessful = true;
			} catch (SocketTimeoutException ignore) {
				listener.error(ExceptionUtils.getStackTrace(ignore));
				throw new BuildException("Unable to connect to ZAP's proxy after " + timeout + " seconds.");
				
			} catch (IOException ignore) {
				// and keep trying but wait some time first...
				try {
					Thread.sleep(pollingIntervalInMs);
				} catch (InterruptedException e) {
					listener.error(ExceptionUtils.getStackTrace(ignore));
					throw new BuildException("The task was interrupted while sleeping between connection polling.", e);
				}

				long ellapsedTime = System.currentTimeMillis() - startTime;
				if (ellapsedTime >= timeoutInMs) {
					listener.error(ExceptionUtils.getStackTrace(ignore));
					throw new BuildException("Unable to connect to ZAP's proxy after " + timeout + " seconds.");
				}
				connectionTimeoutInMs = (int) (timeoutInMs - ellapsedTime);
			} finally {
				if(socket != null) {
					try {
						socket.close();
					} catch (IOException e) {
						listener.error(ExceptionUtils.getStackTrace(e));
					}
				}
			}
		} while (!connectionSuccessful);
	}
	
	/**
	 * Converts seconds in milliseconds.
	 * @param seconds the time in second to convert
	 * @return the time in milliseconds
	 */
	private static int getMilliseconds(int seconds) {
		return seconds * MILLISECONDS_IN_SECOND;
	}
	
	/**
	 * Generates security report for one format. Reports are saved into build's workspace.
	 * 
	 * @param reportFormat the format of the report
	 * @param listener the listener to display log during the job execution in jenkins
	 * @param workspace a {@link FilePath} representing the build's workspace
	 * @param clientApi the ZAP client API to call method
	 * @throws ClientApiException 
	 * @throws IOException
	 */
	private void saveReport(ZAPreport reportFormat, BuildListener listener, FilePath workspace, 
			ClientApi clientApi) throws IOException, ClientApiException {
		
		String dateStr = new SimpleDateFormat ("yyyy-MM-dd HH-mm-ss").format(new Date()); 
		final String fullFileName = String.format("%s_%s.%s", REPORT_PREFIX, dateStr, reportFormat.getFormat());
		File reportsFile = new File(workspace.getRemote(), fullFileName);
		FileUtils.writeByteArrayToFile(reportsFile, reportFormat.generateReport(clientApi, API_KEY));
		listener.getLogger().println("File ["+ reportsFile.getAbsolutePath() +"] saved");
	}

	public boolean executeZAP(FilePath workspace, BuildListener listener) 
	{
		ClientApi zapClientAPI = new ClientApi(zapProxyHost, zapProxyPort, false);
		boolean buildSuccess = true;
		
		try 
		{
			List<String> urls = getTargetUrls(listener, zapClientAPI);
			this.contextId = createContext(listener, zapClientAPI, urls);
			
			if (authentication)
			{
				setUpAuthenticationMethod(listener, zapClientAPI);
				this.userId = setUpUser(listener, zapClientAPI, "username", "password", contextId);
			}
			
			if (spiderURL)
				spider(listener, zapClientAPI, urls);
			
			if (ajaxSpiderURL)
				ajaxSpider(listener, zapClientAPI, urls);
			
			if (scanURL) {				
				listener.getLogger().println("Scan the site [" + targetURL + "]");
				scanURL(urls, listener, zapClientAPI);
			} else {
				listener.getLogger().println("Skip scanning the site [" + targetURL + "]");
			}
			
			ZAPreport report = ZAPreportCollection.getInstance().getMapFormatReport().get("html");
			saveReport(report, listener, workspace, zapClientAPI);
			
			listener.getLogger().println("Total alerts = " + zapClientAPI.core.numberOfAlerts("").toString(2));
			listener.getLogger().println("Total messages = " + zapClientAPI.core.numberOfMessages("").toString(2));
			
		} catch (Exception e) {
			listener.error(ExceptionUtils.getStackTrace(e));
			buildSuccess = false;
		} finally {
			try {
				stopZAP(zapClientAPI, listener);
			} catch (ClientApiException e) {
				listener.error(ExceptionUtils.getStackTrace(e));
				buildSuccess = false;
			}
		}
		return buildSuccess;
	}
	
	/**
	 * Converts the ZAP API status response to an integer
	 *
	 * @param response the ZAP API response code
	 * @return the integer status of the ApiResponse
	 */
	private int statusToInt(final ApiResponse response) {
		return Integer.parseInt(((ApiResponseElement)response).getValue());
	}

	/**
	 * Converts the ZAP API status response to an String
	 *
	 * @param response the ZAP API response code
	 * @return the String status of the ApiResponse
	 */
	private String statusToString(final ApiResponse response) {
		return ((ApiResponseElement)response).getValue();
	}

	/**
	 *get user id
	 * @param response the ZAP API response code
	 * @return the user ID of the  user
	 */
	private String extractUserId(ApiResponse response) {
		return ((ApiResponseElement) response).getValue();
	}

	/**
	 *get context id
	 * @param response the ZAP API response code
	 * @return the context ID of the context
	 */
	private String extractContextId(ApiResponse response) {
		return ((ApiResponseElement) response).getValue();
	}

	private String createContext(BuildListener listener, ClientApi zapClientAPI, List<String> targetURLs) throws ClientApiException 
	{
		String contextName = "context1";
		
		String contextId = extractContextId(zapClientAPI.context.newContext(API_KEY, contextName));

		listener.getLogger().println(String.format("Created Context {contextName:%s, contextId:%s}", contextName, contextId));
		
		includeUrlToContext(listener, contextName, zapClientAPI, targetURLs);
		excludeUrlToContext(listener, contextName, zapClientAPI);
		
		return contextId;
	}
	
	private List<String> getTargetUrls(BuildListener listener, ClientApi zapClientAPI) throws ClientApiException 
	{
		if (targetURL != null && targetURL.trim().length() > 0)
		{
			String[] splits = targetURL.split(";|,");
			return Arrays.asList(splits);
		}
		else
		{
			return getAllSites(listener, zapClientAPI);
		}
	}
	
	private List<String> getAllSites(BuildListener listener, ClientApi zapClientAPI) throws ClientApiException 
	{
		List<String> targetURLs = new ArrayList<String>();
		
		ApiResponse response = zapClientAPI.core.sites();
		List<ApiResponse> items = ((ApiResponseList)response).getItems();
		
		for (ApiResponse item : items)
			targetURLs.add(this.statusToString(item));
		
		return targetURLs;
	}
	
	private void includeUrlToContext(BuildListener listener, String contextName, ClientApi zapClientAPI, List<String> targetURLs) throws ClientApiException 
	{
		List<String> includeURLsList = getIncludeURLsAsList();
		includeURLsList.addAll(targetURLs);
		
		for (String url : includeURLsList)
		{
			url = "\\Q" + url + "\\E.*";
			zapClientAPI.context.includeInContext(API_KEY, contextName, url);
			listener.getLogger().println(String.format("URL [%s] include to Context [%s]", url, contextName));
		}
	}
	
	private void excludeUrlToContext(BuildListener listener, String contextName, ClientApi zapClientAPI) throws ClientApiException 
	{
		List<String> excludeURLsList = getExcludeURLsAsList();
		
		for (String url : excludeURLsList)
		{
			url = "\\Q" + url + "\\E.*";
			zapClientAPI.context.excludeFromContext(API_KEY, contextName, url);
			listener.getLogger().println(String.format("URL [%s] exclude to Context [%s]", url, contextName));
		}
	}
	
	private List<String> getIncludeURLsAsList()
	{
		if (includeURLs == null || includeURLs.trim().length() == 0)
			return new ArrayList<String>();
		
		String[] splits = includeURLs.split(";|,");
		return new ArrayList<String>(Arrays.asList(splits));
	}
	
	private List<String> getExcludeURLsAsList()
	{
		if (excludeURLs == null  || excludeURLs.trim().length() == 0)
			return new ArrayList<String>();
		
		String[] splits = excludeURLs.split(";|,");
		return new ArrayList<String>(Arrays.asList(splits));
	}
	
	private void setUpAuthenticationMethod(BuildListener listener, ClientApi zapClientAPI) throws ClientApiException, UnsupportedEncodingException
	{
		if (authenticationSteps == null)
			return;
			
		StringBuilder methodConfigParams = new StringBuilder();	
		StringBuilder jsonAuthenticationSteps = new StringBuilder();
		
		methodConfigParams.append("scriptName=").append(SCRIPT_NAME);
		
		jsonAuthenticationSteps.append("[");
		
		for (int i = 0; i < authenticationSteps.size(); i++)
		{
			AuthenticationStep authenticationStep = authenticationSteps.get(i);
			
			if (i > 0)
				jsonAuthenticationSteps.append(",");
			
			jsonAuthenticationSteps.append("{");
			jsonAuthenticationSteps.append("\"method\":\"").append(authenticationStep.getMethod()).append("\"");
			jsonAuthenticationSteps.append(",\"url\":\"").append(authenticationStep.getUrl()).append("\"");
			
			if (authenticationStep.getParameters() != null && authenticationStep.getParameters().trim().length() > 0)
				jsonAuthenticationSteps.append(",\"parameters\":\"").append(authenticationStep.getParameters()).append("\"");
			
			if (authenticationStep.getPostData() != null && authenticationStep.getPostData().trim().length() > 0)
				jsonAuthenticationSteps.append(",\"postData\":\"").append(authenticationStep.getPostData()).append("\"");
			
			if (authenticationStep.getAdditionalHeaders() != null && authenticationStep.getAdditionalHeaders().trim().length() > 0)
				jsonAuthenticationSteps.append(",\"additionalHeaders\":\"").append(authenticationStep.getAdditionalHeaders()).append("\"");
			
			if (authenticationStep.getAdditionalCookies() != null && authenticationStep.getAdditionalCookies().trim().length() > 0)
				jsonAuthenticationSteps.append(",\"additionalCookies\":\"").append(authenticationStep.getAdditionalCookies()).append("\"");
			
			jsonAuthenticationSteps.append("}");
		}
		
		jsonAuthenticationSteps.append("]");
		
		methodConfigParams.append("&AuthenticationSteps=").append(URLEncoder.encode(jsonAuthenticationSteps.toString(), "UTF-8"));
		
		zapClientAPI.authentication.setAuthenticationMethod(API_KEY, contextId, "scriptBasedAuthentication", methodConfigParams.toString());
		listener.getLogger().println("Script Based Authentication added to context");
		
		if (loggedInIndicator != null && loggedInIndicator.trim().length() > 0)
		{
			zapClientAPI.authentication.setLoggedInIndicator(API_KEY, contextId, "\\Q" + loggedInIndicator + "\\E.*");
			listener.getLogger().println("Logged in indicator " + loggedInIndicator + " added to context ");
		}
		
		if (loggedOutIndicator != null && loggedOutIndicator.trim().length() > 0)
		{
			zapClientAPI.authentication.setLoggedOutIndicator(API_KEY, contextId, "\\Q" + loggedOutIndicator + "\\E.*");
			listener.getLogger().println("Logged out indicator " + loggedInIndicator+ " added to context ");
		}
	}

	private String setUpUser(BuildListener listener, ClientApi zapClientAPI, String username, String password, String contextId) throws ClientApiException, UnsupportedEncodingException 
	{
		String userId = extractUserId(zapClientAPI.users.newUser(API_KEY, contextId, username));
		zapClientAPI.users.setUserEnabled(API_KEY, contextId, userId, "true");
		
		StringBuilder userAuthConfig = new StringBuilder();
		userAuthConfig.append("Username=").append(URLEncoder.encode(username, "UTF-8"));
		userAuthConfig.append("&Password=").append(URLEncoder.encode(password, "UTF-8"));
		String authCon=userAuthConfig.toString();
		
		zapClientAPI.users.setAuthenticationCredentials(API_KEY, contextId, userId, authCon);
		
		return userId;
	}
	
	private void spider(BuildListener listener, ClientApi zapClientAPI, List<String> urls)	throws ClientApiException, InterruptedException, UnsupportedEncodingException 
	{	
		for (String url : urls)
		{
			listener.getLogger().println(String.format("Start to spider the site [%s]", url));
			
			ApiResponse response;
			if (authentication && authenticationSteps.size() > 0)
				response = zapClientAPI.spider.scanAsUser(API_KEY, url, this.contextId, userId, "0", "");
			else
				response = zapClientAPI.spider.scan(API_KEY, url, "", "");
			
			String scanId = this.statusToString(response);
			while (!isScanFinished(zapClientAPI, scanId, true)) 
			{
				listener.getLogger().println("Status spider = " + statusToInt(zapClientAPI.spider.status("")) + "%");
				listener.getLogger().println("Alerts number = " + zapClientAPI.core.numberOfAlerts("").toString(2));
				Thread.sleep(1000);
			}
			
			listener.getLogger().println(String.format("Fininsh spider the site [%s]", url));
		}
	}
	
	private void ajaxSpider(BuildListener listener, ClientApi zapClientAPI, List<String> urls) throws ClientApiException, InterruptedException
	{
		String fieldValues = getFieldValues();
		
		for (String url : urls)
		{
			listener.getLogger().println(String.format("Start to ajaxSpider the site [%s]", url));
			
			Map<String, String> localHashMap = new HashMap<String, String>();
		    localHashMap.put("apikey", API_KEY);
		    localHashMap.put("url", url);
		    localHashMap.put("inScope", "true");
		    localHashMap.put("fieldValues", fieldValues);
		    localHashMap.put("clickElements", clickElements);
		    
			zapClientAPI.callApi("ajaxSpider", "action", "scan", localHashMap);
			
			while ("running".equalsIgnoreCase(statusToString(zapClientAPI.ajaxSpider.status()))) { 
			    listener.getLogger().println("Status spider = " + statusToString(zapClientAPI.ajaxSpider.status()));
				listener.getLogger().println("Alerts number = " + zapClientAPI.core.numberOfAlerts("").toString(2));
				Thread.sleep(2500);
			} 
			
			listener.getLogger().println(String.format("Fininsh ajaxSpider the site [%s]", url));
		}
	}
	
	private String getFieldValues()
	{
		if (this.ajaxSpiderFieldValues == null || this.ajaxSpiderFieldValues.size() == 0)
			return null;
		
		
		StringBuffer fieldValues = new StringBuffer();
		for (int i = 0; i < ajaxSpiderFieldValues.size(); i++)
		{
			AjaxSpiderFieldValue ajaxSpiderFieldValue = ajaxSpiderFieldValues.get(i);
			
			if (i > 0)
				fieldValues.append("&");
			
			fieldValues.append(ajaxSpiderFieldValue.getField()).append("=").append(ajaxSpiderFieldValue.getValue());
		}
		
		return fieldValues.toString();
	}
	
	/**
	 * Scan all pages found at url and raised actives alerts
	 *
	 * @param url the url to scan
	 * @param listener the listener to display log during the job execution in jenkins
	 * @param zapClientAPI the client API to use ZAP API methods
	 * @throws ClientApiException
	 * @throws InterruptedException 
	 */
	private void scanURL(final List<String> urls, BuildListener listener, ClientApi zapClientAPI) 
			throws ClientApiException, InterruptedException {
		// Method signature : scan(String apikey, String url, String recurse, String inscopeonly, String scanpolicyname, String method, String postdata)
		// Use a default policy if chosenPolicy is null or empty
		for (String url : urls)
		{
			ApiResponse response;
			if (authentication && authenticationSteps.size() > 0)
				response = zapClientAPI.ascan.scanAsUser(API_KEY, url, contextId, userId, "true", "Default policy", "", "");
			else
				response = zapClientAPI.ascan.scan(API_KEY, url, "true", "false", "", "", "");
			
			String scanId = this.statusToString(response);
			
			// Wait for complete scanning (equal to 100)
			// Method signature : status(String scanId)
			while (!isScanFinished(zapClientAPI, scanId, false)) {
				listener.getLogger().println("Status scan = " + statusToInt(zapClientAPI.ascan.status("")) + "%");
				listener.getLogger().println("Alerts number = " + zapClientAPI.core.numberOfAlerts("").toString(2));
				listener.getLogger().println("Messages number = " + zapClientAPI.core.numberOfMessages("").toString(2));
				Thread.sleep(5000);
			}
		}
	}
	
	private boolean isScanFinished(ClientApi zapClientAPI, String scanId, boolean spdier) throws ClientApiException
	{
		ApiResponseList response;
		
		if (spdier)
			response = (ApiResponseList)zapClientAPI.spider.scans();
		else
			response = (ApiResponseList)zapClientAPI.ascan.scans();
		
		List<ApiResponse> items = response.getItems();
		
		for (ApiResponse item : items)
		{
			ApiResponseSet itemSet = (ApiResponseSet)item;
			if (!scanId.equals(itemSet.getAttribute("id")))
				continue;
			
			if ("FINISHED".equalsIgnoreCase(itemSet.getAttribute("state")))
				return true;
			
			if ("100".equals(itemSet.getAttribute("progress")))
				return true;
		}
		
		return false;
	}
	
	/**
	 * Stop ZAproxy if it has been previously started.
	 * 
	 * @param zapClientAPI the client API to use ZAP API methods
	 * @param listener the listener to display log during the job execution in jenkins
	 * @throws ClientApiException 
	 */
	private void stopZAP(ClientApi zapClientAPI, BuildListener listener) throws ClientApiException {
		
		if (zapClientAPI == null)
		{
			listener.getLogger().println("No shutdown of ZAP (zapClientAPI==null)");
			return;
		}
		
		if (!zapProxyLocalLaunch || !ZAPUtil.isLocalIP(zapProxyHost))
		{
			listener.getLogger().println("Reset ZAProxy");
			//zapClientAPI.core.newSession(API_KEY, "", "");
		}
		else
		{
			listener.getLogger().println("Shutdown ZAProxy");
			zapClientAPI.core.shutdown(API_KEY);
		}
	}
	
	
	/**
	 * Descriptor for {@link ZAProxy}. Used as a singleton.
	 * The class is marked as public so that it can be accessed from views.
	 *
	 * <p>
	 * See <tt>src/main/resources/fr/novia/zaproxyplugin/ZAProxy/*.jelly</tt>
	 * for the actual HTML fragment for the configuration screen.
	 */
	@Extension
	public static class ZAProxyDescriptorImpl extends Descriptor<ZAProxy> implements Serializable {
		
		private static final long serialVersionUID = 4028279269334325901L;
		
		/**
		 * To persist global configuration information,
		 * simply store it in a field and call save().
		 *
		 * <p>
		 * If you don't want fields to be persisted, use <tt>transient</tt>.
		 */
		
		
		/**
		 * In order to load the persisted global configuration, you have to
		 * call load() in the constructor.
		 */
		public ZAProxyDescriptorImpl() {
			load();
		}
		
		@Override
		public String getDisplayName() { 
			return null; 
		}
		
	}
		
	
	/**
	 * This class allows to launch a method on a remote machine (if there is, otherwise, on a local machine).
	 * The method launched is to wait the complete initialization of ZAProxy.
	 * 
	 * @author ludovic.roucoux
	 *
	 */
	private static class WaitZAProxyInitCallable implements FileCallable<Void> {

		private static final long serialVersionUID = -313398999885177679L;
		
		private ZAProxy zaproxy; 
		private BuildListener listener;
		
		public WaitZAProxyInitCallable(ZAProxy zaproxy, BuildListener listener) {
			this.zaproxy = zaproxy;
			this.listener = listener;
		}

		@Override
		public Void invoke(File f, VirtualChannel channel) {
			zaproxy.waitForSuccessfulConnectionToZap(TimeoutInSec, listener);
			return null;
		}
		
		@Override
		public void checkRoles(RoleChecker checker) throws SecurityException {
			// Nothing to do
		}
	}
}
