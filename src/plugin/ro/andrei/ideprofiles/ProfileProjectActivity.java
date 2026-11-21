package ro.andrei.ideprofiles;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.notification.*;
import com.google.gson.*;
import java.nio.file.*;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import ro.andrei.ideprofiles.bridge.ProfileBridge;

public final class ProfileProjectActivity implements ProjectActivity {
  @Override public Object execute(Project project,Continuation<? super Unit> continuation) {
    if(project.isWelcomeProject()) return Unit.INSTANCE;
    try {
      if(!ProfileBridge.agentStatus.equals("active")) {
        ProfileRouter.error("Automatic Project Plugins is inactive for this IDE build. The project was opened with its current plugin settings.");
        return Unit.INSTANCE;
      }
    } catch(LinkageError unavailable) { return Unit.INSTANCE; }
    try {
      ProfileRuntime.write(project); ProfileRouter.refreshRecents();
      Path planFile=ProfileFiles.directory(ProfileFiles.canonicalRoot(Path.of(project.getBasePath()))).resolve("plan.json");
      if(Files.isRegularFile(planFile)) {
        JsonObject plan=JsonParser.parseString(Files.readString(planFile)).getAsJsonObject();
        if(plan.get("conservative").getAsBoolean()) {
          String reason=plan.getAsJsonArray("warnings").isEmpty()?"The project could not be classified.":plan.getAsJsonArray("warnings").get(0).getAsString();
          NotificationGroupManager.getInstance().getNotificationGroup("Automatic Project Plugins")
              .createNotification("Automatic plugin filtering was not applied",reason+" See Tools > Project Plugin Profile for details.",NotificationType.WARNING).notify(project);
        }
      }
    }
    catch(Exception e) { ProfileRouter.error("Could not record the project plugin profile: "+e.getMessage()); }
    ApplicationManager.getApplication().invokeLater(()->{
      if(!project.isDisposed()) ApplicationManager.getApplication().executeOnPooledThread(()->{
        try { ProfileRuntime.write(project); } catch(Exception ignored) {}
      });
    });
    return Unit.INSTANCE;
  }
}
