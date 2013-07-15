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

import com.google.gwt.core.client.JavaScriptObject;

public class ReviewInput extends JavaScriptObject {
  public static enum NotifyHandling {
    NONE, OWNER, OWNER_REVIEWERS, ALL;
  }

  public static ReviewInput create() {
    ReviewInput r = createObject().cast();
    r.init();
    return r;
  }

  public final native void message(String m) /*-{ if(m)this.message=m; }-*/;
  public final native void label(String n, short v) /*-{ this.labels[n]=v; }-*/;

  public final void notify(NotifyHandling e) {
    _notify(e.name());
  }
  private final native void _notify(String n) /*-{ this.notify=n; }-*/;

  private final native void init() /*-{
    this.labels = {};
    this.strict_labels = true;
    this.drafts = 'PUBLISH';
  }-*/;

  protected ReviewInput() {
  }
}
