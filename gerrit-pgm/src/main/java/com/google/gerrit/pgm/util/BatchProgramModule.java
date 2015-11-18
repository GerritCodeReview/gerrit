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
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.rules.PrologModule;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountByEmailCacheImpl;
import com.google.gerrit.server.account.AccountCacheImpl;
import com.google.gerrit.server.account.AccountInfoCacheFactory;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.account.FakeRealm;
import com.google.gerrit.server.account.GroupCacheImpl;
import com.google.gerrit.server.account.GroupDetailFactory;
import com.google.gerrit.server.account.GroupIncludeCacheImpl;
import com.google.gerrit.server.account.GroupInfoCacheFactory;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.avatar.AvatarProvider;
import com.google.gerrit.server.cache.CacheRemovalListener;
import com.google.gerrit.server.cache.h2.DefaultCacheFactory;
import com.google.gerrit.server.change.ChangeKindCacheImpl;
import com.google.gerrit.server.change.MergeabilityCacheImpl;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.CanonicalWebUrlProvider;
import com.google.gerrit.server.config.DisableReverseDnsLookup;
import com.google.gerrit.server.config.DisableReverseDnsLookupProvider;
import com.google.gerrit.server.config.FactoryModule;
import com.google.gerrit.server.config.GitReceivePackGroups;
import com.google.gerrit.server.config.GitUploadPackGroups;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.git.TagCache;
import com.google.gerrit.server.group.GroupModule;
import com.google.gerrit.server.mail.ReplacePatchSetSender;
import com.google.gerrit.server.notedb.NoteDbModule;
import com.google.gerrit.server.patch.DiffExecutorModule;
import com.google.gerrit.server.patch.PatchListCacheImpl;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.CommentLinkInfo;
import com.google.gerrit.server.project.CommentLinkProvider;
import com.google.gerrit.server.project.ProjectCacheImpl;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.SectionSortCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Providers;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Module for programs that perform batch operations on a site.
 * <p>
 * Any program that requires this module likely also requires using
 * {@link ThreadLimiter} to limit the number of threads accessing the database
 * concurrently.
 */
public class BatchProgramModule extends FactoryModule {
  private final Module reviewDbModule;

  @Inject
  BatchProgramModule(PerThreadReviewDbModule reviewDbModule) {
    this.reviewDbModule = reviewDbModule;
  }

  @SuppressWarnings("rawtypes")
  @Override
  protected void configure() {
    install(reviewDbModule);
    install(new DiffExecutorModule());
    install(PatchListCacheImpl.module());
    // Plugins are not loaded and we're just running through each change
    // once, so don't worry about cache removal.
    bind(new TypeLiteral<DynamicSet<CacheRemovalListener>>() {})
        .toInstance(DynamicSet.<CacheRemovalListener> emptySet());
    bind(new TypeLiteral<DynamicMap<Cache<?, ?>>>() {})
        .toInstance(DynamicMap.<Cache<?, ?>> emptyMap());
    bind(new TypeLiteral<List<CommentLinkInfo>>() {})
        .toProvider(CommentLinkProvider.class).in(SINGLETON);
    bind(String.class).annotatedWith(CanonicalWebUrl.class)
        .toProvider(CanonicalWebUrlProvider.class);
    bind(Boolean.class).annotatedWith(DisableReverseDnsLookup.class)
        .toProvider(DisableReverseDnsLookupProvider.class).in(SINGLETON);
    bind(Realm.class).to(FakeRealm.class);
    bind(IdentifiedUser.class)
      .toProvider(Providers.<IdentifiedUser> of(null));
    bind(ReplacePatchSetSender.Factory.class).toProvider(
        Providers.<ReplacePatchSetSender.Factory>of(null));
    bind(CurrentUser.class).to(IdentifiedUser.class);
    factory(MergeUtil.Factory.class);
    factory(PatchSetInserter.Factory.class);

    bind(new TypeLiteral<Set<AccountGroup.UUID>>() {})
      .annotatedWith(GitUploadPackGroups.class)
      .toInstance(Collections.<AccountGroup.UUID> emptySet());
    bind(new TypeLiteral<Set<AccountGroup.UUID>>() {})
      .annotatedWith(GitReceivePackGroups.class)
      .toInstance(Collections.<AccountGroup.UUID> emptySet());
    factory(ChangeControl.AssistedFactory.class);
    factory(AccountLoader.Factory.class);
    factory(GroupDetailFactory.Factory.class);
    factory(AccountInfoCacheFactory.Factory.class);
    factory(GroupInfoCacheFactory.Factory.class);
    factory(ProjectControl.AssistedFactory.class);

    install(new BatchGitModule());
    install(new DefaultCacheFactory.Module());
    install(new GroupModule());
    install(new NoteDbModule());
    install(new PrologModule());
    install(AccountByEmailCacheImpl.module());
    install(AccountCacheImpl.module());
    install(GroupCacheImpl.module());
    install(GroupIncludeCacheImpl.module());
    install(ProjectCacheImpl.module());
    install(SectionSortCache.module());
    install(ChangeKindCacheImpl.module());
    install(MergeabilityCacheImpl.module());
    install(TagCache.module());
    factory(CapabilityControl.Factory.class);
    factory(ChangeData.Factory.class);
    factory(ProjectState.Factory.class);

    DynamicItem.itemOf(binder(), AvatarProvider.class);
  }
}
