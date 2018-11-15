// Copyright (C) 2015 The Android Open Source Project
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

import com.google.gerrit.client.info.WebLinkInfo;
import com.google.gwt.core.client.JsArray;

public class TagInfo extends RefInfo {
  public final native boolean canDelete() /*-{ return this['can_delete'] ? true : false; }-*/;

  public final native JsArray<WebLinkInfo> webLinks() /*-{ return this.web_links; }-*/;

  // TODO(dpursehouse) add extra tag-related fields (message, tagger, etc)
  protected TagInfo() {}
}
