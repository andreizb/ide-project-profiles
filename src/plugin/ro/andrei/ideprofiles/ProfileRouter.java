package ro.andrei.ideprofiles;

import com.google.gson.*;
import com.intellij.ide.*;
import com.intellij.notification.*;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectManager;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public final class ProfileRouter {
  private static final Logger LOG=Logger.getInstance(ProfileRouter.class);
  private static final Set<Path> pending=ConcurrentHashMap.newKeySet();
  public static boolean intercept(Path original,Object options) {
    try {
      if(WelcomeProjects.isWelcomeOpening(original)) return false;
      Path root=ProfileFiles.canonicalRoot(original);
      String bound=System.getProperty("ideprofiles.projectRoot");
      if(bound!=null&&root.equals(Path.of(bound).toRealPath())) return false;
      if(pending.add(root)) {
        ApplicationManager.getApplication().executeOnPooledThread(()->{
          boolean success=false;
          try { success=open(root,original); }
          catch(Exception e) { LOG.warn("Profile opening failed for "+root,e); error("Could not prepare plugins for "+root.getFileName()+": "+e.getMessage()); }
          finally { pending.remove(root); if(success) closeEmptyHub(); }
        });
      }
      return true;
    } catch(Exception e) {
      error("Could not identify this project: "+e.getMessage());
      return true; // No accidental second project in an already bound process.
    }
  }
  private static boolean open(Path root,Path original) throws Exception {
    Path profile=ProfileFiles.directory(root); ProfileFiles.privateDirectory(profile);
    try(FileChannel channel=FileChannel.open(profile.resolve("prepare.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        FileLock lock=channel.lock()) {
      boolean alreadyRunning=ProfileFiles.running(profile);
      if(!alreadyRunning) {
        if(!ProfileTrust.confirm(root)) return false;
        ProjectDetector.Detection detection=ProjectDetector.detect(root);
        PluginCatalog catalog=PluginCatalog.read();
        ProfileFiles.Overrides overrides=ProfileFiles.manualOverrides(profile);
        Set<String> disabled=ProfileFiles.disabledAtOrigin(); disabled.addAll(overrides.disabled()); disabled.removeAll(overrides.enabled());
        PluginCatalog.Plan plan=catalog.plan(root.toString(),detection,disabled,ProfileFiles.extraTags(profile),overrides.enabled());
        ProfileFiles.prepare(root,plan);
        ProfileTrust.copyDecision(profile,root);
        ProfileFiles.json(profile.resolve("catalog.json"),catalog.entries);
        LOG.info("Prepared "+root+" technologies="+plan.technologies()+" disabled="+plan.disabled().size());
      }
      ProfileFiles.remember(root); refreshRecents();
      ProcessBuilder builder=new ProcessBuilder("/usr/bin/open","-n","-a",PathManager.getHomeDir().getParent().toString(),
          "--env","IDEA_PROPERTIES="+profile.resolve("idea.properties"),
          "--env","IDEA_VM_OPTIONS="+profile.resolve("idea.vmoptions"),
          "--env","PWD="+PathManager.getHomeDir(),
          "--stdout",profile.resolve("launcher.log").toString(),"--stderr",profile.resolve("launcher.log").toString(),
          "--args",original.toString());
      builder.directory(PathManager.getHomeDir().toFile());
      builder.environment().put("PWD",PathManager.getHomeDir().toString());
      builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(profile.resolve("launcher.log").toFile()));
      try(DockHandoff.Ticket handoff=DockHandoff.beginCurrent()) {
      Process child=builder.start();
      ProfileFiles.json(profile.resolve("last-launch.json"),Map.of("pid",child.pid(),"requested",Instant.now().toString(),"root",root.toString(),"sourcePid",ProcessHandle.current().pid()));
      if(alreadyRunning) {handoff.succeeded();return true;}
      long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(180);
      boolean ready=false;
      while(System.nanoTime()<deadline) {
        if(ProfileFiles.running(profile)) {
          JsonObject runtime=JsonParser.parseString(Files.readString(profile.resolve("runtime.json"))).getAsJsonObject();
          if(runtime.has("project")&&root.toString().equals(runtime.get("project").getAsString())) { ready=true; break; }
        }
        if(!child.isAlive()&&child.exitValue()!=0) throw new IllegalStateException("IntelliJ exited during startup ("+child.exitValue()+"); see "+profile.resolve("launcher.log"));
        Thread.sleep(250);
      }
      if(!ready) { error("IntelliJ is still opening "+root.getFileName()+". Check its window for a trust or licence prompt."); return false; }
      LOG.info("Isolated project ready: "+root);
      handoff.succeeded();
      return true;
      }
    }
  }
  private static void closeEmptyHub() {
    if(System.getProperty("ideprofiles.projectRoot")!=null || Boolean.getBoolean("ideprofiles.keepHub")) return;
    ApplicationManager.getApplication().invokeLater(()->{
      if(!WelcomeProjects.hasUserProjects() && pending.isEmpty()&&DockHandoff.currentMayExit())
        ((ApplicationEx)ApplicationManager.getApplication()).exit();
    });
  }
  public static void refreshRecents() {
    try {
      Path f=ProfileFiles.storage().resolve("recent.json"); if(!Files.exists(f)) return;
      JsonObject all=JsonParser.parseString(Files.readString(f)).getAsJsonObject();
      ApplicationManager.getApplication().invokeLater(()->{
        RecentProjectsManagerBase manager=RecentProjectsManagerBase.getInstanceEx();
        Set<String> existing=new HashSet<>(manager.getRecentPaths());
        all.entrySet().stream().sorted(Comparator.comparingLong(e->e.getValue().getAsLong())).forEach(e->{
          if(existing.contains(e.getKey())) return;
          RecentProjectMetaInfo info=new RecentProjectMetaInfo(); info.setOpened(false);
          info.setDisplayName(Path.of(e.getKey()).getFileName().toString()); info.setProjectOpenTimestamp(e.getValue().getAsLong());
          manager.addRecentPath(e.getKey(),info);
        });
      });
    } catch(Exception e) { LOG.warn("Cannot refresh recent project list",e); }
  }
  public static void error(String message) {
    LOG.warn(message);
    ApplicationManager.getApplication().invokeLater(()->NotificationGroupManager.getInstance()
        .getNotificationGroup("Automatic Project Plugins").createNotification(message,NotificationType.WARNING).notify(null));
  }
}
