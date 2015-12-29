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

import hudson.Extension;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.Launcher.LocalLauncher;
import hudson.Launcher.RemoteLauncher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Node;
import hudson.remoting.VirtualChannel;
import hudson.slaves.SlaveComputer;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import net.sf.json.JSONObject;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;

/**
 * /!\ 
 * Au jour du 27/03/2015
 * La version 2.3.1 de ZAPROXY ne contient pas le plugin "pscanrules-release-10.zap" qui sert à 
 * remonter les alertes lors d'un scan passif (spider). Il faut donc ajouter ce plugin manuellement ou 
 * télécharger la prochaine version de ZAPROXY (2.4) via Custom Tools Plugin (et non la 2.3.1) 
 * /!\
 * 
 * The main class of the plugin. This class adds a build step in a Jenkins job that allows you
 * to launch the ZAProxy security tool and get alerts reports from it.
 * 
 * @author ludovic.roucoux
 *
 */
public class ZAProxyBuilder extends Builder {
	
	/** To start ZAP as a prebuild step */
	private final boolean startZAPFirst;
	
	/** To start ZAP with GUI */
	private final boolean startZAPGUI;
	
	/** To set zap proxy */
	private final boolean autoProxy;
	
	/** To Maven Project directory */
	private final String projectDir;
	
	/** Set Test Framework for autoproxy */
	private final String testFramework;
	
	/** The objet to start and call ZAProxy methods */
	private final ZAProxy zaproxy;
	
	// Fields in fr/novia/zaproxyplugin/ZAProxyBuilder/config.jelly must match the parameter names in the "DataBoundConstructor"
	@DataBoundConstructor
	public ZAProxyBuilder(boolean startZAPFirst, boolean startZAPGUI, boolean autoProxy, String projectDir, String testFramework, ZAProxy zaproxy) {
		this.startZAPFirst = startZAPFirst;
		this.startZAPGUI = startZAPGUI;
		this.autoProxy = autoProxy;
		this.projectDir = projectDir;
		this.testFramework = testFramework;
		this.zaproxy = zaproxy;
		this.zaproxy.setZapProxyHost(getDescriptor().getZapProxyDefaultHost());
		this.zaproxy.setZapProxyPort(getDescriptor().getZapProxyDefaultPort());
		this.zaproxy.setZapProxyHome(getDescriptor().getZapProxyDefaultHome());
		this.zaproxy.setZapProxyLocalLaunch(getDescriptor().getZapProxyLocalLaunch());
		
		System.out.println(this.zaproxy.toString());
	}

	/*
	 * Getters allows to access member via UI (config.jelly)
	 */
	public boolean getStartZAPFirst() {
		return startZAPFirst;
	}
	
	public boolean getStartZAPGUI() {
		return startZAPGUI;
	}
	
	public boolean getAutoProxy() {
		return autoProxy;
	}
	
	public String getProjectDir() {
		return projectDir;
	}

	public String getTestFramework() {
		return testFramework;
	}

	public ZAProxy getZaproxy() {
		return zaproxy;
	}
	
	// Overridden for better type safety.
	// If your plugin doesn't really define any property on Descriptor,
	// you don't have to do this.
	@Override
	public ZAProxyBuilderDescriptorImpl getDescriptor() {
		return (ZAProxyBuilderDescriptorImpl)super.getDescriptor();
	}
	
	// Method called before launching the build
	public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
		if (autoProxy) 
			new AutoProxy(build, listener, projectDir, testFramework, String.format("%s:%d", getDescriptor().getZapProxyDefaultHost(), getDescriptor().getZapProxyDefaultPort())).setProxy();
		
		if(getDescriptor().getZapProxyLocalLaunch() && startZAPFirst) {
			listener.getLogger().println("------- START Prebuild -------");
			
			try {
				Launcher launcher = null;
				Node node = build.getBuiltOn();
				
				// Create launcher according to the build's location (Master or Slave) and the build's OS
				
				if("".equals(node.getNodeName())) { // Build on master 
					launcher = new LocalLauncher(listener, build.getWorkspace().getChannel());
				} else { // Build on slave
					boolean isUnix;
					if( "Unix".equals(((SlaveComputer)node.toComputer()).getOSDescription()) ) {
						isUnix = true;
					} else {
						isUnix = false;
					}
					launcher = new RemoteLauncher(listener, build.getWorkspace().getChannel(), isUnix);
				}		
				zaproxy.startZAP(build, listener, launcher, startZAPGUI);
			} catch (Exception e) {
				e.printStackTrace();
				listener.error(ExceptionUtils.getStackTrace(e));
				return false;
			}
			listener.getLogger().println("------- END Prebuild -------");
		}
		
