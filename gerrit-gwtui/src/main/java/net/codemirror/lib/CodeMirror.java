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
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.rpc.AsyncCallback;

/**
 * Glue to connect CodeMirror to be callable from GWT.
 *
 * @link http://codemirror.net/doc/manual.html#api
 */
public class CodeMirror extends JavaScriptObject {
  public static void initLibrary(AsyncCallback<Void> cb) {
    Loader.initLibrary(cb);
  }

  public static native CodeMirror create(Element parent, Configuration cfg) /*-{
    return $wnd.CodeMirror(parent, cfg);
  }-*/;

  public final native void setValue(String v) /*-{ this.setValue(v); }-*/;
  public final native String getValue() /*-{ return this.getValue(); }-*/;

  public final native void setWidth(int w) /*-{ this.setSize(w, null); }-*/;
  public final native void setWidth(String w) /*-{ this.setSize(w, null); }-*/;
  public final native void setHeight(int h) /*-{ this.setSize(null, h); }-*/;
  public final native void setHeight(String h) /*-{ this.setSize(null, h); }-*/;

  public final native void setMode(String m) /*-{ this.setOption("mode", m); }-*/;

  public final native void refresh() /*-{ this.refresh(); }-*/;
  public final native Element getWrapperElement() /*-{ return this.getWrapperElement(); }-*/;

  protected CodeMirror() {
  }
}
