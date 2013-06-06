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
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.rpc.AsyncCallback;

import java.util.Set;

public class ProjectApi {
  /** Create a new project */
  public static void createProject(String projectName, String parent,
      Boolean createEmptyCcommit, Boolean permissionsOnly,
      AsyncCallback<VoidResult> cb) {
    ProjectInput input = ProjectInput.create();
    input.setName(projectName);
    input.setParent(parent);
    input.setPermissionsOnly(permissionsOnly);
    input.setCreateEmptyCommit(createEmptyCcommit);
    new RestApi("/projects/").id(projectName).ifNoneMatch()
        .put(input, cb);
  }

  /** Create a new branch */
  public static void createBranch(Project.NameKey projectName, String ref,
      String revision, AsyncCallback<BranchInfo> cb) {
    BranchInput input = BranchInput.create();
    input.setRevision(revision);
    new RestApi("/projects/").id(projectName.get()).view("branches").id(ref)
        .ifNoneMatch().put(input, cb);
  }

  /** Retrieve all visible branches of the project */
  public static void getBranches(Project.NameKey projectName,
      AsyncCallback<JsArray<BranchInfo>> cb) {
    new RestApi("/projects/").id(projectName.get()).view("branches").get(cb);
  }

  /**
   * Delete branches. For each branch to be deleted a separate DELETE request is
   * fired to the server. The {@code onSuccess} method of the provided callback
   * is invoked once after all requests succeeded. If any request fails the
   * callbacks' {@code onFailure} method is invoked. In a failure case it can be
   * that still some of the branches were successfully deleted.
   */
  public static void deleteBranches(Project.NameKey projectName,
      Set<String> refs, AsyncCallback<VoidResult> cb) {
    CallbackGroup group = new CallbackGroup();
    for (String ref : refs) {
      new RestApi("/projects/").id(projectName.get()).view("branches").id(ref)
          .delete(group.add(cb));
      cb = CallbackGroup.emptyCallback();
    }
  }

  static RestApi config(Project.NameKey name) {
    return new RestApi("/projects/").id(name.get()).view("config");
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

  private static class BranchInput extends JavaScriptObject {
    static BranchInput create() {
      return (BranchInput) createObject();
    }

    protected BranchInput() {
    }

    final native void setRevision(String r) /*-{ if(r)this.revision=r; }-*/;
  }
}
