// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.server.index.IndexModule.IndexType;
import com.google.gerrit.server.query.change.AbstractQueryChangesTest;
import com.google.gerrit.testutil.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.eclipse.jgit.lib.Config;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.junit.After;
import org.junit.Before;

public class ElasticsearchQueryChangesTest extends AbstractQueryChangesTest {
  private Node server;

  @Before
  public void startElasticsearchServer() {
    server = NodeBuilder.nodeBuilder().local(true).node();
  }

  @After
  public void stopElasticsearchServer() {
    if (server != null) {
      server.close();
    }
  }

  @Override
  protected Injector createInjector() {
    Config elasticsearchConfig = new Config(config);
    config.setEnum("index", null, "type", IndexType.ELASTICSEARCH);
    config.setString("index", null, "url", "http://localhost:9200");
    config.setString("index", null, "name", "gerrit");
    InMemoryModule.setDefaults(elasticsearchConfig);
    return Guice.createInjector(new InMemoryModule(elasticsearchConfig));
  }
}
