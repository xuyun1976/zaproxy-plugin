package fr.novia.zaproxyplugin.asm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ModifyRemoteWebDriverClassWriter extends ClassVisitor 
{
	private ClassWriter cw;
	private String proxy;
	
	public ModifyRemoteWebDriverClassWriter(ClassWriter cw, String proxy) 
	{
		super(Opcodes.ASM4, cw);
		
		this.cw = cw;
		this.proxy = proxy;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) 
	{
		MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
		
		if (Opcodes.ACC_PUBLIC == access && name.equals("<init>") && desc.equals("(Lorg/openqa/selenium/remote/CommandExecutor;Lorg/openqa/selenium/Capabilities;Lorg/openqa/selenium/Capabilities;)V") && signature == null && exceptions == null)
		{
			addMethodSetSecurityScanProxy(cw);
			mv = new ModifyInitMethod(mv);
		}
		
		return mv;
	}
	
	private void addMethodSetSecurityScanProxy(ClassWriter cw)
	{
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "setSecurityScanProxy", "(Lorg/openqa/selenium/Capabilities;)Lorg/openqa/selenium/Capabilities;", null, null);
		mv.visitCode();
		mv.visitLdcInsn(proxy);
		mv.visitVarInsn(Opcodes.ASTORE, 2);
		mv.visitTypeInsn(Opcodes.NEW, "org/openqa/selenium/Proxy");
		mv.visitInsn(Opcodes.DUP);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "org/openqa/selenium/Proxy", "<init>", "()V");
		mv.visitVarInsn(Opcodes.ASTORE, 3);
		mv.visitVarInsn(Opcodes.ALOAD, 3);
		mv.visitVarInsn(Opcodes.ALOAD, 2);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "org/openqa/selenium/Proxy", "setHttpProxy", "(Ljava/lang/String;)Lorg/openqa/selenium/Proxy;");
		mv.visitVarInsn(Opcodes.ALOAD, 2);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "org/openqa/selenium/Proxy", "setFtpProxy", "(Ljava/lang/String;)Lorg/openqa/selenium/Proxy;");
		mv.visitVarInsn(Opcodes.ALOAD, 2);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "org/openqa/selenium/Proxy", "setSslProxy", "(Ljava/lang/String;)Lorg/openqa/selenium/Proxy;");
		mv.visitLdcInsn("");
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "org/openqa/selenium/Proxy", "setNoProxy", "(Ljava/lang/String;)Lorg/openqa/selenium/Proxy;");
		mv.visitInsn(Opcodes.POP);
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		Label l0 = new Label();
		mv.visitJumpInsn(Opcodes.IFNONNULL, l0);
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;");
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;");
		mv.visitLdcInsn("ChromeDriver");
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z");
		Label l1 = new Label();
		mv.visitJumpInsn(Opcodes.IFEQ, l1);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/openqa/selenium/remote/DesiredCapabilities", "chrome", "()Lorg/openqa/selenium/remote/DesiredCapabilities;");
		mv.visitVarInsn(Opcodes.ASTORE, 1);
		mv.visitJumpInsn(Opcodes.GOTO, l0);
		mv.visitLabel(l1);
		mv.visitFrame(Opcodes.F_APPEND,2, new Object[] {"java/lang/String", "org/openqa/selenium/Proxy"}, 0, null);
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;");
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;");
		mv.visitLdcInsn("FirefoxDriver");
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z");
		Label l2 = new Label();
		mv.visitJumpInsn(Opcodes.IFEQ, l2);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/openqa/selenium/remote/DesiredCapabilities", "firefox", "()Lorg/openqa/selenium/remote/DesiredCapabilities;");
		mv.visitVarInsn(Opcodes.ASTORE, 1);
		mv.visitJumpInsn(Opcodes.GOTO, l0);
		mv.visitLabel(l2);
		mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;");
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;");
		mv.visitLdcInsn("InternetExplorerDriver");
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z");
		Label l3 = new Label();
		mv.visitJumpInsn(Opcodes.IFEQ, l3);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/openqa/selenium/remote/DesiredCapabilities", "internetExplorer", "()Lorg/openqa/selenium/remote/DesiredCapabilities;");
		mv.visitVarInsn(Opcodes.ASTORE, 1);
		mv.visitJumpInsn(Opcodes.GOTO, l0);
		mv.visitLabel(l3);
		mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;");
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;");
		mv.visitLdcInsn("SafariDriver");
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z");
		Label l4 = new Label();
		mv.visitJumpInsn(Opcodes.IFEQ, l4);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/openqa/selenium/remote/DesiredCapabilities", "safari", "()Lorg/openqa/selenium/remote/DesiredCapabilities;");
		mv.visitVarInsn(Opcodes.ASTORE, 1);
		mv.visitJumpInsn(Opcodes.GOTO, l0);
		mv.visitLabel(l4);
		mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;");
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;");
		mv.visitLdcInsn("HtmlUnitDriver");
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z");
		Label l5 = new Label();
		mv.visitJumpInsn(Opcodes.IFEQ, l5);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/openqa/selenium/remote/DesiredCapabilities", "htmlUnit", "()Lorg/openqa/selenium/remote/DesiredCapabilities;");
		mv.visitVarInsn(Opcodes.ASTORE, 1);
		mv.visitJumpInsn(Opcodes.GOTO, l0);
		mv.visitLabel(l5);
		mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;");
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;");
		mv.visitLdcInsn("EdgeDriver");
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z");
		mv.visitJumpInsn(Opcodes.IFEQ, l0);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/openqa/selenium/remote/DesiredCapabilities", "edge", "()Lorg/openqa/selenium/remote/DesiredCapabilities;");
		mv.visitVarInsn(Opcodes.ASTORE, 1);
		mv.visitLabel(l0);
		mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitTypeInsn(Opcodes.CHECKCAST, "org/openqa/selenium/remote/DesiredCapabilities");
		mv.visitLdcInsn("proxy");
		mv.visitVarInsn(Opcodes.ALOAD, 3);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "org/openqa/selenium/remote/DesiredCapabilities", "setCapability", "(Ljava/lang/String;Ljava/lang/Object;)V");
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitInsn(Opcodes.ARETURN);
		mv.visitMaxs(3, 4);
		mv.visitEnd();
	}
	
	class ModifyInitMethod extends MethodVisitor 
	{
		int ALOAD_3 = 0;
		
		public ModifyInitMethod(MethodVisitor mv) 
		{
			super(Opcodes.ASM4, mv);
		}
		
		@Override
		public void visitVarInsn(int paramInt1, int paramInt2)
		{
			if (paramInt1 == Opcodes.ALOAD)
			{
				if (paramInt2 == 3)
				{
					ALOAD_3++;
					if (ALOAD_3 == 1)
					{
						mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "org/openqa/selenium/remote/RemoteWebDriver", "setSecurityScanProxy", "(Lorg/openqa/selenium/Capabilities;)Lorg/openqa/selenium/Capabilities;");
						mv.visitVarInsn(Opcodes.ASTORE, 2);
						mv.visitVarInsn(Opcodes.ALOAD, 0);
						mv.visitVarInsn(Opcodes.ALOAD, 2);
					}
				}
			}
			
			mv.visitVarInsn(paramInt1, paramInt2);
		}
	}
	
	public static void main(String args[]) throws Exception
	{
		FileInputStream fis = new FileInputStream(new File("D:\\GitHub\\bodgeit-maven\\lib\\RemoteWebDriver.class"));
		
		ClassReader cr = new ClassReader(fis);
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		
		cr.accept(new ModifyRemoteWebDriverClassWriter(cw, "localhost:8008"), 0);
		
		
		byte[] data = cw.toByteArray();
        File file = new File("c:\\temp\\1.class");
        FileOutputStream fout = new FileOutputStream(file);
        fout.write(data);
        fout.close();
	}
}
