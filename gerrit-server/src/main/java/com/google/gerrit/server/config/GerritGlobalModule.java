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

import com.google.common.cache.Cache;
import com.google.gerrit.audit.AuditModule;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.events.NewProjectCreatedListener;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gerrit.rules.PrologModule;
import com.google.gerrit.rules.RulesCache;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.FileTypeRegistry;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.MimeUtilFileTypeRegistry;
import com.google.gerrit.server.account.AccountByEmailCacheImpl;
import com.google.gerrit.server.account.AccountCacheImpl;
import com.google.gerrit.server.account.AccountInfoCacheFactory;
import com.google.gerrit.server.account.AccountVisibility;
import com.google.gerrit.server.account.AccountVisibilityProvider;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.account.DefaultRealm;
import com.google.gerrit.server.account.EmailExpander;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupCacheImpl;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.account.GroupIncludeCacheImpl;
import com.google.gerrit.server.account.GroupInfoCacheFactory;
import com.google.gerrit.server.account.IncludingGroupMembership;
import com.google.gerrit.server.account.InternalGroupBackend;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.account.UniversalGroupBackend;
import com.google.gerrit.server.auth.ldap.LdapModule;
import com.google.gerrit.server.cache.CacheRemovalListener;
import com.google.gerrit.server.events.EventFactory;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.ChangeCache;
import com.google.gerrit.server.git.ChangeMergeQueue;
import com.google.gerrit.server.git.GitModule;
import com.google.gerrit.server.git.MergeQueue;
import com.google.gerrit.server.git.ReloadSubmitQueueOp;
import com.google.gerrit.server.git.TagCache;
import com.google.gerrit.server.git.TransferConfig;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.mail.FromAddressGenerator;
import com.google.gerrit.server.mail.FromAddressGeneratorProvider;
import com.google.gerrit.server.mail.VelocityRuntimeProvider;
import com.google.gerrit.server.patch.PatchListCacheImpl;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.AccessControlModule;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.PermissionCollection;
import com.google.gerrit.server.project.ProjectCacheImpl;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectNode;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.SectionSortCache;
import com.google.gerrit.server.tools.ToolsCatalog;
import com.google.gerrit.server.util.IdGenerator;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gerrit.server.workflow.FunctionState;
import com.google.inject.Inject;
import com.google.inject.TypeLiteral;

import org.apache.velocity.runtime.RuntimeInstance;
import org.eclipse.jgit.lib.Config;


/** Starts global state with standard dependencies. */
public class GerritGlobalModule extends FactoryModule {
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
      case LDAP_BIND:
      case CLIENT_SSL_CERT_LDAP:
        install(new LdapModule());
        break;

      case CUSTOM_EXTENSION:
        break;

      default:
        bind(Realm.class).to(DefaultRealm.class);
        break;
    }

    bind(ApprovalTypes.class).toProvider(ApprovalTypesProvider.class).in(
        SINGLETON);
    bind(EmailExpander.class).toProvider(EmailExpanderProvider.class).in(
        SINGLETON);

    bind(IdGenerator.class);
    bind(RulesCache.class);
    install(AccountByEmailCacheImpl.module());
    install(AccountCacheImpl.module());
    install(GroupCacheImpl.module());
    install(GroupIncludeCacheImpl.module());
    install(PatchListCacheImpl.module());
    install(ProjectCacheImpl.module());
    install(SectionSortCache.module());
    install(TagCache.module());
    install(ChangeCache.module());

    install(new AccessControlModule());
    install(new GitModule());
    install(new PrologModule());
    install(ThreadLocalRequestContext.module());

    factory(AccountInfoCacheFactory.Factory.class);
    factory(CapabilityControl.Factory.class);
    factory(GroupInfoCacheFactory.Factory.class);
    factory(InternalUser.Factory.class);
    factory(ProjectNode.Factory.class);
    factory(ProjectState.Factory.class);
    bind(PermissionCollection.Factory.class);
    bind(AccountVisibility.class)
        .toProvider(AccountVisibilityProvider.class)
        .in(SINGLETON);

    bind(GroupControl.Factory.class).in(SINGLETON);
    factory(IncludingGroupMembership.Factory.class);
    bind(InternalGroupBackend.class).in(SINGLETON);
    bind(GroupBackend.class).to(UniversalGroupBackend.class).in(SINGLETON);
    DynamicSet.setOf(binder(), GroupBackend.class);
    DynamicSet.bind(binder(), GroupBackend.class).to(InternalGroupBackend.class);

    bind(FileTypeRegistry.class).to(MimeUtilFileTypeRegistry.class);
    bind(ToolsCatalog.class);
    bind(EventFactory.class);
    bind(TransferConfig.class);

    bind(ChangeMergeQueue.class).in(SINGLETON);
    bind(MergeQueue.class).to(ChangeMergeQueue.class).in(SINGLETON);
    factory(ReloadSubmitQueueOp.Factory.class);

    bind(RuntimeInstance.class)
        .toProvider(VelocityRuntimeProvider.class)
        .in(SINGLETON);
    bind(FromAddressGenerator.class).toProvider(
        FromAddressGeneratorProvider.class).in(SINGLETON);

    bind(PatchSetInfoFactory.class);
    bind(IdentifiedUser.GenericFactory.class).in(SINGLETON);
    bind(ChangeControl.GenericFactory.class);
    bind(ProjectControl.GenericFactory.class);
    factory(FunctionState.Factory.class);

    install(new AuditModule());

    bind(GitReferenceUpdated.class);
    DynamicMap.mapOf(binder(), new TypeLiteral<Cache<?, ?>>() {});
    DynamicSet.setOf(binder(), CacheRemovalListener.class);
    DynamicSet.setOf(binder(), GitReferenceUpdatedListener.class);
    DynamicSet.setOf(binder(), NewProjectCreatedListener.class);
    DynamicSet.bind(binder(), GitReferenceUpdatedListener.class).to(ChangeCache.class);
    DynamicSet.setOf(binder(), CommitValidationListener.class);

    bind(AnonymousUser.class);
  }
}
