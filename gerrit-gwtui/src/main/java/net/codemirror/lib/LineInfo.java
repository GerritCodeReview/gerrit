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

import com.google.gerrit.client.rpc.NativeMap;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.dom.client.Element;

import net.codemirror.lib.CodeMirror.LineHandle;

/** Objects returned by lineInfo(). */
public class LineInfo extends JavaScriptObject {

  public final native int line() /*-{ return this.line; }-*/;
  public final native LineHandle handle() /*-{ return this.handle; }-*/;
  public final native String text() /*-{ return this.text; }-*/;

  public final native NativeMap<Element> gutterMarkers() /*-{
    return this.gutterMarkers;
  }-*/;

  public final native String textClass() /*-{ return this.textClass; }-*/;
  public final native String bgClass() /*-{ return this.bgClass; }-*/;
  public final native String wrapClass() /*-{ return this.wrapClass; }-*/;
  public final native JsArray<LineWidget> widgets() /*-{ return this.widgets; }-*/;

  protected LineInfo() {
  }
}
