import ro.andrei.ideprofiles.*;
import java.nio.file.*;
import java.util.*;

public class PolicyTests {
  static int count=0;
  static void check(boolean b,String message) { if(!b) throw new AssertionError(message); count++; }
  static Path file(Path root,String name,String contents)throws Exception { Path p=root.resolve(name);Files.createDirectories(p.getParent());Files.writeString(p,contents);return p; }
  static void git(Path root,String... arguments)throws Exception {
    var command=new ArrayList<String>(List.of("/usr/bin/git","-C",root.toString()));command.addAll(List.of(arguments));
    Process p=new ProcessBuilder(command).redirectErrorStream(true).start();
    String output=new String(p.getInputStream().readAllBytes());
    if(p.waitFor()!=0) throw new AssertionError("Git fixture: "+output);
  }
  static PluginCatalog catalog() {
    PluginCatalog c=new PluginCatalog();
    for(String id:List.of("core","PythonCore","Pythonid","org.jetbrains.android","com.intellij.spring","com.intellij.hibernate","com.intellij.javaee.jpa","com.intellij.database","JavaScript","com.intellij.react","ro.andrei.ideprofiles","com.intellij.settingsSync","unknown.new.plugin","shared.dependency"))
      c.entries.put(id,new PluginCatalog.Entry(id,id,"1","/none",true,Set.of()));
    c.entries.put("Pythonid",new PluginCatalog.Entry("Pythonid","Python Pro","1","/none",true,Set.of("PythonCore","shared.dependency")));
    c.entries.put("com.intellij.hibernate",new PluginCatalog.Entry("com.intellij.hibernate","Hibernate","1","/none",true,Set.of("com.intellij.javaee.jpa","com.intellij.database")));
    c.essential.add("core");return c;
  }
  public static void main(String[] args)throws Exception {
    Path base=Files.createTempDirectory("profile-policy-tests-");
    Path spring=base.resolve("spring");file(spring,"pom.xml","<project><dependency>org.springframework.boot:spring-boot-starter-web</dependency></project>");
    file(spring,"node_modules/example/AndroidManifest.xml","android");file(spring,".venv/foo.py","ignore");
    var sd=ProjectDetector.detect(spring);check(sd.tags().containsAll(Set.of("java","maven","spring","spring-boot","spring-web")),"Spring web detection");check(!sd.tags().contains("android")&&!sd.tags().contains("python"),"Ignore installed dependencies");
    var sp=catalog().plan(spring.toString(),sd,Set.of(),Set.of());check(sp.enabled().containsKey("com.intellij.spring"),"Spring retained");check(sp.disabled().containsKey("org.jetbrains.android"),"Android disabled in Spring");check(sp.disabled().containsKey("com.intellij.hibernate"),"Hibernate not implied by Spring");check(sp.disabled().containsKey("PythonCore"),"Python disabled in Spring");
    Path python=base.resolve("python");file(python,"pyproject.toml","[project]\nname='sample'\n");
    var pp=catalog().plan(python.toString(),ProjectDetector.detect(python),Set.of(),Set.of());check(pp.enabled().keySet().containsAll(Set.of("Pythonid","PythonCore","shared.dependency","unknown.new.plugin","core")),"Python dependency closure and unknown defaults");check(pp.disabled().keySet().containsAll(Set.of("com.intellij.spring","com.intellij.hibernate","org.jetbrains.android","com.intellij.settingsSync")),"Python excludes JVM frameworks and shared sync");
    Path mixed=base.resolve("mixed");file(mixed,"backend/pom.xml","<project>org.springframework.boot spring-boot-starter-data-jpa</project>");file(mixed,"frontend/package.json","{\"dependencies\":{\"react\":\"19\"}}");
    var md=ProjectDetector.detect(mixed);var mp=catalog().plan(mixed.toString(),md,Set.of(),Set.of());check(md.tags().containsAll(Set.of("jpa","spring-data","react","web")),"Mixed monorepo detected");check(mp.enabled().containsKey("com.intellij.hibernate")&&mp.enabled().containsKey("JavaScript"),"Mixed stack retains both languages and JPA");check(mp.disabled().containsKey("org.jetbrains.android"),"Mixed web backend does not imply Android");
    Path android=base.resolve("android");file(android,"app/build.gradle.kts","plugins { id(\"com.android.application\") }");check(ProjectDetector.detect(android).tags().containsAll(Set.of("android","gradle","kotlin","java")),"Android Kotlin detection");
    Path empty=base.resolve("empty");Files.createDirectories(empty);check(catalog().plan(empty.toString(),ProjectDetector.detect(empty),Set.of(),Set.of()).conservative(),"Unknown projects remain usable");
    var override=catalog().plan(python.toString(),ProjectDetector.detect(python),Set.of(),Set.of("graphql","spring"));check(override.enabled().containsKey("com.intellij.spring"),"Per-project extra technology override");
    check(pp.enabled().equals(catalog().plan(python.toString(),ProjectDetector.detect(python),Set.of(),Set.of()).enabled()),"Other plans cannot mutate Python plan");
    var manual=catalog().plan(python.toString(),ProjectDetector.detect(python),Set.of("shared.dependency"),Set.of());
    check(manual.disabled().containsKey("Pythonid")&&manual.disabled().containsKey("shared.dependency"),"Explicitly disabled dependencies are respected");
    var manualOn=catalog().plan(python.toString(),ProjectDetector.detect(python),Set.of(),Set.of(),Set.of("org.jetbrains.android"));
    check(manualOn.enabled().containsKey("org.jetbrains.android"),"Manual enable survives classification");
    Path inherited=base.resolve("inherited");file(inherited,"src/Entity.java","import jakarta.persistence.Entity;\n@Entity class EntitySample {}\n");
    check(ProjectDetector.detect(inherited).tags().contains("jpa"),"Source imports detect inherited persistence dependencies");
    file(inherited,".idea/externalDependencies.xml","<project><component name=\"ExternalDependencies\"><plugin id=\"org.jetbrains.android\"/></component></project>");
    var ip=catalog().plan(inherited.toString(),ProjectDetector.detect(inherited),Set.of(),Set.of());
    check(ip.enabled().containsKey("org.jetbrains.android"),"Explicit Required Plugins are honoured");
    Path deep=base.resolve("deep");
    file(deep,"backend/service/src/main/java/com/example/one/two/three/four/five/six/seven/Application.java","import org.springframework.boot.SpringApplication;\nclass Application {}\n");
    var dd=ProjectDetector.detect(deep);
    check(!dd.truncated()&&dd.tags().contains("spring-boot"),"Deep Java packages are fully inspected without Git");
    check(catalog().plan(deep.toString(),dd,Set.of(),Set.of()).disabled().containsKey("org.jetbrains.android"),"Deep Java source must not enable every plugin");
    Path repo=base.resolve("git-project");Files.createDirectories(repo);git(repo,"init","-q");
    file(repo,".gitignore","/test-output/\n/ignored-source/\n!ignored-source/keep.rs\n/nested-local/\n");
    file(repo,"pom.xml","org.springframework.boot spring-boot-starter-web");
    file(repo,"test-output/validator-deps/AndroidManifest.xml","generated");
    file(repo,"test-output/validator-deps/dependency.py","generated");
    file(repo,"new code/line\nbreak.py","print('new uncommitted source')");
    file(repo,"ignored-source/keep.rs","fn main() {}\n");git(repo,"add","-f","ignored-source/keep.rs");
    file(repo,"backend/service/src/main/java/com/example/one/two/three/four/five/six/seven/Application.java","import org.springframework.boot.SpringApplication;");
    var gd=ProjectDetector.detect(repo);
    check(!gd.truncated()&&gd.tags().containsAll(Set.of("java","spring","python","rust")),"Git inventory includes deep, tracked and untracked source with unusual filenames");
    check(!gd.tags().contains("android")&&gd.evidence().stream().noneMatch(e->e.contains("test-output")),"Gitignored generated files cannot enable unrelated plugins");
    check(catalog().plan(repo.toString(),gd,Set.of(),Set.of()).disabled().containsKey("org.jetbrains.android"),"Git project filtering stays active");
    var sub=ProjectDetector.detect(repo.resolve("backend"));
    check(!sub.truncated()&&sub.tags().contains("spring")&&!sub.tags().contains("python"),"Opening a repository subfolder stays scoped to that subfolder");
    Path nested=repo.resolve("nested");Files.createDirectories(nested);git(nested,"init","-q");
    file(nested,"go.mod","module example.test/nested");file(nested,".gitignore","generated/\n");
    file(nested,"generated/AndroidManifest.xml","generated");
    var nd=ProjectDetector.detect(repo);
    check(nd.tags().contains("go")&&!nd.tags().contains("android")&&!nd.truncated(),"Nested repositories are inspected using their own ignore rules");
    var incomplete=catalog().plan(repo.toString(),new ProjectDetector.Detection(Set.of("java"),List.of(),true,Set.of(),List.of("Cannot inspect example")),Set.of(),Set.of());
    check(incomplete.conservative()&&incomplete.warnings().contains("Cannot inspect example"),"Genuine scan failures explain why filtering was not applied");
    System.out.println("PASS: "+count+" policy assertions");
  }
}
