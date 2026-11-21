import ro.andrei.ideprofiles.bridge.ProfileBridge;
public class AgentInactiveVerification {
  public static void main(String[] args)throws Exception {
    Class.forName("com.intellij.openapi.project.impl.ProjectManagerImpl",false,ClassLoader.getSystemClassLoader()).getDeclaredMethods();
    if(!ProfileBridge.agentStatus.equals("unsupported-build"))throw new AssertionError(ProfileBridge.agentStatus);
    System.out.println("PASS: an unrecognised IDE class is left unmodified");
  }
}
