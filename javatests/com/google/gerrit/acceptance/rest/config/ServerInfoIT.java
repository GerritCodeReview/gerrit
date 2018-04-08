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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.config.AllProjectsNameProvider;
import com.google.gerrit.config.AllUsersNameProvider;
import com.google.gerrit.extensions.client.AccountFieldName;
import com.google.gerrit.extensions.client.AuthType;
import com.google.gerrit.extensions.common.AccountVisibility;
import com.google.gerrit.extensions.common.InstallPluginInput;
import com.google.gerrit.extensions.common.ServerInfo;
import com.google.gerrit.server.config.AnonymousCowardNameProvider;
import org.junit.Test;

@NoHttpd
@UseSsh
public class ServerInfoIT extends AbstractDaemonTest {
  private static final byte[] JS_PLUGIN_CONTENT =
      "Gerrit.install(function(self){});\n".getBytes(UTF_8);

  @Test
  // accounts
  @GerritConfig(name = "accounts.visibility", value = "VISIBLE_GROUP")

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
  @GerritConfig(name = "change.largeChange", value = "300")
  @GerritConfig(name = "change.replyTooltip", value = "Publish votes and draft comments")
  @GerritConfig(name = "change.replyLabel", value = "Vote")
  @GerritConfig(name = "change.updateDelay", value = "50s")
  @GerritConfig(name = "change.disablePrivateChanges", value = "true")

  // download
  @GerritConfig(
    name = "download.archive",
    values = {"tar", "tbz2", "tgz", "txz"}
  )

  // gerrit
  @GerritConfig(name = "gerrit.allProjects", value = "Root")
  @GerritConfig(name = "gerrit.allUsers", value = "Users")
  @GerritConfig(name = "gerrit.enableGwtUi", value = "true")
  @GerritConfig(name = "gerrit.reportBugText", value = "REPORT BUG")
  @GerritConfig(name = "gerrit.reportBugUrl", value = "https://example.com/report")

  // suggest
  @GerritConfig(name = "suggest.from", value = "3")

  // user
  @GerritConfig(name = "user.anonymousCoward", value = "Unnamed User")
  public void serverConfig() throws Exception {
    ServerInfo i = gApi.config().server().getInfo();

    // accounts
    assertThat(i.accounts.visibility).isEqualTo(AccountVisibility.VISIBLE_GROUP);

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
    assertThat(i.change.largeChange).isEqualTo(300);
    assertThat(i.change.replyTooltip).startsWith("Publish votes and draft comments");
    assertThat(i.change.replyLabel).isEqualTo("Vote\u2026");
    assertThat(i.change.updateDelay).isEqualTo(50);
    assertThat(i.change.disablePrivateChanges).isTrue();

    // download
    assertThat(i.download.archives).containsExactly("tar", "tbz2", "tgz", "txz");
    assertThat(i.download.schemes).isEmpty();

    // gerrit
    assertThat(i.gerrit.allProjects).isEqualTo("Root");
    assertThat(i.gerrit.allUsers).isEqualTo("Users");
    assertThat(i.gerrit.reportBugUrl).isEqualTo("https://example.com/report");
    assertThat(i.gerrit.reportBugText).isEqualTo("REPORT BUG");

    // Acceptance tests force --headless even when UIs are specified in config.
    assertThat(i.gerrit.webUis).isEmpty();

    // plugin
    assertThat(i.plugin.jsResourcePaths).isEmpty();

    // sshd
    assertThat(i.sshd).isNotNull();

    // suggest
    assertThat(i.suggest.from).isEqualTo(3);

    // user
    assertThat(i.user.anonymousCowardName).isEqualTo("Unnamed User");

    // notedb
    notesMigration.setReadChanges(true);
    assertThat(gApi.config().server().getInfo().noteDbEnabled).isTrue();
    notesMigration.setReadChanges(false);
    assertThat(gApi.config().server().getInfo().noteDbEnabled).isNull();
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
    assertThat(i.change.largeChange).isEqualTo(500);
    assertThat(i.change.replyTooltip).startsWith("Reply and score");
    assertThat(i.change.replyLabel).isEqualTo("Reply\u2026");
    assertThat(i.change.updateDelay).isEqualTo(300);
    assertThat(i.change.disablePrivateChanges).isNull();

    // download
    assertThat(i.download.archives).containsExactly("tar", "tbz2", "tgz", "txz");
    assertThat(i.download.schemes).isEmpty();

    // gerrit
    assertThat(i.gerrit.allProjects).isEqualTo(AllProjectsNameProvider.DEFAULT);
    assertThat(i.gerrit.allUsers).isEqualTo(AllUsersNameProvider.DEFAULT);
    assertThat(i.gerrit.reportBugUrl).isNull();
    assertThat(i.gerrit.reportBugText).isNull();

    // plugin
    assertThat(i.plugin.jsResourcePaths).isEmpty();

    // sshd
    assertThat(i.sshd).isNotNull();

    // suggest
    assertThat(i.suggest.from).isEqualTo(0);

    // user
    assertThat(i.user.anonymousCowardName).isEqualTo(AnonymousCowardNameProvider.DEFAULT);
  }

  @Test
  @GerritConfig(name = "auth.contributorAgreements", value = "true")
  public void anonymousAccess() throws Exception {
    configureContributorAgreement(true);

    setApiUserAnonymous();
    gApi.config().server().getInfo();
  }
}
