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

package com.google.gerrit.server.restapi.config;

import static java.util.stream.Collectors.toList;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.ContributorAgreement;
import com.google.gerrit.extensions.common.AccountDefaultDisplayName;
import com.google.gerrit.extensions.common.AccountsInfo;
import com.google.gerrit.extensions.common.AuthInfo;
import com.google.gerrit.extensions.common.ChangeConfigInfo;
import com.google.gerrit.extensions.common.DownloadInfo;
import com.google.gerrit.extensions.common.DownloadSchemeInfo;
import com.google.gerrit.extensions.common.GerritInfo;
import com.google.gerrit.extensions.common.PluginConfigInfo;
import com.google.gerrit.extensions.common.ReceiveInfo;
import com.google.gerrit.extensions.common.ServerInfo;
import com.google.gerrit.extensions.common.SshdInfo;
import com.google.gerrit.extensions.common.SuggestInfo;
import com.google.gerrit.extensions.common.UserConfigInfo;
import com.google.gerrit.extensions.config.CloneCommand;
import com.google.gerrit.extensions.config.DownloadCommand;
import com.google.gerrit.extensions.config.DownloadScheme;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.webui.WebUiPlugin;
import com.google.gerrit.server.EnableSignedPush;
import com.google.gerrit.server.account.AccountVisibilityProvider;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.avatar.AvatarProvider;
import com.google.gerrit.server.change.ArchiveFormatInternal;
import com.google.gerrit.server.change.MergeabilityComputationBehavior;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.documentation.QueryDocumentationExecutor;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.plugincontext.PluginItemContext;
import com.google.gerrit.server.plugincontext.PluginMapContext;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.restapi.change.AllowedFormats;
import com.google.gerrit.server.submit.MergeSuperSet;
import com.google.inject.Inject;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

public class GetServerInfo implements RestReadView<ConfigResource> {
  private final Config config;
  private final AccountVisibilityProvider accountVisibilityProvider;
  private final AccountDefaultDisplayName accountDefaultDisplayName;
  private final AuthConfig authConfig;
  private final Realm realm;
  private final PluginMapContext<DownloadScheme> downloadSchemes;
  private final PluginMapContext<DownloadCommand> downloadCommands;
  private final PluginMapContext<CloneCommand> cloneCommands;
  private final PluginSetContext<WebUiPlugin> plugins;
  private final AllowedFormats archiveFormats;
  private final AllProjectsName allProjectsName;
  private final AllUsersName allUsersName;
  private final String anonymousCowardName;
  private final PluginItemContext<AvatarProvider> avatar;
  private final boolean enableSignedPush;
  private final QueryDocumentationExecutor docSearcher;
  private final ProjectCache projectCache;
  private final AgreementJson agreementJson;
  private final ChangeIndexCollection indexes;
  private final SitePaths sitePaths;

  @Inject
  public GetServerInfo(
      @GerritServerConfig Config config,
      AccountVisibilityProvider accountVisibilityProvider,
      AccountDefaultDisplayName accountDefaultDisplayName,
      AuthConfig authConfig,
      Realm realm,
      PluginMapContext<DownloadScheme> downloadSchemes,
      PluginMapContext<DownloadCommand> downloadCommands,
      PluginMapContext<CloneCommand> cloneCommands,
      PluginSetContext<WebUiPlugin> webUiPlugins,
      AllowedFormats archiveFormats,
      AllProjectsName allProjectsName,
      AllUsersName allUsersName,
      @AnonymousCowardName String anonymousCowardName,
      PluginItemContext<AvatarProvider> avatar,
      @EnableSignedPush boolean enableSignedPush,
      QueryDocumentationExecutor docSearcher,
      ProjectCache projectCache,
      AgreementJson agreementJson,
      ChangeIndexCollection indexes,
      SitePaths sitePaths) {
    this.config = config;
    this.accountVisibilityProvider = accountVisibilityProvider;
    this.accountDefaultDisplayName = accountDefaultDisplayName;
    this.authConfig = authConfig;
    this.realm = realm;
    this.downloadSchemes = downloadSchemes;
    this.downloadCommands = downloadCommands;
    this.cloneCommands = cloneCommands;
    this.plugins = webUiPlugins;
    this.archiveFormats = archiveFormats;
    this.allProjectsName = allProjectsName;
    this.allUsersName = allUsersName;
    this.anonymousCowardName = anonymousCowardName;
    this.avatar = avatar;
    this.enableSignedPush = enableSignedPush;
    this.docSearcher = docSearcher;
    this.projectCache = projectCache;
    this.agreementJson = agreementJson;
    this.indexes = indexes;
    this.sitePaths = sitePaths;
  }

