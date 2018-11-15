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

package com.google.gerrit.client.dashboards;

import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.rpc.AsyncCallback;

/** Project dashboards from {@code /projects/<name>/dashboards/}. */
public class DashboardList extends JsArray<DashboardInfo> {
  public static void all(Project.NameKey project, AsyncCallback<JsArray<DashboardList>> callback) {
    base(project).addParameterTrue("inherited").get(callback);
  }

  public static void getDefault(Project.NameKey project, AsyncCallback<DashboardInfo> callback) {
    base(project).view("default").addParameterTrue("inherited").get(callback);
  }

  public static void get(
      Project.NameKey project, String id, AsyncCallback<DashboardInfo> callback) {
    base(project).idRaw(encodeDashboardId(id)).get(callback);
  }

  private static RestApi base(Project.NameKey project) {
    return new RestApi("/projects/").id(project.get()).view("dashboards");
  }

  private static String encodeDashboardId(String id) {
    int c = id.indexOf(':');
    if (0 <= c) {
      String ref = URL.encodeQueryString(id.substring(0, c));
      String path = URL.encodeQueryString(id.substring(c + 1));
      return ref + ':' + path;
    }
    return URL.encodeQueryString(id);
  }

  protected DashboardList() {}
}
