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

package com.google.gerrit.server;

import com.google.gerrit.client.data.ChangeHeader;
import com.google.gerrit.client.data.ChangeListService;
import com.google.gerrit.client.data.MineResult;
import com.google.gerrit.client.data.ProjectIdentity;
import com.google.gerrit.client.data.UserIdentity;
import com.google.gwt.user.client.rpc.AsyncCallback;

import java.util.ArrayList;
import java.util.Date;

public class ChangeListServiceImpl extends GerritJsonServlet implements ChangeListService {
  @Override
  protected Object createServiceHandle() throws Exception {
    return this;
  }

  public void mine(final AsyncCallback<MineResult> callback) {
    final MineResult r = new MineResult();
    r.byMe = new ArrayList<ChangeHeader>();
    for (int i = 10; i < 10 + 2; i++) {
      final ChangeHeader c = new ChangeHeader();
      c.id = i;
      c.subject = "Change " + i;

      c.owner = new UserIdentity();
      c.owner.fullName = "User " + i;

      c.project = new ProjectIdentity();
      c.project.name = "platform/test";
      c.lastUpdate = new Date();
      r.byMe.add(c);
    }
    callback.onSuccess(r);
  }
}