  @Override
  public Response<ServerInfo> apply(ConfigResource rsrc) throws PermissionBackendException {
    ServerInfo info = new ServerInfo();
    info.accounts = getAccountsInfo();
    info.auth = getAuthInfo();
    info.change = getChangeInfo();
    info.download = getDownloadInfo();
    info.gerrit = getGerritInfo();
    info.noteDbEnabled = true;
    info.plugin = getPluginInfo();
    info.defaultTheme = getDefaultTheme();
    info.sshd = getSshdInfo();
    info.suggest = getSuggestInfo();

    info.user = getUserInfo();
    info.receive = getReceiveInfo();
    return Response.ok(info);
  }

  private AccountsInfo getAccountsInfo() {
    AccountsInfo info = new AccountsInfo();
    info.visibility = accountVisibilityProvider.get();
    info.defaultDisplayName = accountDefaultDisplayName;
    return info;
  }

  private AuthInfo getAuthInfo() throws PermissionBackendException {
    AuthInfo info = new AuthInfo();
    info.authType = authConfig.getAuthType();
    info.useContributorAgreements = toBoolean(authConfig.isUseContributorAgreements());
    info.editableAccountFields = new ArrayList<>(realm.getEditableFields());
    info.switchAccountUrl = authConfig.getSwitchAccountUrl();
    info.gitBasicAuthPolicy = authConfig.getGitBasicAuthPolicy();

    if (info.useContributorAgreements != null) {
      Collection<ContributorAgreement> agreements =
          projectCache.getAllProjects().getConfig().getContributorAgreements().values();
      if (!agreements.isEmpty()) {
        info.contributorAgreements = Lists.newArrayListWithCapacity(agreements.size());
        for (ContributorAgreement agreement : agreements) {
          info.contributorAgreements.add(agreementJson.format(agreement));
        }
      }
    }

    switch (info.authType) {
      case LDAP:
      case LDAP_BIND:
        info.registerUrl = authConfig.getRegisterUrl();
        info.registerText = authConfig.getRegisterText();
        info.editFullNameUrl = authConfig.getEditFullNameUrl();
        break;

      case CUSTOM_EXTENSION:
        info.registerUrl = authConfig.getRegisterUrl();
        info.registerText = authConfig.getRegisterText();
        info.editFullNameUrl = authConfig.getEditFullNameUrl();
        info.httpPasswordUrl = authConfig.getHttpPasswordUrl();
        break;

      case HTTP:
      case HTTP_LDAP:
        info.loginUrl = authConfig.getLoginUrl();
        info.loginText = authConfig.getLoginText();
        break;

      case CLIENT_SSL_CERT_LDAP:
      case DEVELOPMENT_BECOME_ANY_ACCOUNT:
      case OAUTH:
      case OPENID:
      case OPENID_SSO:
        break;
    }
    return info;
  }

  private ChangeConfigInfo getChangeInfo() {
    ChangeConfigInfo info = new ChangeConfigInfo();
    info.allowBlame = toBoolean(config.getBoolean("change", "allowBlame", true));
    boolean hasAssigneeInIndex =
        indexes.getSearchIndex().getSchema().hasField(ChangeField.ASSIGNEE);
    info.showAssigneeInChangesTable =
        toBoolean(
            config.getBoolean("change", "showAssigneeInChangesTable", false) && hasAssigneeInIndex);
    info.largeChange = config.getInt("change", "largeChange", 500);
    info.replyTooltip =
        Optional.ofNullable(config.getString("change", null, "replyTooltip"))
                .orElse("Reply and score")
            + " (Shortcut: a)";
    info.replyLabel =
        Optional.ofNullable(config.getString("change", null, "replyLabel")).orElse("Reply")
            + "\u2026";
    info.updateDelay =
        (int) ConfigUtil.getTimeUnit(config, "change", null, "updateDelay", 300, TimeUnit.SECONDS);
    info.submitWholeTopic = toBoolean(MergeSuperSet.wholeTopicEnabled(config));
    info.disablePrivateChanges =
        toBoolean(this.config.getBoolean("change", null, "disablePrivateChanges", false));
    info.mergeabilityComputationBehavior =
        MergeabilityComputationBehavior.fromConfig(config).name();
    info.enableAttentionSet =
        toBoolean(this.config.getBoolean("change", null, "enableAttentionSet", false));
    info.enableAssignee = toBoolean(this.config.getBoolean("change", null, "enableAssignee", true));
    return info;
  }

  private DownloadInfo getDownloadInfo() {
    DownloadInfo info = new DownloadInfo();
    info.schemes = new HashMap<>();
    downloadSchemes.runEach(
        extension -> {
          DownloadScheme scheme = extension.get();
          if (scheme.isEnabled() && scheme.getUrl("${project}") != null) {
            info.schemes.put(extension.getExportName(), getDownloadSchemeInfo(scheme));
          }
        });
    info.archives =
        archiveFormats.getAllowed().stream()
            .map(ArchiveFormatInternal::getShortName)
            .collect(toList());
    return info;
  }

