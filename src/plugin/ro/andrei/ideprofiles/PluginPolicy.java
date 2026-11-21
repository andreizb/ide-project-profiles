package ro.andrei.ideprofiles;

import java.util.*;

/** Explicit, reviewable classifications. Unknown plugin IDs remain global. */
public final class PluginPolicy {
  private static final Map<String, Set<String>> GROUPS = new HashMap<>();
  static {
    group("python", "PythonCore", "Pythonid");
    group("notebook", "intellij.jupyter", "com.intellij.notebooks.core");
    group("go", "org.jetbrains.plugins.go"); group("rust", "com.jetbrains.rust");
    group("php", "com.jetbrains.php"); group("ruby", "org.jetbrains.plugins.ruby");
    group("dart", "Dart"); group("flutter", "io.flutter");
    group("android", "org.jetbrains.android", "org.jetbrains.idea.gradle.dsl", "com.android.tools.gradle.dcl");
    group("compose", "com.intellij.compose");
    group("kotlin", "org.jetbrains.kotlin");
    group("groovy,gradle", "org.intellij.groovy", "org.intellij.groovy.live.templates", "com.intellij.groovy.scripting");
    group("gradle", "com.intellij.gradle", "org.jetbrains.plugins.gradle");
    group("maven", "org.jetbrains.idea.maven", "intellij.java.aetherDependencyResolver.plugin");
    group("java", "JUnit", "Coverage", "ByteCodeViewer", "org.jetbrains.java.decompiler", "com.intellij.java-i18n", "com.intellij.debugger.collections.visualizer", "org.jetbrains.idea.eclipse");
    group("lombok", "Lombook Plugin"); group("testng", "TestNG-J"); group("javafx", "org.jetbrains.plugins.javaFX");
    group("spring", "com.intellij.spring", "com.intellij.aop");
    group("spring-boot", "com.intellij.spring.boot", "com.intellij.spring.boot.initializr");
    group("spring-data", "com.intellij.spring.data"); group("spring-web", "com.intellij.spring.mvc");
    for (String x : List.of("cloud","security","integration","messaging","modulith")) group("spring-"+x,"com.intellij.spring."+x);
    group("jpa", "com.intellij.hibernate", "com.intellij.javaee.jpa", "com.intellij.jpa.jpb.model", "com.intellij.persistence", "com.intellij.javaee.reverseEngineering");
    group("jakarta", "com.intellij.javaee", "com.intellij.javaee.app.servers.integration", "com.intellij.javaee.el", "com.intellij.javaee.extensions", "com.intellij.javaee.jakarta.data", "com.intellij.javaee.web", "com.intellij.beanValidation", "com.intellij.cdi", "com.jetbrains.restWebServices", "JBoss", "Tomcat");
    group("database,jpa,flyway,liquibase", "com.intellij.database");
    for (String x : List.of("flyway","liquibase","micronaut","quarkus","thymeleaf","freemarker","velocity")) group(x,"com.intellij."+x);
    group("ktor", "intellij.ktor");
    group("spring,micronaut,quarkus,ktor,jakarta", "com.intellij.microservices.jvm");
    group("web,php,ruby", "JavaScript", "JavaScriptDebugger", "JSIntentionPowerPack");
    group("web,html,php,ruby", "HtmlTools", "com.intellij.css", "com.intellij.plugins.webcomponents");
    group("node,web", "NodeJS", "org.jetbrains.plugins.node-remote-interpreter");
    group("react", "com.intellij.react", "com.deadlock.scsyntax"); group("vue", "org.jetbrains.plugins.vue");
    group("angular", "AngularJS"); group("nextjs", "intellij.nextjs"); group("vite", "intellij.vitejs");
    group("webpack", "intellij.webpack"); group("tailwindcss", "com.intellij.tailwindcss");
    group("prettier", "intellij.prettierJS"); group("eslint", "intellij.javascript.eslint"); group("stylelint", "com.intellij.stylelint");
    group("postcss", "org.intellij.plugins.postcss"); group("sass", "org.jetbrains.plugins.sass"); group("less", "org.jetbrains.plugins.less");
    group("karma", "Karma"); group("jshint", "jshint"); group("tslint", "tslint");
    group("graphql", "com.intellij.lang.jsgraphql"); group("openapi", "com.intellij.swagger"); group("protobuf", "idea.plugin.protoeditor");
    group("docker,devcontainers", "Docker"); group("devcontainers", "org.jetbrains.plugins.docker.gateway"); group("kubernetes", "com.intellij.kubernetes");
    group("rust,cpp", "com.intellij.nativeDebug");
  }
  private static void group(String tags, String... ids) { for (String id : ids) GROUPS.put(id, Set.of(tags.split(","))); }
  public static boolean wanted(String id, Set<String> tags, boolean conservative) {
    if (id.equals("com.intellij.settingsSync")) return false;
    Set<String> group = GROUPS.get(id);
    return conservative || group == null || !Collections.disjoint(group, tags);
  }
  public static String reason(String id, Set<String> tags) {
    Set<String> g=GROUPS.get(id); return g==null ? "general tool or unclassified plugin" : "technology: "+String.join(", ",g);
  }
  public static Set<String> knownTags() { Set<String> result=new TreeSet<>(); GROUPS.values().forEach(result::addAll); return result; }
}
