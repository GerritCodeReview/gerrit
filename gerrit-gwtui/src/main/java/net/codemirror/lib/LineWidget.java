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

/** LineWidget objects used within CodeMirror. */
public class LineWidget extends JavaScriptObject {
  public final native void clear() /*-{ this.clear() }-*/;

  public final native void changed() /*-{ this.changed() }-*/;

  public final native void onRedraw(Runnable thunk) /*-{
    this.on("redraw", $entry(function() {
      thunk.@java.lang.Runnable::run()();
    }))
  }-*/;

  public final native void onFirstRedraw(Runnable thunk) /*-{
    var w = this;
    var h = $entry(function() {
      thunk.@java.lang.Runnable::run()();
      w.off("redraw", h);
    });
    w.on("redraw", h);
  }-*/;

  protected LineWidget() {}
}
