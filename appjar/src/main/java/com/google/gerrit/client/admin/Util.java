// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.admin;

import com.google.gwt.core.client.GWT;
import com.google.gwtjsonrpc.client.JsonUtil;

public class Util {
  public static final AdminConstants C = GWT.create(AdminConstants.class);
  public static final AdminMessages M = GWT.create(AdminMessages.class);
  public static final GroupAdminService GROUP_SVC;

  static {
    GROUP_SVC = GWT.create(GroupAdminService.class);
    JsonUtil.bind(GROUP_SVC, "rpc/GroupAdminService");
  }
}
