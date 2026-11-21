package ro.andrei.ideprofiles;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Consumer;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;

/** Static inspection only: never executes a project's build scripts. */
public final class ProjectDetector {
  public record Detection(Set<String> tags, List<String> evidence, boolean truncated, Set<String> requiredPlugins,
                          List<String> scanProblems) {
    public Detection(Set<String> tags,List<String> evidence,boolean truncated,Set<String> requiredPlugins) {
      this(tags,evidence,truncated,requiredPlugins,List.of());
    }
  }
  private static final Set<String> SKIP = Set.of(".git", ".hg", ".svn", ".idea", "node_modules", "vendor",
      ".venv", "venv", "env", "__pycache__", "target", "build", "dist", "out", ".gradle", ".next", ".dart_tool",
      "Pods", ".terraform", ".mypy_cache", ".pytest_cache", ".cache");

  public static Detection detect(Path root) throws IOException {
    Set<String> tags = new TreeSet<>(),required=new TreeSet<>(); List<String> evidence = new ArrayList<>();
    int[] files = {0}; boolean[] truncated = {false}; List<String> problems=new ArrayList<>();
    SimpleFileVisitor<Path> visitor = new SimpleFileVisitor<>() {
      private void incomplete(String message) {
        truncated[0]=true;
        if(problems.size()<10) problems.add(message);
      }
      private void add(String tag, Path p) {
        if (tags.add(tag)) evidence.add(tag + ": " + root.relativize(p));
      }
      @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes a) {
        if (!dir.equals(root) && SKIP.contains(dir.getFileName().toString())) return FileVisitResult.SKIP_SUBTREE;
        return FileVisitResult.CONTINUE;
      }
      @Override public FileVisitResult visitFile(Path p, BasicFileAttributes a) throws IOException {
        if (++files[0] > 30000) { incomplete("File limit reached at "+root.relativize(p)); return FileVisitResult.TERMINATE; }
        if (!a.isRegularFile()) return FileVisitResult.CONTINUE;
        String n = p.getFileName().toString(), lower = n.toLowerCase(Locale.ROOT);
        if (n.endsWith(".java")) add("java", p);
        if (n.endsWith(".cpp") || n.endsWith(".cc") || n.endsWith(".c") || n.equals("CMakeLists.txt")) add("cpp",p);
        if (n.endsWith(".kt") || n.endsWith(".kts")) add("kotlin", p);
        if (n.endsWith(".groovy")) add("groovy", p);
        if (n.endsWith(".py") || Set.of("pyproject.toml","requirements.txt","Pipfile","setup.py","environment.yml").contains(n)) add("python",p);
        if (n.endsWith(".ipynb")) { add("python",p); add("notebook",p); }
        if (n.equals("go.mod") || n.equals("go.work") || n.endsWith(".go")) add("go",p);
        if (n.equals("Cargo.toml") || n.endsWith(".rs")) add("rust",p);
        if (n.equals("composer.json") || n.endsWith(".php")) add("php",p);
        if (n.equals("Gemfile") || n.endsWith(".rb") || n.endsWith(".gemspec")) add("ruby",p);
        if (n.endsWith(".dart") || n.equals("pubspec.yaml")) add("dart",p);
        if (n.endsWith(".js") || n.endsWith(".ts") || n.endsWith(".jsx") || n.endsWith(".tsx") || n.equals("package.json")) add("web",p);
        if (n.endsWith(".jsx") || n.endsWith(".tsx")) add("react",p);
        if (n.endsWith(".vue")) { add("vue",p); add("web",p); }
        if (n.endsWith(".html") || n.endsWith(".css")) add("html",p);
        if (n.endsWith(".scss") || n.endsWith(".sass")) add("sass",p);
        if (n.endsWith(".less")) add("less",p);
        if (n.endsWith(".sql")) add("database",p);
        if (n.endsWith(".graphql") || n.endsWith(".gql")) add("graphql",p);
        if (n.endsWith(".proto")) add("protobuf",p);
        if(n.endsWith(".java")||n.endsWith(".kt")||n.endsWith(".groovy")) {
          try(var input=Files.newInputStream(p)) {
            frameworks(new String(input.readNBytes(32768),StandardCharsets.UTF_8).toLowerCase(Locale.ROOT),tag->add(tag,p));
          } catch(IOException e) { incomplete("Cannot read source "+root.relativize(p)); }
        }
        if (n.equals("AndroidManifest.xml")) add("android",p);
        if (n.equals("Dockerfile") || n.startsWith("Dockerfile.") || lower.matches("(docker-)?compose.*\\.ya?ml")) add("docker",p);
        if (n.equals("devcontainer.json")) { add("devcontainers",p); add("docker",p); }
        boolean build = n.equals("pom.xml") || n.endsWith(".gradle") || n.endsWith(".gradle.kts") || n.equals("libs.versions.toml");
        if (n.equals("pom.xml")) { add("maven",p); add("java",p); }
        if (n.endsWith(".gradle") || n.endsWith(".gradle.kts")) add("gradle",p);
        boolean config = build || Set.of("package.json","composer.json","Gemfile","pubspec.yaml","pyproject.toml","requirements.txt").contains(n)
            || lower.endsWith(".yaml") || lower.endsWith(".yml") || lower.endsWith(".xml") || lower.endsWith(".iml");
        if (config && a.size() <= 512000) {
          String text;
          try { text = new String(Files.readAllBytes(p),StandardCharsets.UTF_8).toLowerCase(Locale.ROOT); }
          catch(IOException e) { incomplete("Cannot read configuration "+root.relativize(p)); return FileVisitResult.CONTINUE; }
          frameworks(text,tag->add(tag,p));
          if (build) {
            if (text.contains("org.springframework") || text.contains("spring-boot") || text.contains("springframework")) add("spring",p);
            if (text.contains("spring-boot")) add("spring-boot",p);
            if (text.contains("spring-data") || text.contains("starter-data")) add("spring-data",p);
            if (text.contains("hibernate") || text.contains("persistence-api") || text.contains("starter-data-jpa")) { add("jpa",p); add("database",p); }
            for (String f : List.of("security","cloud","integration","modulith")) if (text.contains("spring-"+f) || text.contains("starter-"+f)) add("spring-"+f,p);
            if (text.contains("spring-web") || text.contains("starter-web")) add("spring-web",p);
            if (text.contains("spring-kafka") || text.contains("spring-amqp") || text.contains("spring-messaging")) add("spring-messaging",p);
            if (text.contains("com.android.application") || text.contains("com.android.library") || text.contains("com.android.tools.build")) add("android",p);
            if (text.contains("org.jetbrains.compose") || text.contains("androidx.compose")) add("compose",p);
            for (String f : List.of("micronaut","quarkus","ktor","javafx","flyway","liquibase","thymeleaf","freemarker","velocity","testng","lombok")) if (text.contains(f)) add(f,p);
            if (text.contains("jakarta.platform") || text.contains("javaee-api") || text.contains("jakartaee-api")) add("jakarta",p);
          }
          if (n.equals("package.json")) {
            for (String f : List.of("react","vue","angular","next","vite","webpack","tailwindcss","prettier","eslint","stylelint","postcss","sass","less","karma","jshint","tslint"))
              if (text.contains("\""+f) || text.contains("\"@"+f)) add(f.equals("next")?"nextjs":f,p);
            add("node",p);
          }
          if (n.equals("pubspec.yaml") && text.matches("(?s).*\\bflutter\\s*:.*")) { add("flutter",p); add("android",p); }
          if (text.contains("apiversion:") && text.contains("kind:")) add("kubernetes",p);
          if (text.matches("(?s).*\\b(openapi|swagger)\\s*:.*")) add("openapi",p);
          if (n.equals("pyproject.toml") || n.equals("requirements.txt")) {
            if (text.contains("jupyter") || text.contains("ipykernel") || text.contains("notebook")) add("notebook",p);
          }
        }
        return FileVisitResult.CONTINUE;
      }
      @Override public FileVisitResult visitFileFailed(Path p, IOException e) { incomplete("Cannot inspect "+root.relativize(p)); return FileVisitResult.CONTINUE; }
      @Override public FileVisitResult postVisitDirectory(Path p,IOException e) {
        if(e!=null) incomplete("Cannot finish inspecting "+root.relativize(p));
        return FileVisitResult.CONTINUE;
      }
    };
    scanFiles(root,visitor,truncated);
    // Existing imported libraries/facets can expose frameworks inherited from a parent build.
    Path metadata=root.resolve(".idea");
    if(Files.isDirectory(metadata)&&!Files.isSymbolicLink(metadata)) try(var stream=Files.walk(metadata,3)) {
      var filesToRead=stream.filter(p->Files.isRegularFile(p)&&!Files.isSymbolicLink(p))
          .filter(p->p.toString().endsWith(".iml")||Set.of("facets.xml","externalDependencies.xml","modules.xml").contains(p.getFileName().toString())||p.getParent().getFileName().toString().equals("libraries"))
          .limit(501).toList();
      if(filesToRead.size()>500) { truncated[0]=true;problems.add("IDE metadata file limit reached"); }
      for(Path p:filesToRead) {
        if(Files.size(p)>512000) { truncated[0]=true;problems.add("IDE metadata too large: "+root.relativize(p));continue; }
        String text=new String(Files.readAllBytes(p),StandardCharsets.UTF_8);
        frameworks(text.toLowerCase(Locale.ROOT),tag->{if(tags.add(tag))evidence.add(tag+": "+root.relativize(p));});
        if(p.getFileName().toString().equals("externalDependencies.xml")) {
          var matcher=Pattern.compile("<plugin\\b[^>]*\\bid\\s*=\\s*[\"']([^\"']+)[\"']").matcher(text);
          while(matcher.find()) required.add(matcher.group(1));
        }
      }
    } catch(IOException e) { truncated[0]=true;problems.add("Cannot read IDE project metadata"); }
    if (tags.contains("spring")) tags.add("java");
    if (tags.contains("android")) { tags.add("java"); tags.add("kotlin"); tags.add("gradle"); }
    if (tags.contains("flutter")) tags.add("dart");
    if (tags.contains("jpa")) tags.add("java");
    return new Detection(tags, evidence, truncated[0],required,List.copyOf(problems));
  }

  /** Git supplies tracked and unignored local files, including new, uncommitted source.
   * No build scripts or hooks are run. Projects without Git use the same scanner without
   * an arbitrary depth limit: Java package depth is not evidence of an incomplete scan. */
  private static void scanFiles(Path root,SimpleFileVisitor<Path> visitor,boolean[] truncated) throws IOException {
    List<Path> indexed=gitFiles(root);
    if(indexed==null) {
      Files.walkFileTree(root,EnumSet.noneOf(FileVisitOption.class),Integer.MAX_VALUE,visitor);
      return;
    }
    for(Path p:indexed) {
      Path relative=root.relativize(p); boolean skip=false;
      for(Path part:relative) if(SKIP.contains(part.toString())) {skip=true;break;}
      if(skip) continue;
      // Do not follow symlinks listed by Git, including a symlink in a parent directory.
      Path checked=root;
      for(Path part:relative) {
        checked=checked.resolve(part);
        if(Files.isSymbolicLink(checked)) {skip=true;break;}
      }
      if(skip) continue;
      try {
        BasicFileAttributes attributes=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(attributes.isDirectory()) {
          // Git lists submodules and nested untracked repositories as directories.
          scanFiles(p,visitor,truncated);
          if(truncated[0]) return;
        } else if(visitor.visitFile(p,attributes)==FileVisitResult.TERMINATE) return;
      } catch(NoSuchFileException deleted) { /* A tracked file deleted locally is not project evidence. */ }
      catch(IOException e) {visitor.visitFileFailed(p,e);}
    }
  }

  private static List<Path> gitFiles(Path root) throws IOException {
    boolean repository=false;
    for(Path p=root;p!=null;p=p.getParent()) if(Files.exists(p.resolve(".git"))) {repository=true;break;}
    if(!repository) return null;
    Path output=Files.createTempFile("project-plugin-files-",".txt");
    Process process=null;
    try {
      ProcessBuilder builder=new ProcessBuilder("/usr/bin/git","-c","core.fsmonitor=false","-c","core.hooksPath=/dev/null","-C",root.toString(),"ls-files","--cached","--others","--exclude-standard","-z","--",".");
      builder.environment().put("GIT_OPTIONAL_LOCKS","0");
      // A parent IDE launched from a Git tool must not redirect this query to another repository.
      for(String key:List.of("GIT_DIR","GIT_WORK_TREE","GIT_INDEX_FILE","GIT_COMMON_DIR")) builder.environment().remove(key);
      process=builder.redirectOutput(output.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
      if(!process.waitFor(15,TimeUnit.SECONDS)) {process.destroyForcibly();process.waitFor();return null;}
      if(process.exitValue()!=0||Files.size(output)>16*1024*1024) return null;
      Set<Path> paths=new TreeSet<>();
      for(String name:Files.readString(output,StandardCharsets.UTF_8).split("\u0000")) {
        if(name.isEmpty()) continue;
        Path p=root.resolve(name).normalize();
        if(p.startsWith(root)&&!p.equals(root)) paths.add(p);
      }
      return new ArrayList<>(paths);
    } catch(InterruptedException e) {Thread.currentThread().interrupt();throw new IOException("Project inspection interrupted",e);}
      catch(IOException unavailable) {return null;}
    finally {if(process!=null&&process.isAlive()) process.destroyForcibly();Files.deleteIfExists(output);}
  }
  private static void frameworks(String text,Consumer<String> add) {
    if(text.contains("org.springframework")||text.contains("type=\"spring\"")) add.accept("spring");
    if(text.contains("org.springframework.boot")) add.accept("spring-boot");
    for(String f:List.of("data","security","web","integration","messaging","modulith","cloud"))
      if(text.contains("org.springframework."+f)) add.accept("spring-"+f);
    if(text.contains("org.hibernate")||text.contains("javax.persistence")||text.contains("jakarta.persistence")||text.contains("type=\"jpa\"")||text.contains("type=\"hibernate\"")) { add.accept("jpa");add.accept("database"); }
    if(text.contains("type=\"python_module\"")) add.accept("python");
    if(text.contains("type=\"java_module\"")) add.accept("java");
    if(text.contains("type=\"android\"")) add.accept("android");
    if(text.contains("io.micronaut")) add.accept("micronaut");
    if(text.contains("io.quarkus")) add.accept("quarkus");
    if(text.contains("io.ktor")) add.accept("ktor");
    if(text.contains("javafx.")) add.accept("javafx");
  }
}
