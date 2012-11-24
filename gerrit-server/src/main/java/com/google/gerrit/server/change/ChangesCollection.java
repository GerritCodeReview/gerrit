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

package com.google.gerrit.server.change;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestCollection;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.QueryChanges;
import com.google.gerrit.server.util.Url;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Constants;

import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.List;

public class ChangesCollection implements
    RestCollection<TopLevelResource, ChangeResource> {
  private final Provider<ReviewDb> db;
  private final ChangeControl.Factory changeControlFactory;
  private final Provider<QueryChanges> queryFactory;
  private final DynamicMap<RestView<ChangeResource>> views;

  @Inject
  ChangesCollection(
      Provider<ReviewDb> dbProvider,
      ChangeControl.Factory changeControlFactory,
      Provider<QueryChanges> queryFactory,
      DynamicMap<RestView<ChangeResource>> views) {
    this.db = dbProvider;
    this.changeControlFactory = changeControlFactory;
    this.queryFactory = queryFactory;
    this.views = views;
  }

  @Override
  public RestView<TopLevelResource> list() {
    return queryFactory.get();
  }

  @Override
  public DynamicMap<RestView<ChangeResource>> views() {
    return views;
  }

  @Override
  public ChangeResource parse(TopLevelResource root, String id)
      throws ResourceNotFoundException, OrmException,
      UnsupportedEncodingException {
    ParsedId p = new ParsedId(id);
    List<Change> changes = findChanges(p);
    if (changes.size() != 1) {
      throw new ResourceNotFoundException(id);
    }

    ChangeControl control;
    try {
      control = changeControlFactory.validateFor(changes.get(0));
    } catch (NoSuchChangeException e) {
      throw new ResourceNotFoundException(id);
    }
    return new ChangeResource(control);
  }

  private List<Change> findChanges(ParsedId k) throws OrmException {
    if (k.legacyId != null) {
      Change c = db.get().changes().get(k.legacyId);
      if (c != null) {
        return ImmutableList.of(c);
      }
      return Collections.emptyList();
    } else if (k.project == null && k.branch == null && k.changeId != null) {
      Change.Key id = new Change.Key(k.changeId);
      if (id.get().length() == 41) {
        return db.get().changes().byKey(id).toList();
      } else {
        return db.get().changes().byKeyRange(id, id.max()).toList();
      }
    }
    return db.get().changes().byBranchKey(
        k.branch(),
        new Change.Key(k.changeId)).toList();
  }

  private static class ParsedId {
    Change.Id legacyId;
    String project;
    String branch;
    String changeId;

    ParsedId(String id) throws ResourceNotFoundException {
      if (id.matches("^[1-9][0-9]*$")) {
        legacyId = Change.Id.parse(id);
        return;
      }

      int t2 = id.lastIndexOf('~');
      int t1 = id.lastIndexOf('~', t2 - 1);
      if (t1 < 0 || t2 < 0) {
        if (!id.matches("^I[0-9a-z]{4,40}$")) {
          throw new ResourceNotFoundException(id);
        }
        changeId = id;
        return;
      }

      project = Url.decode(id.substring(0, t1));
      branch = Url.decode(id.substring(t1 + 1, t2));
      changeId = Url.decode(id.substring(t2 + 1));

      if (!branch.startsWith(Constants.R_REFS)) {
        branch = Constants.R_HEADS + branch;
      }
    }

    Branch.NameKey branch() {
      return new Branch.NameKey(new Project.NameKey(project), branch);
    }
  }
}
