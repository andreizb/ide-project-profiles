package ro.andrei.ideprofiles;

import com.intellij.ide.plugins.*;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.text.VersionComparatorUtil;
import java.util.*;

public final class PluginCatalog {
  public record Entry(String id, String name, String version, String path, boolean bundled, Set<String> requires) {}
  public record Plan(String project, Set<String> technologies, List<String> evidence, boolean conservative,
                     Map<String,String> enabled, Map<String,String> disabled, List<String> warnings) {}
  public final Map<String,Entry> entries = new TreeMap<>();
  public final Set<String> essential = new TreeSet<>();

  public static PluginCatalog read() {
    PluginCatalog result = new PluginCatalog();
    ResolvedPluginSet resolved = PluginManagerCore.getPluginSet().getResolvedPluginSet();
    UnambiguousPluginSet candidates = resolved.getCandidateSet();
    Map<String,PluginMainDescriptor> installed=new TreeMap<>();
    for(PluginMainDescriptor p:PluginManagerCore.getPluginSet().allPlugins) {
      if(!PluginManagerCore.isCompatible(p)) continue;
      String id=p.getPluginId().getIdString();PluginMainDescriptor old=installed.get(id);
      if(old==null||VersionComparatorUtil.compare(p.getVersion(),old.getVersion())>0) installed.put(id,p);
    }
    for(PluginMainDescriptor p:candidates.getPlugins()) installed.put(p.getPluginId().getIdString(),p);
    Resolver resolver=new Resolver(installed.values(),candidates);
    for (PluginId id : resolved.getInitContext().getEssentialPlugins()) result.essential.add(id.getIdString());
    result.essential.add("com.intellij"); result.essential.add("ro.andrei.ideprofiles");
    for (PluginMainDescriptor plugin : installed.values()) {
      Set<String> requires=new TreeSet<>();
      collect(plugin, resolver, requires, Collections.newSetFromMap(new IdentityHashMap<>()));
      String id=plugin.getPluginId().getIdString(); requires.remove(id);
      result.entries.put(id,new Entry(id,plugin.getName(),plugin.getVersion(),plugin.getPluginPath().toString(),plugin.isBundled(),requires));
    }
    return result;
  }
  private static final class Resolver {
    final Map<PluginId,PluginModuleDescriptor> plugins=new HashMap<>();
    final Map<PluginModuleId,ContentModuleDescriptor> modules=new HashMap<>();
    final UnambiguousPluginSet active;
    Resolver(Collection<PluginMainDescriptor> all,UnambiguousPluginSet active) {
      this.active=active;
      for(PluginMainDescriptor p:all) {
        plugins.put(p.getPluginId(),p);
        for(PluginId id:p.getPluginAliases()) plugins.putIfAbsent(id,p);
        for(ContentModuleDescriptor m:p.getContentModules()) {
          modules.put(m.moduleId,m);
          for(PluginId id:m.getPluginAliases()) plugins.putIfAbsent(id,m);
        }
      }
    }
    PluginModuleDescriptor resolvePluginId(PluginId id) {
      PluginModuleDescriptor live=active.resolvePluginId(id);return live!=null?live:plugins.get(id);
    }
    ContentModuleDescriptor resolveContentModuleId(PluginModuleId id) {
      ContentModuleDescriptor live=active.resolveContentModuleId(id);return live!=null?live:modules.get(id);
    }
  }
  private static void collect(IdeaPluginDescriptorImpl d, Resolver all, Set<String> ids, Set<IdeaPluginDescriptorImpl> seen) {
    if (!seen.add(d)) return;
    if (d instanceof PluginMainDescriptor main) {
      ids.add(main.getPluginId().getIdString());
      for (ContentModuleDescriptor m : main.getContentModules())
        if (m.moduleLoadingRule==ModuleLoadingRule.REQUIRED || m.moduleLoadingRule==ModuleLoadingRule.EMBEDDED) collect(m,all,ids,seen);
    } else if (d instanceof ContentModuleDescriptor module) {
      ids.add(module.parent.getPluginId().getIdString());
      collect(module.parent,all,ids,seen);
    }
    for (PluginDependency dep : d.getDependencies()) if (!dep.isOptional()) {
      PluginModuleDescriptor target=all.resolvePluginId(dep.getPluginId());
      if (target!=null) collect(target,all,ids,seen);
    }
    for (PluginId id : d.getModuleDependencies().getPlugins()) {
      PluginModuleDescriptor target=all.resolvePluginId(id); if (target!=null) collect(target,all,ids,seen);
    }
    for (PluginModuleId id : d.getModuleDependencies().getModules()) {
      ContentModuleDescriptor target=all.resolveContentModuleId(id); if (target!=null) collect(target,all,ids,seen);
    }
  }

