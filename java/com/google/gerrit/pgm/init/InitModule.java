// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.pgm.init;

import static com.google.gerrit.server.Sequence.LightweightAccounts;
import static com.google.inject.Scopes.SINGLETON;

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.pgm.init.api.Section;
import com.google.gerrit.pgm.init.api.SequencesOnInit.DisabledGitRefUpdatedRepoAccountsSequenceProvider;
import com.google.gerrit.server.Sequence;
import com.google.gerrit.server.account.externalids.ExternalIdFactory;
import com.google.gerrit.server.account.externalids.storage.notedb.ExternalIdFactoryNoteDbImpl;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Singleton;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.internal.UniqueAnnotations;
import java.lang.annotation.Annotation;

/** Injection configuration for the site initialization process. */
public class InitModule extends FactoryModule {

  private final boolean standalone;

  public InitModule(boolean standalone) {
    this.standalone = standalone;
  }

  @Override
  protected void configure() {
    bind(SitePaths.class);
    bind(AllUsersName.class).toProvider(AllUsersNameProvider.class).in(SINGLETON);
    bind(Sequence.class)
        .annotatedWith(LightweightAccounts.class)
        .toProvider(DisabledGitRefUpdatedRepoAccountsSequenceProvider.class);
    factory(Section.Factory.class);
    factory(VersionedAuthorizedKeysOnInit.Factory.class);
    factory(VersionedAuthTokensOnInit.Factory.class);

    // Steps are executed in the order listed here.
    //
    step().to(InitGitManager.class);
    step().to(InitJGitConfig.class);
    step().to(InitLogging.class);
    step().to(InitIndex.class);
    step().to(InitAuth.class);
    step().to(InitAdminUser.class);
    step().to(InitLabels.class);
    step().to(InitSendEmail.class);
    if (standalone) {
      step().to(InitContainer.class);
    }
    step().to(InitSshd.class);
    step().to(InitHttpd.class);
    step().to(InitCache.class);
    step().to(InitPlugins.class);
    step().to(InitDev.class);

    bind(AccountsOnInit.class).to(AccountsOnInitNoteDbImpl.class);
    bind(ExternalIdFactory.class).to(ExternalIdFactoryNoteDbImpl.class).in(Singleton.class);
  }

  protected LinkedBindingBuilder<InitStep> step() {
    final Annotation id = UniqueAnnotations.create();
    return bind(InitStep.class).annotatedWith(id);
  }
}
