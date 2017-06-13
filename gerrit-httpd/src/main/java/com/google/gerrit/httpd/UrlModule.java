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

import static com.google.inject.Scopes.SINGLETON;

import com.google.common.base.Strings;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.extensions.client.AuthType;
import com.google.gerrit.httpd.raw.CatServlet;
import com.google.gerrit.httpd.raw.HostPageServlet;
import com.google.gerrit.httpd.raw.LegacyGerritServlet;
import com.google.gerrit.httpd.raw.SshInfoServlet;
import com.google.gerrit.httpd.raw.ToolServlet;
import com.google.gerrit.httpd.restapi.AccessRestApiServlet;
import com.google.gerrit.httpd.restapi.AccountsRestApiServlet;
import com.google.gerrit.httpd.restapi.ChangesRestApiServlet;
import com.google.gerrit.httpd.restapi.ConfigRestApiServlet;
import com.google.gerrit.httpd.restapi.GroupsRestApiServlet;
import com.google.gerrit.httpd.restapi.ProjectsRestApiServlet;
import com.google.gerrit.httpd.rpc.doc.QueryDocumentationFilter;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.GerritOptions;
import com.google.gwtexpui.server.CacheControlFilter;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.internal.UniqueAnnotations;
import com.google.inject.servlet.ServletModule;
import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.lib.Constants;

class UrlModule extends ServletModule {
  private GerritOptions options;
  private AuthConfig authConfig;

  UrlModule(GerritOptions options, AuthConfig authConfig) {
    this.options = options;
    this.authConfig = authConfig;
  }

  @Override
  protected void configureServlets() {
    filter("/*").through(Key.get(CacheControlFilter.class));
    bind(Key.get(CacheControlFilter.class)).in(SINGLETON);

    if (options.enableGwtUi()) {
      filter("/").through(XsrfCookieFilter.class);
      filter("/accounts/self/detail").through(XsrfCookieFilter.class);
      serve("/").with(HostPageServlet.class);
      serve("/Gerrit").with(LegacyGerritServlet.class);
      serve("/Gerrit/*").with(legacyGerritScreen());
      // Forward PolyGerrit URLs to their respective GWT equivalents.
      serveRegex("^/(c|q|x|admin|dashboard|settings)/(.*)").with(gerritUrl());
    }
    serve("/cat/*").with(CatServlet.class);

    if (authConfig.getAuthType() != AuthType.OAUTH && authConfig.getAuthType() != AuthType.OPENID) {
      serve("/logout").with(HttpLogoutServlet.class);
      serve("/signout").with(HttpLogoutServlet.class);
    }
    serve("/ssh_info").with(SshInfoServlet.class);

    serve("/Main.class").with(notFound());
    serve("/com/google/gerrit/launcher/*").with(notFound());
    serve("/servlet/*").with(notFound());

    serve("/all").with(query("status:merged"));
    serve("/mine").with(screen(PageLinks.MINE));
    serve("/open").with(query("status:open"));
    serve("/watched").with(query("is:watched status:open"));
    serve("/starred").with(query("is:starred"));

    serveRegex("^/settings/?$").with(screen(PageLinks.SETTINGS));
    serveRegex("^/register(/.*)?$").with(registerScreen());
    serveRegex("^/([1-9][0-9]*)/?$").with(directChangeById());
    serveRegex("^/p/(.*)$").with(queryProjectNew());
    serveRegex("^/r/(.+)/?$").with(DirectChangeByCommit.class);

    filter("/a/*").through(RequireIdentifiedUserFilter.class);

    // Must be after RequireIdentifiedUserFilter so auth happens before checking
    // for RunAs capability.
    install(new RunAsFilter.Module());

    serveRegex("^/(?:a/)?tools/(.*)$").with(ToolServlet.class);

    // Bind servlets for REST root collections.
    // The '/plugins/' root collection is already handled by HttpPluginServlet
    // which is bound in HttpPluginModule. We cannot bind it here again although
    // this means that plugins can't add REST views on PLUGIN_KIND.
    serveRegex("^/(?:a/)?access/(.*)$").with(AccessRestApiServlet.class);
    serveRegex("^/(?:a/)?accounts/(.*)$").with(AccountsRestApiServlet.class);
    serveRegex("^/(?:a/)?changes/(.*)$").with(ChangesRestApiServlet.class);
    serveRegex("^/(?:a/)?config/(.*)$").with(ConfigRestApiServlet.class);
    serveRegex("^/(?:a/)?groups/(.*)?$").with(GroupsRestApiServlet.class);
    serveRegex("^/(?:a/)?projects/(.*)?$").with(ProjectsRestApiServlet.class);

    filter("/Documentation/").through(QueryDocumentationFilter.class);
  }

