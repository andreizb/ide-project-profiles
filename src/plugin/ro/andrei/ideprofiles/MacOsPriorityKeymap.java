package ro.andrei.ideprofiles;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.*;
import com.intellij.openapi.keymap.*;
import com.intellij.openapi.keymap.impl.*;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.diagnostic.LoadingState;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** An opt-in IDE keymap. macOS preferences and native shortcut assignments are read only. */
public final class MacOsPriorityKeymap implements AppLifecycleListener {
  @Override public void appStarted() {schedule();}
  private static final String NAME="macOS Priority";
  public static void schedule() {
    if(!SystemInfo.isMac||!Files.isRegularFile(ProfileFiles.originConfig().resolve("macos-priority.enabled"))) return;
    ApplicationManager.getApplication().invokeLater(MacOsPriorityKeymap::apply);
  }
  private static void apply() {
    try {
      if(!LoadingState.COMPONENTS_LOADED.isOccurred()) throw new IllegalStateException("IDE services are not ready");
      KeymapManagerImpl manager=(KeymapManagerImpl)KeymapManager.getInstance();
      KeymapImpl base=(KeymapImpl)manager.getKeymap("macOS System Shortcuts");
      if(base==null) throw new IllegalStateException("The macOS System Shortcuts keymap is unavailable");
      KeymapImpl keymap=(KeymapImpl)manager.getKeymap(NAME);
      boolean created=keymap==null;
      if(created) keymap=base.deriveKeymap(NAME);
      // IntelliJ implements the native Minimize menu command itself. Keeping Cmd-M
      // on that action preserves macOS behavior; it is not a competing IDE command.
      if(keymap.getShortcuts("MinimizeCurrentWindow").length==0) keymap.clearOwnActionsId("MinimizeCurrentWindow");
      SystemShortcuts system=SystemShortcuts.getInstance();
      var reserved=system.createKeystroke2SysShortcutMap();
      if(reserved.isEmpty()) throw new IllegalStateException("macOS did not report its reserved shortcuts");
      List<Map<String,String>> removed=new ArrayList<>();
      for(String id:keymap.getActionIds()) {
        for(Shortcut shortcut:keymap.getShortcuts(id)) if(shortcut instanceof KeyboardShortcut keyboard) {
          if(nativeEquivalent(id,keyboard)) continue;
          String reason=reserved.get(keyboard.getFirstKeyStroke());
          if(reason==null&&keyboard.getSecondKeyStroke()!=null) reason=reserved.get(keyboard.getSecondKeyStroke());
          if(reason!=null) {
            keymap.removeShortcutFromSettings(id,keyboard);
            removed.add(Map.of("action",id,"shortcut",keyboard.toString(),"reservedFor",reason));
          }
        }
      }
      if(created) manager.getSchemeManager().addScheme(keymap);
      manager.setActiveKeymap(keymap);
      PropertiesComponent properties=PropertiesComponent.getInstance();
      List<String> muted=properties.getList("muted.system.shortcut.conflicts.actions");
      Set<String> equivalents=new LinkedHashSet<>(muted==null?List.of():muted);
      equivalents.add("MinimizeCurrentWindow");
      properties.setList("muted.system.shortcut.conflicts.actions",equivalents);
      system.updateKeymapConflicts(keymap);
      List<String> remaining=new ArrayList<>();
      for(String id:keymap.getActionIds()) for(Shortcut shortcut:keymap.getShortcuts(id))
        if(shortcut instanceof KeyboardShortcut keyboard&&!nativeEquivalent(id,keyboard)&&(reserved.containsKey(keyboard.getFirstKeyStroke())||(keyboard.getSecondKeyStroke()!=null&&reserved.containsKey(keyboard.getSecondKeyStroke()))))
          remaining.add(id+": "+keyboard);
      Map<String,Object> report=new LinkedHashMap<>();
      report.put("updatedAt",Instant.now().toString());report.put("pid",ProcessHandle.current().pid());
      report.put("componentsLoaded",LoadingState.COMPONENTS_LOADED.isOccurred());
      report.put("keymap",keymap.getName());report.put("parent",base.getName());
      report.put("systemShortcutsRead",reserved.size());report.put("removedConflicts",removed);report.put("remainingConflicts",remaining);
      report.put("nativeEquivalentPreserved","Command-M: MinimizeCurrentWindow performs the standard macOS minimize action");
      ProfileFiles.json(Path.of(PathManager.getConfigPath()).resolve("macos-priority-report.json"),report);
      if(!remaining.isEmpty()) ProfileRouter.error("Some system shortcut conflicts remain. See the macOS Priority keymap report.");
    } catch(Exception e) {ProfileRouter.error("Could not apply macOS shortcut priority: "+e.getMessage());}
  }
  private static boolean nativeEquivalent(String id,KeyboardShortcut shortcut) {
    return id.equals("MinimizeCurrentWindow")&&shortcut.getSecondKeyStroke()==null&&
        shortcut.getFirstKeyStroke().equals(KeyStroke.getKeyStroke(KeyEvent.VK_M,InputEvent.META_DOWN_MASK));
  }
}
