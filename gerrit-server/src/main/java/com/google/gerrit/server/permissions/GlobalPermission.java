// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.permissions;

import com.google.gerrit.common.data.GlobalCapability;
import java.util.Locale;

public enum GlobalPermission {
  ACCESS_DATABASE(GlobalCapability.ACCESS_DATABASE),
  ADMINISTRATE_SERVER(GlobalCapability.ADMINISTRATE_SERVER),
  CREATE_ACCOUNT(GlobalCapability.CREATE_ACCOUNT),
  CREATE_GROUP(GlobalCapability.CREATE_GROUP),
  CREATE_PROJECT(GlobalCapability.CREATE_PROJECT),
  EMAIL_REVIEWERS(GlobalCapability.EMAIL_REVIEWERS),
  FLUSH_CACHES(GlobalCapability.FLUSH_CACHES),
  KILL_TASK(GlobalCapability.KILL_TASK),
  MAINTAIN_SERVER(GlobalCapability.MAINTAIN_SERVER),
  MODIFY_ACCOUNT(GlobalCapability.MODIFY_ACCOUNT),
  RUN_AS(GlobalCapability.RUN_AS),
  RUN_GC(GlobalCapability.RUN_GC),
  STREAM_EVENTS(GlobalCapability.STREAM_EVENTS),
  VIEW_ALL_ACCOUNTS(GlobalCapability.VIEW_ALL_ACCOUNTS),
  VIEW_CACHES(GlobalCapability.VIEW_CACHES),
  VIEW_CONNECTIONS(GlobalCapability.VIEW_CONNECTIONS),
  VIEW_PLUGINS(GlobalCapability.VIEW_PLUGINS),
  VIEW_QUEUE(GlobalCapability.VIEW_QUEUE);

  private final String name;

  GlobalPermission(String name) {
    this.name = name;
  }

  /** @return name used in {@code project.config} permissions. */
  public String permissionName() {
    return name;
  }

  public String describeForException() {
    return toString().toLowerCase(Locale.US).replace('_', ' ');
  }
}
