import ro.andrei.ideprofiles.*;
import java.nio.file.*;
import java.util.*;

public class StorageTests {
  public static void main(String[] args)throws Exception {
    Path base=Files.createTempDirectory("profile-storage-tests-"); Path a=base.resolve("a"),b=base.resolve("b");
    ProfileFiles.write(a.resolve("config/disabled_plugins.txt"),"auto.off\nmanual.off\n");
    ProfileFiles.json(a.resolve("plan.json"),Map.of("disabled",Map.of("auto.off","automatic")));
    ProfileFiles.write(b.resolve("config/disabled_plugins.txt"),"other.project\n");
    String other=Files.readString(b.resolve("config/disabled_plugins.txt"));
    var first=ProfileFiles.manualOverrides(a);
    if(!first.disabled().contains("manual.off"))throw new AssertionError("Manual disable not retained");
    ProfileFiles.write(a.resolve("config/disabled_plugins.txt"),"manual.off\n");
    var second=ProfileFiles.manualOverrides(a);
    if(!second.enabled().contains("auto.off"))throw new AssertionError("Manual enable not retained");
    if(!other.equals(Files.readString(b.resolve("config/disabled_plugins.txt"))))throw new AssertionError("Other project modified");
    Path source=base.resolve("source");Files.createDirectories(source);ProfileFiles.write(source.resolve("plugin.txt"),"original");
    Path copy=base.resolve("copy");ProfileFiles.cloneTree(source,copy);Files.writeString(copy.resolve("plugin.txt"),"changed");
    if(!Files.readString(source.resolve("plugin.txt")).equals("original"))throw new AssertionError("Plugin files are not independent");
    Path real=base.resolve("project");Files.createDirectory(real);Path alias=base.resolve("alias");Files.createSymbolicLink(alias,real);
    if(!ProfileFiles.canonicalRoot(real).equals(ProfileFiles.canonicalRoot(alias)))throw new AssertionError("Alias creates a second project identity");
    Path origin=base.resolve("origin-plugins"),profile=base.resolve("snapshot-profile");
    Files.createDirectories(origin);System.setProperty("ideprofiles.origin.plugins",origin.toString());
    ProfileFiles.write(origin.resolve("now-bundled/plugin.txt"),"managed original");
    ProfileFiles.write(origin.resolve("locally-updated/plugin.txt"),"managed original");
    ProfileFiles.snapshotPlugins(profile);
    Files.writeString(profile.resolve("plugins/locally-updated/plugin.txt"),"user's local update");
    Files.move(origin.resolve("now-bundled"),base.resolve("retired-bundled"));
    Files.move(origin.resolve("locally-updated"),base.resolve("retired-local"));
    ProfileFiles.snapshotPlugins(profile);
    if(Files.exists(profile.resolve("plugins/now-bundled")))throw new AssertionError("Obsolete managed plugin still shadows its bundled replacement");
    try(var backups=Files.list(profile.resolve("plugin-backups"))) {
      if(backups.noneMatch(p->p.getFileName().toString().endsWith("-now-bundled")))throw new AssertionError("Removed snapshot was not retained in backup");
    }
    if(!Files.readString(profile.resolve("plugins/locally-updated/plugin.txt")).equals("user's local update"))throw new AssertionError("A project-local plugin update was overwritten");
    System.clearProperty("ideprofiles.origin.plugins");
    System.out.println("PASS: local overrides, isolated snapshots, obsolete-plugin retirement with backups, local updates preserved and canonical identity");
  }
}
