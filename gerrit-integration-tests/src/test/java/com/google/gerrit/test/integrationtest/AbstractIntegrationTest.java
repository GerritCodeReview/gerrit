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

package com.google.gerrit.test.integrationtest;

import com.google.gerrit.test.AbstractTest;
import com.google.gerrit.test.Gerrit;
import com.google.gerrit.test.GerritServer;
import com.google.gerrit.test.GerritSshInterface;
import com.google.gerrit.test.GerritWebInterface;

import com.jcraft.jsch.JSchException;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for all integration tests. For every integration test a new Gerrit
 * server is initialized and started. By this it is ensured that each
 * integration test is executed against a clean (empty) Gerrit server and there
 * are no adverse effects between the tests.
 *
 * All integration tests should have a name ending with 'IT' (for 'Integration
 * Test') because the Maven build uses the name pattern '*IT.java' for detecting
 * integration tests.
 *
 * All integration tests should be in the package
 * 'com.google.gerrit.test.integrationtest' because the Eclipse launch
 * configuration for the integration tests only executed tests from this
 * package.
 */
public abstract class AbstractIntegrationTest extends AbstractTest {

  private static final Logger log = LoggerFactory
      .getLogger(AbstractIntegrationTest.class);

  protected static GerritServer server;
  protected static GerritSshInterface ssh;
  protected static GerritWebInterface web;

  @BeforeClass
  public static void initServer() throws JSchException {
    log.info("Preparing integration test...");
    server = Gerrit.startTestServer();
    ssh = server.createSsh(server.getAdminUser());
    web = server.createWeb();
  }

  @Before
  public void initTestcase() {
    web.login(server.getAdminUser(), server.getAdminIdent().getName(), null);
  }

  @After
  public void afterTestCase() {
    web.logout();
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
    if (server != null) {
      server.close();
    }
  }
}
