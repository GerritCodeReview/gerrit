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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.WalkSorter.PatchSetData;
import com.google.gerrit.server.git.ChangeSet;
import com.google.gerrit.server.git.MergeSuperSet;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

@Singleton
public class SubmittedTogether implements RestReadView<ChangeResource> {
  private static final Logger log = LoggerFactory.getLogger(
      SubmittedTogether.class);

  private final ChangeJson.Factory json;
  private final Provider<ReviewDb> dbProvider;
  private final Provider<InternalChangeQuery> queryProvider;
  private final MergeSuperSet mergeSuperSet;
  private final Provider<WalkSorter> sorter;

  @Inject
  SubmittedTogether(ChangeJson.Factory json,
      Provider<ReviewDb> dbProvider,
      Provider<InternalChangeQuery> queryProvider,
      MergeSuperSet mergeSuperSet,
      Provider<WalkSorter> sorter) {
    this.json = json;
    this.dbProvider = dbProvider;
    this.queryProvider = queryProvider;
    this.mergeSuperSet = mergeSuperSet;
    this.sorter = sorter;
  }

  @Override
  public List<ChangeInfo> apply(ChangeResource resource)
      throws AuthException, BadRequestException,
      ResourceConflictException, Exception {
    try {
      boolean addHiddenDummy = false;
      Change c = resource.getChange();
      List<ChangeData> cds;
      if (c.getStatus().isOpen()) {
        ChangeSet cs = getForOpenChange(c, resource.getControl().getUser());
        cds = cs.changes().asList();
        addHiddenDummy = !cs.isComplete();
      } else if (c.getStatus().asChangeStatus() == ChangeStatus.MERGED) {
        cds = getForMergedChange(c);
      } else {
        cds = getForAbandonedChange();
      }

      if (cds.size() <= 1 && !addHiddenDummy) {
        cds = Collections.emptyList();
      } else {
        // Skip sorting for singleton lists, to avoid WalkSorter opening the
        // repo just to fill out the commit field in PatchSetData.
        cds = sort(cds);
      }
      List<ChangeInfo> ret = json.create(EnumSet.of(
          ListChangesOption.CURRENT_REVISION,
          ListChangesOption.CURRENT_COMMIT))
        .formatChangeDatas(cds);
      if (addHiddenDummy) {
        // Instead of crafting a fake entry here, we just copy the first entry
        // from the list and modify it enough to get the message across.
        // TODO(sbeller): cleanup to a hand crafted real fake?
        List<ChangeInfo> r = json.create(EnumSet.of(
            ListChangesOption.CURRENT_REVISION,
            ListChangesOption.CURRENT_COMMIT))
          .formatChangeDatas(ImmutableList.of(cds.get(0)));
        ChangeInfo i = r.get(0);
        i.revisions.get(i.currentRevision).commit.subject = "Some changes are not visible";
        i.subject = "Some changes are not visible";
        i.project = null;
        i.branch = null;
        i.submittable = false;
        i.mergeable = false;
        i.changeId = null;
        i._number = 0;
        ret.add(i);
      }
      return ret;
    } catch (OrmException | IOException e) {
      log.error("Error on getting a ChangeSet", e);
      throw e;
    }
  }

  private ChangeSet getForOpenChange(Change c, CurrentUser user)
      throws OrmException, IOException {
    return mergeSuperSet.completeChangeSet(dbProvider.get(), c, user);
  }

  private List<ChangeData> getForMergedChange(Change c) throws OrmException {
    return queryProvider.get().bySubmissionId(c.getSubmissionId());
  }

  private List<ChangeData> getForAbandonedChange() {
    return Collections.emptyList();
  }

  private List<ChangeData> sort(List<ChangeData> cds)
      throws OrmException, IOException {
    List<ChangeData> sorted = new ArrayList<>(cds.size());
    for (PatchSetData psd : sorter.get().sort(cds)) {
      sorted.add(psd.data());
    }
    return sorted;
  }
}
