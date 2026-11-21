package ro.andrei.ideprofiles;

import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.openapi.application.*;
import com.intellij.openapi.diagnostic.Logger;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import ro.andrei.ideprofiles.bridge.ProfileBridge;

public final class ProfileStartup implements ApplicationInitializedListener {
  private static final Logger LOG=Logger.getInstance(ProfileStartup.class);
  @Override public Object execute(Continuation<? super Unit> continuation) {
    try {
      String expected=System.getProperty("ideprofiles.expectedBuild");
      if(expected!=null&&!expected.equals(ApplicationInfo.getInstance().getBuild().asStringWithoutProductCode())) {
        ProfileRouter.error("Automatic Project Plugins needs rebuilding for this IntelliJ update. Projects will open normally until it is updated.");
        return Unit.INSTANCE;
      }
      if(!ProfileBridge.agentStatus.equals("active")&&!ProfileBridge.agentStatus.equals("waiting-for-project-manager")) {
        ProfileRouter.error("The opening agent is inactive ("+ProfileBridge.agentStatus+"). Automatic profiles have not been applied.");
        return Unit.INSTANCE;
      }
      ProfileBridge.handler=ProfileRouter::intercept;
      LOG.info("Automatic project routing installed; agent="+ProfileBridge.agentStatus);
      ApplicationManager.getApplication().executeOnPooledThread(()->{
        ProfileRouter.refreshRecents();
        try {
          ProfileRuntime.write(null);
          if(System.getProperty("ideprofiles.projectRoot")==null)
            ProfileFiles.json(ProfileFiles.storage().resolve("available-plugins.json"),PluginCatalog.read().entries);
        } catch(Exception e) { LOG.warn(e); }
      });
    } catch(LinkageError e) { ProfileRouter.error("The profile plugin needs its Java agent. The IDE will open projects normally until the agent is enabled."); }
    return Unit.INSTANCE;
  }
}
