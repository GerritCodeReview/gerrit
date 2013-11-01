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

import com.google.gerrit.common.data.ToggleStarRequest;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.core.client.JavaScriptObject;

public class StarredChangesInput extends JavaScriptObject {
  public static StarredChangesInput create(ToggleStarRequest r) {
    StarredChangesInput m = createObject().cast();
    m.init();
    if (r.getAddSet() != null) {
      for (Change.Id id : r.getAddSet()) {
        m.addOn(id.toString());
      }
    }
    if (r.getRemoveSet() != null) {
      for (Change.Id id : r.getRemoveSet()) {
        m.addOff(id.toString());
      }
    }
    return m;
  }

  public final native void addOn(String id) /*-{ this.on.push(id); }-*/;
  public final native void addOff(String id) /*-{ this.off.push(id); }-*/;

  private final native void init() /*-{
    this.on = [];
    this.off = [];
  }-*/;

  protected StarredChangesInput() {
  }
}
