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
import com.google.gerrit.server.account.PerformVisibleGroups;
import com.google.inject.Inject;

public class VisibleGroups extends Handler<GroupList> {

  interface Factory {
    VisibleGroups create();
  }

  private final PerformVisibleGroups.Factory performVisibleGroupsFactory;

  @Inject
  VisibleGroups(final PerformVisibleGroups.Factory performVisibleGroupsFactory) {
    this.performVisibleGroupsFactory = performVisibleGroupsFactory;
  }

  @Override
  public GroupList call() throws Exception {
    return performVisibleGroupsFactory.create().getVisibleGroups();
  }
}
