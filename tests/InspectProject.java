import ro.andrei.ideprofiles.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Read-only diagnostic: run the production detector and planner against an exported catalog. */
public class InspectProject {
  public static void main(String[] args)throws Exception {
    Path root=Path.of(args[0]).toRealPath();
    JsonObject json=JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject();
    PluginCatalog catalog=new PluginCatalog();
    for(var e:json.entrySet()) catalog.entries.put(e.getKey(),ProfileFiles.JSON.fromJson(e.getValue(),PluginCatalog.Entry.class));
    catalog.essential.addAll(Set.of("com.intellij","com.intellij.java","com.intellij.java.ide","com.intellij.modules.json","ro.andrei.ideprofiles"));
    var detection=ProjectDetector.detect(root);
    var plan=catalog.plan(root.toString(),detection,Set.of(),Set.of());
    for(String id:plan.enabled().keySet()) for(String dep:catalog.entries.get(id).requires())
      if(catalog.entries.containsKey(dep)&&!plan.enabled().containsKey(dep)) throw new AssertionError(id+" lost dependency "+dep);
    Files.writeString(Path.of(args[2]),ProfileFiles.JSON.toJson(plan)+"\n");
    System.out.println("Detected: "+plan.technologies());
    System.out.println("Filtering active: "+!plan.conservative()+"; enabled: "+plan.enabled().size()+"; disabled: "+plan.disabled().size());
    System.out.println("Warnings: "+plan.warnings());
  }
}
