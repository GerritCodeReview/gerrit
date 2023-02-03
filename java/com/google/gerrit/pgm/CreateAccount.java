// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.pgm;

import static com.google.inject.Scopes.SINGLETON;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.api.projects.CommentLinkInfo;
import com.google.gerrit.extensions.common.AccountVisibility;
import com.google.gerrit.extensions.config.DownloadCommand;
import com.google.gerrit.extensions.config.DownloadScheme;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.systemstatus.ServerInformation;
import com.google.gerrit.gpg.GpgModule;
import com.google.gerrit.index.IndexType;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.lucene.LuceneIndexModule;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.util.BatchGitModule;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.DefaultRefLogIdentityProvider;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.account.AccountCacheImpl;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.AccountTagProvider;
import com.google.gerrit.server.account.AccountVisibilityProvider;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.CapabilityCollection;
import com.google.gerrit.server.account.FakeRealm;
import com.google.gerrit.server.account.GroupCacheImpl;
import com.google.gerrit.server.account.GroupIncludeCacheImpl;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.account.ServiceUserClassifierImpl;
import com.google.gerrit.server.account.VersionedAuthorizedKeys;
import com.google.gerrit.server.account.externalids.ExternalIdCacheModule;
import com.google.gerrit.server.account.externalids.ExternalIdNotes;
import com.google.gerrit.server.account.externalids.ExternalIdUpsertPreprocessor;
import com.google.gerrit.server.audit.AuditModule;
import com.google.gerrit.server.avatar.AvatarProvider;
import com.google.gerrit.server.cache.mem.DefaultMemoryCacheModule;
import com.google.gerrit.server.change.AccountPatchReviewStore;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeKindCacheImpl;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.EmailNewPatchSet;
import com.google.gerrit.server.change.FileInfoJsonModule;
import com.google.gerrit.server.change.MergeabilityCacheImpl;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.change.RevisionJson;
import com.google.gerrit.server.change.RevisionResource;
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
import com.google.gerrit.server.config.SysExecutorModule;
import com.google.gerrit.server.git.PureRevertCache;
import com.google.gerrit.server.git.SearchingChangeCacheImpl;
import com.google.gerrit.server.git.TagCache;
import com.google.gerrit.server.git.WorkQueue.WorkQueueModule;
import com.google.gerrit.server.group.db.GroupDbModule;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.index.IndexModule;
import com.google.gerrit.server.index.options.AutoFlush;
import com.google.gerrit.server.notedb.NoteDbModule;
import com.google.gerrit.server.patch.DiffExecutorModule;
import com.google.gerrit.server.patch.DiffOperationsImpl;
import com.google.gerrit.server.patch.PatchListCacheImpl;
import com.google.gerrit.server.permissions.DefaultPermissionBackendModule;
import com.google.gerrit.server.permissions.SectionSortCache;
import com.google.gerrit.server.plugins.PluginGuiceEnvironment;
import com.google.gerrit.server.plugins.ServerInformationImpl;
import com.google.gerrit.server.project.CommentLinkProvider;
import com.google.gerrit.server.project.CommitResource;
import com.google.gerrit.server.project.ProjectCacheImpl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.SubmitRequirementsEvaluatorImpl;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.query.approval.ApprovalModule;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeIsVisibleToPredicate;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ConflictsCacheImpl;
import com.google.gerrit.server.restapi.group.GroupModule;
import com.google.gerrit.server.rules.PrologModule;
import com.google.gerrit.server.submitrequirement.predicate.DistinctVotersPredicate;
import com.google.gerrit.server.submitrequirement.predicate.FileEditsPredicate;
import com.google.gerrit.server.submitrequirement.predicate.HasSubmoduleUpdatePredicate;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.sshd.SshKeyCacheImpl;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Providers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.lib.Config;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

public class CreateAccount extends SiteProgram {

  @Argument(metaVar = "USERNAME", index = 0, required = true, usage = "Username for account")
  private String username;

  @Option(
      metaVar = "DISPLAY_NAME",
      name = "--display-name",
      required = false,
      usage = "Name displayed in the UI.")
  private String displayName;

  @Option(
      metaVar = "EMAIL",
      name = "--email",
      required = false,
      usage = "Email associated with the account.")
  private String email;

