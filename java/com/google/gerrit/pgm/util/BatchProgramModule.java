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
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.extensions.api.projects.CommentLinkInfo;
import com.google.gerrit.extensions.common.AccountVisibility;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.DefaultRefLogIdentityProvider;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.LibModuleLoader;
import com.google.gerrit.server.LibModuleType;
import com.google.gerrit.server.ModuleOverloader;
import com.google.gerrit.server.account.AccountCacheImpl;
import com.google.gerrit.server.account.AccountVisibilityProvider;
import com.google.gerrit.server.account.CapabilityCollection;
import com.google.gerrit.server.account.FakeRealm;
import com.google.gerrit.server.account.GroupCacheImpl;
import com.google.gerrit.server.account.GroupIncludeCacheImpl;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.account.ServiceUserClassifierImpl;
import com.google.gerrit.server.cache.CacheRemovalListener;
import com.google.gerrit.server.cache.h2.CacheOptions;
import com.google.gerrit.server.cache.h2.H2CacheModule;
import com.google.gerrit.server.cache.mem.DefaultMemoryCacheModule;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeKindCacheImpl;
import com.google.gerrit.server.change.EmailNewPatchSet;
import com.google.gerrit.server.change.MergeabilityCacheImpl;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.change.RebaseChangeOp;
import com.google.gerrit.server.config.AdministrateServerGroups;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.CanonicalWebUrlProvider;
import com.google.gerrit.server.config.DefaultPreferencesCacheImpl;
import com.google.gerrit.server.config.DefaultUrlFormatter.DefaultUrlFormatterModule;
import com.google.gerrit.server.config.EnablePeerIPInReflogRecord;
import com.google.gerrit.server.config.EnablePeerIPInReflogRecordProvider;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.GitReceivePackGroups;
import com.google.gerrit.server.config.GitUploadPackGroups;
import com.google.gerrit.server.config.SkipCurrentRulesEvaluationOnClosedChangesModule;
import com.google.gerrit.server.config.SysExecutorModule;
import com.google.gerrit.server.extensions.events.AttentionSetObserver;
import com.google.gerrit.server.extensions.events.EventUtil;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.extensions.events.RevisionCreated;
import com.google.gerrit.server.extensions.events.WorkInProgressStateChanged;
import com.google.gerrit.server.git.ChangesByProjectCache;
import com.google.gerrit.server.git.PureRevertCache;
import com.google.gerrit.server.git.TagCache;
import com.google.gerrit.server.notedb.NoteDbModule;
import com.google.gerrit.server.patch.DiffExecutorModule;
import com.google.gerrit.server.patch.DiffOperationsForCommitValidation;
import com.google.gerrit.server.patch.DiffOperationsImpl;
import com.google.gerrit.server.patch.PatchListCacheImpl;
import com.google.gerrit.server.permissions.DefaultPermissionBackendModule;
import com.google.gerrit.server.permissions.SectionSortCache;
import com.google.gerrit.server.plugins.PluginModule;
import com.google.gerrit.server.project.CommentLinkProvider;
import com.google.gerrit.server.project.CommitResource;
import com.google.gerrit.server.project.ProjectCacheImpl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.SubmitRequirementsEvaluatorImpl;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.query.approval.ApprovalModule;
import com.google.gerrit.server.query.approval.ApprovalQueryBuilder;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeIsVisibleToPredicate;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ConflictsCacheImpl;
import com.google.gerrit.server.restapi.group.GroupModule;
import com.google.gerrit.server.rules.DefaultSubmitRule.DefaultSubmitRuleModule;
import com.google.gerrit.server.rules.IgnoreSelfApprovalRule.IgnoreSelfApprovalRuleModule;
import com.google.gerrit.server.rules.SubmitRule;
import com.google.gerrit.server.rules.prolog.PrologModule;
import com.google.gerrit.server.submitrequirement.predicate.DistinctVotersPredicate;
import com.google.gerrit.server.submitrequirement.predicate.FileEditsPredicate;
import com.google.gerrit.server.submitrequirement.predicate.HasSubmoduleUpdatePredicate;
import com.google.gerrit.server.submitrequirement.predicate.SubmitRequirementLabelExtensionPredicate;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Providers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.lib.Config;

/** Module for programs that perform batch operations on a site. */
public class BatchProgramModule extends FactoryModule {
  private final Injector parentInjector;
  private final ImmutableSet<CacheOptions> cacheOptions;

  public BatchProgramModule(Injector parentInjector, ImmutableSet<CacheOptions> cacheOptions) {
    this.parentInjector = parentInjector;
    this.cacheOptions = cacheOptions;
  }

