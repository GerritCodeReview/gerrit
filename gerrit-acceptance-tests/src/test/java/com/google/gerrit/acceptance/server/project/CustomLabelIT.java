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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.project.Util.category;
import static com.google.gerrit.server.project.Util.value;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.Util;

import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class CustomLabelIT extends AbstractDaemonTest {

  private final LabelType label = category("CustomLabel",
      value(1, "Positive"),
      value(0, "No score"),
      value(-1, "Negative"));

  @Before
  public void setUp() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    AccountGroup.UUID anonymousUsers =
        SystemGroupBackend.getGroup(ANONYMOUS_USERS).getUUID();
    Util.allow(cfg, Permission.forLabel(label.getName()), -1, 1, anonymousUsers,
        "refs/heads/*");
    saveProjectConfig(project, cfg);
  }

  @Test
  public void customLabelNoOp_NegativeVoteNotBlock() throws Exception {
    label.setFunctionName("NoOp");
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    revision(r).review(new ReviewInput().label(label.getName(), -1));
    ChangeInfo c = get(r.getChangeId());
    LabelInfo q = c.labels.get(label.getName());
    assertThat(q.all).hasSize(1);
    assertThat(q.rejected).isNotNull();
    assertThat(q.blocking).isNull();
  }

  @Test
  public void customLabelNoBlock_NegativeVoteNotBlock() throws Exception {
    label.setFunctionName("NoBlock");
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    revision(r).review(new ReviewInput().label(label.getName(), -1));
    ChangeInfo c = get(r.getChangeId());
    LabelInfo q = c.labels.get(label.getName());
    assertThat(q.all).hasSize(1);
    assertThat(q.rejected).isNotNull();
    assertThat(q.blocking).isNull();
  }

  @Test
  public void customLabelMaxNoBlock_NegativeVoteNotBlock() throws Exception {
    label.setFunctionName("MaxNoBlock");
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    revision(r).review(new ReviewInput().label(label.getName(), -1));
    ChangeInfo c = get(r.getChangeId());
    LabelInfo q = c.labels.get(label.getName());
    assertThat(q.all).hasSize(1);
    assertThat(q.rejected).isNotNull();
    assertThat(q.blocking).isNull();
  }

  @Test
  public void customLabelAnyWithBlock_NegativeVoteBlock() throws Exception {
    label.setFunctionName("AnyWithBlock");
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    revision(r).review(new ReviewInput().label(label.getName(), -1));
    ChangeInfo c = get(r.getChangeId());
    LabelInfo q = c.labels.get(label.getName());
    assertThat(q.all).hasSize(1);
    assertThat(q.disliked).isNull();
    assertThat(q.rejected).isNotNull();
    assertThat(q.blocking).isTrue();
  }

  @Test
  public void customLabelMaxWithBlock_NegativeVoteBlock() throws Exception {
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    revision(r).review(new ReviewInput().label(label.getName(), -1));
    ChangeInfo c = get(r.getChangeId());
    LabelInfo q = c.labels.get(label.getName());
    assertThat(q.all).hasSize(1);
    assertThat(q.disliked).isNull();
    assertThat(q.rejected).isNotNull();
    assertThat(q.blocking).isTrue();
  }

  private void saveLabelConfig() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    cfg.getLabelSections().put(label.getName(), label);
    saveProjectConfig(project, cfg);
  }
}