		return true;
	}

	// Method called when the build is launching
	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
		
		listener.getLogger().println("Perform ZAProxy");
		
		if(getDescriptor().getZapProxyLocalLaunch() && !startZAPFirst) {
			try {
				zaproxy.startZAP(build, listener, launcher, startZAPGUI);
			} catch (Exception e) {
				e.printStackTrace();
				listener.error(ExceptionUtils.getStackTrace(e));
				return false;
			}
		}
		
		boolean res;
		try {
			//copyPolicyFile(build.getWorkspace(), listener); // TODO maybe in future version
			res = build.getWorkspace().act(new ZAProxyCallable(this.zaproxy, listener));
		} catch (Exception e) {
			e.printStackTrace();
			listener.error(ExceptionUtils.getStackTrace(e));
			return false;
		}
		return res;
	}
	
	/**
	 * Descriptor for {@link ZAProxyBuilder}. Used as a singleton.
	 * The class is marked as public so that it can be accessed from views.
	 *
	 * <p>
	 * See <tt>src/main/resources/fr/novia/zaproxyplugin/ZAProxyBuilder/*.jelly</tt>
	 * for the actual HTML fragment for the configuration screen.
	 */
	@Extension // This indicates to Jenkins this is an implementation of an extension point.
	public static final class ZAProxyBuilderDescriptorImpl extends BuildStepDescriptor<Builder> {
		/**
		 * To persist global configuration information,
		 * simply store it in a field and call save().
		 *
		 * <p>
		 * If you don't want fields to be persisted, use <tt>transient</tt>.
		 */
		private String zapProxyDefaultHost;
		private int zapProxyDefaultPort;
		private String zapProxyDefaultHome;
		private boolean zapProxyLocalLaunch;

		/**
		 * In order to load the persisted global configuration, you have to
		 * call load() in the constructor.
		 */
		public ZAProxyBuilderDescriptorImpl() {
			load();
		}
		
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			// Indicates that this builder can be used with all kinds of project types
			return true;
		}

		/**
		 * This human readable name is used in the configuration screen.
		 */
		@Override
		public String getDisplayName() {
			return "Penetration Test";
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
			// To persist global configuration information,
			// set that to properties and call save().
			zapProxyDefaultHost = formData.getString("zapProxyDefaultHost");
			zapProxyDefaultPort = formData.getInt("zapProxyDefaultPort");
			zapProxyDefaultHome = formData.getString("zapProxyDefaultHome");
			zapProxyLocalLaunch = formData.getBoolean("zapProxyLocalLaunch");
			// ^Can also use req.bindJSON(this, formData);
			//  (easier when there are many fields; need set* methods for this, like setUseFrench)
			save();
			return super.configure(req,formData);
		}

		public String getZapProxyDefaultHost() {
			return zapProxyDefaultHost;
		}

		public int getZapProxyDefaultPort() {
			return zapProxyDefaultPort;
		}

		public String getZapProxyDefaultHome() {
			return zapProxyDefaultHome;
		}

		public boolean getZapProxyLocalLaunch() {
			return zapProxyLocalLaunch;
		}

		
	}
	
	/**
	 * Used to execute ZAP remotely.
	 * 
	 * @author ludovic.roucoux
	 *
	 */
	private static class ZAProxyCallable implements FileCallable<Boolean> {

		private static final long serialVersionUID = -313398999885177679L;
		
		private ZAProxy zaproxy;
		private BuildListener listener;
		
		public ZAProxyCallable(ZAProxy zaproxy, BuildListener listener) {
			this.zaproxy = zaproxy;
			this.listener = listener;
		}

		@Override
		public Boolean invoke(File f, VirtualChannel channel) {
			return zaproxy.executeZAP(new FilePath(f), listener);
		}
		
		@Override
		public void checkRoles(RoleChecker checker) throws SecurityException {
			// Nothing to do
		}
	}
}
