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

package com.google.gerrit.client.info;

import com.google.gerrit.extensions.client.UiType;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArrayString;
import java.util.ArrayList;
import java.util.List;

public class GerritInfo extends JavaScriptObject {
  public final Project.NameKey allProjectsNameKey() {
    return new Project.NameKey(allProjects());
  }

  public final boolean isAllProjects(Project.NameKey p) {
    return allProjectsNameKey().equals(p);
  }

  public final Project.NameKey allUsersNameKey() {
    return new Project.NameKey(allUsers());
  }

  public final boolean isAllUsers(Project.NameKey p) {
    return allUsersNameKey().equals(p);
  }

  public final native String allProjects() /*-{ return this.all_projects; }-*/;

  public final native String allUsers() /*-{ return this.all_users; }-*/;

  public final native boolean docSearch() /*-{ return this.doc_search; }-*/;

  public final native String docUrl() /*-{ return this.doc_url; }-*/;

  public final native boolean editGpgKeys() /*-{ return this.edit_gpg_keys || false; }-*/;

  public final native String reportBugUrl() /*-{ return this.report_bug_url; }-*/;

  public final native String reportBugText() /*-{ return this.report_bug_text; }-*/;

  private native JsArrayString _webUis() /*-{ return this.web_uis; }-*/;

  public final List<UiType> webUis() {
    JsArrayString webUis = _webUis();
    List<UiType> result = new ArrayList<>(webUis.length());
    for (int i = 0; i < webUis.length(); i++) {
      UiType t = UiType.parse(webUis.get(i));
      if (t != null) {
        result.add(t);
      }
    }
    return result;
  }

  protected GerritInfo() {}
}
