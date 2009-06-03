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

package com.google.gerrit.server.ssh;

import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet hosting an SSH daemon on another port. During a standard HTTP GET
 * request the servlet returns the hostname and port number back to the client
 * in the form <code>${host} ${port}</code>.
 * <p>
 * Use a Git URL such as <code>ssh://${email}@${host}:${port}/${path}</code>,
 * e.g. <code>ssh://sop@google.com@gerrit.com:8010/tools/gerrit.git</code> to
 * access the SSH daemon itself.
 * <p>
 * Versions of Git before 1.5.3 may require setting the username and port
 * properties in the user's <code>~/.ssh/config</code> file, and using a host
 * alias through a URL such as <code>gerrit-alias:/tools/gerrit.git:
 * <pre>
 * Host gerrit-alias
 *  User sop@google.com
 *  Hostname gerrit.com
 *  Port 8010
 * </pre>
 */
@SuppressWarnings("serial")
public class SshServlet extends HttpServlet {
  @Override
  public void init(final ServletConfig config) throws ServletException {
    super.init(config);
    try {
      GerritSshDaemon.startSshd();
    } catch (SocketException e) {
      throw new ServletException(e);
    } catch (OrmException e) {
      throw new ServletException(e);
    } catch (XsrfException e) {
      throw new ServletException(e);
    }
  }

  @Override
  public void destroy() {
    GerritSshDaemon.stopSshd();
    super.destroy();
  }

  @Override
  protected void doGet(final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {
    rsp.setHeader("Expires", "Fri, 01 Jan 1980 00:00:00 GMT");
    rsp.setHeader("Pragma", "no-cache");
    rsp.setHeader("Cache-Control", "no-cache, must-revalidate");

    final InetSocketAddress addr = GerritSshDaemon.getAddress();
    final String out;
    if (addr != null) {
      final InetAddress ip = addr.getAddress();
      String host;
      if (ip != null && ip.isAnyLocalAddress()) {
        host = req.getServerName();
      } else if (ip instanceof Inet6Address) {
        host = "[" + addr.getHostName() + "]";
      } else {
        host = addr.getHostName();
      }
      out = host + " " + addr.getPort();
    } else {
      out = "NOT_AVAILABLE";
    }

    rsp.setCharacterEncoding("UTF-8");
    rsp.setContentType("text/plain");
    final PrintWriter w = rsp.getWriter();
    try {
      w.write(out);
    } finally {
      w.close();
    }
  }
}
