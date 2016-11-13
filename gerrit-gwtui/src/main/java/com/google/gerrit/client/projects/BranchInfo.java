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

import com.google.gerrit.client.info.ActionInfo;
import com.google.gerrit.client.info.WebLinkInfo;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gwt.core.client.JsArray;

public class BranchInfo extends RefInfo {
  public final native boolean canDelete() /*-{ return this['can_delete'] ? true : false; }-*/;

  public final native NativeMap<ActionInfo> actions() /*-{ return this.actions }-*/;

  public final native JsArray<WebLinkInfo> webLinks() /*-{ return this.web_links; }-*/;

  protected BranchInfo() {}
}
