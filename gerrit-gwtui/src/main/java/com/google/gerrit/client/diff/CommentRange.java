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

package com.google.gerrit.client.diff;

import com.google.gwt.core.client.JavaScriptObject;

public class CommentRange extends JavaScriptObject {
  public static CommentRange create(int sl, int sc, int el, int ec) {
    CommentRange r = createObject().cast();
    r.setStartLine(sl);
    r.setStartCharacter(sc);
    r.setEndLine(el);
    r.setEndCharacter(ec);
    return r;
  }

  public final native int start_line() /*-{ return this.start_line; }-*/;
  public final native int start_character() /*-{ return this.start_character; }-*/;
  public final native int end_line() /*-{ return this.end_line; }-*/;
  public final native int end_character() /*-{ return this.end_character; }-*/;

  private final native void setStartLine(int sl) /*-{ this.start_line = sl; }-*/;
  private final native void setStartCharacter(int sc) /*-{ this.start_character = sc; }-*/;
  private final native void setEndLine(int el) /*-{ this.end_line = el; }-*/;
  private final native void setEndCharacter(int ec) /*-{ this.end_character = ec; }-*/;

  protected CommentRange() {
  }
}