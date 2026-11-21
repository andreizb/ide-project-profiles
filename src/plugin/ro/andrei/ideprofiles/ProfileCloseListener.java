package ro.andrei.ideprofiles;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.project.*;

/** Empty managed processes are released so reopening recalculates the profile. */
public final class ProfileCloseListener implements ProjectManagerListener {
  @Override public void projectClosed(Project project) {
    if(System.getProperty("ideprofiles.projectRoot")==null) return;
    ApplicationManager.getApplication().invokeLater(()->{
      if(ApplicationManager.getApplication().isDisposed()) return;
      if(!WelcomeProjects.hasUserProjects())
        ((ApplicationEx)ApplicationManager.getApplication()).exit();
    });
  }
}
