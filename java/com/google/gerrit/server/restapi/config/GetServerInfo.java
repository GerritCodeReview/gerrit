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
import com.google.gerrit.config.AllProjectsName;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.config.AnonymousCowardName;
import com.google.gerrit.config.ArchiveFormat;
import com.google.gerrit.config.ConfigUtil;
import com.google.gerrit.config.GerritOptions;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.extensions.client.UiType;
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
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.webui.WebUiPlugin;
import com.google.gerrit.server.EnableSignedPush;
import com.google.gerrit.server.account.AccountVisibilityProvider;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.avatar.AvatarProvider;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.documentation.QueryDocumentationExecutor;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.restapi.change.AllowedFormats;
import com.google.gerrit.server.submit.MergeSuperSet;
import com.google.inject.Inject;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

public class GetServerInfo implements RestReadView<ConfigResource> {
  private static final String URL_ALIAS = "urlAlias";
  private static final String KEY_MATCH = "match";
  private static final String KEY_TOKEN = "token";

  private final Config config;
  private final AccountVisibilityProvider accountVisibilityProvider;
  private final AuthConfig authConfig;
  private final Realm realm;
  private final DynamicMap<DownloadScheme> downloadSchemes;
  private final DynamicMap<DownloadCommand> downloadCommands;
  private final DynamicMap<CloneCommand> cloneCommands;
  private final DynamicSet<WebUiPlugin> plugins;
  private final AllowedFormats archiveFormats;
  private final AllProjectsName allProjectsName;
  private final AllUsersName allUsersName;
  private final String anonymousCowardName;
  private final DynamicItem<AvatarProvider> avatar;
  private final boolean enableSignedPush;
  private final QueryDocumentationExecutor docSearcher;
  private final NotesMigration migration;
  private final ProjectCache projectCache;
  private final AgreementJson agreementJson;
  private final GerritOptions gerritOptions;
  private final ChangeIndexCollection indexes;
  private final SitePaths sitePaths;

  @Inject
  public GetServerInfo(
      @GerritServerConfig Config config,
      AccountVisibilityProvider accountVisibilityProvider,
      AuthConfig authConfig,
      Realm realm,
      DynamicMap<DownloadScheme> downloadSchemes,
      DynamicMap<DownloadCommand> downloadCommands,
      DynamicMap<CloneCommand> cloneCommands,
      DynamicSet<WebUiPlugin> webUiPlugins,
      AllowedFormats archiveFormats,
      AllProjectsName allProjectsName,
      AllUsersName allUsersName,
      @AnonymousCowardName String anonymousCowardName,
      DynamicItem<AvatarProvider> avatar,
      @EnableSignedPush boolean enableSignedPush,
      QueryDocumentationExecutor docSearcher,
      NotesMigration migration,
      ProjectCache projectCache,
      AgreementJson agreementJson,
      GerritOptions gerritOptions,
      ChangeIndexCollection indexes,
      SitePaths sitePaths) {
    this.config = config;
    this.accountVisibilityProvider = accountVisibilityProvider;
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
    this.migration = migration;
    this.projectCache = projectCache;
    this.agreementJson = agreementJson;
    this.gerritOptions = gerritOptions;
    this.indexes = indexes;
    this.sitePaths = sitePaths;
  }

  @Override
  public ServerInfo apply(ConfigResource rsrc) throws MalformedURLException {
    ServerInfo info = new ServerInfo();
    info.accounts = getAccountsInfo(accountVisibilityProvider);
    info.auth = getAuthInfo(authConfig, realm);
    info.change = getChangeInfo(config);
    info.download =
        getDownloadInfo(downloadSchemes, downloadCommands, cloneCommands, archiveFormats);
    info.gerrit = getGerritInfo(config, allProjectsName, allUsersName);
    info.noteDbEnabled = toBoolean(isNoteDbEnabled());
    info.plugin = getPluginInfo();
    if (Files.exists(sitePaths.site_theme)) {
      info.defaultTheme = "/static/" + SitePaths.THEME_FILENAME;
    }
    info.sshd = getSshdInfo(config);
    info.suggest = getSuggestInfo(config);

    Map<String, String> urlAliases = getUrlAliasesInfo(config);
    info.urlAliases = !urlAliases.isEmpty() ? urlAliases : null;

    info.user = getUserInfo(anonymousCowardName);
    info.receive = getReceiveInfo();
    return info;
  }

