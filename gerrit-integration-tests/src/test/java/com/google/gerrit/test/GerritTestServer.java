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

import com.jcraft.jsch.JSchException;

import org.apache.commons.io.FileUtils;
import org.apache.mina.util.AvailablePortFinder;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class GerritTestServer implements GerritServer {

  private static final int MIN_HTTP_PORT = 8080;

  private static final Logger log = LoggerFactory
      .getLogger(GerritTestServer.class);

  private final static int SSH_PORT = 29418;
  private final static String ADMIN = "admin";
  private final static PersonIdent ADMIN_IDENT = new PersonIdent(
      "Administrator", "admin@test.com");
  private final File site;

  private URL url;
  private final File warFile;

  private Process serverProcess;

  private final RsaKeyPair keyPair;

  GerritTestServer(final String sitePath) {
    warFile = GerritTestProperty.GERRIT_WAR.getOrFail();
    site = new File("target/" + sitePath);

    keyPair = RsaKeyPair.create();

  }

  public void start() {
    init();

    try {
      log.info("Starting Gerrit Server...");
      log.info("site-path = " + site.getAbsolutePath());
      final ProcessBuilder pb =
          new ProcessBuilder("java", "-jar", warFile.getAbsolutePath(), "daemon",
              "--site-path", site.getAbsolutePath());
      pb.redirectErrorStream(true);
      serverProcess = pb.start();

      GerritWebInterface.waitUntilReachable(url);

      log.info("Creating initial user...");
      try {
        final GerritWebInterface gerrit = createWeb();
        try {
          gerrit.createInitialAdminUser(ADMIN, ADMIN_IDENT.getName(),
              ADMIN_IDENT.getEmailAddress(), keyPair.getPublicKeyAsText());
        } finally {
          gerrit.close();
        }
      } catch (Exception e) {
        log.error("Creation of initial user failed.", e);
        throw e;
      }
      log.info("Initial user successfully created.");

      waitUntilReachableBySsh();
      log.info("Gerrit Server successfully started.");
    } catch (Exception e) {
      log.error("Starting Gerrit Server failed.", e);
      destroy();
      throw new RuntimeException(e);
    }
  }

  private void init() {
    try {
      if (!site.exists()) {
        log.info("Initializing Gerrit Server... ");
        log.info("site-path = " + site.getAbsolutePath());
        final File etc = new File(site, "etc");
        if (!etc.mkdirs()) {
          final RuntimeException e =
              new RuntimeException("Failed to create folder '"
                  + etc.getAbsolutePath() + "'.");
          log.error("Initialization of Gerrit Server failed.", e);
          throw e;
        }
        final int httpPort;
        Integer configuredHttpPort = GerritTestProperty.HTTP_PORT.get();
        if (configuredHttpPort != null) {
          httpPort = configuredHttpPort;
        } else {
          httpPort = AvailablePortFinder.getNextAvailable(MIN_HTTP_PORT);
        }
        createConfigFile(httpPort);

        final ProcessBuilder pb =
            new ProcessBuilder("java", "-jar", warFile.getAbsolutePath(), "init",
                "--site-path", site.getAbsolutePath(), "--batch",
                "--no-auto-start");
        pb.redirectErrorStream(true);
        final Process process = pb.start();
        process.waitFor();
        log.info("Gerrit Server successfully initialized.");
      }

      final FileBasedConfig c =
          new FileBasedConfig(new File(site, "/etc/gerrit.config"), FS.DETECTED);
      c.load();
      url = new URL(c.getString("gerrit", null, "canonicalWebUrl"));
    } catch (Exception e) {
      log.error("Initialization of Gerrit Server failed.", e);
      destroy();
      throw new RuntimeException(e);
    }
  }

  private void createConfigFile(int httpPort) throws IOException {
    File configFile = new File(site.getPath(), "etc/gerrit.config");
    PropertyFileWriter writer = new PropertyFileWriter(configFile);
    writer.writeHeader("gerrit");
    writer.writeProperty("canonicalWebUrl","http://localhost:" + httpPort);
    writer.writeHeader("auth");
    writer.writeProperty("type","DEVELOPMENT_BECOME_ANY_ACCOUNT");
    writer.writeHeader("httpd");
    writer.writeProperty("listenUrl","http://*:" + httpPort + "/");
    writer.close();
  }

  private void waitUntilReachableBySsh() throws JSchException {
    final GerritSshInterface ssh = createSsh(getAdminUser());
    try {
      ssh.waitUntilReachable();
    } finally {
      ssh.close();
    }
  }

  @Override
  public void close() {
    destroy();
  }

  private void destroy() {
    stop();

    if (site.exists()) {
      log.info("Deleting Gerrit Server...");
      log.info("site-path = " + site.getAbsolutePath());
      try {
        FileUtils.deleteDirectory(site);
      } catch (IOException e) {
        final RuntimeException re = new RuntimeException("Failed to delete Gerrit site '"
            + site.getAbsolutePath() + "'.", e);
        log.error("Deletion of Gerrit Server failed.", re);
        throw re;
      }
      log.info("Gerrit Server successfully deleted.");
    }
  }

  private void stop() {
    if (serverProcess != null) {
      log.info("Stopping Gerrit Server...");
      log.info("site-path = " + site.getAbsolutePath());
      serverProcess.destroy();
      try {
        serverProcess.waitFor();
        log.info("Gerrit Server successfully stopped");
      } catch (InterruptedException e) {
      }
      serverProcess = null;
    }
  }

  @Override
  public String getAdminUser() {
    return ADMIN;
  }

  @Override
  public PersonIdent getAdminIdent() {
    return ADMIN_IDENT;
  }

  @Override
  public GerritWebInterface createWeb() {
    return new GerritWebInterface(url);
  }

  @Override
  public GerritSshInterface createSsh(final String user) throws JSchException {
    return new GerritSshInterface(url.getHost(), SSH_PORT, user,
        keyPair.getPrivateKey());
  }
}
