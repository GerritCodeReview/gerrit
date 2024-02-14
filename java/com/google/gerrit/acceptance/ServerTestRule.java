// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import autovalue.shaded.kotlin.jvm.functions.Function2;
import com.google.gerrit.acceptance.ProjectResetter.Config;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.util.RequestContext;
import com.google.inject.Injector;
import java.net.InetSocketAddress;
import java.util.Optional;
import org.junit.rules.TestRule;

public interface ServerTestRule extends TestRule {
  /**
   * Initialize a server.
   *
   * <p>All other methods must be called after this method is executed.
   */
  void initServer() throws Exception;

  @Nullable
  ProjectResetter createProjectResetter(
      Function2<AllProjectsName, AllUsersName, Config> resetConfigSupplier) throws Exception;

  Injector getTestInjector();

  Optional<Injector> getHttpdInjector();

  /**
   * Initializes Ssh if a test requires it.
   *
   * <p>The method shouldn't throw an exception if the test doesn't require Ssh. If the test
   * requires ssh and ssh is not supported (e.g. in internal google tests) the method throws {@link
   * UnsupportedOperationException}.
   */
  void initSsh() throws Exception;

  /**
   * Restart backend as a slave and re-init Ssh if a test requires ssh.
   *
   * <p>The method throws {@link UnsupportedOperationException} if restarting is not supported (e.g.
   * in internal google tests).
   */
  void restartAsSlave() throws Exception;

  /**
   * Restart backend as a and re-init Ssh if a test requires ssh.
   *
   * <p>The method throws {@link UnsupportedOperationException} if restarting is not supported (e.g.
   * in internal google tests).
   */
  void restart() throws Exception;

  RestSession createRestSession(@Nullable TestAccount account);

  boolean isReplica();

  Optional<InetSocketAddress> getHttpAddress();

  /**
   * Gets or creates a session associated with the given context.
   *
   * <p>The method throws {@link UnsupportedOperationException} if ssh is not supported (e.g. in
   * internal google tests). The method must be called only if a test or class is annotated with the
   * UseSsh annotation.
   */
  SshSession getOrCreateSshSessionForContext(RequestContext ctx);

  String getUrl();

  boolean sshInitialized();
}
