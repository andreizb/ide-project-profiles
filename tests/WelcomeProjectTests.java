import ro.andrei.ideprofiles.WelcomeProjects;
import com.intellij.openapi.project.Project;
import java.lang.reflect.Proxy;
import java.nio.file.*;

public class WelcomeProjectTests {
  static Project project(boolean welcome) {
    return (Project)Proxy.newProxyInstance(Project.class.getClassLoader(),new Class<?>[]{Project.class},(proxy,method,args)->{
      if(method.getName().equals("isWelcomeProject")) return welcome;
      throw new AssertionError("Unexpected project access: "+method);
    });
  }
  static void check(boolean value,String message) {if(!value) throw new AssertionError(message);}
  public static void main(String[] args)throws Exception {
    Path root=Files.createTempDirectory("welcome-routing-test-");
    Path home=root.resolve("ide-owned/IdeaHome");Files.createDirectories(home);
    Path alias=root.resolve("welcome-alias");Files.createSymbolicLink(alias,home);
    check(WelcomeProjects.samePath(home,home),"The actual IDE Home must bypass user routing");
    check(WelcomeProjects.samePath(alias,home),"An alias must not create a plugin profile for IDE Home");
    check(!WelcomeProjects.samePath(root.resolve("user/IdeaHome"),home),"A user project merely named IdeaHome must still be routed");
    check(!WelcomeProjects.samePath(home,null),"No configured Home must not bypass user routing");
    check(!WelcomeProjects.hasUserProjects(new Project[]{project(true)}),"Home alone must not prevent welcome Dock handoff and shutdown");
    check(WelcomeProjects.hasUserProjects(new Project[]{project(true),project(false)}),"A real open project must retain its Dock process");
    check(!WelcomeProjects.hasUserProjects(new Project[]{}),"Classic empty welcome state is supported");
    System.out.println("PASS: IDE Home bypass, alias identity, real-project routing and Dock ownership with the 2026.3 welcome project");
  }
}
