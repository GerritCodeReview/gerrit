// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.httpd;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.GerritConfig;
import com.google.gerrit.common.data.GitwebConfig;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.change.ArchiveFormat;
import com.google.gerrit.server.change.GetArchive;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.DownloadConfig;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.contact.ContactStore;
import com.google.gerrit.server.mail.EmailSender;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

import org.eclipse.jgit.lib.Config;

import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContext;

class GerritConfigProvider implements Provider<GerritConfig> {
  private final Realm realm;
  private final Config cfg;
  private final AuthConfig authConfig;
  private final DownloadConfig downloadConfig;
  private final GetArchive.AllowedFormats archiveFormats;
  private final GitWebConfig gitWebConfig;
  private final AllProjectsName wildProject;
  private final SshInfo sshInfo;

  private EmailSender emailSender;
  private final ContactStore contactStore;
  private final ServletContext servletContext;
  private final String anonymousCowardName;

  @Inject
  GerritConfigProvider(final Realm r, @GerritServerConfig final Config gsc,
      final AuthConfig ac, final GitWebConfig gwc, final AllProjectsName wp,
      final SshInfo si, final ContactStore cs,
      final ServletContext sc, final DownloadConfig dc,
      final GetArchive.AllowedFormats af,
      final @AnonymousCowardName String acn) {
    realm = r;
    cfg = gsc;
    authConfig = ac;
    downloadConfig = dc;
    archiveFormats = af;
    gitWebConfig = gwc;
    sshInfo = si;
    wildProject = wp;
    contactStore = cs;
    servletContext = sc;
    anonymousCowardName = acn;
  }

  @Inject(optional = true)
  void setEmailSender(final EmailSender d) {
    emailSender = d;
  }

  private GerritConfig create() throws MalformedURLException {
    final GerritConfig config = new GerritConfig();
    switch (authConfig.getAuthType()) {
      case LDAP:
      case LDAP_BIND:
        config.setRegisterUrl(cfg.getString("auth", null, "registerurl"));
        config.setRegisterText(cfg.getString("auth", null, "registertext"));
        config.setEditFullNameUrl(cfg.getString("auth", null, "editFullNameUrl"));
        config.setHttpPasswordSettingsEnabled(!authConfig.isGitBasicAuth());
        break;

      case CUSTOM_EXTENSION:
        config.setRegisterUrl(cfg.getString("auth", null, "registerurl"));
        config.setRegisterText(cfg.getString("auth", null, "registertext"));
        config.setEditFullNameUrl(cfg.getString("auth", null, "editFullNameUrl"));
        config.setHttpPasswordUrl(cfg.getString("auth", null, "httpPasswordUrl"));
        break;

      case HTTP:
      case HTTP_LDAP:
        config.setLoginUrl(cfg.getString("auth", null, "loginurl"));
        config.setLoginText(cfg.getString("auth", null, "logintext"));
        break;

      case CLIENT_SSL_CERT_LDAP:
      case DEVELOPMENT_BECOME_ANY_ACCOUNT:
      case OPENID:
      case OPENID_SSO:
        break;
    }
    config.setSwitchAccountUrl(cfg.getString("auth", null, "switchAccountUrl"));
    config.setUseContributorAgreements(cfg.getBoolean("auth",
        "contributoragreements", false));
    config.setGitDaemonUrl(cfg.getString("gerrit", null, "canonicalgiturl"));
    config.setGitHttpUrl(cfg.getString("gerrit", null, "gitHttpUrl"));
    config.setUseContactInfo(contactStore != null && contactStore.isEnabled());
    config.setDownloadSchemes(downloadConfig.getDownloadSchemes());
    config.setDownloadCommands(downloadConfig.getDownloadCommands());
    config.setAuthType(authConfig.getAuthType());
    config.setWildProject(wildProject);
    config.setDocumentationAvailable(servletContext
        .getResource("/Documentation/index.html") != null);
    config.setAnonymousCowardName(anonymousCowardName);
    config.setSuggestFrom(cfg.getInt("suggest", "from", 0));
    config.setChangeUpdateDelay((int) ConfigUtil.getTimeUnit(
        cfg, "change", null, "updateDelay", 30, TimeUnit.SECONDS));
    config.setLargeChangeSize(cfg.getInt("change", "largeChange", 500));
    config.setArchiveFormats(Lists.newArrayList(Iterables.transform(
        archiveFormats.getAllowed(),
        new Function<ArchiveFormat, String>() {
          @Override
          public String apply(ArchiveFormat in) {
            return in.getShortName();
          }
        })));

    config.setReportBugUrl(cfg.getString("gerrit", null, "reportBugUrl"));
    config.setReportBugText(cfg.getString("gerrit", null, "reportBugText"));

    final Set<Account.FieldName> fields = new HashSet<>();
    for (final Account.FieldName n : Account.FieldName.values()) {
      if (realm.allowsEdit(n)) {
        fields.add(n);
      }
    }
    if (emailSender != null && emailSender.isEnabled()
        && realm.allowsEdit(Account.FieldName.REGISTER_NEW_EMAIL)) {
      fields.add(Account.FieldName.REGISTER_NEW_EMAIL);
    }
    config.setEditableAccountFields(fields);

    if (gitWebConfig.getUrl() != null) {
      config.setGitwebLink(new GitwebConfig(gitWebConfig.getUrl(), gitWebConfig
          .getGitWebType()));
    }

    if (sshInfo != null && !sshInfo.getHostKeys().isEmpty()) {
      config.setSshdAddress(sshInfo.getHostKeys().get(0).getHost());
    }

    String replyTitle =
        Optional.fromNullable(cfg.getString("change", null, "replyTooltip"))
        .or("Reply and score")
        + " (Shortcut: a)";
    String replyLabel =
        Optional.fromNullable(cfg.getString("change", null, "replyLabel"))
        .or("Reply")
        + "\u2026";
    config.setReplyTitle(replyTitle);
    config.setReplyLabel(replyLabel);

    config.setAllowDraftChanges(cfg.getBoolean("change", "allowDrafts", true));

    return config;
  }

  @Override
  public GerritConfig get() {
    try {
      return create();
    } catch (MalformedURLException e) {
      throw new ProvisionException("Cannot create GerritConfig instance", e);
    }
  }
}
