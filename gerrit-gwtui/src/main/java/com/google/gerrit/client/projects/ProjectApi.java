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

import com.google.gerrit.client.VoidResult;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.user.client.rpc.AsyncCallback;

public class ProjectApi {
  /** Create a new project */
  public static void createProject(String projectName, String parent,
      Boolean createEmptyCcommit, Boolean permissionsOnly,
      AsyncCallback<VoidResult> asyncCallback) {
    ProjectInput input = ProjectInput.create();
    input.setName(projectName);
    input.setParent(parent);
    input.setPermissionsOnly(permissionsOnly);
    input.setCreateEmptyCommit(createEmptyCcommit);
    new RestApi("/projects/").id(projectName).ifNoneMatch()
        .put(input, asyncCallback);
  }

  public static RestApi project(Project.NameKey name) {
    return new RestApi("/projects/").id(name.get());
  }

  static RestApi config(Project.NameKey name) {
    return project(name).view("config");
  }

  private static class ProjectInput extends JavaScriptObject {
    static ProjectInput create() {
      return (ProjectInput) createObject();
    }

    protected ProjectInput() {
    }

    final native void setName(String n) /*-{ if(n)this.name=n; }-*/;

    final native void setParent(String p) /*-{ if(p)this.parent=p; }-*/;

    final native void setPermissionsOnly(boolean po) /*-{ if(po)this.permissions_only=po; }-*/;

    final native void setCreateEmptyCommit(boolean cc) /*-{ if(cc)this.create_empty_commit=cc; }-*/;
  }
}
