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

import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.WalkSorter.PatchSetData;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.ChangeSet;
import com.google.gerrit.server.git.MergeSuperSet;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Config;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class SubmittedTogether implements RestReadView<ChangeResource> {
  private static final Logger log = LoggerFactory.getLogger(
      SubmittedTogether.class);

  // workaround for three state boolean with args4j
  // when 'branchOnly' option is not provided we would like to get
  // the default from gerrit.config
  enum IncludingTopicValues {
    TRUE, FALSE;
  }

  @Option(name = "--including-topic", aliases = {"-i"}, usage = "Control submission of whole topic")
  private IncludingTopicValues includingTopic;

  private final Config cfg;
  private final ChangeJson.Factory json;
  private final Provider<ReviewDb> dbProvider;
  private final Provider<InternalChangeQuery> queryProvider;
  private final MergeSuperSet mergeSuperSet;
  private final Provider<WalkSorter> sorter;

  @Inject
  SubmittedTogether(@GerritServerConfig Config cfg,
      ChangeJson.Factory json,
      Provider<ReviewDb> dbProvider,
      Provider<InternalChangeQuery> queryProvider,
      MergeSuperSet mergeSuperSet,
      Provider<WalkSorter> sorter) {
    this.cfg = cfg;
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
      Change c = resource.getChange();
      List<ChangeData> cds;
      if (c.getStatus().isOpen()) {
        cds = getForOpenChange(c);
      } else if (c.getStatus().asChangeStatus() == ChangeStatus.MERGED) {
        cds = getForMergedChange(c);
      } else {
        cds = getForAbandonedChange();
      }

      if (cds.size() <= 1) {
        cds = Collections.emptyList();
      } else {
        // Skip sorting for singleton lists, to avoid WalkSorter opening the
        // repo just to fill out the commit field in PatchSetData.
        cds = sort(cds);
      }

      return json.create(EnumSet.of(
          ListChangesOption.CURRENT_REVISION,
          ListChangesOption.CURRENT_COMMIT))
        .formatChangeDatas(cds);
    } catch (OrmException | IOException e) {
      log.error("Error on getting a ChangeSet", e);
      throw e;
    }
  }

  private List<ChangeData> getForOpenChange(Change c)
      throws OrmException, IOException {
    ChangeSet cs = mergeSuperSet.completeChangeSet(dbProvider.get(), c,
        includingTopic != null ? includingTopic == IncludingTopicValues.TRUE
            : Submit.wholeTopicEnabled(cfg));
    return cs.changes().asList();
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
