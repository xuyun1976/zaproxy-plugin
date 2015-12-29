package fr.novia.zaproxyplugin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import fr.novia.zaproxyplugin.asm.ModifyClassWriterFactory;
import hudson.maven.MavenModuleSet;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;

public class AutoProxy 
{
	private final static String POM_FILE_NAME = "POM_FILE_NAME";
	private final static String POM_FILE_PATH = "POM_FILE_PATH";
	private final static String POM_FILE_DEFAULT_NAME = "pom.xml";
	
	private AbstractBuild<?, ?> build;
	private BuildListener listener;
	private String workspace;
	private String projectDir;
	private String proxy;
	private String testFramework;
	
	private List<String> classNames = new ArrayList<String>();

	
	public AutoProxy(AbstractBuild<?, ?> build, BuildListener listener, String projectDir, String testFramework, String proxy)
	{
		this.build = build;
		this.listener = listener;
		this.proxy = proxy;
		this.projectDir = projectDir;
		this.testFramework = testFramework;
		
		this.workspace = this.build.getWorkspace().getRemote();
		
		modifiedClassName();
	}
	
	private void modifiedClassName()
	{
		classNames.clear();
		
		if ("testng".equalsIgnoreCase(testFramework))
			classNames.add(ModifyClassWriterFactory.TestNGRunner);
		else if ("junit".equalsIgnoreCase(testFramework))
			classNames.add(ModifyClassWriterFactory.JUnitRunner);
		if ("selenium".equalsIgnoreCase(testFramework))
			classNames.add(ModifyClassWriterFactory.RemoteWebDriver);
	}
	
	public void setProxy()
	{
		List<String> poms = new ArrayList<String>();
		
		Map<String, String> pomInfo = getPOMInfo();
		
		String pomFilePath = pomInfo.get(POM_FILE_PATH);
		String pomFileName = pomInfo.get(POM_FILE_NAME);
		
		getPomFiles(new File(pomFilePath), poms, pomFileName);
		
		for (String pom : poms)
			modifyPom(pom);
	}
	
	private void getPomFiles(File root, List<String> poms, String pomFileName)
	{
		 File[] files = root.listFiles();
	     
		 for (File file : files) 
	     {
			 if (file.isFile()) 
			 {
				 if (file.getName().equalsIgnoreCase(pomFileName))
					 poms.add(file.getAbsolutePath());
	         } 
			 else 
			 {
				 getPomFiles(file, poms, pomFileName);
	         }
	     }
	}
	
	private Map<String, String> getPOMInfo()
	{
		Map<String, String> pomInfo = new HashMap<String, String>();
		
		calPOMInfoFromProjectDir(pomInfo);
		
		if (pomInfo.isEmpty())
			calPOMInfoFromBuildProject(pomInfo);
		if (pomInfo.isEmpty())
			calPOMInfoByDefault(pomInfo);
		
		return pomInfo;
	}
	
	private void calPOMInfoFromProjectDir(Map<String, String> pomInfo)
	{
		if (projectDir == null || this.projectDir.trim().length() == 0)
			return;
		
		if (projectDir.endsWith(".xml"))
		{
			int index = projectDir.lastIndexOf(File.separator);
			
			if (index == -1)
			{
				pomInfo.put(POM_FILE_NAME, projectDir);
				pomInfo.put(POM_FILE_PATH, workspace);
			}
			else
			{
				pomInfo.put(POM_FILE_NAME, projectDir.substring(index + 1));
				pomInfo.put(POM_FILE_PATH, projectDir.substring(0, index));
			}
		}
		else
		{
			pomInfo.put(POM_FILE_NAME, POM_FILE_DEFAULT_NAME);
			pomInfo.put(POM_FILE_PATH, projectDir);
		}
	}
	