  private Key<HttpServlet> notFound() {
    return key(
        new HttpServlet() {
          private static final long serialVersionUID = 1L;

          @Override
          protected void doGet(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
            rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
          }
        });
  }

  private Key<HttpServlet> gerritUrl() {
    return key(
        new HttpServlet() {
          private static final long serialVersionUID = 1L;

          @Override
          protected void doGet(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
            toGerrit(req.getRequestURI(), req, rsp);
          }
        });
  }

  private Key<HttpServlet> screen(String target) {
    return key(
        new HttpServlet() {
          private static final long serialVersionUID = 1L;

          @Override
          protected void doGet(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
            toGerrit(target, req, rsp);
          }
        });
  }

  private Key<HttpServlet> legacyGerritScreen() {
    return key(
        new HttpServlet() {
          private static final long serialVersionUID = 1L;

          @Override
          protected void doGet(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
            final String token = req.getPathInfo().substring(1);
            toGerrit(token, req, rsp);
          }
        });
  }

  private Key<HttpServlet> directChangeById() {
    return key(
        new HttpServlet() {
          private static final long serialVersionUID = 1L;

          @Override
          protected void doGet(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
            try {
              String idString = req.getPathInfo();
              if (idString.endsWith("/")) {
                idString = idString.substring(0, idString.length() - 1);
              }
              Change.Id id = Change.Id.parse(idString);
              toGerrit(PageLinks.toChange(id), req, rsp);
            } catch (IllegalArgumentException err) {
              rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
          }
        });
  }

  private Key<HttpServlet> queryProjectNew() {
    return key(
        new HttpServlet() {
          private static final long serialVersionUID = 1L;

          @Override
          protected void doGet(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
            String name = req.getPathInfo();
            if (Strings.isNullOrEmpty(name)) {
              toGerrit(PageLinks.ADMIN_PROJECTS, req, rsp);
              return;
            }

            while (name.endsWith("/")) {
              name = name.substring(0, name.length() - 1);
            }
            if (name.endsWith(Constants.DOT_GIT_EXT)) {
              name =
                  name.substring(
                      0, //
                      name.length() - Constants.DOT_GIT_EXT.length());
            }
            while (name.endsWith("/")) {
              name = name.substring(0, name.length() - 1);
            }
            Project.NameKey project = new Project.NameKey(name);
            toGerrit(
                PageLinks.toChangeQuery(PageLinks.projectQuery(project, Change.Status.NEW)),
                req,
                rsp);
          }
        });
  }

  private Key<HttpServlet> query(String query) {
    return key(
        new HttpServlet() {
          private static final long serialVersionUID = 1L;

          @Override
          protected void doGet(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
            toGerrit(PageLinks.toChangeQuery(query), req, rsp);
          }
        });
  }

  private Key<HttpServlet> key(HttpServlet servlet) {
    final Key<HttpServlet> srv = Key.get(HttpServlet.class, UniqueAnnotations.create());
    bind(srv)
        .toProvider(
            new Provider<HttpServlet>() {
              @Override
              public HttpServlet get() {
                return servlet;
              }
            })
        .in(SINGLETON);
    return srv;
  }

  private Key<HttpServlet> registerScreen() {
    return key(
        new HttpServlet() {
          private static final long serialVersionUID = 1L;

          @Override
          protected void doGet(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
            toGerrit("/register" + req.getPathInfo(), req, rsp);
          }
        });
  }

  static void toGerrit(String target, HttpServletRequest req, HttpServletResponse rsp)
      throws IOException {
    final StringBuilder url = new StringBuilder();
    url.append(req.getContextPath());
    url.append('/');
    url.append('#');
    url.append(target);
    rsp.sendRedirect(url.toString());
  }
}
