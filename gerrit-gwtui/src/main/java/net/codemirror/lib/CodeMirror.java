// Copyright (C) 2013 The Android Open Source Project
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

package net.codemirror.lib;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.ScriptElement;
import com.google.gwt.dom.client.StyleInjector;
import com.google.gwt.resources.client.ExternalTextResource;
import com.google.gwt.resources.client.ResourceCallback;
import com.google.gwt.resources.client.ResourceException;
import com.google.gwt.resources.client.TextResource;
import com.google.gwt.safehtml.shared.SafeUri;

import net.codemirror.mode.Modes;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Glue to connect CodeMirror to be callable from GWT.
 *
 * @link http://codemirror.net/doc/manual.html#api
 */
public class CodeMirror extends JavaScriptObject {
  public static native CodeMirror create(Element parent, Configuration cfg) /*-{
    return $wnd.CodeMirror(parent, cfg);
  }-*/;

  public final native void setValue(String v) /*-{ this.setValue(v); }-*/;

  public final native void setWidth(int w) /*-{ this.setSize(w, null); }-*/;
  public final native void setWidth(String w) /*-{ this.setSize(w, null); }-*/;
  public final native void setHeight(int h) /*-{ this.setSize(null, h); }-*/;
  public final native void setHeight(String h) /*-{ this.setSize(null, h); }-*/;

  public final native void refresh() /*-{ this.refresh(); }-*/;
  public final native Element getWrapperElement() /*-{ return this.getWrapperElement(); }-*/;

  public static void install() {
    asyncInjectCss(Lib.I.css());
    asyncInjectScript(Lib.I.js().getSafeUri());

    // TODO lazy load modes, e.g. http://codemirror.net/addon/mode/loadmode.js
    asyncInjectScript(Modes.I.clike().getSafeUri());
  }

  private static void asyncInjectCss(ExternalTextResource css) {
    try {
      css.getText(new ResourceCallback<TextResource>() {
        @Override
        public void onSuccess(TextResource resource) {
          StyleInjector.inject(resource.getText());
        }

        @Override
        public void onError(ResourceException e) {
          error(e);
        }
      });
    } catch (ResourceException e) {
      error(e);
    }
  }

  private static void asyncInjectScript(SafeUri uri) {
    ScriptElement script = Document.get().createScriptElement();
    script.setSrc(uri.asString());
    script.setLang("javascript");
    script.setType("text/javascript");
    Document.get().getBody().appendChild(script);
  }

  private static void error(ResourceException e) {
    Logger log = Logger.getLogger("net.codemirror");
    log.log(Level.SEVERE, "Cannot fetch CSS", e);
  }

  protected CodeMirror() {
  }
}
