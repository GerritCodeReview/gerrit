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

package com.google.gerrit.server.http;

import static com.google.inject.Scopes.SINGLETON;

import com.google.gerrit.client.Link;
import com.google.gerrit.client.reviewdb.RevId;
import com.google.gwtexpui.server.CacheControlFilter;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.internal.UniqueAnnotations;
import com.google.inject.servlet.ServletModule;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

class UrlModule extends ServletModule {
  @Override
  protected void configureServlets() {
    filter("/*").through(Key.get(CacheControlFilter.class));
    bind(Key.get(CacheControlFilter.class)).in(SINGLETON);

    serve("/").with(HostPageServlet.class);
    serve("/Gerrit").with(LegacyGerritServlet.class);
    serve("/Gerrit/*").with(legacyGerritScreen());
    serve("/cat/*").with(CatServlet.class);
    serve("/logout").with(HttpLogoutServlet.class);
    serve("/prettify/*").with(PrettifyServlet.class);
    serve("/signout").with(HttpLogoutServlet.class);
    serve("/ssh_info").with(SshServlet.class);
    serve("/static/*").with(StaticServlet.class);

    serve("/all").with(screen(Link.ALL_MERGED));
    serve("/mine").with(screen(Link.MINE));
    serve("/open").with(screen(Link.ALL_OPEN));
    serve("/settings").with(screen(Link.SETTINGS));
    serve("/starred").with(screen(Link.MINE_STARRED));

    serveRegex( //
        "^/([1-9][0-9]*)/?$", //
        "^/r/([0-9a-fA-F]{4," + RevId.LEN + "})/?$" //
    ).with(changeQuery());
  }

  private Key<HttpServlet> screen(final String target) {
    return key(new HttpServlet() {
      @Override
      protected void doGet(final HttpServletRequest req,
          final HttpServletResponse rsp) throws IOException {
        toGerrit(target, req, rsp);
      }
    });
  }

  private Key<HttpServlet> legacyGerritScreen() {
    return key(new HttpServlet() {
      @Override
      protected void doGet(final HttpServletRequest req,
          final HttpServletResponse rsp) throws IOException {
        final String token = req.getPathInfo().substring(1);
        toGerrit(token, req, rsp);
      }
    });
  }

  private Key<HttpServlet> changeQuery() {
    return key(new HttpServlet() {
      @Override
      protected void doGet(final HttpServletRequest req,
          final HttpServletResponse rsp) throws IOException {
        toGerrit(Link.toChangeQuery(req.getPathInfo()), req, rsp);
      }
    });
  }

  private Key<HttpServlet> key(final HttpServlet servlet) {
    final Key<HttpServlet> srv =
        Key.get(HttpServlet.class, UniqueAnnotations.create());
    bind(srv).toProvider(new Provider<HttpServlet>() {
      @Override
      public HttpServlet get() {
        return servlet;
      }
    }).in(SINGLETON);
    return srv;
  }

  private void toGerrit(final String target, final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {
    final StringBuilder url = new StringBuilder();
    url.append(req.getContextPath());
    url.append('/');
    url.append('#');
    url.append(target);
    rsp.sendRedirect(url.toString());
  }
}
