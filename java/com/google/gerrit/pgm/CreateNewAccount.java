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

import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.index.IndexType;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.lucene.LuceneIndexModule;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.util.BatchProgramModule;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.AccountTagProvider;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.VersionedAuthorizedKeys;
import com.google.gerrit.server.account.externalids.ExternalIdNotes;
import com.google.gerrit.server.account.externalids.ExternalIdUpsertPreprocessor;
import com.google.gerrit.server.audit.AuditModule;
import com.google.gerrit.server.avatar.AvatarProvider;
import com.google.gerrit.server.change.AccountPatchReviewStore;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.git.WorkQueue.WorkQueueModule;
import com.google.gerrit.server.group.db.GroupDbModule;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.index.IndexModule;
import com.google.gerrit.server.index.options.AutoFlush;
import com.google.gerrit.server.plugins.PluginGuiceEnvironment;
import com.google.gerrit.server.restapi.account.CreateAccount;
import com.google.gerrit.server.ssh.NoSshKeyCache;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.gerrit.server.ssh.SshKeyCreator;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.kohsuke.args4j.Option;

public class CreateNewAccount extends SiteProgram {

  @Option(
      metaVar = "USERNAME",
      name = "--username",
      aliases = {"-u"},
      required = true,
      usage = "Username for account")
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
      required = true,
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

  @Inject CreateAccount createAccount;

  @Override
  public int run() throws Exception {
    LifecycleManager dbManager = new LifecycleManager();
    dbInjector = createInjector();
    dbInjector
        .getInstance(PluginGuiceEnvironment.class)
        .setDbCfgInjector(dbInjector, dbInjector.createChildInjector());
    dbManager.add(dbInjector);
    dbManager.start();

    dbInjector.injectMembers(this);

    try {
      dbInjector.injectMembers(this);
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
    }
  }

  private Injector createInjector() {
    Injector dbInjector = createDbInjector();
    List<Module> modules = new ArrayList<>();
    modules.add(
        new FactoryModule() {
          @Override
          protected void configure() {
            //            bind(IdentifiedUser.GenericFactory.class).in(SINGLETON);
            //            bind(IdentifiedUser.class).toProvider(Providers.of(null));
            //            bind(AccountCache.class).toProvider(Providers.of(null));
            //            bind(ChangeKindCache.class).toProvider(Providers.of(null));
            //            bind(ConflictsCache.class).toProvider(Providers.of(null));
            //            bind(ExternalIdCache.class).toProvider(Providers.of(null));
            //            bind(MergeabilityCache.class).toProvider(Providers.of(null));
            //            bind(GroupCache.class).toProvider(Providers.of(null));
            //            bind(GroupIncludeCache.class).toProvider(Providers.of(null));
            //            bind(PatchListCache.class).toProvider(Providers.of(null));
            //            bind(ProjectCache.class).toProvider(Providers.of(null));
            bind(SshKeyCache.class).to(NoSshKeyCache.class);
            bind(SshKeyCreator.class).to(NoSshKeyCache.class);
            //            bind(Realm.class).to(FakeRealm.class);
            //            bind(CurrentUser.class).to(InternalUser.class);
            //
            // bind(AccountVisibility.class).toProvider(AccountVisibilityProvider.class).in(SINGLETON);
            //            bind(new TypeLiteral<List<CommentLinkInfo>>() {})
            //            .toProvider(CommentLinkProvider.class)
            //            .in(SINGLETON);
            //            bind(EmailNewPatchSet.Factory.class).toProvider(Providers.of(null));
            factory(AccountsUpdate.Factory.class);
            factory(GroupsUpdate.Factory.class);
            factory(ChangeResource.Factory.class);
            factory(AccountLoader.Factory.class);
            factory(VersionedAuthorizedKeys.Factory.class);
            //            factory(AccountsUpdate.Factory.class);
            //            factory(GroupsUpdate.Factory.class);
            //            factory(RevisionJson.Factory.class);
            //            factory(ChangeIsVisibleToPredicate.Factory.class);
            //            factory(ChangeData.AssistedFactory.class);
            //            factory(ChangeJson.AssistedFactory.class);
            //            factory(BatchUpdate.Factory.class);
            //            factory(SubmitRuleEvaluator.Factory.class);
            //            factory(SubmitRuleEvaluator.Factory.class);
            //            factory(PatchSetInserter.Factory.class);
            //            factory(MetaDataUpdate.InternalFactory.class);
            //            factory(HasSubmoduleUpdatePredicate.Factory.class);
            //            factory(FileEditsPredicate.Factory.class);
            //            factory(DistinctVotersPredicate.Factory.class);
            //            factory(PrologRuleEvaluator.Factory.class);
            //            factory(ProjectState.Factory.class);

            DynamicItem.itemOf(binder(), AvatarProvider.class);
            //            DynamicItem.itemOf(binder(), UrlFormatter.class);
            DynamicItem.itemOf(binder(), AccountPatchReviewStore.class);
            DynamicMap.mapOf(binder(), AccountTagProvider.class);
            DynamicMap.mapOf(binder(), ExternalIdUpsertPreprocessor.class);
            //            DynamicMap.mapOf(binder(),
            // ChangeQueryBuilder.ChangeHasOperandFactory.class);
            //            DynamicMap.mapOf(binder(),
            // ChangeQueryBuilder.ChangeIsOperandFactory.class);
            //            DynamicMap.mapOf(binder(),
            // ChangeQueryBuilder.ChangeOperatorFactory.class);
            //            bind(String.class)
            //                .annotatedWith(CanonicalWebUrl.class)
            //                .toProvider(CanonicalWebUrlProvider.class);
            //            bind(Boolean.class)
            //                .annotatedWith(EnablePeerIPInReflogRecord.class)
            //                .toProvider(EnablePeerIPInReflogRecordProvider.class);
            //
            //            bind(new TypeLiteral<ImmutableSet<GroupReference>>() {})
            //                .annotatedWith(AdministrateServerGroups.class)
            //                .toInstance(ImmutableSet.of());
            //            bind(new TypeLiteral<Set<AccountGroup.UUID>>() {})
            //                .annotatedWith(GitUploadPackGroups.class)
            //                .toInstance(Collections.emptySet());
            //            bind(new TypeLiteral<Set<AccountGroup.UUID>>() {})
            //                .annotatedWith(GitReceivePackGroups.class)
            //                .toInstance(Collections.emptySet());
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
    //    modules.add(new DefaultRefLogIdentityProvider.Module());
    //    modules.add(new GroupModule());
    //    modules.add(new DefaultPermissionBackendModule());
    modules.add(new WorkQueueModule());
    modules.add(new BatchProgramModule(dbInjector));
    modules.add(new GroupDbModule());
    modules.add(new AuditModule());
    //    modules.add(new ApprovalModule());
    //    modules.add(ServiceUserClassifierImpl.module());
    //    modules.add(SubmitRequirementsEvaluatorImpl.module());
    //    modules.add(DiffOperationsImpl.module());
    //    modules.add(new SysExecutorModule());
    //    modules.add(new DefaultMemoryCacheModule());
    //    modules.add(new DiffExecutorModule());
    //    modules.add(new NoteDbModule());
    //    modules.add(new PrologModule(dbInjector.getInstance(Key.get(Config.class,
    // GerritServerConfig.class))));
    //    modules.add(new FileInfoJsonModule());

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
}