  private DownloadSchemeInfo getDownloadSchemeInfo(DownloadScheme scheme) {
    DownloadSchemeInfo info = new DownloadSchemeInfo();
    info.url = scheme.getUrl("${project}");
    info.isAuthRequired = toBoolean(scheme.isAuthRequired());
    info.isAuthSupported = toBoolean(scheme.isAuthSupported());

    info.commands = new HashMap<>();
    downloadCommands.runEach(
        extension -> {
          String commandName = extension.getExportName();
          DownloadCommand command = extension.get();
          String c = command.getCommand(scheme, "${project}", "${ref}");
          if (c != null) {
            info.commands.put(commandName, c);
          }
        });

    info.cloneCommands = new HashMap<>();
    cloneCommands.runEach(
        extension -> {
          String commandName = extension.getExportName();
          CloneCommand command = extension.getProvider().get();
          String c = command.getCommand(scheme, "${project-path}/${project-base-name}");
          if (c != null) {
            c =
                c.replaceAll(
                    "\\$\\{project-path\\}/\\$\\{project-base-name\\}", "\\$\\{project\\}");
            info.cloneCommands.put(commandName, c);
          }
        });

    return info;
  }

  private GerritInfo getGerritInfo() {
    GerritInfo info = new GerritInfo();
    info.allProjects = allProjectsName.get();
    info.allUsers = allUsersName.get();
    info.reportBugUrl = config.getString("gerrit", null, "reportBugUrl");
    info.docUrl = getDocUrl();
    info.docSearch = docSearcher.isAvailable();
    info.editGpgKeys =
        toBoolean(enableSignedPush && config.getBoolean("gerrit", null, "editGpgKeys", true));
    info.primaryWeblinkName = config.getString("gerrit", null, "primaryWeblinkName");
    return info;
  }

  private String getDocUrl() {
    String docUrl = config.getString("gerrit", null, "docUrl");
    if (Strings.isNullOrEmpty(docUrl)) {
      return null;
    }
    return CharMatcher.is('/').trimTrailingFrom(docUrl) + '/';
  }

  private PluginConfigInfo getPluginInfo() {
    PluginConfigInfo info = new PluginConfigInfo();
    info.hasAvatars = toBoolean(avatar.hasImplementation());
    info.jsResourcePaths = new ArrayList<>();
    info.htmlResourcePaths = new ArrayList<>();
    plugins.runEach(
        plugin -> {
          String path =
              String.format(
                  "plugins/%s/%s", plugin.getPluginName(), plugin.getJavaScriptResourcePath());
          if (path.endsWith(".html")) {
            info.htmlResourcePaths.add(path);
          } else {
            info.jsResourcePaths.add(path);
          }
        });
    return info;
  }

  private static final String DEFAULT_THEME = "/static/" + SitePaths.THEME_FILENAME;
  private static final String DEFAULT_THEME_JS = "/static/" + SitePaths.THEME_JS_FILENAME;

  private String getDefaultTheme() {
    if (config.getString("theme", null, "enableDefault") == null) {
      // If not explicitly enabled or disabled, check for the existence of the theme file.
      return Files.exists(sitePaths.site_theme_js)
          ? DEFAULT_THEME_JS
          : Files.exists(sitePaths.site_theme) ? DEFAULT_THEME : null;
    }
    if (config.getBoolean("theme", null, "enableDefault", true)) {
      // Return non-null theme path without checking for file existence. Even if the file doesn't
      // exist under the site path, it may be served from a CDN (in which case it's up to the admin
      // to also pass a proper asset path to the index Soy template).
      return DEFAULT_THEME_JS;
    }
    return null;
  }

  private SshdInfo getSshdInfo() {
    String[] addr = config.getStringList("sshd", null, "listenAddress");
    if (addr.length == 1 && isOff(addr[0])) {
      return null;
    }
    return new SshdInfo();
  }

  private static boolean isOff(String listenHostname) {
    return "off".equalsIgnoreCase(listenHostname)
        || "none".equalsIgnoreCase(listenHostname)
        || "no".equalsIgnoreCase(listenHostname);
  }

  private SuggestInfo getSuggestInfo() {
    SuggestInfo info = new SuggestInfo();
    info.from = config.getInt("suggest", "from", 0);
    return info;
  }

  private UserConfigInfo getUserInfo() {
    UserConfigInfo info = new UserConfigInfo();
    info.anonymousCowardName = anonymousCowardName;
    return info;
  }

  private ReceiveInfo getReceiveInfo() {
    ReceiveInfo info = new ReceiveInfo();
    info.enableSignedPush = enableSignedPush;
    return info;
  }

  private static Boolean toBoolean(boolean v) {
    return v ? v : null;
  }
}
