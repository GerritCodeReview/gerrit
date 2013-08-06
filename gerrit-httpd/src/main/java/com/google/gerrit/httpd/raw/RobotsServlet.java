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

import com.google.common.io.ByteStreams;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * This class provides a mechanism to use a configurable robots.txt file,
 * outside of the .war of the application. In order to configure it add the
 * following to the <code>httpd</code> section of the <code>gerrit.conf</code>
 * file:
 *
 * <pre>
 * [httpd]
 *         robotsFile = etc/myrobots.txt
 * </pre>
 *
 * If the specified file name is relative it will resolved as a sub directory of
 * the site directory, if it is absolute it will be used as is.
 *
 * If the specified file doesn't exist or isn't readable the servlet will
 * default to the <code>robots.txt</code> file bundled with the .war file of the
 * application.
 */
@SuppressWarnings("serial")
@Singleton
public class RobotsServlet extends HttpServlet {
  private static final Logger log =
      LoggerFactory.getLogger(RobotsServlet.class);

  private final File robotsFile;

  @Inject
  RobotsServlet(@GerritServerConfig final Config config, final SitePaths sitePaths) {
    File file = sitePaths.resolve(
      config.getString("httpd", null, "robotsFile"));
    if (file != null && (!file.exists() || !file.canRead())) {
      log.warn("Cannot read httpd.robotsFile, using default");
      file = null;
    }
    robotsFile = file;
  }

  @Override
  protected void doGet(final HttpServletRequest req, final HttpServletResponse rsp)
      throws IOException {
    rsp.setContentType("text/plain");
    InputStream in = openRobotsFile();
    try {
      OutputStream out = rsp.getOutputStream();
      try {
        ByteStreams.copy(in, out);
      } finally {
        out.close();
      }
    } finally {
      in.close();
    }
  }

  private InputStream openRobotsFile() {
    if (robotsFile != null) {
      try {
        return new FileInputStream(robotsFile);
      } catch (IOException e) {
        log.warn("Cannot read " + robotsFile + "; using default", e);
      }
    }
    return getServletContext().getResourceAsStream("/robots.txt");
  }
}
