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

package com.google.gerrit.client.projects;

import com.google.gerrit.client.rpc.NativeMap;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwtexpui.safehtml.client.FindReplace;
import com.google.gwtexpui.safehtml.client.LinkFindReplace;
import com.google.gwtexpui.safehtml.client.RawFindReplace;

import java.util.ArrayList;
import java.util.List;

public class ConfigInfo extends JavaScriptObject {
  public final native JavaScriptObject has_require_change_id()
  /*-{ return this.hasOwnProperty('require_change_id'); }-*/;
  public final native boolean require_change_id()
  /*-{ return this.require_change_id; }-*/;

  public final native JavaScriptObject has_use_content_merge()
  /*-{ return this.hasOwnProperty('use_content_merge'); }-*/;
  public final native boolean use_content_merge()
  /*-{ return this.use_content_merge; }-*/;

  public final native JavaScriptObject has_use_contributor_agreements()
  /*-{ return this.hasOwnProperty('use_contributor_agreements'); }-*/;
  public final native boolean use_contributor_agreements()
  /*-{ return this.use_contributor_agreements; }-*/;

  public final native JavaScriptObject has_use_signed_off_by()
  /*-{ return this.hasOwnProperty('use_signed_off_by'); }-*/;
  public final native boolean use_signed_off_by()
  /*-{ return this.use_signed_off_by; }-*/;

  private final native NativeMap<CommentLinkInfo> commentlinks0()
  /*-{ return this.commentlinks; }-*/;
  final List<FindReplace> commentlinks() {
    JsArray<CommentLinkInfo> cls = commentlinks0().values();
    List<FindReplace> commentLinks = new ArrayList<FindReplace>(cls.length());
    for (int i = 0; i < cls.length(); i++) {
      CommentLinkInfo cl = cls.get(i);
      if (!cl.enabled()) {
        continue;
      }
      if (cl.link() != null) {
        commentLinks.add(new LinkFindReplace(cl.match(), cl.link()));
      } else {
        commentLinks.add(new RawFindReplace(cl.match(), cl.html()));
      }
    }
    return commentLinks;
  }

  final native ThemeInfo theme() /*-{ return this.theme; }-*/;

  protected ConfigInfo() {
  }

  static class CommentLinkInfo extends JavaScriptObject {
    final native String match() /*-{ return this.match; }-*/;
    final native String link() /*-{ return this.link; }-*/;
    final native String html() /*-{ return this.html; }-*/;
    final native boolean enabled() /*-{
      return !this.hasOwnProperty('enabled') || this.enabled;
    }-*/;

    protected CommentLinkInfo() {
    }
  }
}
