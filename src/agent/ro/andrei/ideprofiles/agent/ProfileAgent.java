package ro.andrei.ideprofiles.agent;

import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.ProtectionDomain;
import java.util.HexFormat;
import java.util.jar.JarFile;
import java.util.Properties;
import org.jetbrains.org.objectweb.asm.*;

/** A single, fingerprint-checked entry guard; never modifies the app on disk. */
public final class ProfileAgent {
  public static void premain(String ignored, Instrumentation instrumentation) {
    try { install(instrumentation); }
    catch(Throwable e) {
      System.setProperty("ideprofiles.agent.failure",e.toString());
      System.err.println("Automatic Project Plugins: agent not installed; normal startup retained: "+e);
    }
  }
  private static void install(Instrumentation instrumentation) throws Exception {
    String configured=System.getProperty("ideprofiles.agent.path");
    if(configured==null) configured=java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
        .filter(s->s.startsWith("-javaagent:")&&s.endsWith("/profile-agent.jar")).map(s->s.substring("-javaagent:".length())).findFirst().orElseThrow();
    Path jar = Path.of(configured);
    System.setProperty("ideprofiles.agent.path",jar.toString());
    instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(jar.resolveSibling("profile-bridge.jar").toFile()));
    Properties expected = new Properties();
    try (InputStream in = ProfileAgent.class.getResourceAsStream("/target.properties")) { expected.load(in); }
    System.setProperty("ideprofiles.expectedBuild",expected.getProperty("build"));
    ro.andrei.ideprofiles.bridge.ProfileBridge.agentStatus = "waiting-for-project-manager";
    instrumentation.addTransformer(new ClassFileTransformer() {
      @Override public byte[] transform(ClassLoader loader, String name, Class<?> redefining,
                                       ProtectionDomain domain, byte[] bytes) {
        if (!name.equals("com/intellij/openapi/project/impl/ProjectManagerImpl")) return null;
        try {
          String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
          if (!hash.equals(expected.getProperty("sha256"))) {
            ro.andrei.ideprofiles.bridge.ProfileBridge.agentStatus = "unsupported-build";
            ro.andrei.ideprofiles.bridge.ProfileBridge.detail = "ProjectManagerImpl fingerprint: " + hash;
            return null;
          }
          ClassReader reader = new ClassReader(bytes);
          ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
          int[] hooked = {0};
          reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String method, String descriptor,
                                                        String signature, String[] exceptions) {
              MethodVisitor mv = super.visitMethod(access, method, descriptor, signature, exceptions);
              if (!method.equals("openProjectAsync") || !descriptor.equals(
                  "(Ljava/nio/file/Path;Lcom/intellij/ide/impl/OpenProjectTask;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;")) return mv;
              hooked[0]++;
              return new MethodVisitor(Opcodes.ASM9, mv) {
                @Override public void visitCode() {
                  super.visitCode();
                  Label proceed = new Label();
                  mv.visitVarInsn(Opcodes.ALOAD, 1);
                  mv.visitVarInsn(Opcodes.ALOAD, 2);
                  mv.visitMethodInsn(Opcodes.INVOKESTATIC, "ro/andrei/ideprofiles/bridge/ProfileBridge", "route",
                      "(Ljava/nio/file/Path;Ljava/lang/Object;)Z", false);
                  mv.visitJumpInsn(Opcodes.IFEQ, proceed);
                  mv.visitInsn(Opcodes.ACONST_NULL);
                  mv.visitInsn(Opcodes.ARETURN);
                  mv.visitLabel(proceed);
                  mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                }
              };
            }
          }, 0);
          if (hooked[0] != 1) throw new IllegalStateException("Expected one opening method; found " + hooked[0]);
          ro.andrei.ideprofiles.bridge.ProfileBridge.agentStatus = "active";
          ro.andrei.ideprofiles.bridge.ProfileBridge.detail = expected.getProperty("build");
          return writer.toByteArray();
        } catch (Throwable e) {
          ro.andrei.ideprofiles.bridge.ProfileBridge.agentStatus = "failed";
          ro.andrei.ideprofiles.bridge.ProfileBridge.detail = e.toString();
          System.err.println("Automatic Project Plugins: agent inactive: " + e);
          return null;
        }
      }
    });
  }
}