  @SuppressWarnings("rawtypes")
  @Override
  protected void configure() {
    List<Module> modules = new ArrayList<>();

    modules.add(new DiffExecutorModule());
    modules.add(new SysExecutorModule());
    modules.add(BatchUpdate.module());
    modules.add(PatchListCacheImpl.module());
    modules.add(new DefaultUrlFormatterModule());
    modules.add(DiffOperationsImpl.module());
    modules.add(new DefaultRefLogIdentityProvider.Module());

    // There is the concept of LifecycleModule, in Gerrit's own extension to Guice, which has these:
    //  listener().to(SomeClassImplementingLifecycleListener.class);
    // and the start() methods of each such listener are executed in the order they are declared.
    // Makes sure that PluginLoader.start() is executed before the LuceneIndexModule.start() so that
    // plugins get loaded and the respective Guice modules installed so that the on-line reindexing
    // will happen with the proper classes (e.g. group backends, custom Prolog predicates) and the
    // associated rules ready to be evaluated.
    modules.add(new PluginModule());

    // We're just running through each change
    // once, so don't worry about cache removal.
    bind(new TypeLiteral<DynamicSet<CacheRemovalListener>>() {}).toInstance(DynamicSet.emptySet());
    DynamicMap.mapOf(binder(), new TypeLiteral<Cache<?, ?>>() {});
    bind(new TypeLiteral<List<CommentLinkInfo>>() {})
        .toProvider(CommentLinkProvider.class)
        .in(SINGLETON);
    bind(new TypeLiteral<DynamicMap<RestView<CommitResource>>>() {})
        .toInstance(DynamicMap.emptyMap());
    bind(String.class)
        .annotatedWith(CanonicalWebUrl.class)
        .toProvider(CanonicalWebUrlProvider.class);
    bind(Boolean.class)
        .annotatedWith(EnablePeerIPInReflogRecord.class)
        .toProvider(EnablePeerIPInReflogRecordProvider.class)
        .in(SINGLETON);
    bind(Realm.class).to(FakeRealm.class);
    bind(IdentifiedUser.class).toProvider(Providers.of(null));
    bind(EmailNewPatchSet.Factory.class).toProvider(Providers.of(null));
    bind(CurrentUser.class).to(InternalUser.class);
    factory(PatchSetInserter.Factory.class);
    factory(RebaseChangeOp.Factory.class);
    factory(DiffOperationsForCommitValidation.Factory.class);

    bind(new TypeLiteral<ImmutableSet<GroupReference>>() {})
        .annotatedWith(AdministrateServerGroups.class)
        .toInstance(ImmutableSet.of());
    bind(new TypeLiteral<Set<AccountGroup.UUID>>() {})
        .annotatedWith(GitUploadPackGroups.class)
        .toInstance(Collections.emptySet());
    bind(new TypeLiteral<Set<AccountGroup.UUID>>() {})
        .annotatedWith(GitReceivePackGroups.class)
        .toInstance(Collections.emptySet());

    modules.add(new BatchGitModule());
    modules.add(
        new ChangesByProjectCache.Module(ChangesByProjectCache.UseIndex.FALSE, getConfig()));
    modules.add(new DefaultPermissionBackendModule());
    modules.add(new DefaultMemoryCacheModule());
    modules.add(new H2CacheModule(cacheOptions));
    modules.add(new GroupModule());
    modules.add(new NoteDbModule());
    modules.add(AccountCacheImpl.module());
    modules.add(AccountCacheImpl.bindingModule());
    modules.add(ConflictsCacheImpl.module());
    modules.add(DefaultPreferencesCacheImpl.module());
    modules.add(GroupCacheImpl.module());
    modules.add(GroupIncludeCacheImpl.module());
    modules.add(ProjectCacheImpl.module());
    modules.add(SectionSortCache.module());
    modules.add(ChangeKindCacheImpl.module());
    modules.add(MergeabilityCacheImpl.module());
    modules.add(ServiceUserClassifierImpl.module());
    modules.add(TagCache.module());
    modules.add(PureRevertCache.module());
    modules.add(new ApprovalModule());
    modules.add(SubmitRequirementsEvaluatorImpl.module());
    factory(CapabilityCollection.Factory.class);
    factory(ChangeData.AssistedFactory.class);
    factory(ChangeIsVisibleToPredicate.Factory.class);
    factory(DistinctVotersPredicate.Factory.class);
    factory(SubmitRequirementLabelExtensionPredicate.Factory.class);
    factory(HasSubmoduleUpdatePredicate.Factory.class);
    factory(ProjectState.Factory.class);

    DynamicMap.mapOf(binder(), ChangeQueryBuilder.ChangeOperatorFactory.class);
    DynamicMap.mapOf(binder(), ChangeQueryBuilder.ChangeHasOperandFactory.class);
    DynamicMap.mapOf(binder(), ChangeQueryBuilder.ChangeIsOperandFactory.class);
    DynamicMap.mapOf(binder(), ApprovalQueryBuilder.UserInOperandFactory.class);

    // Submit rules
    DynamicSet.setOf(binder(), SubmitRule.class);
    factory(SubmitRuleEvaluator.Factory.class);
    modules.add(new PrologModule(getConfig()));
    modules.add(new DefaultSubmitRuleModule());
    modules.add(new IgnoreSelfApprovalRuleModule());
    modules.add(new SkipCurrentRulesEvaluationOnClosedChangesModule());

    // Global submit requirements
    DynamicSet.setOf(binder(), SubmitRequirement.class);

    factory(FileEditsPredicate.Factory.class);

    bind(ChangeJson.Factory.class).toProvider(Providers.of(null));
    bind(EventUtil.class).toProvider(Providers.of(null));
    bind(GitReferenceUpdated.class).toInstance(GitReferenceUpdated.DISABLED);
    bind(RevisionCreated.class).toInstance(RevisionCreated.DISABLED);
    bind(WorkInProgressStateChanged.class).toInstance(WorkInProgressStateChanged.DISABLED);
    bind(AccountVisibility.class).toProvider(AccountVisibilityProvider.class).in(SINGLETON);
    bind(AttentionSetObserver.class).toInstance(AttentionSetObserver.DISABLED);

    ModuleOverloader.override(
            modules,
            LibModuleLoader.loadModules(parentInjector, LibModuleType.SYS_BATCH_MODULE_TYPE))
        .stream()
        .forEach(this::install);
  }

  protected Config getConfig() {
    return parentInjector.getInstance(Key.get(Config.class, GerritServerConfig.class));
  }
}
