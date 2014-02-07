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

package com.google.gerrit.acceptance.server.project;

import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.project.Util.category;
import static com.google.gerrit.server.project.Util.grant;
import static com.google.gerrit.server.project.Util.value;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;

import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class CustomLabelIT extends AbstractDaemonTest {

  @Inject
  private ProjectCache projectCache;

  @Inject
  private AllProjectsName allProjects;

  @Inject
  private MetaDataUpdate.Server metaDataUpdateFactory;

  private final LabelType Q = category("CustomLabel",
      value(1, "Positive"),
      value(0, "No score"),
      value(-1, "Negative"));

  @Before
  public void setUp() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(allProjects).getConfig();
    AccountGroup.UUID anonymousUsers =
        SystemGroupBackend.getGroup(ANONYMOUS_USERS).getUUID();
    grant(cfg, Permission.forLabel(Q.getName()), -1, 1, anonymousUsers,
        "refs/heads/*");
    saveProjectConfig(cfg);
  }

  @Test
  public void customLabelNoOp_NegativeVoteNotBlock() throws Exception {
    Q.setFunctionName("NoOp");
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    revision(r).review(new ReviewInput().label(Q.getName(), -1));
    ChangeInfo c = get(r.getChangeId());
    LabelInfo q = c.labels.get(Q.getName());
    assertEquals(1, q.all.size());
    assertNull(q.rejected);
    assertNotNull(q.disliked);
  }

  @Test
  public void customLabelNoBlock_NegativeVoteNotBlock() throws Exception {
    Q.setFunctionName("NoBlock");
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    revision(r).review(new ReviewInput().label(Q.getName(), -1));
    ChangeInfo c = get(r.getChangeId());
    LabelInfo q = c.labels.get(Q.getName());
    assertEquals(1, q.all.size());
    assertNull(q.rejected);
    assertNotNull(q.disliked);
  }

  @Test
  public void customLabelMaxNoBlock_NegativeVoteNotBlock() throws Exception {
    Q.setFunctionName("MaxNoBlock");
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    revision(r).review(new ReviewInput().label(Q.getName(), -1));
    ChangeInfo c = get(r.getChangeId());
    LabelInfo q = c.labels.get(Q.getName());
    assertEquals(1, q.all.size());
    assertNull(q.rejected);
    assertNotNull(q.disliked);
  }

  @Test
  public void customLabelAnyWithBlock_NegativeVoteBlock() throws Exception {
    Q.setFunctionName("AnyWithBlock");
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    revision(r).review(new ReviewInput().label(Q.getName(), -1));
    ChangeInfo c = get(r.getChangeId());
    LabelInfo q = c.labels.get(Q.getName());
    assertEquals(1, q.all.size());
    assertNull(q.disliked);
    assertNotNull(q.rejected);
  }

  @Test
  public void customLabelMaxWithBlock_NegativeVoteBlock() throws Exception {
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    revision(r).review(new ReviewInput().label(Q.getName(), -1));
    ChangeInfo c = get(r.getChangeId());
    LabelInfo q = c.labels.get(Q.getName());
    assertEquals(1, q.all.size());
    assertNull(q.disliked);
    assertNotNull(q.rejected);
  }

  private void saveLabelConfig() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(allProjects).getConfig();
    cfg.getLabelSections().put(Q.getName(), Q);
    saveProjectConfig(cfg);
  }

  private void saveProjectConfig(ProjectConfig cfg) throws Exception {
    MetaDataUpdate md = metaDataUpdateFactory.create(allProjects);
    try {
      cfg.commit(md);
    } finally {
      md.close();
    }
    projectCache.evict(allProjects);
  }
}
