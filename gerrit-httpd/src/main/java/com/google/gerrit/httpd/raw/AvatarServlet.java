// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.httpd.raw;

import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.avatar.AvatarProvider;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Serves avatars based on the installed avatar plugin. If no plugin is
 * installed, serves 404s.
 */
@Singleton
public class AvatarServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;
  private static final Logger log = LoggerFactory
      .getLogger(AvatarServlet.class);

  private final AccountResolver accountResolver;
  private final DynamicItem<AvatarProvider> avatarProviderItem;

  @Inject
  AvatarServlet(AccountResolver accountResolver,
      DynamicItem<AvatarProvider> avatarProvider) {
    this.accountResolver = accountResolver;
    this.avatarProviderItem = avatarProvider;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse rsp)
      throws IOException {
    AvatarProvider avatarProvider = avatarProviderItem.get();
    if (avatarProvider == null) {
      rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    String user = req.getPathInfo();
    if (user == null) {
      rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    // Hack off leading '/'
    user = user.substring(1);

    final Account account;
    try {
      account = accountResolver.find(user);
    } catch (OrmException e1) {
      log.error("Exception while looking up avatar for user: " + user, e1);
      rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    if (account == null) {
      // Account was not found.
      rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    String size = req.getParameter("size");
    int imageSize = 0;
    if (size != null) {
      try {
        imageSize = Integer.parseInt(size);
      } catch (NumberFormatException e) {
        // Ignore, keep size at 0.
      }
    }

    final String url = avatarProvider.getUrl(account, imageSize);
    if (url == null) {
      rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
    } else {
      rsp.sendRedirect(rsp.encodeRedirectURL(url));
    }
  }
}
