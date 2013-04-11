// Copyright (C) 2009 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gwtexpui.linker.server;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

/** A single permutation of the compiled GWT application. */
public class Permutation {
  private final PermutationSelector selector;
  private final String cacheHTML;
  private final String[] values;

  Permutation(PermutationSelector sel, String cacheHTML, String[] values) {
    this.selector = sel;
    this.cacheHTML = cacheHTML;
    this.values = values;
  }

  boolean matches(String[] r) {
    return Arrays.equals(values, r);
  }

  /**
   * Append GWT bootstrap for this permutation onto the end of the body.
   * <p>
   * The GWT bootstrap for this particular permutation is appended onto the end
   * of the {@code body} element of the passed host page.
   * <p>
   * To keep the bootstrap code small and simple, not all GWT features are
   * actually supported. The {@code gwt:property}, {@code gwt:onPropertyErrorFn}
   * and {@code gwt:onLoadErrorFn} meta tags are ignored and not handled.
   * <p>
   * Load order may differ from the standard GWT {@code nocache.js}. The browser
   * is asked to load the iframe immediately, rather than after the body has
   * finished loading.
   *
   * @param dom host page HTML document.
   */
  public void inject(Document dom) {
    String moduleName = selector.getModuleName();
    String moduleFunc = moduleName;

    StringBuilder s = new StringBuilder();
    s.append("\n");
    s.append("function " + moduleFunc + "(){");
    s.append("var s,l,t");
    s.append(",w=window");
    s.append(",d=document");
    s.append(",n='" + moduleName + "'");
    s.append(",f=d.createElement('iframe')");
    s.append(";");

    // Callback to execute the module once both s and l are true.
    //
    s.append("function m(){");
    s.append("if(s&&l){");
    // Base path needs to be absolute. There isn't an easy way to do this
    // other than forcing an image to load and then pulling the URL back.
    //
    s.append("var b,i=d.createElement('img');");
    s.append("i.src=n+'/clear.cache.gif';");
    s.append("b=i.src;");
    s.append("b=b.substring(0,b.lastIndexOf('/')+1);");
    s.append(moduleFunc + "=null;"); // allow us to GC
    s.append("f.contentWindow.gwtOnLoad(undefined,n,b);");
    s.append("}");
    s.append("}");

    // Set s true when the module script has finished loading. The
    // exact name here is known to the IFrameLinker and is called by
    // the code in the iframe.
    //
    s.append(moduleFunc + ".onScriptLoad=function(){");
    s.append("s=1;m();");
    s.append("};");

    // Set l true when the browser has finished processing the iframe
    // tag, and everything else on the page.
    //
    s.append(moduleFunc + ".r=function(){");
    s.append("l=1;m();");
    s.append("};");

    // Prevents mixed mode security in IE6/7.
    s.append("f.src=\"javascript:''\";");
    s.append("f.id=n;");
    s.append("f.style.cssText"
        + "='position:absolute;width:0;height:0;border:none';");
    s.append("f.tabIndex=-1;");
    s.append("d.body.appendChild(f);");

    // The src has to be set after the iframe is attached to the DOM to avoid
    // refresh quirks in Safari. We have to use the location.replace trick to
    // avoid FF2 refresh quirks.
    //
    s.append("f.contentWindow.location.replace(n+'/" + cacheHTML + "');");

    // defer attribute here is to workaround IE running immediately.
    //
    s.append("d.write('<script defer=\"defer\">" //
        + moduleFunc + ".r()</'+'script>');");
    s.append("}");
    s.append(moduleFunc + "();");
    s.append("\n//");

    final Element html = dom.getDocumentElement();
    final Element head = (Element) html.getElementsByTagName("head").item(0);
    final Element body = (Element) html.getElementsByTagName("body").item(0);

    for (String css : selector.getCSS()) {
      if (isRelativeURL(css)) {
        css = moduleName + '/' + css;
      }

      final Element link = dom.createElement("link");
      link.setAttribute("rel", "stylesheet");
      link.setAttribute("href", css);
      head.appendChild(link);
    }

    final Element script = dom.createElement("script");
    script.setAttribute("type", "text/javascript");
    script.setAttribute("language", "javascript");
    script.appendChild(dom.createComment(s.toString()));
    body.appendChild(script);
  }

  private static boolean isRelativeURL(String src) {
    if (src.startsWith("/")) {
      return false;
    }

    try {
      // If it parses as a URL, assume it is not relative.
      //
      new URL(src);
      return false;
    } catch (MalformedURLException e) {
    }

    return true;
  }
}
