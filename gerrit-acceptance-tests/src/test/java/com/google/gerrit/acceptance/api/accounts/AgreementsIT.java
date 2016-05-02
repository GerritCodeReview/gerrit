// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.accounts;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.common.data.ContributorAgreement;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.extensions.api.accounts.AccountApi;
import com.google.gerrit.extensions.api.groups.GroupApi;
import com.google.gerrit.extensions.common.AgreementInfo;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.testutil.ConfigSuite;

import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class AgreementsIT extends AbstractDaemonTest {
  private ContributorAgreement ca;

  @ConfigSuite.Default
  public static Config enableAgreementsConfig() {
    Config cfg = new Config();
    cfg.setBoolean("auth", null, "contributorAgreements", true);
    return cfg;
  }

  @Before
  public void setUp() throws Exception {
    String g = createGroup("cla-test-group");
    GroupApi groupApi = gApi.groups().id(g);
    groupApi.description("CLA test group");
    //groupApi.addMembers("user");
    GroupReference group = new GroupReference(
        new AccountGroup.UUID(groupApi.detail().id), g);
    PermissionRule rule = new PermissionRule(group);
    rule.setAction(PermissionRule.Action.ALLOW);
    ca = new ContributorAgreement(g);
    ca.setDescription("description");
    ca.setAutoVerify(group);
    ca.setAccepted(ImmutableList.of(rule));

    ProjectConfig cfg = projectCache.checkedGet(allProjects).getConfig();
    cfg.replace(ca);
    saveProjectConfig(allProjects, cfg);
    setApiUser(user);
  }

  @Test
  public void signNonExistingAgreement() throws Exception {
    exception.expect(ResourceNotFoundException.class);
    gApi.accounts().self().signAgreement("does-not-exist");
  }

  @Test
  public void signAgreement() throws Exception {
    AccountApi accountApi = gApi.accounts().self();
    List<AgreementInfo> result = accountApi.listAgreements();
    assertThat(result).isEmpty();
    accountApi.signAgreement(ca.getName());
    result = accountApi.listAgreements();
    //TODO: fails because it doesn't find the user in the group
    assertThat(result).hasSize(1);
  }
}
