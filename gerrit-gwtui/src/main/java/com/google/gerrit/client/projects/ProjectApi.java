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
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.InheritableBoolean;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
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
  public static void createBranch(Project.NameKey name, String ref,
      String revision, AsyncCallback<BranchInfo> cb) {
    BranchInput input = BranchInput.create();
    input.setRevision(revision);
    project(name).view("branches").id(ref).ifNoneMatch().put(input, cb);
  }

  /** Retrieve all visible branches of the project */
  public static void getBranches(Project.NameKey name,
      AsyncCallback<JsArray<BranchInfo>> cb) {
    project(name).view("branches").get(cb);
  }

  /**
   * Delete branches. For each branch to be deleted a separate DELETE request is
   * fired to the server. The {@code onSuccess} method of the provided callback
   * is invoked once after all requests succeeded. If any request fails the
   * callbacks' {@code onFailure} method is invoked. In a failure case it can be
   * that still some of the branches were successfully deleted.
   */
  public static void deleteBranches(Project.NameKey name,
      Set<String> refs, AsyncCallback<VoidResult> cb) {
    CallbackGroup group = new CallbackGroup();
    for (String ref : refs) {
      project(name).view("branches").id(ref)
          .delete(group.add(cb));
      cb = CallbackGroup.emptyCallback();
    }
    group.done();
  }

  public static void getConfig(Project.NameKey name,
      AsyncCallback<ConfigInfo> cb) {
    project(name).view("config").get(cb);
  }

  public static void setConfig(Project.NameKey name, String description,
      InheritableBoolean useContributorAgreements,
      InheritableBoolean useContentMerge, InheritableBoolean useSignedOffBy,
      InheritableBoolean requireChangeId, String maxObjectSizeLimit,
      SubmitType submitType, Project.State state, AsyncCallback<ConfigInfo> cb) {
    ConfigInput in = ConfigInput.create();
    in.setDescription(description);
    in.setUseContributorAgreements(useContributorAgreements);
    in.setUseContentMerge(useContentMerge);
    in.setUseSignedOffBy(useSignedOffBy);
    in.setRequireChangeId(requireChangeId);
    in.setMaxObjectSizeLimit(maxObjectSizeLimit);
    in.setSubmitType(submitType);
    in.setState(state);
    project(name).view("config").put(in, cb);
  }

  public static void getParent(Project.NameKey name,
      final AsyncCallback<Project.NameKey> cb) {
    project(name).view("parent").get(
        new AsyncCallback<NativeString>() {
          @Override
          public void onSuccess(NativeString result) {
            cb.onSuccess(new Project.NameKey(result.asString()));
          }

          @Override
          public void onFailure(Throwable caught) {
            cb.onFailure(caught);
          }
        });
  }

  public static void getDescription(Project.NameKey name,
      AsyncCallback<NativeString> cb) {
    project(name).view("description").get(cb);
  }

  public static void setDescription(Project.NameKey name, String description,
      AsyncCallback<NativeString> cb) {
    RestApi call = project(name).view("description");
    if (description != null && !description.isEmpty()) {
      DescriptionInput input = DescriptionInput.create();
      input.setDescription(description);
      call.put(input, cb);
    } else {
      call.delete(cb);
    }
  }

  public static RestApi project(Project.NameKey name) {
    return new RestApi("/projects/").id(name.get());
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

  private static class ConfigInput extends JavaScriptObject {
    static ConfigInput create() {
      return (ConfigInput) createObject();
    }

    protected ConfigInput() {
    }

    final native void setDescription(String d)
    /*-{ if(d)this.description=d; }-*/;

    final void setUseContributorAgreements(InheritableBoolean v) {
      setUseContributorAgreementsRaw(v.name());
    }
    private final native void setUseContributorAgreementsRaw(String v)
    /*-{ if(v)this.use_contributor_agreements=v; }-*/;

    final void setUseContentMerge(InheritableBoolean v) {
      setUseContentMergeRaw(v.name());
    }
    private final native void setUseContentMergeRaw(String v)
    /*-{ if(v)this.use_content_merge=v; }-*/;

    final void setUseSignedOffBy(InheritableBoolean v) {
      setUseSignedOffByRaw(v.name());
    }
    private final native void setUseSignedOffByRaw(String v)
    /*-{ if(v)this.use_signed_off_by=v; }-*/;

    final void setRequireChangeId(InheritableBoolean v) {
      setRequireChangeIdRaw(v.name());
    }
    private final native void setRequireChangeIdRaw(String v)
    /*-{ if(v)this.require_change_id=v; }-*/;

    final native void setMaxObjectSizeLimit(String l)
    /*-{ if(l)this.max_object_size_limit=l; }-*/;

    final void setSubmitType(SubmitType t) {
      setSubmitTypeRaw(t.name());
    }
    private final native void setSubmitTypeRaw(String t)
    /*-{ if(t)this.submit_type=t; }-*/;

    final void setState(Project.State s) {
      setStateRaw(s.name());
    }
    private final native void setStateRaw(String s)
    /*-{ if(s)this.state=s; }-*/;
  }

  private static class BranchInput extends JavaScriptObject {
    static BranchInput create() {
      return (BranchInput) createObject();
    }

    protected BranchInput() {
    }

    final native void setRevision(String r) /*-{ if(r)this.revision=r; }-*/;
  }

  private static class DescriptionInput extends JavaScriptObject {
    static DescriptionInput create() {
      return (DescriptionInput) createObject();
    }

    protected DescriptionInput() {
    }

    final native void setDescription(String d) /*-{ if(d)this.description=d; }-*/;
  }
}