  @Option(
      metaVar = "SSH_KEY",
      name = "--ssh-key",
      required = false,
      usage = "Public SSH key used for authenticating SSH requests.")
  private String sshKey;

  @Option(
      metaVar = "PASSWORD",
      name = "--password",
      required = false,
      usage = "HTTP Password to be used for git and REST API requests.")
  private String password;

  @Option(
      metaVar = "GROUPS",
      name = "--group",
      required = false,
      usage = "Groups to which the account should be added.")
  private String[] groups;

  private Injector dbInjector;
  private Injector sysInjector;

  @Inject com.google.gerrit.server.restapi.account.CreateAccount createAccount;

  @Override
  public int run() throws Exception {
    dbInjector = createDbInjector();
    LifecycleManager dbManager = new LifecycleManager();
    dbManager.add(dbInjector);
    dbManager.start();

    sysInjector = createSysInjector();
    sysInjector
        .getInstance(PluginGuiceEnvironment.class)
        .setDbCfgInjector(dbInjector, dbInjector.createChildInjector());
    LifecycleManager sysManager = new LifecycleManager();
    sysManager.add(sysInjector);
    sysManager.start();

    try {
      sysInjector.injectMembers(this);
      if (password == null) {
        ConsoleUI ui = ConsoleUI.getInstance();
        password = ui.password("%s", "Password: ");
      }

      AccountInput accountInput = new AccountInput();
      accountInput.username = username;
      accountInput.displayName = displayName;
      accountInput.email = email;
      accountInput.sshKey = sshKey;
      accountInput.httpPassword = password;
      accountInput.groups = groups != null ? Arrays.asList(groups) : List.of();
      try {
        createAccount.apply(IdString.fromDecoded(username), accountInput);
      } catch (RestApiException e) {
        throw die(e.getMessage());
      }
      return 0;
    } finally {
      dbManager.stop();
      sysManager.stop();
    }
  }

