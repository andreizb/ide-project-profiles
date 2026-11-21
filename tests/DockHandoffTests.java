import ro.andrei.ideprofiles.DockHandoff;

public class DockHandoffTests {
  static class Presentation implements DockHandoff.Presentation {
    boolean visible=true,available=true;int hides,restores;
    public boolean hide() {hides++;if(available)visible=false;return available;}
    public void restore() {restores++;visible=true;}
  }
  static void check(boolean value,String reason) {if(!value)throw new AssertionError(reason);}
  public static void main(String[] args) {
    var p=new Presentation();var h=new DockHandoff(p);
    try(var ticket=h.begin(false)) {check(p.visible,"An existing project must retain its Dock icon");ticket.succeeded();}
    check(p.hides==0,"No native changes in a project process");
    try(var ticket=h.begin(true)) {check(!p.visible,"Welcome icon leaves before launching the child");ticket.succeeded();}
    check(!p.visible&&h.mayExit(),"Successful handoff leaves only the child visible until the welcome process exits");
    p=new Presentation();h=new DockHandoff(p);
    try(var ticket=h.begin(true)) {check(!p.visible,"Hide on launch");}
    check(p.visible&&!h.mayExit(),"Failed startup restores a usable welcome app instead of exiting");
    var a=h.begin(true);var b=h.begin(true);a.succeeded();a.close();
    check(!h.mayExit(),"One ready project cannot terminate another pending launch");
    b.close();check(p.visible&&!h.mayExit(),"Concurrent failure restores welcome even after another launch succeeded");
    try(var retry=h.begin(true)) {retry.succeeded();}
    check(h.mayExit()&&!p.visible,"A retry can complete normally after a failure");
    p=new Presentation();p.available=false;h=new DockHandoff(p);
    try(var ticket=h.begin(true)) {ticket.succeeded();}
    check(p.visible&&p.restores==0&&h.mayExit(),"Unsupported native policy changes do not prevent project opening");
    System.out.println("PASS: welcome Dock handoff, existing-project visibility, startup failure recovery, concurrent launches and retry");
  }
}
