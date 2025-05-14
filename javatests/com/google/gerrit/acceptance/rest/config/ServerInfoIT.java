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

package com.google.gerrit.acceptance.rest.config;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.config.GerritInstanceIdProvider.INSTANCE_ID_SYSTEM_PROPERTY;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.config.GerritSystemProperty;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.extensions.api.plugins.InstallPluginInput;
import com.google.gerrit.extensions.client.AccountFieldName;
import com.google.gerrit.extensions.client.AuthType;
import com.google.gerrit.extensions.common.AccountDefaultDisplayName;
import com.google.gerrit.extensions.common.AccountVisibility;
import com.google.gerrit.extensions.common.MetadataInfo;
import com.google.gerrit.extensions.common.ServerInfo;
import com.google.gerrit.server.ServerStateProvider;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.config.AnonymousCowardNameProvider;
import com.google.inject.Inject;
import java.util.ArrayList;
import org.junit.Test;

@NoHttpd
@UseSsh
public class ServerInfoIT extends AbstractDaemonTest {
  private static final byte[] JS_PLUGIN_CONTENT =
      "Gerrit.install(function(self){});\n".getBytes(UTF_8);

  @Inject protected ExtensionRegistry extensionRegistry;

  @Test
  // accounts
  @GerritConfig(name = "accounts.visibility", value = "VISIBLE_GROUP")
  @GerritConfig(name = "accounts.defaultDisplayName", value = "FIRST_NAME")

  // auth
  @GerritConfig(name = "auth.type", value = "HTTP")
  @GerritConfig(name = "auth.contributorAgreements", value = "true")
  @GerritConfig(name = "auth.loginUrl", value = "https://example.com/login")
  @GerritConfig(name = "auth.loginText", value = "LOGIN")
  @GerritConfig(name = "auth.switchAccountUrl", value = "https://example.com/switch")

  // auth fields ignored when auth == HTTP
  @GerritConfig(name = "auth.registerUrl", value = "https://example.com/register")
  @GerritConfig(name = "auth.registerText", value = "REGISTER")
  @GerritConfig(name = "auth.editFullNameUrl", value = "https://example.com/editname")
  @GerritConfig(name = "auth.httpPasswordUrl", value = "https://example.com/password")

  // change
  @GerritConfig(name = "change.updateDelay", value = "50s")
  @GerritConfig(name = "change.disablePrivateChanges", value = "true")
  @GerritConfig(name = "change.enableRobotComments", value = "false")
  @GerritConfig(name = "change.allowMarkdownBase64ImagesInComments", value = "false")
  // download
  @GerritConfig(
      name = "download.archive",
      values = {"tar", "tbz2", "tgz", "txz"})

  // gerrit
  @GerritConfig(name = "gerrit.allProjects", value = "Root")
  @GerritConfig(name = "gerrit.allUsers", value = "Users")
  @GerritConfig(name = "gerrit.reportBugUrl", value = "https://example.com/report")
  @GerritConfig(name = "gerrit.instanceId", value = "devops-instance")

  // suggest
  @GerritConfig(name = "suggest.from", value = "3")

