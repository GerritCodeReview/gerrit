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

package com.google.gerrit.server.account;

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.StarredChange;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.StarredChanges.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.List;
import java.util.Set;

class StarredChanges implements RestModifyView<AccountResource, Input> {
  static class Input {
    List<String> on;
    List<String> off;

    static Input init(Input in) {
      if (in == null) {
        in = new Input();
      }
      if (in.on == null) {
        in.on = Lists.newArrayList();
      }
      if (in.off == null) {
        in.off = Lists.newArrayList();
      }
      return in;
    }
  }

  private final Provider<CurrentUser> currentUser;
  private final ReviewDb db;

  @Inject
  StarredChanges(Provider<CurrentUser> currentUser, ReviewDb db) {
    this.currentUser = currentUser;
    this.db = db;
  }

  @Override
  public Object apply(AccountResource rsrc, Input in)
      throws OrmException {
    Input.init(in);
    Account.Id me = rsrc.getUser().getAccountId();
    Set<Change.Id> existing = currentUser.get().getStarredChanges();
    List<StarredChange> add = Lists.newArrayList();
    List<StarredChange.Key> remove = Lists.newArrayList();

    for (String id : in.on) {
      if (!existing.contains(id)) {
        add.add(
            new StarredChange(new StarredChange.Key(me, Change.Id.parse(id))));
      }
    }

    for (String id : in.off) {
      remove.add(new StarredChange.Key(me, Change.Id.parse(id)));
    }

    db.starredChanges().insert(add);
    db.starredChanges().deleteKeys(remove);
    return Response.none();
  }
}
