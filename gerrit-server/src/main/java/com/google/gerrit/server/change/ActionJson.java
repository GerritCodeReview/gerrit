// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.common.collect.Maps;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.extensions.webui.UiActions;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Map;

public class ActionJson {
  private final Provider<CurrentUser> userProvider;
  private final Provider<ReviewDb> db;
  private final Revisions revisions;
  private final DynamicMap<RestView<ChangeResource>> changeViews;
  private final ChangeData.Factory changeDataFactory;

  @Inject
  ActionJson(
      Provider<CurrentUser> user,
      Provider<ReviewDb> db,
      Revisions revisions,
      DynamicMap<RestView<ChangeResource>> changeViews,
      ChangeData.Factory changeDataFactory) {
    this.userProvider = user;
    this.db = db;
    this.revisions = revisions;
    this.changeViews = changeViews;
    this.changeDataFactory = changeDataFactory;
  }

  public Map<String, ActionInfo> format(RevisionResource rsrc) throws OrmException {
    ChangeData cd = changeDataFactory.create(db.get(), rsrc.getControl());
    return toRevisionActionInfoMap(cd, rsrc.getPatchSet().getId());
  }

  public ChangeInfo addChangeActions(ChangeInfo to, ChangeData cd)
      throws OrmException {
    to.actions = toChangeActionInfoMap(cd);
    return to;
  }

  public RevisionInfo addRevisionActions(RevisionInfo to, ChangeData cd,
      PatchSet.Id ps) throws OrmException {
    to.actions = toRevisionActionInfoMap(cd, ps);
    return to;
  }

  private Map<String, ActionInfo> toChangeActionInfoMap(ChangeData cd)
      throws OrmException {
    Map<String, ActionInfo> out = Maps.newLinkedHashMap();
    if (userProvider.get().isIdentifiedUser()) {
      ChangeControl ctl = cd.changeControl().forUser(userProvider.get());
      for (UiAction.Description d : UiActions.from(
          changeViews,
          new ChangeResource(ctl),
          userProvider)) {
        out.put(d.getId(), new ActionInfo(d));
      }
    }
    return out;
  }

  private Map<String, ActionInfo> toRevisionActionInfoMap(ChangeData cd, PatchSet.Id in)
      throws OrmException {
    Map<String, ActionInfo> out = Maps.newLinkedHashMap();
    if (userProvider.get().isIdentifiedUser()) {
      ChangeControl ctl = cd.changeControl().forUser(userProvider.get());
      for (UiAction.Description d : UiActions.from(
          revisions,
          new RevisionResource(new ChangeResource(ctl), cd.patch(in)),
          userProvider)) {
        out.put(d.getId(), new ActionInfo(d));
      }
    }
    return out;
  }
}
