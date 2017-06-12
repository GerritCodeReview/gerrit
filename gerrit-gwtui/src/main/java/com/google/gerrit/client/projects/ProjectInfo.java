// Copyright (C) 2012 The Android Open Source Project
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
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.ui.SuggestOracle;

public class ProjectInfo extends JavaScriptObject implements SuggestOracle.Suggestion {
  public final Project.NameKey name_key() {
    return new Project.NameKey(name());
  }

  public final native String name() /*-{ return this.name; }-*/;

  public final native String description() /*-{ return this.description; }-*/;

  public final native JsArray<WebLinkInfo> webLinks() /*-{ return this.web_links; }-*/;

  public final ProjectState state() {
    return ProjectState.valueOf(getStringState());
  }

  private native String getStringState() /*-{ return this.state; }-*/;

  @Override
  public final String getDisplayString() {
    if (description() != null) {
      return name() + " (" + description() + ")";
    }
    return name();
  }

  @Override
  public final String getReplacementString() {
    return name();
  }

  protected ProjectInfo() {}
}
