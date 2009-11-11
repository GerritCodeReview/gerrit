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

package com.google.gerrit.server.config;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.Stage.PRODUCTION;

import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.AuthType;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.FileTypeRegistry;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.GerritPersonIdentProvider;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.MimeUtilFileTypeRegistry;
import com.google.gerrit.server.ReplicationUser;
import com.google.gerrit.server.account.AccountByEmailCacheImpl;
import com.google.gerrit.server.account.AccountCacheImpl;
import com.google.gerrit.server.account.AccountInfoCacheFactory;
import com.google.gerrit.server.account.DefaultRealm;
import com.google.gerrit.server.account.EmailExpander;
import com.google.gerrit.server.account.GroupCacheImpl;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.auth.ldap.LdapModule;
import com.google.gerrit.server.cache.CachePool;
import com.google.gerrit.server.git.ChangeMergeQueue;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeOp;
import com.google.gerrit.server.git.MergeQueue;
import com.google.gerrit.server.git.PatchSetImporter;
import com.google.gerrit.server.git.PushAllProjectsOp;
import com.google.gerrit.server.git.PushReplication;
import com.google.gerrit.server.git.ReloadSubmitQueueOp;
import com.google.gerrit.server.git.ReplicationQueue;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.mail.AbandonedSender;
import com.google.gerrit.server.mail.CommentSender;
import com.google.gerrit.server.mail.EmailSender;
import com.google.gerrit.server.mail.FromAddressGenerator;
import com.google.gerrit.server.mail.FromAddressGeneratorProvider;
import com.google.gerrit.server.mail.MergeFailSender;
import com.google.gerrit.server.mail.MergedSender;
import com.google.gerrit.server.mail.RegisterNewEmailSender;
import com.google.gerrit.server.mail.SmtpEmailSender;
import com.google.gerrit.server.patch.PatchListCacheImpl;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.ProjectCacheImpl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.workflow.FunctionState;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;

import java.util.ArrayList;
import java.util.List;

/** Starts global state with standard dependencies. */
public class GerritGlobalModule extends FactoryModule {
  public static Injector createInjector() {
    final Injector db = Guice.createInjector(PRODUCTION, new DatabaseModule());
    final CanonicalWebUrlModule canonicalWebUrl = new CanonicalWebUrlModule() {
      @Override
      protected Class<? extends Provider<String>> provider() {
        return CanonicalWebUrlProvider.class;
      }
    };
    return createInjector(db, canonicalWebUrl);
  }

  public static Injector createInjector(final Injector db,
      final CanonicalWebUrlModule canonicalWebUrl) {
    final Injector cfg = db.createChildInjector(new GerritConfigModule());
    final List<Module> modules = new ArrayList<Module>();
    modules.add(cfg.getInstance(GerritGlobalModule.class));
    modules.add(canonicalWebUrl);
    return cfg.createChildInjector(modules);
  }

  private final AuthType loginType;

  @Inject
  GerritGlobalModule(final AuthConfig authConfig,
      @GerritServerConfig final Config config) {
    loginType = authConfig.getAuthType();
  }

  @Override
  protected void configure() {
    switch (loginType) {
      case HTTP_LDAP:
      case LDAP:
        install(new LdapModule());
        break;

      default:
        bind(Realm.class).to(DefaultRealm.class);
        break;
    }

    bind(ApprovalTypes.class).toProvider(ApprovalTypesProvider.class).in(
        SINGLETON);
    bind(EmailExpander.class).toProvider(EmailExpanderProvider.class).in(
        SINGLETON);
    bind(AnonymousUser.class);

    bind(PersonIdent.class).annotatedWith(GerritPersonIdent.class).toProvider(
        GerritPersonIdentProvider.class);

    bind(CachePool.class);
    install(AccountByEmailCacheImpl.module());
    install(AccountCacheImpl.module());
    install(GroupCacheImpl.module());
    install(PatchListCacheImpl.module());
    install(ProjectCacheImpl.module());

    factory(AccountInfoCacheFactory.Factory.class);
    factory(ProjectState.Factory.class);

    bind(GitRepositoryManager.class);
    bind(FileTypeRegistry.class).to(MimeUtilFileTypeRegistry.class);
    bind(WorkQueue.class);

    bind(ReplicationQueue.class).to(PushReplication.class).in(SINGLETON);
    factory(PushAllProjectsOp.Factory.class);

    bind(MergeQueue.class).to(ChangeMergeQueue.class).in(SINGLETON);
    factory(MergeOp.Factory.class);
    factory(ReloadSubmitQueueOp.Factory.class);

    bind(FromAddressGenerator.class).toProvider(
        FromAddressGeneratorProvider.class).in(SINGLETON);
    bind(EmailSender.class).to(SmtpEmailSender.class).in(SINGLETON);

    factory(PatchSetImporter.Factory.class);
    bind(PatchSetInfoFactory.class);
    bind(IdentifiedUser.GenericFactory.class).in(SINGLETON);
    factory(FunctionState.Factory.class);

    factory(AbandonedSender.Factory.class);
    factory(CommentSender.Factory.class);
    factory(MergedSender.Factory.class);
    factory(MergeFailSender.Factory.class);
    factory(RegisterNewEmailSender.Factory.class);
    factory(ReplicationUser.Factory.class);

    install(new LifecycleModule() {
      @Override
      protected void configure() {
        listener().to(GitRepositoryManager.Lifecycle.class);
        listener().to(CachePool.Lifecycle.class);
        listener().to(WorkQueue.Lifecycle.class);
      }
    });
  }
}
