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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.rpc.NativeMap;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;

public class ReviewInput extends JavaScriptObject {
  public enum NotifyHandling {
    NONE,
    OWNER,
    OWNER_REVIEWERS,
    ALL
  }

  public enum DraftHandling {
    DELETE,
    PUBLISH,
    KEEP,
    PUBLISH_ALL_REVISIONS
  }

  public static ReviewInput create() {
    ReviewInput r = createObject().cast();
    r.init();
    r.drafts(DraftHandling.PUBLISH);
    return r;
  }

  public final native void message(String m) /*-{ if(m)this.message=m; }-*/;

  public final native void label(String n, short v) /*-{ this.labels[n]=v; }-*/;

  public final native void comments(NativeMap<JsArray<CommentInfo>> m)/*-{ this.comments=m }-*/ ;

  public final void notify(NotifyHandling e) {
    _notify(e.name());
  }

  private native void _notify(String n) /*-{ this.notify=n; }-*/;

  public final void drafts(DraftHandling e) {
    _drafts(e.name());
  }

  private native void _drafts(String n) /*-{ this.drafts=n; }-*/;

  private native void init() /*-{
    this.labels = {};
    this.strict_labels = true;
  }-*/;

  public final native void prePost() /*-{
    var m=this.comments;
    if (m) {
      for (var p in m) {
        var l=m[p];
        for (var i=0;i<l.length;i++) {
          var c=l[i];
          delete c['path'];
          delete c['updated'];
        }
      }
    }
  }-*/;

  public final native void mergeLabels(ReviewInput o) /*-{
    var l=o.labels;
    if (l) {
      for (var n in l)
        this.labels[n]=l[n];
    }
  }-*/;

  protected ReviewInput() {}
}
