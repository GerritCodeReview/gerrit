// Copyright (C) 2013 The Android Open Source Project
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
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.solr;

import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.IndexModule;
import com.google.inject.name.Names;

public class SolrIndexModule extends LifecycleModule {
  private final boolean checkVersion;
  private final int threads;
  private final String url;

  public SolrIndexModule(String url) {
    this(url, true, 0);
  }

  public SolrIndexModule(String url, boolean checkVersion, int threads) {
    this.checkVersion = checkVersion;
    this.threads = threads;
    this.url = url;
  }

  @Override
  protected void configure() {
    install(new IndexModule(threads));
    bind(String.class).annotatedWith(Names.named("url")).toInstance(url);
    bind(ChangeIndex.class).to(SolrChangeIndex.class);
    listener().to(SolrChangeIndex.class);
    if (checkVersion) {
      listener().to(SolrIndexVersionCheck.class);
    }
  }
}
