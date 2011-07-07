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

package com.google.gerrit.test.pulsecheck;

import com.google.gerrit.test.AbstractTest;
import com.google.gerrit.test.ConfiguredGerritServer;
import com.google.gerrit.test.Gerrit;
import com.google.gerrit.test.GerritSshInterface;
import com.google.gerrit.test.GerritTestProperty;
import com.google.gerrit.test.GerritWebInterface;

import com.jcraft.jsch.JSchException;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for all pulse checks. A pulse check is always executed against an
 * existing Gerrit server instance. The Gerrit server against which the pulse
 * checks are executed has to be configured by the system property
 * 'gerrit.it.gerrit-url' (see {@link GerritTestProperty#GERRIT_URL} ).
 *
 * All pulse checks should have a name ending with 'PC' (for 'Pulse Check')
 * because the Maven build uses the name pattern '*PC.java' for detecting pulse
 * checks.
 *
 * All pulse checks should be in the package 'com.google.gerrit.test.pulsecheck'
 * because the Eclipse launch configuration for the pulse checks only executed
 * tests from this package.
 */
public abstract class AbstractPulseCheck extends AbstractTest {

  private static final Logger log = LoggerFactory
      .getLogger(AbstractPulseCheck.class);

  protected static ConfiguredGerritServer server;
  protected static GerritSshInterface ssh;
  protected static GerritWebInterface web;

  @BeforeClass
  public static void connectToServer() throws JSchException {
    log.info("Preparing pulse check...");
    server = Gerrit.getConfiguredServer();
    ssh = server.createSsh(server.getAdminUser());
    web = server.createWeb();
  }

  @AfterClass
  public static void cleanUp() {
    log.info("Cleaning up after test...");
    if (ssh != null) {
      ssh.close();
    }
    if (web != null) {
      web.close();
    }
  }
}
