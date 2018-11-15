// Copyright (C) 2016 The Android Open Source Project
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
import com.google.gwt.dom.client.StyleInjector;

public class LintLine extends JavaScriptObject {
  public static LintLine create(String shortMsg, String msg, String sev, Pos line) {
    StyleInjector.inject(
        ".CodeMirror-lint-marker-"
            + sev
            + " {\n"
            + "  visibility: hidden;\n"
            + "  text-overflow: ellipsis;\n"
            + "  white-space: nowrap;\n"
            + "  overflow: hidden;\n"
            + "  position: relative;\n"
            + "}\n"
            + ".CodeMirror-lint-marker-"
            + sev
            + ":after {\n"
            + "  content:'"
            + shortMsg
            + "';\n"
            + "  visibility: visible;\n"
            + "}");
    return create(msg, sev, line, null);
  }

  public static native LintLine create(String msg, String sev, Pos f, Pos t) /*-{
    return {
      message : msg,
      severity : sev,
      from : f,
      to : t
    };
  }-*/;

  public final native String message() /*-{ return this.message; }-*/;

  public final native String detailedMessage() /*-{ return this.message; }-*/;

  public final native String severity() /*-{ return this.severity; }-*/;

  public final native Pos from() /*-{ return this.from; }-*/;

  public final native Pos to() /*-{ return this.to; }-*/;

  protected LintLine() {}
}