  private Injector createSysInjector() {
    List<Module> modules = new ArrayList<>();
    modules.add(
        new FactoryModule() {
          @Override
          protected void configure() {
            bind(CurrentUser.class).to(InternalUser.class);
            bind(Realm.class).to(FakeRealm.class);

            bind(AccountVisibility.class).toProvider(AccountVisibilityProvider.class).in(SINGLETON);
            bind(EmailNewPatchSet.Factory.class).toProvider(Providers.of(null));
            bind(IdentifiedUser.class).toProvider(Providers.of(null));
            bind(SearchingChangeCacheImpl.class).toProvider(Providers.of(null));
            bind(ServerInformation.class).to(ServerInformationImpl.class);

            bind(new TypeLiteral<List<CommentLinkInfo>>() {})
                .toProvider(CommentLinkProvider.class)
                .in(SINGLETON);
            bind(new TypeLiteral<DynamicMap<RestView<ChangeResource>>>() {})
                .toInstance(DynamicMap.emptyMap());
            bind(new TypeLiteral<DynamicMap<RestView<CommitResource>>>() {})
                .toInstance(DynamicMap.emptyMap());
            bind(new TypeLiteral<DynamicMap<RestView<RevisionResource>>>() {})
                .toInstance(DynamicMap.emptyMap());

            bind(String.class)
                .annotatedWith(CanonicalWebUrl.class)
                .toProvider(CanonicalWebUrlProvider.class);
            bind(Boolean.class)
                .annotatedWith(EnablePeerIPInReflogRecord.class)
                .toProvider(EnablePeerIPInReflogRecordProvider.class)
                .in(SINGLETON);

            bind(new TypeLiteral<ImmutableSet<GroupReference>>() {})
                .annotatedWith(AdministrateServerGroups.class)
                .toInstance(ImmutableSet.of());
            bind(new TypeLiteral<Set<AccountGroup.UUID>>() {})
                .annotatedWith(GitReceivePackGroups.class)
                .toInstance(Collections.emptySet());
            bind(new TypeLiteral<Set<AccountGroup.UUID>>() {})
                .annotatedWith(GitUploadPackGroups.class)
                .toInstance(Collections.emptySet());

            factory(AccountLoader.Factory.class);
            factory(AccountsUpdate.Factory.class);
            factory(CapabilityCollection.Factory.class);
            factory(ChangeIsVisibleToPredicate.Factory.class);
            factory(ChangeResource.Factory.class);
            factory(DistinctVotersPredicate.Factory.class);
            factory(FileEditsPredicate.Factory.class);
            factory(GroupsUpdate.Factory.class);
            factory(HasSubmoduleUpdatePredicate.Factory.class);
            factory(PatchSetInserter.Factory.class);
            factory(ProjectState.Factory.class);
            factory(RevisionJson.Factory.class);
            factory(SubmitRuleEvaluator.Factory.class);
            factory(VersionedAuthorizedKeys.Factory.class);

            factory(ChangeData.AssistedFactory.class);
            factory(ChangeJson.AssistedFactory.class);

            DynamicItem.itemOf(binder(), AvatarProvider.class);
            DynamicItem.itemOf(binder(), AccountPatchReviewStore.class);
            DynamicMap.mapOf(binder(), AccountTagProvider.class);
            DynamicMap.mapOf(binder(), ChangeQueryBuilder.ChangeOperatorFactory.class);
            DynamicMap.mapOf(binder(), ChangeQueryBuilder.ChangeHasOperandFactory.class);
            DynamicMap.mapOf(binder(), ChangeQueryBuilder.ChangeIsOperandFactory.class);
            DynamicMap.mapOf(binder(), DownloadCommand.class);
            DynamicMap.mapOf(binder(), DownloadScheme.class);
            DynamicMap.mapOf(binder(), ExternalIdUpsertPreprocessor.class);
          }

          @Provides
          @UserInitiated
          AccountsUpdate provideUserInitiatedAccountsUpdate(
              AccountsUpdate.Factory accountsUpdateFactory,
              ExternalIdNotes.Factory extIdNotesFactory) {
            return accountsUpdateFactory.createWithServerIdent(extIdNotesFactory);
          }

          @Provides
          @UserInitiated
          GroupsUpdate provideUserInitiatedGroupsUpdate(GroupsUpdate.Factory groupsUpdateFactory) {
            return groupsUpdateFactory.createWithServerIdent();
          }
        });

    modules.add(new ApprovalModule());
    modules.add(new AuditModule());
    modules.add(new BatchGitModule());
    modules.add(new DefaultMemoryCacheModule());
    modules.add(new DefaultPermissionBackendModule());
    modules.add(new DefaultUrlFormatterModule());
    modules.add(new DiffExecutorModule());
    modules.add(new ExternalIdCacheModule());
    modules.add(new FileInfoJsonModule());
    modules.add(new GpgModule(getConfig()));
    modules.add(new GroupDbModule());
    modules.add(new GroupModule());
    modules.add(new NoteDbModule());
    modules.add(new PrologModule(getConfig()));
    modules.add(new SysExecutorModule());
    modules.add(new WorkQueueModule());

    modules.add(new DefaultRefLogIdentityProvider.Module());

    modules.add(AccountCacheImpl.module());
    modules.add(BatchUpdate.module());
    modules.add(ChangeKindCacheImpl.module());
    modules.add(ConflictsCacheImpl.module());
    modules.add(DefaultPreferencesCacheImpl.module());
    modules.add(DiffOperationsImpl.module());
    modules.add(GroupCacheImpl.module());
    modules.add(GroupIncludeCacheImpl.module());
    modules.add(MergeabilityCacheImpl.module());
    modules.add(PatchListCacheImpl.module());
    modules.add(ProjectCacheImpl.module());
    modules.add(PureRevertCache.module());
    modules.add(SectionSortCache.module());
    modules.add(ServiceUserClassifierImpl.module());
    modules.add(SshKeyCacheImpl.module());
    modules.add(SubmitRequirementsEvaluatorImpl.module());
    modules.add(TagCache.module());

    Module indexModule;
    IndexType indexType = IndexModule.getIndexType(dbInjector);
    if (indexType.isLucene()) {
      indexModule = LuceneIndexModule.singleVersionAllLatest(1, false, AutoFlush.ENABLED);
    } else {
      throw new IllegalStateException("unsupported index.type = " + indexType);
    }
    modules.add(indexModule);

    return dbInjector.createChildInjector(modules);
  }

  protected Config getConfig() {
    return dbInjector.getInstance(Key.get(Config.class, GerritServerConfig.class));
  }
}
