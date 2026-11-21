package ro.andrei.ideprofiles;

import com.google.gson.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.*;

public final class ProfileAction extends AnAction implements DumbAware {
  @Override public void actionPerformed(AnActionEvent event) {
    if(event.getProject()==null) {
      JOptionPane.showMessageDialog(null,"Open a project normally. Its plugins will be selected automatically.","Project Plugin Profile",JOptionPane.INFORMATION_MESSAGE); return;
    }
    try {
      Path root=ProfileFiles.canonicalRoot(Path.of(event.getProject().getBasePath()));
      Path profile=ProfileFiles.directory(root),planFile=profile.resolve("plan.json");
      String summary="This project has no automatic profile.";
      if(Files.exists(planFile)) {
        JsonObject plan=JsonParser.parseString(Files.readString(planFile)).getAsJsonObject();
        Path catalogFile=profile.resolve("catalog.json");
        JsonObject catalog=Files.exists(catalogFile)?JsonParser.parseString(Files.readString(catalogFile)).getAsJsonObject():new JsonObject();
        StringBuilder report=new StringBuilder("Detected technologies: ");
        report.append(String.join(", ",plan.getAsJsonArray("technologies").asList().stream().map(JsonElement::getAsString).toList()));
        for(String state:java.util.List.of("enabled","disabled")) {
          report.append("\n\n").append(state.equals("enabled")?"Enabled":"Disabled").append(" ( ").append(plan.getAsJsonObject(state).size()).append(" )\n");
          for(var e:plan.getAsJsonObject(state).entrySet()) {
            String name=catalog.has(e.getKey())?catalog.getAsJsonObject(e.getKey()).get("name").getAsString():e.getKey();
            report.append(name).append(" — ").append(e.getValue().getAsString()).append('\n');
          }
        }
        for(JsonElement warning:plan.getAsJsonArray("warnings")) report.append("\n").append(warning.getAsString());
        summary=report.toString();
      }
      JTextArea text=new JTextArea(summary,24,90); text.setEditable(false); text.setCaretPosition(0);
      JPanel panel=new JPanel(new BorderLayout(0,10)); panel.add(new JLabel("Extra technologies apply after you close and reopen this project. Normal plugin settings remain available."),BorderLayout.NORTH);
      panel.add(new JScrollPane(text),BorderLayout.CENTER);
      JPanel choices=new JPanel(new GridLayout(0,5)); Map<String,JCheckBox> boxes=new TreeMap<>();
      Set<String> extras=ProfileFiles.extraTags(profile);
      for(String tag:PluginPolicy.knownTags()) { JCheckBox box=new JCheckBox(tag,extras.contains(tag)); boxes.put(tag,box); choices.add(box); }
      panel.add(choices,BorderLayout.SOUTH);
      if(JOptionPane.showConfirmDialog(null,panel,"Project Plugin Profile",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION) {
        var selected=boxes.entrySet().stream().filter(e->e.getValue().isSelected()).map(Map.Entry::getKey).toList();
        ProfileFiles.write(profile.resolve("extra-technologies.txt"),String.join("\n",selected)+"\n");
      }
    } catch(Exception e) { ProfileRouter.error("Could not display the profile: "+e.getMessage()); }
  }
}
