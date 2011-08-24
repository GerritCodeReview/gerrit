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

import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.GerritConfig;
import com.google.gerrit.common.data.GitwebLink;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.DownloadSchemeConfig;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.contact.ContactStore;
import com.google.gerrit.server.mail.EmailSender;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gwtexpui.safehtml.client.RegexFindReplace;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

import org.eclipse.jgit.lib.Config;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.servlet.ServletContext;

class GerritConfigProvider implements Provider<GerritConfig> {
  private final Realm realm;
  private final Config cfg;
  private final AuthConfig authConfig;
  private final DownloadSchemeConfig schemeConfig;
  private final GitWebConfig gitWebConfig;
  private final AllProjectsName wildProject;
  private final SshInfo sshInfo;
  private final ApprovalTypes approvalTypes;

  private EmailSender emailSender;
  private final ContactStore contactStore;
  private final ServletContext servletContext;

  @Inject
  GerritConfigProvider(final Realm r, @GerritServerConfig final Config gsc,
      final AuthConfig ac, final GitWebConfig gwc,
      final AllProjectsName wp, final SshInfo si,
      final ApprovalTypes at, final ContactStore cs, final ServletContext sc,
      final DownloadSchemeConfig dc) {
    realm = r;
    cfg = gsc;
    authConfig = ac;
    schemeConfig = dc;
    gitWebConfig = gwc;
    sshInfo = si;
    wildProject = wp;
    approvalTypes = at;
    contactStore = cs;
    servletContext = sc;
  }

  @Inject(optional = true)
  void setEmailSender(final EmailSender d) {
    emailSender = d;
  }

  private GerritConfig create() throws MalformedURLException {
    final GerritConfig config = new GerritConfig();
    switch (authConfig.getAuthType()) {
      case OPENID:
        config.setAllowedOpenIDs(authConfig.getAllowedOpenIDs());
        break;

      case LDAP:
      case LDAP_BIND:
        config.setRegisterUrl(cfg.getString("auth", null, "registerurl"));
        break;
    }
    config.setUseContributorAgreements(cfg.getBoolean("auth",
        "contributoragreements", false));
    config.setGitDaemonUrl(cfg.getString("gerrit", null, "canonicalgiturl"));
    config.setUseContactInfo(contactStore != null && contactStore.isEnabled());
    config.setDownloadSchemes(schemeConfig.getDownloadScheme());
    config.setAuthType(authConfig.getAuthType());
    config.setWildProject(wildProject);
    config.setApprovalTypes(approvalTypes);
    config.setDocumentationAvailable(servletContext
        .getResource("/Documentation/index.html") != null);
    config.setTestChangeMerge(cfg.getBoolean("changeMerge",
        "test", false));

    final Set<Account.FieldName> fields = new HashSet<Account.FieldName>();
    for (final Account.FieldName n : Account.FieldName.values()) {
      if (realm.allowsEdit(n)) {
        fields.add(n);
      }
    }
    if (emailSender != null && emailSender.isEnabled()) {
      fields.add(Account.FieldName.REGISTER_NEW_EMAIL);
    }
    config.setEditableAccountFields(fields);

    if (gitWebConfig.getUrl() != null) {
      config.setGitwebLink(new GitwebLink(gitWebConfig.getUrl(), gitWebConfig
          .getGitWebType()));
    }

    if (sshInfo != null && !sshInfo.getHostKeys().isEmpty()) {
      config.setSshdAddress(sshInfo.getHostKeys().get(0).getHost());
    }

    List<RegexFindReplace> links = new ArrayList<RegexFindReplace>();
    for (String name : cfg.getSubsections("commentlink")) {
      String match = cfg.getString("commentlink", name, "match");

      // Unfortunately this validation isn't entirely complete. Clients
      // can have exceptions trying to evaluate the pattern if they don't
      // support a token used, even if the server does support the token.
      //
      // At the minimum, we can trap problems related to unmatched groups.
      try {
        Pattern.compile(match);
      } catch (PatternSyntaxException e) {
        throw new ProvisionException("Invalid pattern \"" + match
            + "\" in commentlink." + name + ".match: " + e.getMessage());
      }

      String link = cfg.getString("commentlink", name, "link");
      String html = cfg.getString("commentlink", name, "html");
      if (html == null || html.isEmpty()) {
        html = "<a href=\"" + link + "\">$&</a>";
      }
      links.add(new RegexFindReplace(match, html));
    }
    config.setCommentLinks(links);

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
