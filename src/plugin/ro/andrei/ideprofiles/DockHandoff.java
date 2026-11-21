package ro.andrei.ideprofiles;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;

/** Only the empty welcome process leaves the Dock while a project process starts. */
public final class DockHandoff {
  public interface Presentation { boolean hide(); void restore(); }
  private static final Logger LOG=Logger.getInstance(DockHandoff.class);
  private static final DockHandoff CURRENT=new DockHandoff(new MacPresentation());
  private final Presentation presentation;
  private int pending;
  private boolean hidden,failed;

  public DockHandoff(Presentation presentation) {this.presentation=presentation;}
  public static Ticket beginCurrent() {
    boolean eligible=SystemInfo.isMac&&System.getProperty("ideprofiles.projectRoot")==null
        &&!Boolean.getBoolean("ideprofiles.keepHub")&&!WelcomeProjects.hasUserProjects();
    return CURRENT.begin(eligible);
  }
  public static boolean currentMayExit() {return CURRENT.mayExit();}
  public synchronized Ticket begin(boolean eligible) {
    if(!eligible) return new Ticket(false);
    if(pending==0) {
      failed=false;
      if(!hidden) hidden=presentation.hide();
    }
    pending++;
    return new Ticket(true);
  }
  public synchronized boolean mayExit() {return pending==0&&!failed;}
  public final class Ticket implements AutoCloseable {
    private final boolean participating;
    private boolean success,closed;
    private Ticket(boolean participating) {this.participating=participating;}
    public void succeeded() {success=true;}
    @Override public void close() {
      synchronized(DockHandoff.this) {
        if(closed||!participating) return;
        closed=true;failed|=!success;pending--;
        if(pending==0&&failed&&hidden) {
          presentation.restore();hidden=false;
        }
      }
    }
  }
  private static final class MacPresentation implements Presentation {
    @Override public boolean hide() {
      boolean[] changed={false};
      try {
        Foundation.executeOnMainThread(true,true,()->{
          ID app=Foundation.invoke("NSApplication","sharedApplication");
          if(Foundation.invoke(app,"activationPolicy").intValue()!=0) return;
          if(Foundation.invoke(app,"setActivationPolicy:",1L).booleanValue()
              &&Foundation.invoke(app,"activationPolicy").intValue()==1) {
            changed[0]=true;
            LOG.info("Welcome process left the Dock before project launch; pid="+ProcessHandle.current().pid());
          }
        });
      } catch(Exception|LinkageError e) {LOG.warn("Cannot hide the welcome Dock icon; continuing normal project launch",e);}
      return changed[0];
    }
    @Override public void restore() {
      try {
        Foundation.executeOnMainThread(true,true,()->{
          ID app=Foundation.invoke("NSApplication","sharedApplication");
          if(Foundation.invoke(app,"setActivationPolicy:",0L).booleanValue()
              &&Foundation.invoke(app,"activationPolicy").intValue()==0) {
            Foundation.invoke(app,"activateIgnoringOtherApps:",true);
            LOG.info("Welcome process restored after incomplete project launch; pid="+ProcessHandle.current().pid());
          } else LOG.warn("macOS did not restore the welcome process activation policy");
        });
      } catch(Exception|LinkageError e) {LOG.warn("Cannot restore welcome Dock presentation",e);}
    }
  }
}
