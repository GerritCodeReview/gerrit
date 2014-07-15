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

package com.googlesource.gerrit.plugins.serverconfig;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteStreams;
import com.google.gerrit.audit.AuditEvent;
import com.google.gerrit.audit.AuditService;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.util.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.diff.RawText;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class ServerConfigServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  private final File site_path;
  private final File etc_dir;
  private final File static_dir;
  private final String gerrit_config_path;
  private final AuditService auditService;
  private final Provider<WebSession> webSession;
  private final String pluginName;

  @Inject
  ServerConfigServlet(SitePaths sitePaths, Provider<WebSession> webSession,
      AuditService auditService, @PluginName String pluginName) {
    this.webSession = webSession;
    this.auditService = auditService;
    this.pluginName = pluginName;
    this.site_path = sitePaths.site_path;
    this.etc_dir = sitePaths.etc_dir;
    this.static_dir = sitePaths.static_dir;
    try {
      this.gerrit_config_path = sitePaths.gerrit_config.getCanonicalPath();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    if (!isValidFile(req)) {
      res.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    streamFile(req, res);
  }

  @Override
  public void doPut(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    if (!isValidFile(req)) {
      res.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    if (isGerritConfig(req)) {
      writeFileAndFireAuditEvent(req, res);
    } else {
      writeFile(req, res);
    }
  }

  private void writeFileAndFireAuditEvent(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    File oldFile = configFile(req);
    File dir = oldFile.getParentFile();
    File newFile = File.createTempFile(oldFile.getName(), ".new", dir);
    streamRequestToFile(req, newFile);

    String diff = diff(oldFile, newFile);
    audit("about to change config file", oldFile.getPath(), diff);

    newFile.renameTo(oldFile);
    audit("changed config file", oldFile.getPath(), diff);

    res.setStatus(HttpServletResponse.SC_NO_CONTENT);
  }

  private static String diff(File oldFile, File newFile) throws IOException {
    RawText oldContext = new RawText(oldFile);
    RawText newContext = new RawText(newFile);
    UnifiedDiffer differ = new UnifiedDiffer();
    return differ.diff(oldContext, newContext);
  }

  private void audit(String what, String path, String diff) {
    String sessionId = webSession.get().getSessionId();
    CurrentUser who = webSession.get().getCurrentUser();
    long when = TimeUtil.nowMs();
    Multimap<String, Object> params = LinkedHashMultimap.create();
    params.put("plugin", pluginName);
    params.put("class", ServerConfigServlet.class);
    params.put("diff", diff);
    params.put("file", path);
    auditService.dispatch(new AuditEvent(sessionId, who, what, when, params, null));
  }

  private boolean isGerritConfig(HttpServletRequest req) throws IOException {
    File f = configFile(req);
    return gerrit_config_path.equals(f.getCanonicalPath());
  }

  private boolean isValidFile(HttpServletRequest req) throws IOException {
    File f = configFile(req);
    if (!f.isFile()) {
      return false;
    }
    return isParent(etc_dir, f) || isParent(static_dir, f);
  }

  private File configFile(HttpServletRequest req) {
    return new File(site_path, req.getPathInfo());
  }

  private boolean isParent(File parent, File child) throws IOException {
    File p = parent.getCanonicalFile();
    File c = child.getCanonicalFile();
    for (;;) {
      c = c.getParentFile();
      if (c == null) {
        return false;
      }
      if (c.equals(p)) {
        return true;
      }
    }
  }

  private void streamFile(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    File f = configFile(req);
    res.setStatus(HttpServletResponse.SC_OK);
    res.setContentType("application/octet-stream");
    res.setContentLength((int) f.length());
    OutputStream out = res.getOutputStream();
    InputStream in = new FileInputStream(f);
    try {
      ByteStreams.copy(in, out);
    } finally {
      in.close();
    }
  }

  private void writeFile(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    res.setStatus(HttpServletResponse.SC_NO_CONTENT);
    streamRequestToFile(req, configFile(req));
  }

  private void streamRequestToFile(HttpServletRequest req, File file)
      throws IOException, FileNotFoundException {
    InputStream in = req.getInputStream();
    OutputStream out = new FileOutputStream(file);
    try {
      ByteStreams.copy(in, out);
    } finally {
      out.close();
    }
  }
}
