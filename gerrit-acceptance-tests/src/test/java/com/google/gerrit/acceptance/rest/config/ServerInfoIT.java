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
import com.google.gerrit.acceptance.GerritConfigs;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.config.AnonymousCowardNameProvider;
import com.google.gerrit.server.config.GetServerInfo.ServerInfo;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class ServerInfoIT extends AbstractDaemonTest {

  @Test
  @GerritConfigs({
    // auth
    @GerritConfig(name = "auth.type", value = "HTTP"),
    @GerritConfig(name = "auth.contributorAgreements", value = "true"),
    @GerritConfig(name = "auth.loginUrl", value = "https://example.com/login"),
    @GerritConfig(name = "auth.loginText", value = "LOGIN"),
    @GerritConfig(name = "auth.switchAccountUrl", value = "https://example.com/switch"),

    // auth fields ignored when auth == HTTP
    @GerritConfig(name = "auth.registerUrl", value = "https://example.com/register"),
    @GerritConfig(name = "auth.registerText", value = "REGISTER"),
    @GerritConfig(name = "auth.editFullNameUrl", value = "https://example.com/editname"),
    @GerritConfig(name = "auth.httpPasswordUrl", value = "https://example.com/password"),

    // change
    @GerritConfig(name = "change.allowDrafts", value = "false"),
    @GerritConfig(name = "change.largeChange", value = "300"),
    @GerritConfig(name = "change.replyTooltip", value = "Publish votes and draft comments"),
    @GerritConfig(name = "change.replyLabel", value = "Vote"),
    @GerritConfig(name = "change.updateDelay", value = "50s"),

    // download
    @GerritConfig(name = "download.archive", values = {"tar",
        "tbz2", "tgz", "txz"}),

    // gerrit
    @GerritConfig(name = "gerrit.allProjects", value = "Root"),
    @GerritConfig(name = "gerrit.allUsers", value = "Users"),
    @GerritConfig(name = "gerrit.reportBugUrl", value = "https://example.com/report"),
    @GerritConfig(name = "gerrit.reportBugText", value = "REPORT BUG"),

    // suggest
    @GerritConfig(name = "suggest.from", value = "3"),

    // user
    @GerritConfig(name = "user.anonymousCoward", value = "Unnamed User"),
  })
  public void serverConfig() throws Exception {
    ServerInfo i = getServerConfig();

    // auth
    assertThat(i.auth.authType).isEqualTo(AuthType.HTTP);
    assertThat(i.auth.editableAccountFields).containsExactly(
        Account.FieldName.REGISTER_NEW_EMAIL, Account.FieldName.FULL_NAME);
    assertThat(i.auth.useContributorAgreements).isTrue();
    assertThat(i.auth.loginUrl).isEqualTo("https://example.com/login");
    assertThat(i.auth.loginText).isEqualTo("LOGIN");
    assertThat(i.auth.switchAccountUrl).isEqualTo("https://example.com/switch");
    assertThat(i.auth.registerUrl).isNull();
    assertThat(i.auth.registerText).isNull();
    assertThat(i.auth.editFullNameUrl).isNull();
    assertThat(i.auth.httpPasswordUrl).isNull();
    assertThat(i.auth.isGitBasicAuth).isNull();

    // change
    assertThat(i.change.allowDrafts).isNull();
    assertThat(i.change.largeChange).isEqualTo(300);
    assertThat(i.change.replyTooltip).startsWith("Publish votes and draft comments");
    assertThat(i.change.replyLabel).isEqualTo("Vote\u2026");
    assertThat(i.change.updateDelay).isEqualTo(50);

    // download
    assertThat(i.download.archives).containsExactly("tar", "tbz2", "tgz", "txz");
    assertThat(i.download.schemes).isEmpty();

    // gerrit
    assertThat(i.gerrit.allProjects).isEqualTo("Root");
    assertThat(i.gerrit.allUsers).isEqualTo("Users");
    assertThat(i.gerrit.reportBugUrl).isEqualTo("https://example.com/report");
    assertThat(i.gerrit.reportBugText).isEqualTo("REPORT BUG");

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
    assertThat(getServerConfig().noteDbEnabled).isTrue();
    notesMigration.setReadChanges(false);
    assertThat(getServerConfig().noteDbEnabled).isNull();
  }

  @Test
  @GerritConfig(name = "plugins.allowRemoteAdmin", value = "true")
  public void serverConfigWithPlugin() throws Exception {
    Path plugins = tempSiteDir.newFolder("plugins").toPath();
    Path jsplugin = plugins.resolve("js-plugin-1.js");
    Files.write(jsplugin, "Gerrit.install(function(self){});\n".getBytes(UTF_8));
    adminSshSession.exec("gerrit plugin reload");

    ServerInfo i = getServerConfig();

    // plugin
    assertThat(i.plugin.jsResourcePaths).hasSize(1);
  }

  @Test
  public void serverConfigWithDefaults() throws Exception {
    ServerInfo i = getServerConfig();

    // auth
    assertThat(i.auth.authType).isEqualTo(AuthType.OPENID);
    assertThat(i.auth.editableAccountFields).containsExactly(
        Account.FieldName.REGISTER_NEW_EMAIL, Account.FieldName.FULL_NAME,
        Account.FieldName.USER_NAME);
    assertThat(i.auth.useContributorAgreements).isNull();
    assertThat(i.auth.loginUrl).isNull();
    assertThat(i.auth.loginText).isNull();
    assertThat(i.auth.switchAccountUrl).isNull();
    assertThat(i.auth.registerUrl).isNull();
    assertThat(i.auth.registerText).isNull();
    assertThat(i.auth.editFullNameUrl).isNull();
    assertThat(i.auth.httpPasswordUrl).isNull();
    assertThat(i.auth.isGitBasicAuth).isNull();

    // change
    assertThat(i.change.allowDrafts).isTrue();
    assertThat(i.change.largeChange).isEqualTo(500);
    assertThat(i.change.replyTooltip).startsWith("Reply and score");
    assertThat(i.change.replyLabel).isEqualTo("Reply\u2026");
    assertThat(i.change.updateDelay).isEqualTo(30);

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

  private ServerInfo getServerConfig() throws Exception {
    RestResponse r = adminRestSession.get("/config/server/info/");
    r.assertOK();
    return newGson().fromJson(r.getReader(), ServerInfo.class);
  }
}
