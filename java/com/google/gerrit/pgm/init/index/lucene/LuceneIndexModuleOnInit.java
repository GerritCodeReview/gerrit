// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.pgm.init.index.lucene;

import static com.google.gerrit.lucene.AbstractLuceneIndex.IS_AUTO_FLUSH_DISABLED;

import com.google.gerrit.lucene.LuceneAccountIndex;
import com.google.gerrit.lucene.LuceneGroupIndex;
import com.google.gerrit.pgm.init.index.IndexModuleOnInit;
import com.google.gerrit.server.index.account.AccountIndex;
import com.google.gerrit.server.index.group.GroupIndex;
import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.name.Names;

public class LuceneIndexModuleOnInit extends AbstractModule {
  @Override
  protected void configure() {
    install(
        new FactoryModuleBuilder()
            .implement(AccountIndex.class, LuceneAccountIndex.class)
            .build(AccountIndex.Factory.class));

    install(
        new FactoryModuleBuilder()
            .implement(GroupIndex.class, LuceneGroupIndex.class)
            .build(GroupIndex.Factory.class));

    install(new IndexModuleOnInit());

    bind(Boolean.class).annotatedWith(Names.named(IS_AUTO_FLUSH_DISABLED)).toInstance(true);
  }
}
