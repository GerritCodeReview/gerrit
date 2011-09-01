// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.account;

import com.google.gerrit.common.data.GroupList;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.server.account.VisibleGroups;
import com.google.inject.Inject;

public class VisibleGroupsHandler extends Handler<GroupList> {

  interface Factory {
    VisibleGroupsHandler create();
  }

  private final VisibleGroups.Factory visibleGroupsFactory;

  @Inject
  VisibleGroupsHandler(final VisibleGroups.Factory performVisibleGroupsFactory) {
    this.visibleGroupsFactory = performVisibleGroupsFactory;
  }

  @Override
  public GroupList call() throws Exception {
    return visibleGroupsFactory.create().get();
  }
}
