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
  public static TextMarker create() {
    return createObject().cast();
  }

  public final native void clear() /*-{ this.clear(); }-*/;
  public final native void changed() /*-{ this.changed(); }-*/;
  public final native FromTo find() /*-{ return this.find(); }-*/;

  protected TextMarker() {
  }

  public static class FromTo extends JavaScriptObject {
    public static FromTo create(LineCharacter from, LineCharacter to) {
      FromTo fromTo = createObject().cast();
      fromTo.setFrom(from);
      fromTo.setTo(to);
      return fromTo;
    }

    public static FromTo create(CommentRange range) {
      return create(
          LineCharacter.create(range.start_line() - 1, range.start_character()),
          LineCharacter.create(range.end_line() - 1, range.end_character()));
    }

    public final native LineCharacter getFrom() /*-{ return this.from; }-*/;
    public final native LineCharacter getTo() /*-{ return this.to; }-*/;

    public final native void setFrom(LineCharacter from) /*-{ this.from = from; }-*/;
    public final native void setTo(LineCharacter to) /*-{ this.to = to; }-*/;

    protected FromTo() {
    }
  }
}