  private AccountsInfo getAccountsInfo(AccountVisibilityProvider accountVisibilityProvider) {
    AccountsInfo info = new AccountsInfo();
    info.visibility = accountVisibilityProvider.get();
    return info;
  }

  private AuthInfo getAuthInfo(AuthConfig cfg, Realm realm) {
    AuthInfo info = new AuthInfo();
    info.authType = cfg.getAuthType();
    info.useContributorAgreements = toBoolean(cfg.isUseContributorAgreements());
    info.editableAccountFields = new ArrayList<>(realm.getEditableFields());
    info.switchAccountUrl = cfg.getSwitchAccountUrl();
    info.gitBasicAuthPolicy = cfg.getGitBasicAuthPolicy();

    if (info.useContributorAgreements != null) {
      Collection<ContributorAgreement> agreements =
          projectCache.getAllProjects().getConfig().getContributorAgreements();
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
        info.registerUrl = cfg.getRegisterUrl();
        info.registerText = cfg.getRegisterText();
        info.editFullNameUrl = cfg.getEditFullNameUrl();
        break;

      case CUSTOM_EXTENSION:
        info.registerUrl = cfg.getRegisterUrl();
        info.registerText = cfg.getRegisterText();
        info.editFullNameUrl = cfg.getEditFullNameUrl();
        info.httpPasswordUrl = cfg.getHttpPasswordUrl();
        break;

      case HTTP:
      case HTTP_LDAP:
        info.loginUrl = cfg.getLoginUrl();
        info.loginText = cfg.getLoginText();
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

  private ChangeConfigInfo getChangeInfo(Config cfg) {
    ChangeConfigInfo info = new ChangeConfigInfo();
    info.allowBlame = toBoolean(cfg.getBoolean("change", "allowBlame", true));
    boolean hasAssigneeInIndex =
        indexes.getSearchIndex().getSchema().hasField(ChangeField.ASSIGNEE);
    info.showAssigneeInChangesTable =
        toBoolean(
            cfg.getBoolean("change", "showAssigneeInChangesTable", false) && hasAssigneeInIndex);
    info.largeChange = cfg.getInt("change", "largeChange", 500);
    info.replyTooltip =
        Optional.ofNullable(cfg.getString("change", null, "replyTooltip")).orElse("Reply and score")
            + " (Shortcut: a)";
    info.replyLabel =
        Optional.ofNullable(cfg.getString("change", null, "replyLabel")).orElse("Reply") + "\u2026";
    info.updateDelay =
        (int) ConfigUtil.getTimeUnit(cfg, "change", null, "updateDelay", 300, TimeUnit.SECONDS);
    info.submitWholeTopic = MergeSuperSet.wholeTopicEnabled(cfg);
    info.disablePrivateChanges =
        toBoolean(config.getBoolean("change", null, "disablePrivateChanges", false));
    return info;
  }

  private DownloadInfo getDownloadInfo(
      DynamicMap<DownloadScheme> downloadSchemes,
      DynamicMap<DownloadCommand> downloadCommands,
      DynamicMap<CloneCommand> cloneCommands,
      AllowedFormats archiveFormats) {
    DownloadInfo info = new DownloadInfo();
    info.schemes = new HashMap<>();
    for (DynamicMap.Entry<DownloadScheme> e : downloadSchemes) {
      DownloadScheme scheme = e.getProvider().get();
      if (scheme.isEnabled() && scheme.getUrl("${project}") != null) {
        info.schemes.put(
            e.getExportName(), getDownloadSchemeInfo(scheme, downloadCommands, cloneCommands));
      }
    }
    info.archives =
        archiveFormats.getAllowed().stream().map(ArchiveFormat::getShortName).collect(toList());
    return info;
  }

  private DownloadSchemeInfo getDownloadSchemeInfo(
      DownloadScheme scheme,
      DynamicMap<DownloadCommand> downloadCommands,
      DynamicMap<CloneCommand> cloneCommands) {
    DownloadSchemeInfo info = new DownloadSchemeInfo();
    info.url = scheme.getUrl("${project}");
    info.isAuthRequired = toBoolean(scheme.isAuthRequired());
    info.isAuthSupported = toBoolean(scheme.isAuthSupported());

    info.commands = new HashMap<>();
    for (DynamicMap.Entry<DownloadCommand> e : downloadCommands) {
      String commandName = e.getExportName();
      DownloadCommand command = e.getProvider().get();
      String c = command.getCommand(scheme, "${project}", "${ref}");
      if (c != null) {
        info.commands.put(commandName, c);
      }
    }

    info.cloneCommands = new HashMap<>();
    for (DynamicMap.Entry<CloneCommand> e : cloneCommands) {
      String commandName = e.getExportName();
      CloneCommand command = e.getProvider().get();
      String c = command.getCommand(scheme, "${project-path}/${project-base-name}");
      if (c != null) {
        c = c.replaceAll("\\$\\{project-path\\}/\\$\\{project-base-name\\}", "\\$\\{project\\}");
        info.cloneCommands.put(commandName, c);
      }
    }

    return info;
  }

  private GerritInfo getGerritInfo(
      Config cfg, AllProjectsName allProjectsName, AllUsersName allUsersName) {
    GerritInfo info = new GerritInfo();
    info.allProjects = allProjectsName.get();
    info.allUsers = allUsersName.get();
    info.reportBugUrl = cfg.getString("gerrit", null, "reportBugUrl");
    info.reportBugText = cfg.getString("gerrit", null, "reportBugText");
    info.docUrl = getDocUrl(cfg);
    info.docSearch = docSearcher.isAvailable();
    info.editGpgKeys =
        toBoolean(enableSignedPush && cfg.getBoolean("gerrit", null, "editGpgKeys", true));
    info.webUis = EnumSet.noneOf(UiType.class);
    if (gerritOptions.enableGwtUi()) {
      info.webUis.add(UiType.GWT);
    }
    if (gerritOptions.enablePolyGerrit()) {
      info.webUis.add(UiType.POLYGERRIT);
    }
    return info;
  }

  private String getDocUrl(Config cfg) {
    String docUrl = cfg.getString("gerrit", null, "docUrl");
    if (Strings.isNullOrEmpty(docUrl)) {
      return null;
    }
    return CharMatcher.is('/').trimTrailingFrom(docUrl) + '/';
  }

  private boolean isNoteDbEnabled() {
    return migration.readChanges();
  }

  private PluginConfigInfo getPluginInfo() {
    PluginConfigInfo info = new PluginConfigInfo();
    info.hasAvatars = toBoolean(avatar.get() != null);
    info.jsResourcePaths = new ArrayList<>();
    info.htmlResourcePaths = new ArrayList<>();
    for (WebUiPlugin u : plugins) {
      String path =
          String.format("plugins/%s/%s", u.getPluginName(), u.getJavaScriptResourcePath());
      if (path.endsWith(".html")) {
        info.htmlResourcePaths.add(path);
      } else {
        info.jsResourcePaths.add(path);
      }
    }
    return info;
  }

  private Map<String, String> getUrlAliasesInfo(Config cfg) {
    Map<String, String> urlAliases = new HashMap<>();
    for (String subsection : cfg.getSubsections(URL_ALIAS)) {
      urlAliases.put(
          cfg.getString(URL_ALIAS, subsection, KEY_MATCH),
          cfg.getString(URL_ALIAS, subsection, KEY_TOKEN));
    }
    return urlAliases;
  }

  private SshdInfo getSshdInfo(Config cfg) {
    String[] addr = cfg.getStringList("sshd", null, "listenAddress");
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

  private SuggestInfo getSuggestInfo(Config cfg) {
    SuggestInfo info = new SuggestInfo();
    info.from = cfg.getInt("suggest", "from", 0);
    return info;
  }

  private UserConfigInfo getUserInfo(String anonymousCowardName) {
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
