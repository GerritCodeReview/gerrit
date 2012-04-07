// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc;

import com.google.gerrit.common.data.ChangeListService;
import com.google.gerrit.common.data.ToggleStarRequest;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.StarredChange;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ChangeListServiceImpl extends BaseServiceImplementation implements
    ChangeListService {
  private final Provider<CurrentUser> currentUser;
  private final ChangeControl.Factory changeControlFactory;

  @Inject
  ChangeListServiceImpl(final Provider<ReviewDb> schema,
      final Provider<CurrentUser> currentUser,
      final ChangeControl.Factory changeControlFactory) {
    super(schema, currentUser);
    this.currentUser = currentUser;
    this.changeControlFactory = changeControlFactory;
  }

  private boolean canRead(final Change c, final ReviewDb db) throws OrmException {
    try {
      return changeControlFactory.controlFor(c).isVisible(db);
    } catch (NoSuchChangeException e) {
      return false;
    }
  }

  public void toggleStars(final ToggleStarRequest req,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException {
        final Account.Id me = getAccountId();
        final Set<Change.Id> existing = currentUser.get().getStarredChanges();
        List<StarredChange> add = new ArrayList<StarredChange>();
        List<StarredChange.Key> remove = new ArrayList<StarredChange.Key>();

        if (req.getAddSet() != null) {
          for (final Change.Id id : req.getAddSet()) {
            if (!existing.contains(id)) {
              add.add(new StarredChange(new StarredChange.Key(me, id)));
            }
          }
        }

        if (req.getRemoveSet() != null) {
          for (final Change.Id id : req.getRemoveSet()) {
            remove.add(new StarredChange.Key(me, id));
          }
        }

        db.starredChanges().insert(add);
        db.starredChanges().deleteKeys(remove);
        return VoidResult.INSTANCE;
      }
    });
  }
}
