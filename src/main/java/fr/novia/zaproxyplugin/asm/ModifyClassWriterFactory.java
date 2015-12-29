package fr.novia.zaproxyplugin.asm;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

public class ModifyClassWriterFactory 
{
	public final static String RemoteWebDriver = "org.openqa.selenium.remote.RemoteWebDriver";
	public final static String JUnitRunner = "org.junit.runner.Runner";
	public final static String TestNGRunner = "org.testng.TestRunner";
	
	public static ClassVisitor createClassWriter(ClassWriter cw, String className, String proxy)
	{
		if (RemoteWebDriver.equals(className))
			return new ModifyRemoteWebDriverClassWriter(cw, proxy);
		else if (JUnitRunner.equals(className))
			return new ModifyJUnitRunnerClassWriter(cw, proxy);
		else if (TestNGRunner.equals(className))
			return new ModifyTestNGRunnerClassWriter(cw, proxy);
			
		return null;
	}
}
