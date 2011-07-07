// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.test;


import com.google.gerrit.test.util.Check;
import com.google.gerrit.test.util.TempUtil;
import com.google.gerrit.test.util.WaitUtil;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Logger;
import com.jcraft.jsch.Session;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.util.FS;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class GerritSshInterface {

  private static int SSH_TIMEOUT = GerritTestProperty.SSH_TIMEOUT.get();
  private static int SSH_POLLING_INTERVAL = GerritTestProperty.SSH_POLLING_INTERVAL.get();

  private static final org.slf4j.Logger log = LoggerFactory
      .getLogger(GerritSshInterface.class);

  private final Session session;

  private final JschIdentity jschIdentity;

  static {
    final SshTraceLevel sshTraceLevel = GerritTestProperty.SSH_TRACE_LEVEL.get();
    if (sshTraceLevel != null) {
      JSch.setLogger(new SshLogger(sshTraceLevel));
    }

    final Properties config = new Properties();
    config.put("StrictHostKeyChecking", "no");
    JSch.setConfig(config);
  }

  private static class SshLogger implements Logger {

    private final SshTraceLevel level;

    SshLogger(final SshTraceLevel level) {
      this.level = level;
    }

    @Override
    public boolean isEnabled(final int level) {
      return this.level.getLevel() <= level;
    }

    @Override
    public void log(final int level, final String message) {
      log.info(SshTraceLevel.valueOf(level).name() + ": " + message);
    }
  }

  private GerritSshInterface(final String host, final int port,
      final String user, final JschIdentity jschIdentity)
      throws JSchException {
    this.jschIdentity = jschIdentity;

    final JSch jsch = new JSch();
    jschIdentity.addTo(jsch);
    session = jsch.getSession(user, host, port);

    log.info("Connecting SSH session");
    session.connect();
  }

  public GerritSshInterface(final String host, final int port,
      final String user, final byte[] prvKeyData) throws JSchException {
    this(host, port, user, new JschIdentity(user, prvKeyData));
  }

  public GerritSshInterface(final String host, final int port,
      final String user, final File privateKeyFile, final String passphrase)
      throws JSchException {
    this(host, port, user, new JschIdentity(privateKeyFile, passphrase));
  }

  public void createProject(final String projectName,
      final boolean createEmptyCommit) throws JSchException {
    final StringBuilder b = new StringBuilder();
    b.append("create-project");
    if (createEmptyCommit) {
      b.append(" --empty-commit");
    }
    b.append(" --name ").append(quote(projectName));
    final String result = sendSshCommand(b.toString());
    if (result.length() > 0) {
      throw new RuntimeException("Failed to create project '" + projectName
          + "': " + result);
    }
  }

  public Git cloneProject(final String projectName) throws JSchException {
    setSshSessionFactory();
    final File gitDir = TempUtil.createTempDirectory();
    log.info("Cloning repository for project '" + projectName + "'...");
    final CloneCommand cloneCmd = Git.cloneRepository();
    cloneCmd.setURI(getProjectUri(projectName));
    cloneCmd.setDirectory(gitDir);
    return cloneCmd.call();
  }

  public boolean projectExists(String name) throws Exception {
    List<String> projects = listProjects();
    return projects.contains(name);
  }

  private void setSshSessionFactory() {
    // register a JschConfigSessionFactory that adds the private key as identity
    // to the JSch instance of JGit so that SSH communication via JGit can
    // succeed
    SshSessionFactory.setInstance(new JschConfigSessionFactory() {
      @Override
      protected void configure(Host hc, Session session) {
        try {
          final JSch jsch = getJSch(hc, FS.DETECTED);
          jschIdentity.addTo(jsch);
        } catch (JSchException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  private String getProjectUri (final String projectName) {
    final StringBuilder b = new StringBuilder();
    b.append("ssh://");
    b.append(session.getUserName());
    b.append("@");
    b.append(session.getHost());
    b.append(":");
    b.append(session.getPort());
    b.append("/");
    b.append(projectName);
    return b.toString();

  }

  public List<String> listProjects() throws JSchException {
    final String result = sendSshCommand("ls-projects");
    return Arrays.asList(result.split("\n"));
  }

  public List<String> listProjectsAsTree() throws JSchException {
    final String result = sendSshCommand("ls-projects --tree");
    return lines(result);
  }

  public void createGroup(final String groupName) throws JSchException {
    sendSshCommand("create-group " + quote(groupName));
  }

  void waitUntilReachable() {
    log.info("Testing SSH Connection...");

    final Check checkThatReachable = new Check() {
      @Override
      public boolean hasFinished() {
        return isReachable();
      }
    };
    WaitUtil.wait(checkThatReachable, SSH_TIMEOUT, SSH_POLLING_INTERVAL, log,
        "Timeout during SSH communication with server. SSH ping failed.");

    log.info("SSH Connection successfully tested.");
  }

  private boolean isReachable() {
    try {
      listProjects();
      return true;
    } catch (JSchException e) {
      return false;
    }
  }

  private String sendSshCommand(final String command) throws JSchException {
    log.info("Sending SSH command...");
    log.info("SSH command = '" + command + "'");
    final ChannelExec channel = (ChannelExec) session.openChannel("exec");
    try {
      channel.setCommand("gerrit " + command);
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      final ByteArrayOutputStream err = new ByteArrayOutputStream();
      try {
        channel.setOutputStream(out);
        channel.setErrStream(err);
        channel.connect(SSH_TIMEOUT);

        final Check checkThatChannelClosed = new Check() {
          @Override
          public boolean hasFinished() {
            return channel.isClosed();
          }
        };
        WaitUtil.wait(checkThatChannelClosed, SSH_TIMEOUT,
            SSH_POLLING_INTERVAL, log,
            "Timeout during SSH communication with server. "
                + "SSH command failed.");

        if (err.size() > 0) {
          final RuntimeException e =
              new RuntimeException("Sending SSH command '" + command
                  + "' failed: " + new String(err.toByteArray())
                  + "; exit status = " + channel.getExitStatus());
          log.error("Sending SSH command failed.", e);
          throw e;
        }
        log.info("SSH command successfully sent.");
        final String sshResponse = new String(out.toByteArray());
        log.info("SSH response = '" + sshResponse + "'");
        return sshResponse;
      } finally {
        closeStream(out);
        closeStream(err);
      }
    } finally {
      channel.disconnect();
    }
  }

  private void closeStream(final OutputStream stream) {
    try {
      stream.close();
    } catch (IOException e) {
    }
  }

  private String quote(final String s) {
    final StringBuilder b = new StringBuilder();
    b.append("\"").append(s).append("\"");
    return b.toString();
  }

  private List<String> lines(final String result) {
    return Arrays.asList(result.split("\n"));
  }

  public void close() {
    log.info("Closing SSH session.");
    session.disconnect();
  }
}
