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
import static com.google.gerrit.server.group.SystemGroupBackend.PROJECT_OWNERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.Util.category;
import static com.google.gerrit.server.project.Util.grant;
import static com.google.gerrit.server.project.Util.value;
import static org.junit.Assert.assertEquals;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.Permission;
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
public class DefaultLabelValuelIT extends AbstractDaemonTest {

  @Inject
  private ProjectCache projectCache;

  @Inject
  private AllProjectsName allProjects;

  @Inject
  private MetaDataUpdate.Server metaDataUpdateFactory;

  private final LabelType Q = category("CustomLabel",
      value(3, "Most Positive"),
      value(2, "More Positive"),
      value(1, "Positive"),
      value(0, "No score"),
      value(-1, "Negative"),
      value(-2, "More Negative"),
      value(-3, "Most Negative"));

  @Before
  public void setUp() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(allProjects).getConfig();
    AccountGroup.UUID anonymousUsers =
        SystemGroupBackend.getGroup(ANONYMOUS_USERS).getUUID();
    AccountGroup.UUID projectOwners =
        SystemGroupBackend.getGroup(PROJECT_OWNERS).getUUID();
    AccountGroup.UUID registeredUsers =
        SystemGroupBackend.getGroup(REGISTERED_USERS).getUUID();
    grant(cfg, Permission.forLabel(Q.getName()), -1, 1, anonymousUsers,
        "refs/heads/*");
    grant(cfg, Permission.forLabel(Q.getName()), -2, 2, registeredUsers,
        "refs/heads/*");
    grant(cfg, Permission.forLabel(Q.getName()), -3, 3, projectOwners,
        "refs/heads/*");
    saveProjectConfig(cfg);
  }

  @Test
  public void defaultLabelValueNotSetInConfig() throws Exception {
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    ChangeInfo c = get(r.getChangeId());
    LabelInfo q = c.labels.get(Q.getName());
    assertEquals(0, (int) q.defaultValue);
  }

  @Test
  public void defaultLabelValueInLabelRanges() throws Exception {
    Q.setDefaultValue((short) -3);
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    ChangeInfo c = get(r.getChangeId());
    LabelInfo q = c.labels.get(Q.getName());
    assertEquals(-3, (int) q.defaultValue);
  }

  @Test
  public void defaultLabelValueNotInLabelRanges() throws Exception {
    Q.setDefaultValue((short) -4);
    saveLabelConfig();
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
