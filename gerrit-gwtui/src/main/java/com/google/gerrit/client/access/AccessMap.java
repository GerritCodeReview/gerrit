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

package com.google.gerrit.client.access;

import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.user.client.rpc.AsyncCallback;
import java.util.Collections;
import java.util.Set;

/** Access rights available from {@code /access/}. */
public class AccessMap extends NativeMap<ProjectAccessInfo> {
  public static void get(Set<Project.NameKey> projects, AsyncCallback<AccessMap> callback) {
    RestApi api = new RestApi("/access/");
    for (Project.NameKey p : projects) {
      api.addParameter("project", p.get());
    }
    api.get(NativeMap.copyKeysIntoChildren(callback));
  }

  public static void get(final Project.NameKey project, final AsyncCallback<ProjectAccessInfo> cb) {
    get(
        Collections.singleton(project),
        new AsyncCallback<AccessMap>() {
          @Override
          public void onSuccess(AccessMap result) {
            cb.onSuccess(result.get(project.get()));
          }

          @Override
          public void onFailure(Throwable caught) {
            cb.onFailure(caught);
          }
        });
  }

  protected AccessMap() {}
}
