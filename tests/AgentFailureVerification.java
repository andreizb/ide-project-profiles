public class AgentFailureVerification {
  public static void main(String[] args) {
    if(System.getProperty("ideprofiles.agent.failure")==null) throw new AssertionError("Expected a recoverable missing-bridge failure");
    System.out.println("PASS: missing bridge does not prevent JVM startup");
  }
}
