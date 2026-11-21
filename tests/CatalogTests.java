import ro.andrei.ideprofiles.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

public class CatalogTests {
  public static void main(String[] args)throws Exception {
    JsonObject json=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
    PluginCatalog catalog=new PluginCatalog();
    for(var e:json.entrySet()) catalog.entries.put(e.getKey(),ProfileFiles.JSON.fromJson(e.getValue(),PluginCatalog.Entry.class));
    catalog.essential.addAll(Set.of("com.intellij","com.intellij.java","com.intellij.java.ide","com.intellij.modules.json","ro.andrei.ideprofiles"));
    Path fixtures=Files.createTempDirectory("profile-catalog-tests-");
    for(String name:List.of("spring","python")) {
      Path root=fixtures.resolve(name);Files.createDirectories(root);
      if(name.equals("spring")) Files.writeString(root.resolve("pom.xml"),"<project>org.springframework.boot spring-boot-starter-web</project>");
      else Files.writeString(root.resolve("pyproject.toml"),"[project]\nname='sample'\n");
      var plan=catalog.plan(root.toString(),ProjectDetector.detect(root),Set.of(),Set.of());
      if(plan.enabled().containsKey("org.jetbrains.android")) throw new AssertionError("Android leaked into "+name);
      if(name.equals("spring")) {
        if(!plan.enabled().containsKey("com.intellij.spring")||plan.enabled().containsKey("PythonCore")||plan.enabled().containsKey("com.intellij.hibernate")) throw new AssertionError("Wrong Spring profile");
      } else if(!plan.enabled().containsKey("PythonCore")||!plan.enabled().containsKey("Pythonid")||plan.enabled().containsKey("com.intellij.spring")||plan.enabled().containsKey("com.intellij.hibernate")) throw new AssertionError("Wrong Python profile");
      for(String id:plan.enabled().keySet()) for(String dep:catalog.entries.get(id).requires())
        if(catalog.entries.containsKey(dep)&&!plan.enabled().containsKey(dep)) throw new AssertionError(id+" lost dependency "+dep);
      System.out.println("PASS: installed-catalog "+name+" plan; "+plan.enabled().size()+" enabled / "+plan.disabled().size()+" disabled; all required dependencies retained");
    }
    if(!catalog.entries.containsKey("com.intellij.settingsSync"))throw new AssertionError("Disabled plugins missing from full catalog");
  }
}
