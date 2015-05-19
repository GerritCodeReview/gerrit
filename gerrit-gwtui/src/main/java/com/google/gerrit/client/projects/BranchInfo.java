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

import com.google.gerrit.client.WebLinkInfo;
import com.google.gerrit.client.actions.ActionInfo;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;

public class BranchInfo extends JavaScriptObject {
  public final String getShortName() {
    return RefNames.shortName(ref());
  }

  public final native String ref() /*-{ return this.ref; }-*/;
  public final native String revision() /*-{ return this.revision; }-*/;
  public final native boolean canDelete() /*-{ return this['can_delete'] ? true : false; }-*/;
  public final native NativeMap<ActionInfo> actions() /*-{ return this.actions }-*/;
  public final native JsArray<WebLinkInfo> webLinks() /*-{ return this.web_links; }-*/;

  protected BranchInfo() {
  }
}
