// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.client.download;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadScheme;
import com.google.gwt.aria.client.Roles;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.gwtjsonrpc.common.VoidResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DownloadUrlLink extends Anchor implements ClickHandler {
  public static class AnonGitLink extends DownloadUrlLink {
    public AnonGitLink(String project) {
      super(DownloadScheme.ANON_GIT, Util.M.anonymousDownload("Git"), project);
    }

    @Override
    public String getUrlData() {
      StringBuilder r = new StringBuilder();
      r.append(Gerrit.getConfig().getGitDaemonUrl());
      r.append(projectName);
      return r.toString();
    }
  }

  public static class AnonHttpLink extends DownloadUrlLink {
    public AnonHttpLink(String project) {
      super(DownloadScheme.ANON_HTTP, Util.M.anonymousDownload("HTTP"), project);
    }

    @Override
    public String getUrlData() {
      StringBuilder r = new StringBuilder();
      if (Gerrit.getConfig().getGitHttpUrl() != null) {
        r.append(Gerrit.getConfig().getGitHttpUrl());
      } else {
        r.append(hostPageUrl);
      }
      r.append(projectName);
      return r.toString();
    }
  }

  public static class SshLink extends DownloadUrlLink {
    public SshLink(String project) {
      super(DownloadScheme.SSH, "SSH", project);
    }

    @Override
    public String getUrlData() {
      String sshAddr = Gerrit.getConfig().getSshdAddress();
      final StringBuilder r = new StringBuilder();
      r.append("ssh://");
      r.append(Gerrit.getUserAccount().getUserName());
      r.append("@");
      if (sshAddr.startsWith("*:") || "".equals(sshAddr)) {
        r.append(Window.Location.getHostName());
      }
      if (sshAddr.startsWith("*")) {
        sshAddr = sshAddr.substring(1);
      }
      r.append(sshAddr);
      r.append("/");
      r.append(projectName);
      return r.toString();
    }
  }

  public static class HttpLink extends DownloadUrlLink {
    protected boolean anonymous;

    public HttpLink(String project, boolean anonymous) {
      super(DownloadScheme.HTTP, "HTTP", project);
      this.anonymous = anonymous;
    }

    @Override
    public String getUrlData() {
      final StringBuilder r = new StringBuilder();
      if (Gerrit.getConfig().getGitHttpUrl() != null
          && (anonymous || siteReliesOnHttp())) {
        r.append(Gerrit.getConfig().getGitHttpUrl());
      } else {
        String base = hostPageUrl;
        int p = base.indexOf("://");
        int s = base.indexOf('/', p + 3);
        if (s < 0) {
          s = base.length();
        }
        String host = base.substring(p + 3, s);
        if (host.contains("@")) {
          host = host.substring(host.indexOf('@') + 1);
        }

        r.append(base.substring(0, p + 3));
        r.append(Gerrit.getUserAccount().getUserName());
        r.append('@');
        r.append(host);
        r.append(base.substring(s));
      }
      r.append(projectName);
      return r.toString();
    }
  }

  public static boolean siteReliesOnHttp() {
    return Gerrit.getConfig().getGitHttpUrl() != null
        && Gerrit.info().auth().isCustomExtension()
        && !Gerrit.getConfig().siteHasUsernames();
  }

  public static List<DownloadUrlLink> createDownloadUrlLinks(String project,
      boolean allowAnonymous) {
    List<DownloadUrlLink> urls = new ArrayList<>();
    Set<String> allowedSchemes = Gerrit.info().download().schemes();

    if (allowAnonymous
        && Gerrit.getConfig().getGitDaemonUrl() != null
        && allowedSchemes.contains("git")) {
      urls.add(new DownloadUrlLink.AnonGitLink(project));
    }

    if (allowAnonymous
        && allowedSchemes.contains("anonymous http")) {
      urls.add(new DownloadUrlLink.AnonHttpLink(project));
    }

    if (Gerrit.getConfig().getSshdAddress() != null
        && hasUserName()
        && allowedSchemes.contains("ssh")) {
      urls.add(new DownloadUrlLink.SshLink(project));
    }

    if ((hasUserName() || siteReliesOnHttp())
        && allowedSchemes.contains("http")) {
      urls.add(new DownloadUrlLink.HttpLink(project, allowAnonymous));
    }
    return urls;
  }

  private static boolean hasUserName() {
    return Gerrit.isSignedIn()
        && Gerrit.getUserAccount().getUserName() != null
        && Gerrit.getUserAccount().getUserName().length() > 0;
  }

  protected DownloadScheme urlType;
  protected String projectName;
  protected String urlData;
  protected String hostPageUrl = GWT.getHostPageBaseURL();

  public DownloadUrlLink(DownloadScheme urlType, String text, String project) {
    super(text);
    setStyleName(Gerrit.RESOURCES.css().downloadLink());
    Roles.getTabRole().set(getElement());
    addClickHandler(this);

    if (!hostPageUrl.endsWith("/")) {
      hostPageUrl += "/";
    }
    this.urlType = urlType;
    this.projectName = project;
  }

  public String getUrlData() {
    return urlData;
  }

  @Override
  public void onClick(ClickEvent event) {
    event.preventDefault();
    event.stopPropagation();

    select();

    if (Gerrit.isSignedIn()) {
      // If the user is signed-in, remember this choice for future panels.
      //
      AccountGeneralPreferences pref =
          Gerrit.getUserAccount().getGeneralPreferences();
      pref.setDownloadUrl(urlType);
      com.google.gerrit.client.account.Util.ACCOUNT_SVC.changePreferences(pref,
          new AsyncCallback<VoidResult>() {
            @Override
            public void onFailure(Throwable caught) {
            }

            @Override
            public void onSuccess(VoidResult result) {
            }
          });
    }
  }

  void select() {
    DownloadUrlPanel parent = (DownloadUrlPanel) getParent();
    for (Widget w : parent) {
      if (w != this && w instanceof DownloadUrlLink) {
        w.removeStyleName(Gerrit.RESOURCES.css().downloadLink_Active());
      }
    }
    parent.setCurrentUrl(this);
    addStyleName(Gerrit.RESOURCES.css().downloadLink_Active());
  }
}