  // user
  @GerritConfig(name = "user.anonymousCoward", value = "Unnamed User")
  public void serverConfig() throws Exception {
    ServerInfo i = gApi.config().server().getInfo();

    // accounts
    assertThat(i.accounts.visibility).isEqualTo(AccountVisibility.VISIBLE_GROUP);
    assertThat(i.accounts.defaultDisplayName).isEqualTo(AccountDefaultDisplayName.FIRST_NAME);

    // auth
    assertThat(i.auth.authType).isEqualTo(AuthType.HTTP);
    assertThat(i.auth.editableAccountFields)
        .containsExactly(AccountFieldName.REGISTER_NEW_EMAIL, AccountFieldName.FULL_NAME);
    assertThat(i.auth.useContributorAgreements).isTrue();
    assertThat(i.auth.loginUrl).isEqualTo("https://example.com/login");
    assertThat(i.auth.loginText).isEqualTo("LOGIN");
    assertThat(i.auth.switchAccountUrl).isEqualTo("https://example.com/switch");
    assertThat(i.auth.registerUrl).isNull();
    assertThat(i.auth.registerText).isNull();
    assertThat(i.auth.editFullNameUrl).isNull();
    assertThat(i.auth.httpPasswordUrl).isNull();

    // change
    assertThat(i.change.updateDelay).isEqualTo(50);
    assertThat(i.change.disablePrivateChanges).isTrue();
    assertThat(i.change.enableRobotComments).isNull();
    assertThat(i.change.allowMarkdownBase64ImagesInComments).isNull();

    // download
    assertThat(i.download.archives).containsExactly("tar", "tbz2", "tgz", "txz");
    assertThat(i.download.schemes).isEmpty();

    // gerrit
    assertThat(i.gerrit.allProjects).isEqualTo("Root");
    assertThat(i.gerrit.allUsers).isEqualTo("Users");
    assertThat(i.gerrit.reportBugUrl).isEqualTo("https://example.com/report");
    assertThat(i.gerrit.instanceId).isEqualTo("devops-instance");

    // plugin
    assertThat(i.plugin.jsResourcePaths).isEmpty();

    // sshd
    assertThat(i.sshd).isNotNull();

    // suggest
    assertThat(i.suggest.from).isEqualTo(3);

    // user
    assertThat(i.user.anonymousCowardName).isEqualTo("Unnamed User");

    // notedb
    assertThat(gApi.config().server().getInfo().noteDbEnabled).isTrue();
  }

  @Test
  @GerritConfig(name = "plugins.allowRemoteAdmin", value = "true")
  public void serverConfigWithPlugin() throws Exception {
    ServerInfo i = gApi.config().server().getInfo();
    assertThat(i.plugin.jsResourcePaths).isEmpty();

    InstallPluginInput input = new InstallPluginInput();
    input.raw = RawInputUtil.create(JS_PLUGIN_CONTENT);
    gApi.plugins().install("js-plugin-1.js", input);

    i = gApi.config().server().getInfo();
    assertThat(i.plugin.jsResourcePaths).hasSize(1);
  }

  @Test
  public void serverConfigWithDefaults() throws Exception {
    ServerInfo i = gApi.config().server().getInfo();

    // auth
    assertThat(i.auth.authType).isEqualTo(AuthType.OPENID);
    assertThat(i.auth.editableAccountFields)
        .containsExactly(
            AccountFieldName.REGISTER_NEW_EMAIL,
            AccountFieldName.FULL_NAME,
            AccountFieldName.USER_NAME);
    assertThat(i.auth.useContributorAgreements).isNull();
    assertThat(i.auth.loginUrl).isNull();
    assertThat(i.auth.loginText).isNull();
    assertThat(i.auth.switchAccountUrl).isNull();
    assertThat(i.auth.registerUrl).isNull();
    assertThat(i.auth.registerText).isNull();
    assertThat(i.auth.editFullNameUrl).isNull();
    assertThat(i.auth.httpPasswordUrl).isNull();

    // change
    assertThat(i.change.updateDelay).isEqualTo(300);
    assertThat(i.change.disablePrivateChanges).isNull();
    assertThat(i.change.submitWholeTopic).isNull();
    assertThat(i.change.mergeabilityComputationBehavior).isEqualTo("NEVER");

    // download
    assertThat(i.download.archives).containsExactly("tar", "tbz2", "tgz", "txz");
    assertThat(i.download.schemes).isEmpty();

    // gerrit
    assertThat(i.gerrit.allProjects).isEqualTo(AllProjectsNameProvider.DEFAULT);
    assertThat(i.gerrit.allUsers).isEqualTo(AllUsersNameProvider.DEFAULT);
    assertThat(i.gerrit.reportBugUrl).isNull();
    assertThat(i.gerrit.instanceId).isNull();

    // plugin
    assertThat(i.plugin.jsResourcePaths).isEmpty();

    // sshd
    assertThat(i.sshd).isNotNull();

    // suggest
    assertThat(i.suggest.from).isEqualTo(0);

    // user
    assertThat(i.user.anonymousCowardName).isEqualTo(AnonymousCowardNameProvider.DEFAULT);

    // submit requirement columns in dashboard
    assertThat(i.submitRequirementDashboardColumns).isEmpty();
    assertThat(i.dashboardShowAllLabels).isNull();
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "true")
  public void serverConfigWithSubmitWholeTopic() throws Exception {
    ServerInfo i = gApi.config().server().getInfo();
    assertThat(i.change.submitWholeTopic).isTrue();
  }