  public Plan plan(String project, ProjectDetector.Detection detection, Set<String> baselineDisabled, Set<String> extraTags) {
    return plan(project,detection,baselineDisabled,extraTags,Set.of());
  }
  public Plan plan(String project, ProjectDetector.Detection detection, Set<String> baselineDisabled, Set<String> extraTags,Set<String> forcedEnabled) {
    Set<String> tags=new TreeSet<>(detection.tags()); tags.addAll(extraTags);
    boolean conservative=tags.isEmpty() || detection.truncated();
    Map<String,String> enabled=new TreeMap<>(), disabled=new TreeMap<>(); List<String> warnings=new ArrayList<>();
    if (conservative) warnings.add(detection.truncated()?"Inspection was incomplete; retaining technology plugins conservatively.":"No recognised technology yet; retaining technology plugins conservatively.");
    warnings.addAll(detection.scanProblems());
    for (Entry e : entries.values()) {
      if (!baselineDisabled.contains(e.id()) && (forcedEnabled.contains(e.id())||detection.requiredPlugins().contains(e.id())||PluginPolicy.wanted(e.id(),tags,conservative))) {
        Set<String> blocked=new TreeSet<>(e.requires()); blocked.retainAll(baselineDisabled); blocked.removeAll(essential);
        if(blocked.isEmpty()) enabled.put(e.id(),forcedEnabled.contains(e.id())?"enabled manually for this project":detection.requiredPlugins().contains(e.id())?"declared required by this project":PluginPolicy.reason(e.id(),tags));
        else warnings.add(e.name()+" remains off because required plugins were disabled manually: "+String.join(", ",blocked));
      }
    }
    for(String id:detection.requiredPlugins()) if(!entries.containsKey(id)) warnings.add("Project declares an uninstalled required plugin: "+id);
    for (String id : essential) if (entries.containsKey(id)) enabled.put(id,"required by IntelliJ or the profile manager");
    boolean changed;
    do {
      changed=false;
      for (String id : new ArrayList<>(enabled.keySet())) {
        Entry e=entries.get(id); if (e==null) continue;
        for (String dep : e.requires()) {
          if (dep.equals("com.intellij.settingsSync")) throw new IllegalStateException(e.name()+" requires Backup and Sync, which conflicts with plugin isolation");
          if (!enabled.containsKey(dep) && entries.containsKey(dep)) {
            if (baselineDisabled.contains(dep)) warnings.add("Required dependency "+dep+" retained for "+e.name());
            enabled.put(dep,"required by "+e.name()); changed=true;
          }
        }
      }
    } while (changed);
    enabled.remove("com.intellij.settingsSync");
    for (Entry e:entries.values()) if (!enabled.containsKey(e.id())) disabled.put(e.id(),e.id().equals("com.intellij.settingsSync")?"keep project plugin settings independent":baselineDisabled.contains(e.id())?"disabled in the original IntelliJ configuration":"not needed for detected technologies");
    for(String id:baselineDisabled) if(!enabled.containsKey(id)) disabled.putIfAbsent(id,"disabled in the original IntelliJ configuration or manually for this project");
    disabled.put("com.intellij.settingsSync","keep project plugin settings independent");
    return new Plan(project,tags,detection.evidence(),conservative,enabled,disabled,warnings);
  }
}
