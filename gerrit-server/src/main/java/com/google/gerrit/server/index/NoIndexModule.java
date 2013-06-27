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
// limitations under the License.

package com.google.gerrit.server.index;

import com.google.gerrit.server.query.change.ChangeQueryRewriter;
import com.google.gerrit.server.query.change.SqlRewriterImpl;
import com.google.inject.AbstractModule;

public class NoIndexModule extends AbstractModule {
  // TODO(dborowitz): This module should go away when the index becomes
  // obligatory, as should the interfaces that exist only to support the
  // non-index case.

  @Override
  protected void configure() {
    bind(ChangeIndex.class).toInstance(ChangeIndex.DISABLED);
    bind(ChangeIndexer.class).toInstance(ChangeIndexer.DISABLED);
    bind(ChangeQueryRewriter.class).to(SqlRewriterImpl.class);
  }
}
