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
import static com.google.gerrit.elasticsearch.ElasticConfiguration.DEFAULT_USERNAME;
import static com.google.gerrit.elasticsearch.ElasticConfiguration.KEY_PASSWORD;
import static com.google.gerrit.elasticsearch.ElasticConfiguration.KEY_PREFIX;
import static com.google.gerrit.elasticsearch.ElasticConfiguration.KEY_SERVER;
import static com.google.gerrit.elasticsearch.ElasticConfiguration.KEY_USERNAME;
import static com.google.gerrit.elasticsearch.ElasticConfiguration.SECTION_ELASTICSEARCH;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.testing.GerritBaseTests;
import com.google.inject.ProvisionException;
import java.util.Arrays;
import org.apache.http.HttpHost;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class ElasticConfigurationTest extends GerritBaseTests {
  @Test
  public void singleServerNoOtherConfig() throws Exception {
    Config cfg = newConfig();
    ElasticConfiguration esCfg = new ElasticConfiguration(cfg);
    assertHosts(esCfg, "http://elastic:1234");
    assertThat(esCfg.username).isNull();
    assertThat(esCfg.password).isNull();
    assertThat(esCfg.prefix).isEmpty();
  }

  @Test
  public void serverWithoutPortSpecified() throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_ELASTICSEARCH, null, KEY_SERVER, "http://elastic");
    ElasticConfiguration esCfg = new ElasticConfiguration(cfg);
    assertHosts(esCfg, "http://elastic:9200");
  }

  @Test
  public void prefix() throws Exception {
    Config cfg = newConfig();
    cfg.setString(SECTION_ELASTICSEARCH, null, KEY_PREFIX, "myprefix");
    ElasticConfiguration esCfg = new ElasticConfiguration(cfg);
    assertThat(esCfg.prefix).isEqualTo("myprefix");
  }

  @Test
  public void withAuthentication() throws Exception {
    Config cfg = newConfig();
    cfg.setString(SECTION_ELASTICSEARCH, null, KEY_USERNAME, "myself");
    cfg.setString(SECTION_ELASTICSEARCH, null, KEY_PASSWORD, "s3kr3t");
    ElasticConfiguration esCfg = new ElasticConfiguration(cfg);
    assertThat(esCfg.username).isEqualTo("myself");
    assertThat(esCfg.password).isEqualTo("s3kr3t");
  }

  @Test
  public void withAuthenticationPasswordOnlyUsesDefaultUsername() throws Exception {
    Config cfg = newConfig();
    cfg.setString(SECTION_ELASTICSEARCH, null, KEY_PASSWORD, "s3kr3t");
    ElasticConfiguration esCfg = new ElasticConfiguration(cfg);
    assertThat(esCfg.username).isEqualTo(DEFAULT_USERNAME);
    assertThat(esCfg.password).isEqualTo("s3kr3t");
  }

  @Test
  public void multipleServers() throws Exception {
    Config cfg = new Config();
    cfg.setStringList(
        SECTION_ELASTICSEARCH,
        null,
        KEY_SERVER,
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
    cfg.setString(SECTION_ELASTICSEARCH, null, KEY_SERVER, "foo");
    assertProvisionException(cfg);
  }

  @Test
  public void multipleServersIncludingInvalid() throws Exception {
    Config cfg = new Config();
    cfg.setStringList(
        SECTION_ELASTICSEARCH, null, KEY_SERVER, ImmutableList.of("http://elastic1:1234", "foo"));
    ElasticConfiguration esCfg = new ElasticConfiguration(cfg);
    assertHosts(esCfg, "http://elastic1:1234");
  }

  private static Config newConfig() {
    Config config = new Config();
    config.setString(SECTION_ELASTICSEARCH, null, KEY_SERVER, "http://elastic:1234");
    return config;
  }

  private void assertHosts(ElasticConfiguration cfg, Object... hostURIs) throws Exception {
    assertThat(Arrays.asList(cfg.getHosts()).stream().map(HttpHost::toURI).collect(toList()))
        .containsExactly(hostURIs);
  }

  private void assertProvisionException(Config cfg) throws Exception {
    exception.expect(ProvisionException.class);
    exception.expectMessage("No valid Elasticsearch servers configured");
    new ElasticConfiguration(cfg);
  }
}
