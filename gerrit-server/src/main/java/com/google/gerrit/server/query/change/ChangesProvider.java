// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import com.google.gerrit.common.data.SearchChangesInfo;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** Provides {@link SearchChangesInfo}. */
public class ChangesProvider implements Provider<SearchChangesInfo> {
  private static final Logger log =
      LoggerFactory.getLogger(ChangesProvider.class);

  private final ReviewDb db;
  private final CurrentUser user;
  private final ProjectControl.Factory projectControlFactory;

  private SearchChangesInfo changeList;

  @Inject
  public ChangesProvider(final ReviewDb db,
      final ProjectControl.Factory projectControlFactory, final CurrentUser user) {
    this.db = db;
    this.user = user;
    this.projectControlFactory = projectControlFactory;
  }

  @SuppressWarnings("deprecation")
  private void setChangeList() {
    final List<Project.NameKey> repoList = new ArrayList<Project.NameKey>();
    final TreeMap<String, Change> changeMap = new TreeMap<String, Change>();

    try {
      for (final Change change : db.changes().all()) {
        final String changeKey = change.getKey().get().substring(1);
        changeMap.put(changeKey, change);

        final Project.NameKey repoName = change.getDest().getParentKey();
        if (!repoList.contains(repoName)) {
          try {
            final ProjectControl c = projectControlFactory.controlFor(repoName);
            if (user.isAdministrator() || c.isVisible() || c.isOwner()) {
              repoList.add(repoName);
            }
          } catch (NoSuchProjectException e) {
            continue;
          }
        }
      }
    } catch (OrmException e) {
      log.error("Cannot query the database" + e);
    }

    changeList = new SearchChangesInfo(changeMap, repoList);
  }

  @Override
  public SearchChangesInfo get() {
    if (changeList == null) {
      setChangeList();
    }
    return changeList;
  }
}