  @Test
  @GerritConfig(
      name = "dashboard.submitRequirementColumns",
      values = {"Code-Review", "Verified"})
  public void serverConfigWithMultipleSubmitRequirementColumn() throws Exception {
    ServerInfo i = gApi.config().server().getInfo();
    assertThat(i.submitRequirementDashboardColumns).containsExactly("Code-Review", "Verified");
  }

  @Test
  @GerritConfig(name = "dashboard.showAllLabels", value = "true")
  public void serverConfigWithDashboardShowAllLabels() throws Exception {
    ServerInfo i = gApi.config().server().getInfo();
    assertThat(i.dashboardShowAllLabels).isTrue();
  }

  @Test
  @GerritConfig(name = "change.mergeabilityComputationBehavior", value = "NEVER")
  public void mergeabilityComputationBehavior_neverCompute() throws Exception {
    ServerInfo i = gApi.config().server().getInfo();
    assertThat(i.change.mergeabilityComputationBehavior).isEqualTo("NEVER");
  }

  @Test
  @GerritConfig(name = "download.scheme", value = "fooBar")
  @GerritConfig(name = "download.command", value = "fooBar")
  public void misconfiguredDownloadCommands() throws Exception {
    ServerInfo i = gApi.config().server().getInfo();
    assertThat(i.download.schemes).isEmpty();
  }

  @Test
  @GerritSystemProperty(name = INSTANCE_ID_SYSTEM_PROPERTY, value = "sysPropInstanceId")
  public void instanceIdFromSystemProperty() throws Exception {
    ServerInfo i = gApi.config().server().getInfo();
    assertThat(i.gerrit.instanceId).isEqualTo("sysPropInstanceId");
  }

  @Test
  public void getMetadata() throws Exception {
    TestServerStateProvider testServerStateProvider = new TestServerStateProvider();
    MetadataInfo metadata1 = testServerStateProvider.addMetadata("bugComponent", "123456", null);
    MetadataInfo metadata2 =
        testServerStateProvider.addMetadata("email", null, "email to contact the host owners");
    MetadataInfo metadata3 =
        testServerStateProvider.addMetadata("ownerGroup", null, "group that owns the host");
    MetadataInfo metadata4 =
        testServerStateProvider.addMetadata("ownerGroup", "Bar", "group that owns the host");
    MetadataInfo metadata5 =
        testServerStateProvider.addMetadata("ownerGroup", "Foo", "group that owns the host");
    try (Registration registration =
        extensionRegistry.newRegistration().add(testServerStateProvider)) {
      ServerInfo serverInfo = gApi.config().server().getInfo();
      assertThat(serverInfo.metadata)
          .containsExactly(metadata1, metadata2, metadata3, metadata4, metadata5)
          .inOrder();
    }
  }

  public static class TestServerStateProvider implements ServerStateProvider {
    private ArrayList<MetadataInfo> metadataList = new ArrayList<>();

    public MetadataInfo addMetadata(
        String name, @Nullable String value, @Nullable String description) {
      MetadataInfo metadata = new MetadataInfo();
      metadata.name = name;
      metadata.value = value;
      metadata.description = description;
      metadataList.add(metadata);
      return metadata;
    }

    @Override
    public ImmutableList<MetadataInfo> getMetadata() {
      return ImmutableList.copyOf(metadataList);
    }
  }
}
