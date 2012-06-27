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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.httpd.RestApiServlet;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.plugins.InvalidPluginException;
import com.google.gerrit.server.plugins.PluginInstallException;
import com.google.gerrit.server.plugins.ReloadPlugins;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
public class ReloadPluginsServlet extends RestApiServlet {
  static final Logger log = LoggerFactory.getLogger(ReloadPluginsServlet.class);
  private static final long serialVersionUID = 1L;
  private final Provider<ReloadPlugins> factory;

  @Inject
  ReloadPluginsServlet(final Provider<CurrentUser> currentUser,
      Provider<ReloadPlugins> reload) {
    super(currentUser);
    this.factory = reload;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    ReloadPlugins impl = factory.get();
    String[] plugins = req.getParameterValues("reload");
    if(plugins == null || (plugins.length == 1 && plugins[0].equals(""))) {
      plugins = new String[0];
    }
    try {
      impl.reload(Arrays.asList(plugins));
    } catch (InvalidPluginException e) {
      handleError(e, req, res);
    } catch (PluginInstallException e) {
      handleError(e, req, res);
    }
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    final PrintWriter stdout;
    try {
      stdout =
          new PrintWriter(new BufferedWriter(new OutputStreamWriter(buf,
              "UTF-8")));
    } catch (UnsupportedEncodingException e) {
      // Our encoding is required by the specifications for the runtime.
      throw new RuntimeException("JVM lacks UTF-8 encoding", e);
    }
    if (acceptsJson(req)) {
      res.setContentType(JSON_TYPE);
      buf.write(JSON_MAGIC);
      stdout.write("{\"reloaded\":true}");
    } else {
      res.setContentType("text/plain");
      stdout.write("reloaded");
    }
    stdout.flush();
    res.setCharacterEncoding("UTF-8");
    send(req, res, buf.toByteArray());
  }
}
