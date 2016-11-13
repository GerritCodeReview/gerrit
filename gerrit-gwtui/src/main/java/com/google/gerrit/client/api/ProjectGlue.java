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

package com.google.gerrit.client.api;

import com.google.gerrit.client.actions.ActionButton;
import com.google.gerrit.client.info.ActionInfo;
import com.google.gerrit.client.projects.BranchInfo;
import com.google.gerrit.client.projects.ProjectApi;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JavaScriptObject;

public class ProjectGlue {
  public static void onAction(
      Project.NameKey project, BranchInfo branch, ActionInfo action, ActionButton button) {
    RestApi api = ProjectApi.project(project).view("branches").id(branch.ref()).view(action.id());
    JavaScriptObject f = branchAction(action.id());
    if (f != null) {
      ActionContext c = ActionContext.create(api);
      c.set(action);
      c.set(project);
      c.set(branch);
      c.button(button);
      ApiGlue.invoke(f, c);
    } else {
      DefaultActions.invoke(project, action, api);
    }
  }

  public static void onAction(Project.NameKey project, ActionInfo action, ActionButton button) {
    RestApi api = ProjectApi.project(project).view(action.id());
    JavaScriptObject f = projectAction(action.id());
    if (f != null) {
      ActionContext c = ActionContext.create(api);
      c.set(action);
      c.set(project);
      c.button(button);
      ApiGlue.invoke(f, c);
    } else {
      DefaultActions.invoke(project, action, api);
    }
  }

  private static native JavaScriptObject projectAction(String id) /*-{
    return $wnd.Gerrit.project_actions[id];
  }-*/;

  private static native JavaScriptObject branchAction(String id) /*-{
    return $wnd.Gerrit.branch_actions[id];
  }-*/;

  private ProjectGlue() {}
}
