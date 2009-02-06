// Copyright 2008 Google Inc.
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

import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.server.GerritServer;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;

import org.apache.sshd.SshServer;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.util.SecurityUtils;
import org.apache.sshd.server.UserAuth;
import org.apache.sshd.server.auth.UserAuthPublicKey;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

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
public class SshServlet extends HttpServlet {
  private static SshServer sshd;
  private static final Logger log = LoggerFactory.getLogger(SshServlet.class);

  public static synchronized void startSshd() throws ServletException {
    final GerritServer srv;
    try {
      srv = GerritServer.getInstance();
    } catch (OrmException e) {
      throw new ServletException("Cannot load GerritServer", e);
    } catch (XsrfException e) {
      throw new ServletException("Cannot load GerritServer", e);
    }

    final int myPort = Common.getGerritConfig().getSshdPort();
    sshd = SshServer.setUpDefaultServer();
    sshd.setPort(myPort);

    final File sitePath = srv.getSitePath();
    if (SecurityUtils.isBouncyCastleRegistered()) {
      sshd.setKeyPairProvider(new FileKeyPairProvider(new String[] {
          new File(sitePath, "ssh_host_rsa_key").getAbsolutePath(),
          new File(sitePath, "ssh_host_dsa_key").getAbsolutePath()}));
    } else {
      final SimpleGeneratorHostKeyProvider keyp;

      keyp = new SimpleGeneratorHostKeyProvider();
      keyp.setPath(new File(sitePath, "ssh_host_key").getAbsolutePath());
      sshd.setKeyPairProvider(keyp);
    }

    sshd.setUserAuthFactories(Arrays
        .<NamedFactory<UserAuth>> asList(new UserAuthPublicKey.Factory()));
    sshd.setPublickeyAuthenticator(new DatabasePubKeyAuth());
    sshd.setCommandFactory(new GerritCommandFactory());
    sshd.setShellFactory(new NoShell());

    try {
      sshd.start();
      log.info("Started Gerrit SSHD on 0.0.0.0:" + myPort);
    } catch (IOException e) {
      log.error("Cannot start Gerrit SSHD on 0.0.0.0:" + myPort, e);
      sshd = null;
      throw new ServletException("Cannot start sshd on " + myPort, e);
    }
  }

  public static synchronized void stopSshd() {
    if (sshd != null) {
      try {
        sshd.stop();
        log.info("Stopped Gerrit SSHD on 0.0.0.0:" + sshd.getPort());
      } finally {
        sshd = null;
      }
    }
  }

  public static synchronized int getSshdPort() {
    return sshd != null ? sshd.getPort() : 0;
  }

  @Override
  public void init(final ServletConfig config) throws ServletException {
    super.init(config);
    startSshd();
  }

  @Override
  public void destroy() {
    stopSshd();
    super.destroy();
  }

  @Override
  protected void doGet(final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {
    rsp.setHeader("Expires", "Fri, 01 Jan 1980 00:00:00 GMT");
    rsp.setHeader("Pragma", "no-cache");
    rsp.setHeader("Cache-Control", "no-cache, must-revalidate");

    final int port = getSshdPort();
    final String out;
    if (0 < port) {
      out = req.getServerName() + " " + port;
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
