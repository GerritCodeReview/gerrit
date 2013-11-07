// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.rules;

import static com.google.gerrit.common.data.Permission.LABEL;
import static com.google.gerrit.server.project.Util.value;
import static com.google.gerrit.server.project.Util.category;
import static com.google.gerrit.server.project.Util.REGISTERED;
import static com.google.gerrit.server.project.Util.grant;

import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.Util;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.AbstractModule;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

public class GerritCommonTest extends PrologTestCase {
  private final LabelType V = category("Verified",
      value(1, "Verified"),
      value(0, "No score"),
      value(-1, "Fails"));
  private final LabelType Q = category("Qualified",
      value(1, "Qualified"),
      value(0, "No score"),
      value(-1, "Fails"));

  private final Project.NameKey localKey = new Project.NameKey("local");
  private ProjectConfig local;
  private Util util;

  @Before
  public void setUp() throws Exception {
    util = new Util();
    load("gerrit", "gerrit_common_test.pl", new AbstractModule() {
      @Override
      protected void configure() {
        bind(PrologEnvironment.Args.class).toInstance(
            new PrologEnvironment.Args(
                null,
                null,
                null,
                null,
                null,
                null));
      }
    });

    local = new ProjectConfig(localKey);
    local.createInMemory();
    Q.setRefPatterns(Arrays.asList("refs/heads/develop"));

    local.getLabelSections().put(V.getName(), V);
    local.getLabelSections().put(Q.getName(), Q);
    util.add(local);
    grant(local, LABEL + V.getName(), -1, +1, REGISTERED, "refs/heads/*");
    grant(local, LABEL + Q.getName(), -1, +1, REGISTERED, "refs/heads/master");
  }

  @Override
  protected void setUpEnvironment(PrologEnvironment env) {
    Change change =
        new Change(new Change.Key("Ibeef"), new Change.Id(1),
            new Account.Id(2),
            new Branch.NameKey(localKey, "refs/heads/master"),
            TimeUtil.nowTs());
    env.set(StoredValues.CHANGE, change);
    env.set(StoredValues.CHANGE_CONTROL, util.user(local).controlFor(change));
  }

  @Test
  public void testGerritCommon() {
    runPrologBasedTests();
  }
}
