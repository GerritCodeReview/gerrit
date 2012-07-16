// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.plugin;

import com.google.gerrit.httpd.RestApiServlet;
import com.google.gerrit.server.CurrentUser;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class PluginDispatcherServlet extends RestApiServlet {
  static final Logger log = LoggerFactory
      .getLogger(PluginDispatcherServlet.class);
  private static final long serialVersionUID = 1L;
  private final ListPluginsServlet listServlet;
  private final DisablePluginsServlet disableServlet;

  @Inject
  PluginDispatcherServlet(final Provider<CurrentUser> currentUser,
      ListPluginsServlet list, DisablePluginsServlet disable) {
    super(currentUser);
    listServlet = list;
    disableServlet = disable;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    if (req.getParameter("disable") != null) {
      disableServlet.doGet(req, res);
    } else {
      listServlet.doGet(req, res);
    }
  }
}
