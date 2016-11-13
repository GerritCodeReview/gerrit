// Copyright (C) 2008 The Android Open Source Project
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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.server.ssh.SshInfo;
import com.google.gwtexpui.server.CacheHeaders;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.jcraft.jsch.HostKey;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet hosting an SSH daemon on another port. During a standard HTTP GET request the servlet
 * returns the hostname and port number back to the client in the form <code>${host} ${port}</code>.
 *
 * <p>Use a Git URL such as <code>ssh://${email}@${host}:${port}/${path}</code>, e.g. {@code
 * ssh://sop@google.com@gerrit.com:8010/tools/gerrit.git} to access the SSH daemon itself.
 *
 * <p>Versions of Git before 1.5.3 may require setting the username and port properties in the
 * user's {@code ~/.ssh/config} file, and using a host alias through a URL such as {@code
 * gerrit-alias:/tools/gerrit.git}:
 *
 * <pre>{@code
 * Host gerrit-alias
 *  User sop@google.com
 *  Hostname gerrit.com
 *  Port 8010
 * }</pre>
 */
@SuppressWarnings("serial")
@Singleton
public class SshInfoServlet extends HttpServlet {
  private final SshInfo sshd;

  @Inject
  SshInfoServlet(final SshInfo daemon) {
    sshd = daemon;
  }

  @Override
  protected void doGet(final HttpServletRequest req, final HttpServletResponse rsp)
      throws IOException {
    final List<HostKey> hostKeys = sshd.getHostKeys();
    final String out;
    if (!hostKeys.isEmpty()) {
      String host = hostKeys.get(0).getHost();
      String port = "22";

      if (host.contains(":")) {
        final int p = host.lastIndexOf(':');
        port = host.substring(p + 1);
        host = host.substring(0, p);
      }

      if (host.equals("*")) {
        host = req.getServerName();

      } else if (host.startsWith("[") && host.endsWith("]")) {
        host = host.substring(1, host.length() - 1);
      }

      out = host + " " + port;
    } else {
      out = "NOT_AVAILABLE";
    }

    CacheHeaders.setNotCacheable(rsp);
    rsp.setCharacterEncoding(UTF_8.name());
    rsp.setContentType("text/plain");
    try (PrintWriter w = rsp.getWriter()) {
      w.write(out);
    }
  }
}
