package ro.andrei.ideprofiles;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider;
import java.nio.file.*;
import java.io.IOException;

/** IntelliJ 2026.3's Home is an IDE-owned project, not a user plugin profile. */
public final class WelcomeProjects {
  public static boolean isWelcomeOpening(Path path) {
    return samePath(path,WelcomeScreenProjectProvider.Companion.getWelcomeScreenProjectPath());
  }
  public static boolean samePath(Path path,Path welcome) {
    return welcome!=null&&canonical(path).equals(canonical(welcome));
  }
  private static Path canonical(Path path) {
    try {return path.toRealPath();}catch(IOException e) {return path.toAbsolutePath().normalize();}
  }
  public static boolean hasUserProjects() {return hasUserProjects(ProjectManager.getInstance().getOpenProjects());}
  public static boolean hasUserProjects(Project[] projects) {
    for(Project project:projects) if(!project.isWelcomeProject()) return true;
    return false;
  }
}
