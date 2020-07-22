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

package com.google.gerrit.pgm.util;

import static com.google.inject.Scopes.SINGLETON;

import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.extensions.api.projects.CommentLinkInfo;
import com.google.gerrit.extensions.common.AccountVisibility;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCacheImpl;
import com.google.gerrit.server.account.AccountVisibilityProvider;
import com.google.gerrit.server.account.CapabilityCollection;
import com.google.gerrit.server.account.FakeRealm;
import com.google.gerrit.server.account.GroupCacheImpl;
import com.google.gerrit.server.account.GroupIncludeCacheImpl;
import com.google.gerrit.server.account.NonInteractiveUserGroupRobotClassifier;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.account.externalids.ExternalIdModule;
import com.google.gerrit.server.cache.CacheRemovalListener;
import com.google.gerrit.server.cache.h2.H2CacheModule;
import com.google.gerrit.server.cache.mem.DefaultMemoryCacheModule;
import com.google.gerrit.server.change.ChangeAttributeFactory;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeKindCacheImpl;
import com.google.gerrit.server.change.MergeabilityCacheImpl;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.change.RebaseChangeOp;
import com.google.gerrit.server.config.AdministrateServerGroups;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.CanonicalWebUrlProvider;
import com.google.gerrit.server.config.DefaultPreferencesCacheImpl;
import com.google.gerrit.server.config.DefaultUrlFormatter;
import com.google.gerrit.server.config.EnableReverseDnsLookup;
import com.google.gerrit.server.config.EnableReverseDnsLookupProvider;
import com.google.gerrit.server.config.GitReceivePackGroups;
import com.google.gerrit.server.config.GitUploadPackGroups;
import com.google.gerrit.server.config.SysExecutorModule;
import com.google.gerrit.server.extensions.events.EventUtil;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.extensions.events.RevisionCreated;
import com.google.gerrit.server.extensions.events.WorkInProgressStateChanged;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.git.PureRevertCache;
import com.google.gerrit.server.git.SearchingChangeCacheImpl;
import com.google.gerrit.server.git.TagCache;
import com.google.gerrit.server.mail.send.ReplacePatchSetSender;
import com.google.gerrit.server.notedb.NoteDbModule;
import com.google.gerrit.server.patch.DiffExecutorModule;
import com.google.gerrit.server.patch.PatchListCacheImpl;
import com.google.gerrit.server.permissions.DefaultPermissionBackendModule;
import com.google.gerrit.server.permissions.SectionSortCache;
import com.google.gerrit.server.plugins.PluginModule;
import com.google.gerrit.server.project.CommentLinkProvider;
import com.google.gerrit.server.project.CommitResource;
import com.google.gerrit.server.project.ProjectCacheImpl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeIsVisibleToPredicate;
import com.google.gerrit.server.restapi.group.GroupModule;
import com.google.gerrit.server.rules.DefaultSubmitRule;
import com.google.gerrit.server.rules.IgnoreSelfApprovalRule;
import com.google.gerrit.server.rules.PrologModule;
import com.google.gerrit.server.rules.SubmitRule;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Providers;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Module for programs that perform batch operations on a site. */
public class BatchProgramModule extends FactoryModule {
  @SuppressWarnings("rawtypes")
  @Override
  protected void configure() {
    install(new DiffExecutorModule());
    install(new SysExecutorModule());
    install(BatchUpdate.module());
    install(PatchListCacheImpl.module());
    install(new DefaultUrlFormatter.Module());

    // There is the concept of LifecycleModule, in Gerrit's own extension to Guice, which has these:
    //  listener().to(SomeClassImplementingLifecycleListener.class);
    // and the start() methods of each such listener are executed in the order they are declared.
    // Makes sure that PluginLoader.start() is executed before the LuceneIndexModule.start() so that
    // plugins get loaded and the respective Guice modules installed so that the on-line reindexing
    // will happen with the proper classes (e.g. group backends, custom Prolog predicates) and the
    // associated rules ready to be evaluated.
    install(new PluginModule());

    // We're just running through each change
    // once, so don't worry about cache removal.
    bind(new TypeLiteral<DynamicSet<CacheRemovalListener>>() {}).toInstance(DynamicSet.emptySet());
    bind(new TypeLiteral<DynamicMap<Cache<?, ?>>>() {}).toInstance(DynamicMap.emptyMap());
    bind(new TypeLiteral<List<CommentLinkInfo>>() {})
        .toProvider(CommentLinkProvider.class)
        .in(SINGLETON);
    bind(new TypeLiteral<DynamicSet<ChangeAttributeFactory>>() {})
        .toInstance(DynamicSet.emptySet());
    bind(new TypeLiteral<DynamicMap<RestView<CommitResource>>>() {})
        .toInstance(DynamicMap.emptyMap());
    bind(String.class)
        .annotatedWith(CanonicalWebUrl.class)
        .toProvider(CanonicalWebUrlProvider.class);
    bind(Boolean.class)
        .annotatedWith(EnableReverseDnsLookup.class)
        .toProvider(EnableReverseDnsLookupProvider.class)
        .in(SINGLETON);
    bind(Realm.class).to(FakeRealm.class);
    bind(IdentifiedUser.class).toProvider(Providers.of(null));
    bind(ReplacePatchSetSender.Factory.class).toProvider(Providers.of(null));
    bind(CurrentUser.class).to(IdentifiedUser.class);
    factory(MergeUtil.Factory.class);
    factory(PatchSetInserter.Factory.class);
    factory(RebaseChangeOp.Factory.class);

    // As Reindex is a batch program, don't assume the index is available for
    // the change cache.
    bind(SearchingChangeCacheImpl.class).toProvider(Providers.of(null));

    bind(new TypeLiteral<ImmutableSet<GroupReference>>() {})
        .annotatedWith(AdministrateServerGroups.class)
        .toInstance(ImmutableSet.of());
    bind(new TypeLiteral<Set<AccountGroup.UUID>>() {})
        .annotatedWith(GitUploadPackGroups.class)
        .toInstance(Collections.emptySet());
    bind(new TypeLiteral<Set<AccountGroup.UUID>>() {})
        .annotatedWith(GitReceivePackGroups.class)
        .toInstance(Collections.emptySet());

    install(new BatchGitModule());
    install(new DefaultPermissionBackendModule());
    install(new DefaultMemoryCacheModule());
    install(new H2CacheModule());
    install(new ExternalIdModule());
    install(new GroupModule());
    install(new NoteDbModule());
    install(AccountCacheImpl.module());
    install(DefaultPreferencesCacheImpl.module());
    install(GroupCacheImpl.module());
    install(GroupIncludeCacheImpl.module());
    install(ProjectCacheImpl.module());
    install(SectionSortCache.module());
    install(ChangeKindCacheImpl.module());
    install(MergeabilityCacheImpl.module());
    install(NonInteractiveUserGroupRobotClassifier.module());
    install(TagCache.module());
    install(PureRevertCache.module());
    factory(CapabilityCollection.Factory.class);
    factory(ChangeData.AssistedFactory.class);
    factory(ChangeIsVisibleToPredicate.Factory.class);
    factory(ProjectState.Factory.class);

    // Submit rules
    DynamicSet.setOf(binder(), SubmitRule.class);
    factory(SubmitRuleEvaluator.Factory.class);
    install(new PrologModule());
    install(new DefaultSubmitRule.Module());
    install(new IgnoreSelfApprovalRule.Module());

    bind(ChangeJson.Factory.class).toProvider(Providers.of(null));
    bind(EventUtil.class).toProvider(Providers.of(null));
    bind(GitReferenceUpdated.class).toInstance(GitReferenceUpdated.DISABLED);
    bind(RevisionCreated.class).toInstance(RevisionCreated.DISABLED);
    bind(WorkInProgressStateChanged.class).toInstance(WorkInProgressStateChanged.DISABLED);
    bind(AccountVisibility.class).toProvider(AccountVisibilityProvider.class).in(SINGLETON);
  }
}
