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

import com.google.gerrit.client.diff.CommentRange;
import com.google.gwt.core.client.JavaScriptObject;

/** Object that represents a text marker within CodeMirror */
public class TextMarker extends JavaScriptObject {
  public final native void clear() /*-{ this.clear(); }-*/;

  public final native void changed() /*-{ this.changed(); }-*/;

  public final native FromTo find() /*-{ return this.find(); }-*/;

  public final native void on(String event, Runnable thunk)
      /*-{ this.on(event, function(){$entry(thunk.@java.lang.Runnable::run()())}) }-*/ ;

  protected TextMarker() {}

  public static class FromTo extends JavaScriptObject {
    public static final native FromTo create(Pos f, Pos t) /*-{
      return {from: f, to: t}
    }-*/;

    public static FromTo create(CommentRange range) {
      return create(
          Pos.create(range.startLine() - 1, range.startCharacter()),
          Pos.create(range.endLine() - 1, range.endCharacter()));
    }

    public final native Pos from() /*-{ return this.from }-*/;

    public final native Pos to() /*-{ return this.to }-*/;

    public final native void from(Pos f) /*-{ this.from = f }-*/;

    public final native void to(Pos t) /*-{ this.to = t }-*/;

    protected FromTo() {}
  }
}
