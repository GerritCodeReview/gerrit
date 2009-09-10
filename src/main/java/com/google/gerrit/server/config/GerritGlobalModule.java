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

import com.google.gerrit.client.data.ApprovalTypes;
import com.google.gerrit.client.reviewdb.AuthType;
import com.google.gerrit.git.ChangeMergeQueue;
import com.google.gerrit.git.MergeOp;
import com.google.gerrit.git.MergeQueue;
import com.google.gerrit.git.PatchSetImporter;
import com.google.gerrit.git.PushAllProjectsOp;
import com.google.gerrit.git.PushReplication;
import com.google.gerrit.git.ReloadSubmitQueueOp;
import com.google.gerrit.git.ReplicationQueue;
import com.google.gerrit.git.WorkQueue;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.FileTypeRegistry;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.GerritPersonIdentProvider;
import com.google.gerrit.server.GerritServer;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.MimeUtilFileTypeRegistry;
import com.google.gerrit.server.account.AccountByEmailCache;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountInfoCacheFactory;
import com.google.gerrit.server.account.DefaultRealm;
import com.google.gerrit.server.account.EmailExpander;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.cache.CachePool;
import com.google.gerrit.server.ldap.LdapModule;
import com.google.gerrit.server.mail.AbandonedSender;
import com.google.gerrit.server.mail.AddReviewerSender;
import com.google.gerrit.server.mail.CommentSender;
import com.google.gerrit.server.mail.CreateChangeSender;
import com.google.gerrit.server.mail.EmailSender;
import com.google.gerrit.server.mail.MergeFailSender;
import com.google.gerrit.server.mail.MergedSender;
import com.google.gerrit.server.mail.RegisterNewEmailSender;
import com.google.gerrit.server.mail.ReplacePatchSetSender;
import com.google.gerrit.server.mail.SmtpEmailSender;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.gerrit.server.workflow.FunctionState;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;

import org.spearce.jgit.lib.Config;
import org.spearce.jgit.lib.PersonIdent;

import java.util.ArrayList;
import java.util.List;

/** Starts global state with standard dependencies. */
public class GerritGlobalModule extends FactoryModule {
  public static Injector createInjector() {
    return createInjector(Guice
        .createInjector(PRODUCTION, new DatabaseModule()));
  }

  public static Injector createInjector(final Injector db) {
    final Injector cfg = db.createChildInjector(new GerritConfigModule());
    final List<Module> modules = new ArrayList<Module>();
    modules.add(cfg.getInstance(GerritGlobalModule.class));
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

    // Note that the CanonicalWebUrl itself must not be a singleton, but its
    // provider must be.
    //
    // If the value was not configured in the system configuration data the
    // provider may try to guess it from the current HTTP request, if we are
    // running in an HTTP environment.
    //
    bind(CanonicalWebUrlProvider.class).in(SINGLETON);
    bind(String.class).annotatedWith(CanonicalWebUrl.class).toProvider(
        CanonicalWebUrlProvider.class);

    bind(PersonIdent.class).annotatedWith(GerritPersonIdent.class).toProvider(
        GerritPersonIdentProvider.class);

    bind(CachePool.class);
    install(AccountByEmailCache.module());
    install(AccountCache.module());
    install(GroupCache.module());
    install(PatchListCache.module());
    install(ProjectCache.module());
    install(SshKeyCache.module());

    factory(AccountInfoCacheFactory.Factory.class);

    bind(GerritServer.class);
    bind(FileTypeRegistry.class).to(MimeUtilFileTypeRegistry.class);
    bind(WorkQueue.class);

    bind(ReplicationQueue.class).to(PushReplication.class).in(SINGLETON);
    factory(PushAllProjectsOp.Factory.class);

    bind(MergeQueue.class).to(ChangeMergeQueue.class).in(SINGLETON);
    factory(MergeOp.Factory.class);
    factory(ReloadSubmitQueueOp.Factory.class);

    bind(EmailSender.class).to(SmtpEmailSender.class).in(SINGLETON);
    factory(PatchSetImporter.Factory.class);
    bind(PatchSetInfoFactory.class);
    bind(IdentifiedUser.GenericFactory.class).in(SINGLETON);
    factory(FunctionState.Factory.class);

    factory(AbandonedSender.Factory.class);
    factory(AddReviewerSender.Factory.class);
    factory(CreateChangeSender.Factory.class);
    factory(CommentSender.Factory.class);
    factory(MergedSender.Factory.class);
    factory(MergeFailSender.Factory.class);
    factory(ReplacePatchSetSender.Factory.class);
    factory(RegisterNewEmailSender.Factory.class);
  }
}
