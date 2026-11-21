package ro.andrei.ideprofiles.bridge;

import java.nio.file.Path;
import java.util.function.BiPredicate;

/** Bootstrap-visible boundary. It has no IntelliJ or third-party dependencies. */
public final class ProfileBridge {
  private ProfileBridge() {}
  public static volatile String agentStatus = "starting";
  public static volatile String detail = "";
  public static volatile BiPredicate<Path, Object> handler;

  public static boolean route(Path path, Object options) {
    BiPredicate<Path, Object> current = handler;
    if(current==null) return false;
    try { return current.test(path, options); }
    catch(Throwable e) {
      detail="Routing failed: "+e;
      System.err.println("Automatic Project Plugins: opening cancelled: "+e);
      return true;
    }
  }
}
