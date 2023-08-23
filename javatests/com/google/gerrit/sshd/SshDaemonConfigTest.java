// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.sshd;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.inject.Scopes.SINGLETON;
import static org.apache.sshd.core.CoreModuleProperties.AUTH_TIMEOUT;
import static org.apache.sshd.core.CoreModuleProperties.IDLE_TIMEOUT;
import static org.apache.sshd.core.CoreModuleProperties.NIO2_READ_TIMEOUT;
import static org.apache.sshd.core.CoreModuleProperties.REKEY_TIME_LIMIT;
import static org.apache.sshd.core.CoreModuleProperties.WAIT_FOR_SPACE_TIMEOUT;
import static org.mockito.Mockito.mock;

import com.google.gerrit.acceptance.SshdModule;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.ssh.SshAddressesModule;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gerrit.testing.GerritServerTests;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.time.Duration;
import java.util.Optional;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.auth.pubkey.RejectAllPublickeyAuthenticator;
import org.apache.sshd.server.command.CommandFactory;
import org.apache.sshd.server.shell.UnknownCommandFactory;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

public class SshDaemonConfigTest extends GerritServerTests {
  @Inject private SshDaemon daemon;

  @Before
  public void setup() {
    NoShell mockNoShell = mock(NoShell.class);
    GerritGSSAuthenticator mockGerritGSSAuthenticator = mock(GerritGSSAuthenticator.class);
    SshLog mockSshLog = mock(SshLog.class);
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(CommandFactory.class).to(UnknownCommandFactory.class);
                bind(NoShell.class).toInstance(mockNoShell);
                bind(PublickeyAuthenticator.class)
                    .toInstance(RejectAllPublickeyAuthenticator.INSTANCE);
                bind(GerritGSSAuthenticator.class).toInstance(mockGerritGSSAuthenticator);
                install(new SshdModule());
                bind(Config.class).annotatedWith(GerritServerConfig.class).toInstance(config);
                bind(SshLog.class).toInstance(mockSshLog);
                install(new SshAddressesModule());
                bind(MetricMaker.class).to(DisabledMetricMaker.class);
                bind(SshInfo.class).to(SshDaemon.class).in(SINGLETON);
              }
            });
    injector.injectMembers(this);
  }

  @Test
  @GerritConfig(name = "sshd.loginGraceTime", value = "1087s")
  public void customLoginGraceTime() throws Exception {
    Optional<?> result = AUTH_TIMEOUT.get(daemon);
    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(Duration.ofSeconds(1087));
  }

  @Test
  @GerritConfig(name = "sshd.idleTimeout", value = "1229s")
  public void customIdleTimeout() throws Exception {
    Optional<?> result1 = IDLE_TIMEOUT.get(daemon);
    assertThat(result1).isPresent();
    assertThat(result1.get()).isEqualTo(Duration.ofSeconds(1229));

    Optional<?> result2 = NIO2_READ_TIMEOUT.get(daemon);
    assertThat(result2).isPresent();
    assertThat(result2).isEqualTo(result1);
  }

  @Test
  @GerritConfig(name = "sshd.rekeyTimeLimit", value = "1381s")
  public void customRekeyTimeLimit() throws Exception {
    Optional<?> result = REKEY_TIME_LIMIT.get(daemon);
    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(Duration.ofSeconds(1381));
  }

  @Test
  @GerritConfig(name = "sshd.waitTimeout", value = "1523s")
  public void customWaitTimeout() throws Exception {
    Optional<?> result = WAIT_FOR_SPACE_TIMEOUT.get(daemon);
    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(Duration.ofSeconds(1523));
  }
}
