package ro.andrei.ideprofiles;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.trustedProjects.*;
import com.intellij.openapi.application.*;
import com.intellij.util.ThreeState;
import java.nio.file.*;
import java.io.*;
import java.util.concurrent.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;
import kotlin.ResultKt;
import kotlin.coroutines.*;
import kotlin.coroutines.intrinsics.IntrinsicsKt;

/** Use IntelliJ's normal trust decision in the originating, already visible window. */
public final class ProfileTrust {
  public static boolean confirm(Path root) throws Exception {
    if(TrustedProjects.INSTANCE.getProjectTrustedState(root)!=ThreeState.UNSURE) return true;
    CompletableFuture<Boolean> answer=new CompletableFuture<>();
    ApplicationManager.getApplication().invokeLater(()->{
      Continuation<Boolean> continuation=new Continuation<>() {
        public CoroutineContext getContext() { return EmptyCoroutineContext.INSTANCE; }
        public void resumeWith(Object value) {
          try { ResultKt.throwOnFailure(value);answer.complete((Boolean)value); }
          catch(Throwable e) { answer.completeExceptionally(e); }
        }
      };
      try {
        Object result=TrustedProjectsDialog.INSTANCE.confirmOpeningOrLinkingUntrustedProject(root,null,
            IdeBundle.message("untrusted.project.open.dialog.title",root.getFileName().toString()),
            IdeBundle.message("untrusted.project.open.dialog.text",ApplicationInfo.getInstance().getFullApplicationName()),
            IdeBundle.message("untrusted.project.dialog.trust.button"),
            IdeBundle.message("untrusted.project.open.dialog.distrust.button"),
            IdeBundle.message("untrusted.project.open.dialog.cancel.button"),continuation);
        if(result!=IntrinsicsKt.getCOROUTINE_SUSPENDED()) answer.complete((Boolean)result);
      } catch(Throwable e) { answer.completeExceptionally(e); }
    });
    return answer.get();
  }
  public static void copyDecision(Path profile,Path root) throws Exception {
    ThreeState state=TrustedProjects.INSTANCE.getProjectTrustedState(root);
    if(state==ThreeState.UNSURE) return;
    Path file=profile.resolve("config/options/trusted-paths.xml");
    DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
    factory.setExpandEntityReferences(false);
    Document doc=Files.exists(file)?factory.newDocumentBuilder().parse(file.toFile()):factory.newDocumentBuilder().newDocument();
    if(doc.getDocumentElement()==null) doc.appendChild(doc.createElement("application"));
    Element component=child(doc,doc.getDocumentElement(),"component","name","Trusted.Paths");
    Element option=child(doc,component,"option","name","TRUSTED_PROJECT_PATHS");
    Element map=child(doc,option,"map",null,null);
    Element entry=child(doc,map,"entry","key",root.toString()); entry.setAttribute("value",state==ThreeState.YES?"true":"false");
    StringWriter out=new StringWriter();Transformer transformer=TransformerFactory.newInstance().newTransformer();
    transformer.setOutputProperty(OutputKeys.INDENT,"yes");transformer.transform(new DOMSource(doc),new StreamResult(out));
    ProfileFiles.write(file,out.toString());
  }
  private static Element child(Document doc,Element parent,String tag,String key,String value) {
    for(Node n=parent.getFirstChild();n!=null;n=n.getNextSibling())
      if(n instanceof Element e&&e.getTagName().equals(tag)&&(key==null||e.getAttribute(key).equals(value))) return e;
    Element e=doc.createElement(tag);if(key!=null)e.setAttribute(key,value);parent.appendChild(e);return e;
  }
}