	private void calPOMInfoFromBuildProject(Map<String, String> pomInfo)
	{
		if (build.getProject() instanceof MavenModuleSet)
		{
			try
			{
				MavenModuleSet mavenProject = (MavenModuleSet)build.getProject();
				String rootPom = mavenProject.getRootPOM(build.getEnvironment(listener));
				File file = mavenProject.getRootDir();
				
				String filePath = file.toPath().toString() + File.separator + "workspace";
				
				int index = rootPom.lastIndexOf(File.separator);
				
				if (index != -1)
				{
					filePath = filePath + File.separator + rootPom.substring(0, index);
					rootPom = rootPom.substring(index + 1);
				}
				
				pomInfo.put(POM_FILE_NAME, rootPom);
				pomInfo.put(POM_FILE_PATH, filePath);
			}
			catch(Exception ex)
			{
				ex.printStackTrace();
			}
		}
	}
	
	private void calPOMInfoByDefault(Map<String, String> pomInfo)
	{
		pomInfo.put(POM_FILE_NAME, POM_FILE_DEFAULT_NAME);
		pomInfo.put(POM_FILE_PATH, workspace);
	}
	
	private void modifyPom(String pom)
	{
		try
		{
			Set<String> modifiedJars = new ModifyClass4Proxy(pom, workspace, proxy).generateProxyJars();
			if (listener != null)
				listener.getLogger().println(modifiedJars);
		
			BufferedReader br = new BufferedReader(new FileReader(pom));
			StringBuffer sb = new StringBuffer();
			String newLine = System.getProperty("line.separator");
			String line;
			while ((line = br.readLine()) != null) 
				sb.append(line).append(newLine);
            br.close();
            
            boolean modified = false;
            for (String modifiedJar : modifiedJars)
			{
            	if (sb.indexOf(modifiedJar) != -1)
					continue;
				
            	int index = sb.indexOf("<dependencies>");
				index = sb.indexOf(">", index + 1);
				sb.insert(index + 1, String.format("%s<dependency><groupId>scan%d</groupId><artifactId>scan-%d</artifactId><version>1</version><scope>system</scope><systemPath>%s</systemPath></dependency>%s", newLine, index, index, modifiedJar, newLine));
				modified = true;
			}
			
            if (!modified)
            	return;
            
            BufferedWriter bw = new BufferedWriter(new FileWriter(pom, false));
            bw.write(sb.toString());
            bw.close();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
	}

	class ModifyClass4Proxy
	{
		private String pom;
		private String destDir;
		private String proxy;
		
		public ModifyClass4Proxy(String pom, String workspace, String proxy)
		{
			this.pom = pom;
			this.proxy = proxy;
			this.destDir = workspace + File.separator + "scan";
		}
		
		public Set<String> generateProxyJars()
		{
			Set<String> modifiedJars = new HashSet<String>();
			
			String[] allJars = getJarsInClasspath();
			if (allJars == null)
				return modifiedJars;
			
			for (String jarName : allJars)
			{
				if (isNeedModify(jarName))
				{
					String copyJarName = copyAndModifyJar(jarName);
					modifiedJars.add(copyJarName);
				}
			}
			
			return modifiedJars;
		}
		
		public String copyAndModifyJar(String jarName)
		{
			String copyJarName = copyJarToWorkspace(jarName);
			modifyJarByASM(copyJarName);
			
			return copyJarName;
		}
		
		private String copyJarToWorkspace(String jarName)
		{
			File fileDir = new File(destDir);
			if (!fileDir.exists())
				fileDir.mkdirs();
			
			String srcFileName = jarName;
			int index = srcFileName.lastIndexOf(File.separator);
			if (index != -1)
				srcFileName = srcFileName.substring(index + 1);
			
			String dstFileName = destDir + File.separator + srcFileName;
			File destFile = new File(dstFileName);
			
			//if (destFile.exists())
			//	return dstFileName;
			
			try
			{
				Files.copy(new File(jarName).toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
			catch(Exception ex)
			{
				ex.printStackTrace();
			}
			
			return dstFileName;
		}
		
		private void modifyJarByASM(String copyJarName)
		{
			List<String> needModifyClasseFiles = new ArrayList<String>();
			
			for (String className : classNames)
			{
				JarFile jarFile = null;
				String classFileName = className;
				
				try
				{
					jarFile = new JarFile(copyJarName);
					classFileName = classFileName.replaceAll("\\.", "/");
					
					if (!classFileName.endsWith(".class"))
						classFileName = classFileName + ".class";
					
					File file = new File(destDir + File.separator + classFileName);
					//if (file.exists())
					//	continue;
					
					JarEntry entry = jarFile.getJarEntry(classFileName);
					if (entry == null)
						continue;
					
					InputStream is = jarFile.getInputStream(entry);
					
					ClassReader cr = new ClassReader(is);
					ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
					ClassVisitor cv = ModifyClassWriterFactory.createClassWriter(cw, className, proxy);
					cr.accept(cv, 0);
			        
			        String classPath = "";
			        int index = classFileName.lastIndexOf("/");
			        
			        if (index != -1)
			        {
			        	classPath = classFileName.substring(0, index);
			        	classPath = classPath.replaceAll("\\/", Matcher.quoteReplacement(File.separator));
			        	File path = new File(destDir + File.separator + classPath);
			        	path.mkdirs();
			        }
			        
			        byte[] data = cw.toByteArray();
			        
			        FileOutputStream fout = new FileOutputStream(file);
			        fout.write(data);
			        fout.close();
			        
			        needModifyClasseFiles.add(classFileName);
				}
				catch(Exception ex)
				{
					ex.printStackTrace();
				}
				finally
				{
					try 
					{
						jarFile.close();
					} 
					catch (IOException e) 
					{
						e.printStackTrace();
					}
				}
			}
			
			updateJar(copyJarName, needModifyClasseFiles);
		}
		
		private void updateJar(String JarName, List<String> needModifyClasseFiles)
		{
			for (String classFileName : needModifyClasseFiles)
			{
				try
				{
					File file = new File(destDir + File.separator + classFileName);
					Path path = Paths.get(new File(JarName).toURI());
					FileSystem zipfs = FileSystems.newFileSystem(path, null);
					Path externalTxtFile = Paths.get(file.getAbsolutePath());
					Path pathInZipfile = zipfs.getPath(classFileName);          
					Files.copy(externalTxtFile, pathInZipfile, StandardCopyOption.REPLACE_EXISTING);
					zipfs.close();
				}
				catch(Exception ex)
				{
					ex.printStackTrace();
				}
			}
		}
		
		public boolean isNeedModify(String jarName)
		{
			JarFile jar = null;
			try 
			{
				jar = new JarFile(jarName);
				
				for (String className : classNames)
				{
					className = className.replaceAll("\\.", "/");
					if (!className.endsWith(".class"))
						className = className + ".class";
				
				if (jar.getJarEntry(className) != null)
					return true;
				}
			} 
			catch (IOException e) 
			{
				e.printStackTrace();
			}
			finally
			{
				if (jar != null)
				{
					try 
					{
						jar.close();
					} 
					catch (IOException e) 
					{
						e.printStackTrace();
					}
				}
			}
			
			return false;
		}
		
		public String[] getJarsInClasspath()
		{
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			ByteArrayOutputStream errorStream = new ByteArrayOutputStream();  
			
			try
			{
				String cmd = String.format("mvn -f %s dependency:build-classpath", pom);
				
				CommandLine commandline =  CommandLine.parse("cmd /c");
				commandline.addArgument(cmd, false);
				
				DefaultExecutor exec = new DefaultExecutor();
			    PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream, errorStream);
			    DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();
			    
			    exec.setStreamHandler(streamHandler);
			    exec.setExitValue(1);
			    
			    exec.execute(commandline, resultHandler);
			    resultHandler.waitFor();
			    
			    String output = outputStream.toString();
			    int start = output.indexOf("[INFO] Dependencies classpath:");
			    if (start == -1)
			    	return null;
			    
				start = output.indexOf(":", start);
				int end = output.indexOf("[INFO]", start);
				
				String classpath = output.substring(start + 1, end).trim();
				
				return classpath.split(";");
			}
			catch(Exception ex)
			{
				ex.printStackTrace();
				System.out.println(errorStream.toString());
			}
			
			return null;
		}
	}
	
	public static void main(String[] args)
	{
		new AutoProxy(null, null, "D:\\GitHub\\bodgeit-maven", "testng", "localhost:8008").setProxy();
	}
}
