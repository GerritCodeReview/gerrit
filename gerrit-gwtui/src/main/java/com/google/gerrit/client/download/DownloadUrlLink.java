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
import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gwt.aria.client.Roles;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.gwtjsonrpc.common.VoidResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DownloadUrlLink extends Anchor implements ClickHandler {
  public static class DownloadRefUrlLink extends DownloadUrlLink {
    protected String projectName;
    protected String ref;

    protected DownloadRefUrlLink(DownloadScheme urlType,
        String text, String project, String ref) {
      super(urlType, text);
      this.projectName = project;
      this.ref = ref;
    }

    protected void appendRef(StringBuilder r) {
      if (ref != null) {
        r.append(" ");
        r.append(ref);
      }
    }
  }

  public static class AnonGitLink extends DownloadRefUrlLink {
    public AnonGitLink(String project, String ref) {
      super(DownloadScheme.ANON_GIT, Util.M.anonymousDownload("Git"), project, ref);
    }

    @Override
    public String getUrlData() {
      StringBuilder r = new StringBuilder();
      r.append(Gerrit.getConfig().getGitDaemonUrl());
      r.append(projectName);
      appendRef(r);
      return r.toString();
    }
  }

  public static class AnonHttpLink extends DownloadRefUrlLink {
    public AnonHttpLink(String project, String ref) {
      super(DownloadScheme.ANON_HTTP, Util.M.anonymousDownload("HTTP"), project, ref);
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
      appendRef(r);
      return r.toString();
    }
  }

  public static class SshLink extends DownloadRefUrlLink {
    public SshLink(String project, String ref) {
      super(DownloadScheme.SSH, "SSH", project, ref);
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
      appendRef(r);
      return r.toString();
    }
  }

  public static class HttpLink extends DownloadRefUrlLink {
    protected boolean anonymous;

    public HttpLink(String project, String ref, boolean anonymous) {
      super(DownloadScheme.HTTP, "HTTP", project, ref);
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
      appendRef(r);
      return r.toString();
    }
  }

  public static boolean siteReliesOnHttp() {
    return Gerrit.getConfig().getGitHttpUrl() != null
        && Gerrit.getConfig().getAuthType() == AuthType.CUSTOM_EXTENSION
        && !Gerrit.getConfig().siteHasUsernames();
  }

  public static List<DownloadUrlLink> createDownloadUrlLinks(String project,
      String ref, boolean allowAnonymous) {
    List<DownloadUrlLink> urls = new ArrayList<DownloadUrlLink>();
    Set<DownloadScheme> allowedSchemes = Gerrit.getConfig().getDownloadSchemes();

    if (allowAnonymous
        && Gerrit.getConfig().getGitDaemonUrl() != null
        && (allowedSchemes.contains(DownloadScheme.ANON_GIT) ||
            allowedSchemes.contains(DownloadScheme.DEFAULT_DOWNLOADS))) {
      urls.add(new DownloadUrlLink.AnonGitLink(project, ref));
    }

    if (allowAnonymous
        && (allowedSchemes.contains(DownloadScheme.ANON_HTTP) ||
            allowedSchemes.contains(DownloadScheme.DEFAULT_DOWNLOADS))) {
      urls.add(new DownloadUrlLink.AnonHttpLink(project, ref));
    }

    if (Gerrit.getConfig().getSshdAddress() != null
        && hasUserName()
        && (allowedSchemes.contains(DownloadScheme.SSH) ||
            allowedSchemes.contains(DownloadScheme.DEFAULT_DOWNLOADS))) {
      urls.add(new DownloadUrlLink.SshLink(project, ref));
    }

    if ((hasUserName() || siteReliesOnHttp())
        && (allowedSchemes.contains(DownloadScheme.HTTP)
            || allowedSchemes.contains(DownloadScheme.DEFAULT_DOWNLOADS))) {
      urls.add(new DownloadUrlLink.HttpLink(project, ref, allowAnonymous));
    }
    return urls;
  }

  private static boolean hasUserName() {
    return Gerrit.isSignedIn()
        && Gerrit.getUserAccount().getUserName() != null
        && Gerrit.getUserAccount().getUserName().length() > 0;
  }

  protected DownloadScheme urlType;
  protected String urlData;
  protected String hostPageUrl = GWT.getHostPageBaseURL();

  public DownloadUrlLink(DownloadScheme urlType, String text, String urlData) {
    this(text);
    this.urlType = urlType;
    this.urlData = urlData;
  }

  public DownloadUrlLink(DownloadScheme urlType, String text) {
    this(text);
    this.urlType = urlType;
  }

  public DownloadUrlLink(String text) {
    super(text);
    setStyleName(Gerrit.RESOURCES.css().downloadLink());
    Roles.getTabRole().set(getElement());
    addClickHandler(this);

    if (!hostPageUrl.endsWith("/")) {
      hostPageUrl += "/";
    }
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
