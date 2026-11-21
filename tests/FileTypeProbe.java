package ro.andrei.ideprofiles.tests;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;

public class FileTypeProbe implements ProjectActivity {
  public Object execute(Project project,Continuation<? super Unit> continuation) {
    if(project.getBasePath()==null||!project.getBasePath().startsWith("/private/tmp/ipp-fixtures/"))return Unit.INSTANCE;
    try {
      Map<String,Object> result=new LinkedHashMap<>();result.put("project",project.getBasePath());
      result.put("rsFileType",FileTypeManager.getInstance().getFileTypeByFileName("main.rs").getName());
      result.put("rustLoaded",PluginManagerCore.isLoaded(PluginId.getId("com.jetbrains.rust")));
      result.put("androidLoaded",PluginManagerCore.isLoaded(PluginId.getId("org.jetbrains.android")));
      Files.writeString(Path.of(System.getProperty("ideprofiles.storageRoot")).resolve("filetypes-"+Path.of(project.getBasePath()).getFileName()+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
    }catch(Exception e){throw new RuntimeException(e);}
    return Unit.INSTANCE;
  }
}
