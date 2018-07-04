// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.elasticsearch;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.inject.ProvisionException;
import java.util.Arrays;
import org.eclipse.jgit.lib.Config;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ElasticConfigurationTest {
  @Rule public ExpectedException exception = ExpectedException.none();

  @Test
  public void singleServer() throws Exception {
    Config cfg = new Config();
    cfg.setString("elasticsearch", null, "server", "http://elastic:1234");
    ElasticConfiguration esCfg = new ElasticConfiguration(cfg);
    assertHosts(esCfg, "http://elastic:1234");
  }

  @Test
  public void multipleServers() throws Exception {
    Config cfg = new Config();
    cfg.setStringList(
        "elasticsearch",
        null,
        "server",
        ImmutableList.of("http://elastic1:1234", "http://elastic2:1234"));
    ElasticConfiguration esCfg = new ElasticConfiguration(cfg);
    assertHosts(esCfg, "http://elastic1:1234", "http://elastic2:1234");
  }

  @Test
  public void noServers() throws Exception {
    assertProvisionException(new Config());
  }

  @Test
  public void singleServerInvalid() throws Exception {
    Config cfg = new Config();
    cfg.setString("elasticsearch", null, "server", "foo");
    assertProvisionException(cfg);
  }

  @Test
  public void multipleServersIncludingInvalid() throws Exception {
    Config cfg = new Config();
    cfg.setStringList(
        "elasticsearch", null, "server", ImmutableList.of("http://elastic1:1234", "foo"));
    ElasticConfiguration esCfg = new ElasticConfiguration(cfg);
    assertHosts(esCfg, "http://elastic1:1234");
  }

  private void assertHosts(ElasticConfiguration cfg, Object... hostURIs) throws Exception {
    assertThat(Arrays.asList(cfg.getHosts()).stream().map(h -> h.toURI()).collect(toList()))
        .containsExactly(hostURIs);
  }

  private void assertProvisionException(Config cfg) throws Exception {
    exception.expect(ProvisionException.class);
    exception.expectMessage("No valid Elasticsearch servers configured");
    new ElasticConfiguration(cfg);
  }
}
