package ro.andrei.ideprofiles;

import com.intellij.ide.plugins.*;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.wm.WindowManager;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import ro.andrei.ideprofiles.bridge.ProfileBridge;

public final class ProfileRuntime {
  public static void write(Project project) throws Exception {
    Map<String,Object> result=new LinkedHashMap<>();
    ProcessHandle process=ProcessHandle.current();
    result.put("pid",process.pid()); result.put("processStart",process.info().startInstant().orElseThrow().toString());
    result.put("time",Instant.now().toString()); result.put("agentStatus",ProfileBridge.agentStatus);
    result.put("agentDetail",ProfileBridge.detail); result.put("config",PathManager.getConfigPath());
    result.put("plugins",PathManager.getPluginsPath()); result.put("system",PathManager.getSystemPath());
    Map<String,String> loaded=new TreeMap<>();
    for(var p:PluginManagerCore.getLoadedPlugins()) loaded.put(p.getPluginId().getIdString(),p.getName());
    result.put("loaded",loaded);
    if(project!=null) {
      result.put("project",ProfileFiles.canonicalRoot(Path.of(project.getBasePath())).toString());
      result.put("moduleCount",ModuleManager.getInstance(project).getModules().length);
      Runnable frameInfo=()->{
        var frame=WindowManager.getInstance().getFrame(project);
        result.put("frameVisible",frame!=null&&frame.isVisible());
        result.put("frameShowing",frame!=null&&frame.isShowing());
        if(frame!=null) result.put("frameTitle",frame.getTitle());
      };
      if(ApplicationManager.getApplication().isDispatchThread()) frameInfo.run();
      else ApplicationManager.getApplication().invokeAndWait(frameInfo);
    }
    String root=System.getProperty("ideprofiles.projectRoot");
    Path target=root==null?ProfileFiles.storage().resolve("hub-"+process.pid()+"-runtime.json"):ProfileFiles.directory(Path.of(root)).resolve("runtime.json");
    ProfileFiles.json(target,result);
  }
}
