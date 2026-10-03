// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.pgm.http.jetty;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.ProxyConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.Test;

public class JettyServerTest {
  @Test
  public void proxyProtocolConnectorUsesProxyConnectionFactory() {
    HttpConfiguration config = new HttpConfiguration();

    ServerConnector connector = JettyServer.newServerConnector(new Server(), 0, 0, config, true);
    ImmutableList<ConnectionFactory> factories =
        ImmutableList.copyOf(connector.getConnectionFactories());

    assertThat(factories).hasSize(2);
    assertThat(factories.get(0)).isInstanceOf(ProxyConnectionFactory.class);
    assertThat(factories.get(1)).isInstanceOf(HttpConnectionFactory.class);
  }

  @Test
  public void regularConnectorDoesNotUseProxyProtocol() {
    HttpConfiguration config = new HttpConfiguration();

    ServerConnector connector = JettyServer.newServerConnector(new Server(), 0, 0, config, false);
    ImmutableList<ConnectionFactory> factories =
        ImmutableList.copyOf(connector.getConnectionFactories());

    assertThat(factories).hasSize(1);
    assertThat(factories.get(0)).isInstanceOf(HttpConnectionFactory.class);
  }
}
