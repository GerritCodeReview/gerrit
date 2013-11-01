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

class StarredChangesInput extends JavaScriptObject {
  static StarredChangesInput create(ToggleStarRequest r) {
    StarredChangesInput m = createObject().cast();
    m.init0();
    if (r.getAddSet() != null) {
      for (Change.Id id : r.getAddSet()) {
        m.add_on(id.toString());
      }
    }
    if (r.getRemoveSet() != null) {
      for (Change.Id id : r.getRemoveSet()) {
        m.add_off(id.toString());
      }
    }
    return m;
  }

  final native void init0() /*-{
    this.on = [];
    this.off = [];
  }-*/;

  final native void add_off(String n) /*-{ this.off.push(n); }-*/;
  final native void add_on(String n) /*-{ this.on.push(n); }-*/;

  protected StarredChangesInput() {
  }
}
