package fr.novia.zaproxyplugin.asm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ModifyJUnitRunnerClassWriter extends ClassVisitor 
{
	private ClassWriter cw;
	private String proxy;
	
	public ModifyJUnitRunnerClassWriter(ClassWriter cw, String proxy) 
	{
		super(Opcodes.ASM4, cw);
		
		this.cw = cw;
		this.proxy = proxy;
	}

	@Override
	public void visitEnd()
	{
		addMethodSetSecurityScanProxy(cw);
		
		cv.visitEnd();
	}
	
	private void addMethodSetSecurityScanProxy(ClassWriter cw)
	{
		int index = proxy.indexOf(":");
		
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
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
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(3, 0);
		mv.visitEnd();
	}
	
	public static void main(String args[]) throws Exception
	{
		FileInputStream fis = new FileInputStream(new File("D:\\GitHub\\junit\\target\\classes\\org\\junit\\runner\\Runner.class"));
		
		ClassReader cr = new ClassReader(fis);
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		
		cr.accept(new ModifyJUnitRunnerClassWriter(cw, "localhost:8008"), 0);
		
		
		byte[] data = cw.toByteArray();
        File file = new File("c:\\temp\\2.class");
        FileOutputStream fout = new FileOutputStream(file);
        fout.write(data);
        fout.close();
	}
}
