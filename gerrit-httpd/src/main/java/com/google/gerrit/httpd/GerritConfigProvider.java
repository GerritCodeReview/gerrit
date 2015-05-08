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

import com.google.common.base.Optional;
import com.google.gerrit.common.data.GerritConfig;
import com.google.gerrit.common.data.GitwebConfig;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

import org.eclipse.jgit.lib.Config;

import java.net.MalformedURLException;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContext;

class GerritConfigProvider implements Provider<GerritConfig> {
  private final Config cfg;
  private final GitWebConfig gitWebConfig;
  private final SshInfo sshInfo;

  private final ServletContext servletContext;
  private final String anonymousCowardName;

  @Inject
  GerritConfigProvider(
      @GerritServerConfig Config gsc,
      GitWebConfig gwc,
      SshInfo si,
      ServletContext sc,
      @AnonymousCowardName String acn) {
    cfg = gsc;
    gitWebConfig = gwc;
    sshInfo = si;
    servletContext = sc;
    anonymousCowardName = acn;
  }

  private GerritConfig create() throws MalformedURLException {
    final GerritConfig config = new GerritConfig();
    config.setGitDaemonUrl(cfg.getString("gerrit", null, "canonicalgiturl"));
    config.setDocumentationAvailable(servletContext
        .getResource("/Documentation/index.html") != null);
    config.setAnonymousCowardName(anonymousCowardName);
    config.setSuggestFrom(cfg.getInt("suggest", "from", 0));
    config.setChangeUpdateDelay((int) ConfigUtil.getTimeUnit(
        cfg, "change", null, "updateDelay", 30, TimeUnit.SECONDS));
    config.setLargeChangeSize(cfg.getInt("change", "largeChange", 500));

    config.setReportBugUrl(cfg.getString("gerrit", null, "reportBugUrl"));
    config.setReportBugText(cfg.getString("gerrit", null, "reportBugText"));

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
