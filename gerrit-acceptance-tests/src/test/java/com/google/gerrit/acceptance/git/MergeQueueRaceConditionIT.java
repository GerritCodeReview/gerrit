// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.acceptance.git;

import static org.junit.Assert.assertEquals;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;

import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class MergeQueueRaceConditionIT extends AbstractDaemonTest {

  @Inject
  private MetaDataUpdate.Server metaDataUpdateFactory;

  @Inject
  private ProjectCache projectCache;

  @Inject
  protected PushOneCommit.Factory pushFactory;

  @Before
  public void setUp() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    cfg.getProject().setSubmitType(SubmitType.CHERRY_PICK);
    saveProjectConfig(cfg);
  }

  @Test
  @GerritConfig(name="changeMerge.checkFrequency", value="1s")
  public void raceConditionManualMergeBackgroundScheduling() throws Exception {
    // Wait for initial delay to simulate race condition
    // between manual and background scheduling
    Thread.sleep(15000);
    for (int i = 0; i < 500; i++) {
      createAndSubmitChange("" + i);
    }
  }

  private void createAndSubmitChange(String s) throws Exception {
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), s, s, s);
    PushOneCommit.Result r = push.to(git, "refs/for/master");
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .review(ReviewInput.approve());
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .submit();

    ChangeInfo info = get(r.getChangeId());
    int size = info.revisions.values().size();
    if (size != 2) {
      int retry = 3;
      do {
        Thread.sleep(1000);
        info = get(r.getChangeId());
        size = info.revisions.values().size();
      } while (size != 2 && --retry > 0);
    }
    assertEquals(2, size);
  }

  private void saveProjectConfig(ProjectConfig cfg) throws Exception {
    MetaDataUpdate md = metaDataUpdateFactory.create(project);
    try {
      cfg.commit(md);
    } finally {
      md.close();
    }
  }
}
