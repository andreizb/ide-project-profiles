package ro.andrei.ideprofiles;

import com.google.gson.*;
import com.intellij.openapi.application.PathManager;
import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

public final class ProfileFiles {
  public static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
  public static Path originConfig() { return Path.of(System.getProperty("ideprofiles.origin.config",PathManager.getConfigPath())); }
  public static Path originPlugins() { return Path.of(System.getProperty("ideprofiles.origin.plugins",PathManager.getPluginsPath())); }
  public static Path originVmOptions() {
    return Path.of(System.getProperty("ideprofiles.origin.vmoptions",System.getProperty("jb.vmOptionsFile",originConfig().resolve("idea.vmoptions").toString())));
  }
  public static Path storage() {
    return Path.of(System.getProperty("ideprofiles.storageRoot",originConfig().resolveSibling("AutomaticProjectPlugins").toString()));
  }
  public static Path canonicalRoot(Path original) throws IOException {
    Path p=original.toRealPath();
    if (Files.isRegularFile(p)) p=p.getParent();
    if (p.getFileName()!=null && p.getFileName().toString().equals(".idea")) p=p.getParent();
    return p;
  }
  public static String hash(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    catch(Exception e) { throw new IllegalStateException(e); }
  }
  public static Path directory(Path project) { return storage().resolve("262.10968").resolve(hash(project.toString()).substring(0,24)); }
  public static void privateDirectory(Path p) throws IOException {
    Files.createDirectories(p);
    Files.setPosixFilePermissions(p,PosixFilePermissions.fromString("rwx------"));
  }
  public static void write(Path p, String text) throws IOException {
    Files.createDirectories(p.getParent()); Path temp=Files.createTempFile(p.getParent(),".profile-",".tmp");
    try { Files.writeString(temp,text); Files.move(temp,p,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
    finally { Files.deleteIfExists(temp); }
  }
  public static void json(Path p,Object value) throws IOException { write(p,JSON.toJson(value)+"\n"); }
  public static Set<String> disabledAtOrigin() throws IOException {
    Path p=originConfig().resolve("disabled_plugins.txt"); Set<String> result=new TreeSet<>();
    if(Files.isRegularFile(p)) for(String l:Files.readAllLines(p)) if(!l.isBlank()) result.add(l.trim());
    return result;
  }
  public static Set<String> extraTags(Path profile) throws IOException {
    Path p=profile.resolve("extra-technologies.txt");
    if (!Files.exists(p)) return Set.of();
    Set<String> result=new TreeSet<>(Files.readAllLines(p)); result.removeIf(String::isBlank); return result;
  }
  public record Overrides(Set<String> enabled,Set<String> disabled) {}
  public static Overrides manualOverrides(Path profile) throws IOException {
    Set<String> on=new TreeSet<>(),off=new TreeSet<>();
    Path stored=profile.resolve("manual-overrides.json");
    if(Files.exists(stored)) {
      JsonObject o=JsonParser.parseString(Files.readString(stored)).getAsJsonObject();
      o.getAsJsonArray("enabled").forEach(e->on.add(e.getAsString()));
      o.getAsJsonArray("disabled").forEach(e->off.add(e.getAsString()));
    }
    Path plan=profile.resolve("plan.json"),current=profile.resolve("config/disabled_plugins.txt");
    if(Files.exists(plan)&&Files.exists(current)) {
      Set<String> previous=new TreeSet<>(JsonParser.parseString(Files.readString(plan)).getAsJsonObject().getAsJsonObject("disabled").keySet());
      Set<String> actual=new TreeSet<>(Files.readAllLines(current)); actual.removeIf(String::isBlank);
      Set<String> newlyOff=new TreeSet<>(actual); newlyOff.removeAll(previous);
      Set<String> newlyOn=new TreeSet<>(previous); newlyOn.removeAll(actual);
      on.addAll(newlyOn); on.removeAll(newlyOff);off.addAll(newlyOff);off.removeAll(newlyOn);
    }
    on.remove("com.intellij.settingsSync"); off.add("com.intellij.settingsSync");
    Overrides result=new Overrides(on,off); json(stored,result); return result;
  }
  public static void cloneTree(Path source,Path target) throws IOException,InterruptedException {
    if(Files.isSymbolicLink(source)) throw new IOException("Shared symlink is not an isolated snapshot: "+source);
    Files.createDirectories(target.getParent());
    Process p=new ProcessBuilder("/bin/cp","-cRp",source.toString(),target.toString()).redirectErrorStream(true).start();
    String output=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
    if(p.waitFor()!=0) throw new IOException("Cannot clone "+source.getFileName()+": "+output);
  }
  public static String signature(Path directory) throws IOException {
    StringBuilder b=new StringBuilder();
    try(Stream<Path> stream=Files.walk(directory)) {
      for(Path p:stream.filter(Files::isRegularFile).sorted().toList()) {
        BasicFileAttributes a=Files.readAttributes(p,BasicFileAttributes.class);
        b.append(directory.relativize(p)).append(':').append(a.size()).append(':').append(a.lastModifiedTime().toMillis()).append('\n');
      }
    }
    return hash(b.toString());
  }
  public static void copySettings(Path config) throws IOException,InterruptedException {
    if(Files.exists(config.resolve(".profile-initialized"))) return;
    privateDirectory(config);
    for(String n:List.of("options","colors","codestyles","keymaps","fileTemplates","inspection","dictionaries","templates","tools","ssl")) {
      Path s=originConfig().resolve(n),d=config.resolve(n); if(Files.exists(s)&&!Files.exists(d)) cloneTree(s,d);
    }
    try(Stream<Path> files=Files.list(originConfig())) {
      for(Path s:files.filter(Files::isRegularFile).toList()) {
        String n=s.getFileName().toString();
        if(n.endsWith(".key")||n.endsWith(".license")||Set.of("app-internal-state.db","early-access-registry.txt","disabled_update.txt","splash-subscription-mode.txt").contains(n))
          Files.copy(s,config.resolve(n),StandardCopyOption.REPLACE_EXISTING);
      }
    }
    // No automatic restoration of another project's windows in a bound process.
    Path general=config.resolve("options/ide.general.xml");
    String xml=Files.exists(general)?Files.readString(general):"<application><component name=\"GeneralSettings\"></component></application>";
    xml=xml.replaceAll("<option name=\"reopenLastProject\"[^>]*/>","");
    if(xml.contains("name=\"GeneralSettings\"")) xml=xml.replaceFirst("(<component name=\"GeneralSettings\"[^>]*>)","$1<option name=\"reopenLastProject\" value=\"false\" />");
    else xml=xml.replace("</application>","<component name=\"GeneralSettings\"><option name=\"reopenLastProject\" value=\"false\" /></component></application>");
    write(general,xml); write(config.resolve(".profile-initialized"),Instant.now().toString());
  }
  public static void snapshotPlugins(Path profile) throws IOException,InterruptedException {
    Path target=profile.resolve("plugins"); privateDirectory(target);
    Path signatures=profile.resolve("snapshots.json");
    JsonObject previous=Files.exists(signatures)?JsonParser.parseString(Files.readString(signatures)).getAsJsonObject():new JsonObject();
    JsonObject next=previous.deepCopy(); Set<String> originNames=new HashSet<>();
    try(Stream<Path> entries=Files.list(originPlugins())) {
      for(Path source:entries.filter(p->!p.getFileName().toString().startsWith(".")).toList()) {
        if(!Files.isDirectory(source)&&!source.toString().endsWith(".jar")) continue;
        String name=source.getFileName().toString(),sig=signature(source); Path dest=target.resolve(name);
        originNames.add(name);
        if(Files.exists(dest)) {
          if(previous.has(name)&&previous.get(name).getAsString().equals(sig)) continue;
          // Never overwrite a plugin installed/updated locally in this project.
          if(!previous.has(name)||!signature(dest).equals(previous.get(name).getAsString())) continue;
          Path old=profile.resolve("plugin-backups").resolve(Instant.now().toEpochMilli()+"-"+name);
          Files.createDirectories(old.getParent()); Files.move(dest,old);
        }
        cloneTree(source,dest); next.addProperty(name,sig);
      }
    }
    // Retire only our unchanged snapshots when a master plugin is removed or
    // becomes bundled by an IDE upgrade. Keep locally installed/modified copies.
    for(String name:previous.keySet()) if(!originNames.contains(name)) {
      Path dest=target.resolve(name);
      if(Files.exists(dest)&&signature(dest).equals(previous.get(name).getAsString())) {
        Path old=profile.resolve("plugin-backups").resolve(Instant.now().toEpochMilli()+"-"+name);
        Files.createDirectories(old.getParent());Files.move(dest,old);next.remove(name);
      } else if(!Files.exists(dest)) next.remove(name);
    }
    json(signatures,next);
  }
  public static boolean running(Path profile) {
    try {
      JsonObject r=JsonParser.parseString(Files.readString(profile.resolve("runtime.json"))).getAsJsonObject();
      ProcessHandle process=ProcessHandle.of(r.get("pid").getAsLong()).orElse(null);
      return process!=null&&process.isAlive()&&process.info().startInstant().map(t->t.toString().equals(r.get("processStart").getAsString())).orElse(false);
    } catch(Exception ignored) { return false; }
  }
  public static Path prepare(Path root,PluginCatalog.Plan plan) throws Exception {
    Path profile=directory(root); privateDirectory(profile);
    if(running(profile)) return profile;
    copySettings(profile.resolve("config")); snapshotPlugins(profile);
    for(String n:List.of("system","log")) privateDirectory(profile.resolve(n));
    write(profile.resolve("config/disabled_plugins.txt"),String.join("\n",plan.disabled().keySet())+"\n");
    json(profile.resolve("plan.json"),plan);
    Properties props=new Properties();
    props.setProperty("idea.config.path",profile.resolve("config").toString());
    props.setProperty("idea.system.path",profile.resolve("system").toString());
    props.setProperty("idea.plugins.path",profile.resolve("plugins").toString());
    props.setProperty("idea.log.path",profile.resolve("log").toString());
    props.setProperty("ideprofiles.projectRoot",root.toString());
    props.setProperty("ideprofiles.storageRoot",storage().toString());
    props.setProperty("ideprofiles.origin.config",originConfig().toString());
    props.setProperty("ideprofiles.origin.plugins",originPlugins().toString());
    props.setProperty("ideprofiles.origin.vmoptions",originVmOptions().toString());
    String agent=System.getProperty("ideprofiles.agent.path");
    if(agent==null||!Files.isRegularFile(Path.of(agent))) throw new IOException("Profile agent path is missing");
    props.setProperty("ideprofiles.agent.path",agent);
    StringWriter s=new StringWriter(); props.store(s,"Automatic Project Plugins: private configuration"); write(profile.resolve("idea.properties"),s.toString());
    List<String> vm=new ArrayList<>();
    Path custom=originVmOptions(); if(Files.exists(custom)) vm.addAll(Files.readAllLines(custom));
    vm.removeIf(l->l.startsWith("-javaagent:")&&l.contains("profile-agent.jar")||l.startsWith("-Dideprofiles.")||l.startsWith("-Didea.config.path=")||l.startsWith("-Didea.system.path=")||l.startsWith("-Didea.plugins.path=")||l.startsWith("-Didea.log.path=")||l.startsWith("-Dtoolbox.")||l.startsWith("-Dide.managed.by.toolbox="));
    vm.add("-javaagent:"+agent); vm.add("-Dideprofiles.agent.path="+agent);
    write(profile.resolve("idea.vmoptions"),String.join("\n",vm)+"\n");
    return profile;
  }
  public static void remember(Path root) throws IOException {
    privateDirectory(storage());
    try(FileChannel ch=FileChannel.open(storage().resolve("recent.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);FileLock lock=ch.lock()) {
      Path f=storage().resolve("recent.json"); JsonObject r=Files.exists(f)?JsonParser.parseString(Files.readString(f)).getAsJsonObject():new JsonObject();
      r.addProperty(root.toString(),System.currentTimeMillis()); json(f,r);
    }
  }
}
