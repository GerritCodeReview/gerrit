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

package com.google.gerrit.client.account;

import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.user.client.rpc.AsyncCallback;

/**
 * Capabilities the call has on a project from
 * {@code /accounts/self/projects/<project-name>/capabilities}.
 */
public class ProjectCapabilities extends JavaScriptObject {
  public static final String CREATE_REF = "createRef";

  public static void all(Project.NameKey project,
      AsyncCallback<ProjectCapabilities> cb, String... filter) {
    new RestApi("/accounts/self/projects").id(project.get())
      .view("capabilities")
      .addParameter("q", filter)
      .get(cb);
  }

  protected ProjectCapabilities() {
  }

  public final native boolean canPerform(String name)
  /*-{ return this[name] ? true : false; }-*/;
}