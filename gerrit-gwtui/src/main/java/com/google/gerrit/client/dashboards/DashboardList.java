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

import com.google.gerrit.client.rpc.NativeList;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.rpc.AsyncCallback;

/** Project dashboards from {@code /projects/<name>/dashboards/}. */
public class DashboardList extends NativeList<DashboardInfo> {
  public static void all(Project.NameKey project,
      AsyncCallback<NativeList<DashboardList>> callback) {
    new RestApi(base(project))
      .addParameterTrue("inherited")
      .get(callback);
  }

  public static void getDefault(Project.NameKey project,
      AsyncCallback<DashboardInfo> callback) {
    new RestApi(base(project) + "default")
      .addParameterTrue("inherited")
      .get(callback);
  }

  public static void get(Project.NameKey project, String dashboardId,
      AsyncCallback<DashboardInfo> callback) {
    new RestApi(base(project) + encodeDashboardId(dashboardId))
        .get(callback);
  }

  private static String base(Project.NameKey project) {
    String name = URL.encodePathSegment(project.get());
    return "/projects/" + name + "/dashboards/";
  }

  private static String encodeDashboardId(String dashboardId) {
    int c = dashboardId.indexOf(":");
    if (0 <= c) {
      final String ref = URL.encodePathSegment(dashboardId.substring(0, c));
      final String path = URL.encodePathSegment(dashboardId.substring(c + 1));
      return ref + ":" + path;
    } else {
      return URL.encodePathSegment(dashboardId);
    }
  }

  protected DashboardList() {
  }
}
