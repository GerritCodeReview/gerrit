// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import com.google.gerrit.entities.Project.NameKey;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.rules.RuleChain;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/** Implements {@link DaemonTestRule} using {@link GerritServer}. */
public class GerritServerDaemonTestRule implements DaemonTestRule {
  public static GerritServerDaemonTestRule create(AbstractDaemonTest test) {
    TestConfigRule configRule = new TestConfigRule(AbstractDaemonTest.temporaryFolder, test);
    GerritServerTestRule server =
        new GerritServerTestRule(
            configRule,
            AbstractDaemonTest.temporaryFolder,
            () -> test.createModule(),
            () -> test.createAuditModule(),
            () -> test.createSshModule());
    TimeSettingsTestRule timeSettingsRule = new TimeSettingsTestRule(configRule);
    return new GerritServerDaemonTestRule(test, configRule, server, timeSettingsRule);
  }

  private final RuleChain ruleChain;
  private final TestConfigRule configRule;
  private final ServerTestRule server;

  private final TimeSettingsTestRule timeSettingsRule;

  private final AbstractDaemonTest test;

  private GerritServerDaemonTestRule(
      AbstractDaemonTest test,
      TestConfigRule configRule,
      ServerTestRule server,
      TimeSettingsTestRule timeSettingsRule) {
    this.configRule = configRule;
    this.server = server;
    this.timeSettingsRule = timeSettingsRule;
    this.test = test;
    // Set the clock step as almost the last step, so that the test setup isn't consuming any
    // timestamps after the
    // clock has been set.
    ruleChain =
        RuleChain.outerRule(configRule)
            .around(server)
            .around(test.testRunner)
            .around(timeSettingsRule);
  }

  @Override
  public TestConfigRule configRule() {
    return configRule;
  }

  @Override
  public ServerTestRule server() {
    return server;
  }

  @Override
  public TimeSettingsTestRule timeSettingsRule() {
    return timeSettingsRule;
  }

  @Override
  public TestRepository<InMemoryRepository> cloneProject(NameKey p, TestAccount testAccount)
      throws Exception {
    return GitUtil.cloneProject(p, test.registerRepoConnection(p, testAccount));
  }

  @Override
  public String name(String name) {
    return test.resourcePrefix + name;
  }

  @Override
  public Statement apply(Statement statement, Description description) {
    return ruleChain.apply(statement, description);
  }
}
