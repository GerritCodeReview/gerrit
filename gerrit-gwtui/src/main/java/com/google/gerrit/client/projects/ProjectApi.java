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
import com.google.gerrit.client.projects.ConfigInfo.ConfigParameterValue;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.rpc.AsyncCallback;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class ProjectApi {
  /** Create a new project */
  public static void createProject(
      String projectName,
      String parent,
      Boolean createEmptyCcommit,
      Boolean permissionsOnly,
      AsyncCallback<VoidResult> cb) {
    ProjectInput input = ProjectInput.create();
    input.setName(projectName);
    input.setParent(parent);
    input.setPermissionsOnly(permissionsOnly);
    input.setCreateEmptyCommit(createEmptyCcommit);
    new RestApi("/projects/").id(projectName).ifNoneMatch().put(input, cb);
  }

  private static RestApi getRestApi(
      Project.NameKey name, String viewName, int limit, int start, String match) {
    RestApi call = project(name).view(viewName);
    call.addParameter("n", limit);
    call.addParameter("s", start);
    if (match != null) {
      if (match.startsWith("^")) {
        call.addParameter("r", match);
      } else {
        call.addParameter("m", match);
      }
    }
    return call;
  }

  /** Retrieve all visible tags of the project */
  public static void getTags(Project.NameKey name, AsyncCallback<JsArray<TagInfo>> cb) {
    project(name).view("tags").get(cb);
  }

  public static void getTags(
      Project.NameKey name,
      int limit,
      int start,
      String match,
      AsyncCallback<JsArray<TagInfo>> cb) {
    getRestApi(name, "tags", limit, start, match).get(cb);
  }

  /** Create a new branch */
  public static void createBranch(
      Project.NameKey name, String ref, String revision, AsyncCallback<BranchInfo> cb) {
    BranchInput input = BranchInput.create();
    input.setRevision(revision);
    project(name).view("branches").id(ref).ifNoneMatch().put(input, cb);
  }

  /** Retrieve all visible branches of the project */
  public static void getBranches(Project.NameKey name, AsyncCallback<JsArray<BranchInfo>> cb) {
    project(name).view("branches").get(cb);
  }

  public static void getBranches(
      Project.NameKey name,
      int limit,
      int start,
      String match,
      AsyncCallback<JsArray<BranchInfo>> cb) {
    getRestApi(name, "branches", limit, start, match).get(cb);
  }

  /** Delete branches. One call is fired to the server to delete all the branches. */
  public static void deleteBranches(
      Project.NameKey name, Set<String> refs, AsyncCallback<VoidResult> cb) {
    if (refs.size() == 1) {
      project(name).view("branches").id(refs.iterator().next()).delete(cb);
    } else {
      DeleteBranchesInput d = DeleteBranchesInput.create();
      for (String ref : refs) {
        d.addBranch(ref);
      }
      project(name).view("branches:delete").post(d, cb);
    }
  }

  public static void getConfig(Project.NameKey name, AsyncCallback<ConfigInfo> cb) {
    project(name).view("config").get(cb);
  }

  public static void setConfig(
      Project.NameKey name,
      String description,
      InheritableBoolean useContributorAgreements,
      InheritableBoolean useContentMerge,
      InheritableBoolean useSignedOffBy,
      InheritableBoolean createNewChangeForAllNotInTarget,
      InheritableBoolean requireChangeId,
      InheritableBoolean enableSignedPush,
      InheritableBoolean requireSignedPush,
      InheritableBoolean rejectImplicitMerges,
      String maxObjectSizeLimit,
      SubmitType submitType,
      ProjectState state,
      Map<String, Map<String, ConfigParameterValue>> pluginConfigValues,
      AsyncCallback<ConfigInfo> cb) {
    ConfigInput in = ConfigInput.create();
    in.setDescription(description);
    in.setUseContributorAgreements(useContributorAgreements);
    in.setUseContentMerge(useContentMerge);
    in.setUseSignedOffBy(useSignedOffBy);
    in.setRequireChangeId(requireChangeId);
    in.setCreateNewChangeForAllNotInTarget(createNewChangeForAllNotInTarget);
    if (enableSignedPush != null) {
      in.setEnableSignedPush(enableSignedPush);
    }
    if (requireSignedPush != null) {
      in.setRequireSignedPush(requireSignedPush);
    }
    in.setRejectImplicitMerges(rejectImplicitMerges);
    in.setMaxObjectSizeLimit(maxObjectSizeLimit);
    in.setSubmitType(submitType);
    in.setState(state);
    in.setPluginConfigValues(pluginConfigValues);

    project(name).view("config").put(in, cb);
  }

  public static void getParent(Project.NameKey name, final AsyncCallback<Project.NameKey> cb) {
    project(name)
        .view("parent")
        .get(
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

  public static void getChildren(
      Project.NameKey name, boolean recursive, AsyncCallback<JsArray<ProjectInfo>> cb) {
    RestApi view = project(name).view("children");
    if (recursive) {
      view.addParameterTrue("recursive");
    }
    view.get(cb);
  }

  public static void getDescription(Project.NameKey name, AsyncCallback<NativeString> cb) {
    project(name).view("description").get(cb);
  }

  public static void setDescription(
      Project.NameKey name, String description, AsyncCallback<NativeString> cb) {
    RestApi call = project(name).view("description");
    if (description != null && !description.isEmpty()) {
      DescriptionInput input = DescriptionInput.create();
      input.setDescription(description);
      call.put(input, cb);
    } else {
      call.delete(cb);
    }
  }

  public static void setHead(Project.NameKey name, String ref, AsyncCallback<NativeString> cb) {
    RestApi call = project(name).view("HEAD");
    HeadInput input = HeadInput.create();
    input.setRef(ref);
    call.put(input, cb);
  }

  public static RestApi project(Project.NameKey name) {
    return new RestApi("/projects/").id(name.get());
  }

  private static class ProjectInput extends JavaScriptObject {
    static ProjectInput create() {
      return (ProjectInput) createObject();
    }

    protected ProjectInput() {}

    final native void setName(String n) /*-{ if(n)this.name=n; }-*/;

    final native void setParent(String p) /*-{ if(p)this.parent=p; }-*/;

    final native void setPermissionsOnly(boolean po) /*-{ if(po)this.permissions_only=po; }-*/;

    final native void setCreateEmptyCommit(boolean cc) /*-{ if(cc)this.create_empty_commit=cc; }-*/;
  }

  private static class ConfigInput extends JavaScriptObject {
    static ConfigInput create() {
      return (ConfigInput) createObject();
    }

    protected ConfigInput() {}

    final native void setDescription(String d) /*-{ if(d)this.description=d; }-*/;

    final void setUseContributorAgreements(InheritableBoolean v) {
      setUseContributorAgreementsRaw(v.name());
    }

    private native void setUseContributorAgreementsRaw(String v)
        /*-{ if(v)this.use_contributor_agreements=v; }-*/ ;

    final void setUseContentMerge(InheritableBoolean v) {
      setUseContentMergeRaw(v.name());
    }

    private native void setUseContentMergeRaw(String v) /*-{ if(v)this.use_content_merge=v; }-*/;

    final void setUseSignedOffBy(InheritableBoolean v) {
      setUseSignedOffByRaw(v.name());
    }

    private native void setUseSignedOffByRaw(String v) /*-{ if(v)this.use_signed_off_by=v; }-*/;

    final void setRequireChangeId(InheritableBoolean v) {
      setRequireChangeIdRaw(v.name());
    }

    private native void setRequireChangeIdRaw(String v) /*-{ if(v)this.require_change_id=v; }-*/;

    final void setCreateNewChangeForAllNotInTarget(InheritableBoolean v) {
      setCreateNewChangeForAllNotInTargetRaw(v.name());
    }

    private native void setCreateNewChangeForAllNotInTargetRaw(String v)
        /*-{ if(v)this.create_new_change_for_all_not_in_target=v; }-*/ ;

    final void setEnableSignedPush(InheritableBoolean v) {
      setEnableSignedPushRaw(v.name());
    }

    private native void setEnableSignedPushRaw(String v) /*-{ if(v)this.enable_signed_push=v; }-*/;

    final void setRequireSignedPush(InheritableBoolean v) {
      setRequireSignedPushRaw(v.name());
    }

    private native void setRequireSignedPushRaw(String v)
        /*-{ if(v)this.require_signed_push=v; }-*/ ;

    final void setRejectImplicitMerges(InheritableBoolean v) {
      setRejectImplicitMergesRaw(v.name());
    }

    private native void setRejectImplicitMergesRaw(String v)
        /*-{ if(v)this.reject_implicit_merges=v; }-*/ ;

    final native void setMaxObjectSizeLimit(String l) /*-{ if(l)this.max_object_size_limit=l; }-*/;

    final void setSubmitType(SubmitType t) {
      setSubmitTypeRaw(t.name());
    }

    private native void setSubmitTypeRaw(String t) /*-{ if(t)this.submit_type=t; }-*/;

    final void setState(ProjectState s) {
      setStateRaw(s.name());
    }

    private native void setStateRaw(String s) /*-{ if(s)this.state=s; }-*/;

    final void setPluginConfigValues(
        Map<String, Map<String, ConfigParameterValue>> pluginConfigValues) {
      if (!pluginConfigValues.isEmpty()) {
        NativeMap<ConfigParameterValueMap> configValues = NativeMap.create().cast();
        for (Entry<String, Map<String, ConfigParameterValue>> e : pluginConfigValues.entrySet()) {
          ConfigParameterValueMap values = ConfigParameterValueMap.create();
          configValues.put(e.getKey(), values);
          for (Entry<String, ConfigParameterValue> e2 : e.getValue().entrySet()) {
            values.put(e2.getKey(), e2.getValue());
          }
        }
        setPluginConfigValuesRaw(configValues);
      }
    }

    private native void setPluginConfigValuesRaw(NativeMap<ConfigParameterValueMap> v)
        /*-{ this.plugin_config_values=v; }-*/ ;
  }

  private static class ConfigParameterValueMap extends JavaScriptObject {
    static ConfigParameterValueMap create() {
      return createObject().cast();
    }

    protected ConfigParameterValueMap() {}

    public final native void put(String n, ConfigParameterValue v) /*-{ this[n] = v; }-*/;
  }

  private static class BranchInput extends JavaScriptObject {
    static BranchInput create() {
      return (BranchInput) createObject();
    }

    protected BranchInput() {}

    final native void setRevision(String r) /*-{ if(r)this.revision=r; }-*/;
  }

  private static class DescriptionInput extends JavaScriptObject {
    static DescriptionInput create() {
      return (DescriptionInput) createObject();
    }

    protected DescriptionInput() {}

    final native void setDescription(String d) /*-{ if(d)this.description=d; }-*/;
  }

  private static class HeadInput extends JavaScriptObject {
    static HeadInput create() {
      return createObject().cast();
    }

    protected HeadInput() {}

    final native void setRef(String r) /*-{ if(r)this.ref=r; }-*/;
  }

  private static class DeleteBranchesInput extends JavaScriptObject {
    static DeleteBranchesInput create() {
      DeleteBranchesInput d = createObject().cast();
      d.init();
      return d;
    }

    protected DeleteBranchesInput() {}

    final native void init() /*-{ this.branches = []; }-*/;

    final native void addBranch(String b) /*-{ this.branches.push(b); }-*/;
  }
}
