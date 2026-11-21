import java.nio.file.Path;
import java.lang.reflect.*;
import ro.andrei.ideprofiles.bridge.ProfileBridge;

/** Verifies the exact installed class, including JVM stack-frame validation. */
public class AgentVerification {
  public static void main(String[] args) throws Exception {
    Class<?> target=Class.forName("com.intellij.openapi.project.impl.ProjectManagerImpl");
    target.getDeclaredMethods();
    if(!ProfileBridge.agentStatus.equals("active")) throw new AssertionError(ProfileBridge.agentStatus+": "+ProfileBridge.detail);
    // This object is used only to call the injected early-return guard, never IDE code.
    Class<?> unsafeType=Class.forName("sun.misc.Unsafe");
    Field f=unsafeType.getDeclaredField("theUnsafe"); f.setAccessible(true);
    Object instance=unsafeType.getMethod("allocateInstance",Class.class).invoke(f.get(null),target);
    int[] calls={0}; ProfileBridge.handler=(path,options)->{calls[0]++;return true;};
    Method method=target.getMethod("openProjectAsync",Path.class,Class.forName("com.intellij.ide.impl.OpenProjectTask"),Class.forName("kotlin.coroutines.Continuation"));
    Object result=method.invoke(instance,Path.of("/tmp/agent-guard-test"),null,null);
    if(result!=null||calls[0]!=1) throw new AssertionError("Opening guard failed");
    System.out.println("PASS: exact IntelliJ class verified; normal opening intercepted before original code");
  }
}
