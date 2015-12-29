package fr.novia.zaproxyplugin.asm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ModifyTestNGRunnerClassWriter extends ClassVisitor 
{
	private String proxy;
	
	public ModifyTestNGRunnerClassWriter(ClassWriter cw, String proxy) 
	{
		super(Opcodes.ASM4, cw);
		
		this.proxy = proxy;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) 
	{
		MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
		
		if (Opcodes.ACC_STATIC == access && name.equals("<clinit>") && desc.equals("()V") && signature == null && exceptions == null)
			mv = new ModifyInitMethod(mv);
		
		return mv;
	}
	
	class ModifyInitMethod extends MethodVisitor 
	{
		
		public ModifyInitMethod(MethodVisitor mv) 
		{
			super(Opcodes.ASM4, mv);
		}
		
		@Override
		public void visitCode()
		{
			int index = proxy.indexOf(":");
			
			mv.visitCode();
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "getProperties", "()Ljava/util/Properties;");
			mv.visitLdcInsn("http.proxyHost");
			mv.visitLdcInsn(proxy.substring(0, index));
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Properties", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
			mv.visitInsn(Opcodes.POP);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "getProperties", "()Ljava/util/Properties;");
			mv.visitLdcInsn("http.proxyPort");
			mv.visitLdcInsn(proxy.substring(index + 1));
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Properties", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
			mv.visitInsn(Opcodes.POP);
		}
	}
	
	public static void main(String args[]) throws Exception
	{
		FileInputStream fis = new FileInputStream(new File("D:\\GitHub\\testng\\target\\classes\\org\\testng\\TestRunner.class"));
		
		ClassReader cr = new ClassReader(fis);
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		
		cr.accept(new ModifyTestNGRunnerClassWriter(cw, "localhost:8008"), 0);
		
		
		byte[] data = cw.toByteArray();
        File file = new File("c:\\temp\\2.class");
        FileOutputStream fout = new FileOutputStream(file);
        fout.write(data);
        fout.close();
	}
}
