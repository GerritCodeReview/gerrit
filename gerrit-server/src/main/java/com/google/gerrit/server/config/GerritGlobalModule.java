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
import com.google.gerrit.common.EventListener;
import com.google.gerrit.extensions.config.CapabilityDefinition;
import com.google.gerrit.extensions.config.DownloadCommand;
import com.google.gerrit.extensions.config.DownloadScheme;
import com.google.gerrit.extensions.config.ExternalIncludedIn;
import com.google.gerrit.extensions.events.GarbageCollectorListener;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.events.HeadUpdatedListener;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.events.NewProjectCreatedListener;
import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.gerrit.extensions.events.UsageDataPublishedListener;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.systemstatus.MessageOfTheDay;
import com.google.gerrit.extensions.webui.BranchWebLink;
import com.google.gerrit.extensions.webui.DiffWebLink;
import com.google.gerrit.extensions.webui.FileWebLink;
import com.google.gerrit.extensions.webui.PatchSetWebLink;
import com.google.gerrit.extensions.webui.ProjectWebLink;
import com.google.gerrit.extensions.webui.TopMenu;
import com.google.gerrit.rules.PrologModule;
import com.google.gerrit.rules.RulesCache;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.CmdLineParserModule;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PluginUser;
import com.google.gerrit.server.account.AccountByEmailCacheImpl;
import com.google.gerrit.server.account.AccountCacheImpl;
import com.google.gerrit.server.account.AccountControl;
import com.google.gerrit.server.account.AccountInfoCacheFactory;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.AccountVisibility;
import com.google.gerrit.server.account.AccountVisibilityProvider;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.account.ChangeUserName;
import com.google.gerrit.server.account.EmailExpander;
import com.google.gerrit.server.account.GroupCacheImpl;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.account.GroupDetailFactory;
import com.google.gerrit.server.account.GroupIncludeCacheImpl;
import com.google.gerrit.server.account.GroupInfoCacheFactory;
import com.google.gerrit.server.account.GroupMembers;
import com.google.gerrit.server.auth.AuthBackend;
import com.google.gerrit.server.auth.UniversalAuthBackend;
import com.google.gerrit.server.avatar.AvatarProvider;
import com.google.gerrit.server.cache.CacheRemovalListener;
import com.google.gerrit.server.change.ChangeKindCacheImpl;
import com.google.gerrit.server.change.MergeabilityCacheImpl;
import com.google.gerrit.server.events.EventFactory;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.ChangeMergeQueue;
import com.google.gerrit.server.git.GitModule;
import com.google.gerrit.server.git.MergeQueue;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.git.NotesBranchUtil;
import com.google.gerrit.server.git.ReceivePackInitializer;
import com.google.gerrit.server.git.TagCache;
import com.google.gerrit.server.git.TransferConfig;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.git.validators.MergeValidationListener;
import com.google.gerrit.server.git.validators.MergeValidators;
import com.google.gerrit.server.git.validators.MergeValidators.ProjectConfigValidator;
import com.google.gerrit.server.git.validators.RefOperationValidationListener;
import com.google.gerrit.server.git.validators.RefOperationValidators;
import com.google.gerrit.server.git.validators.UploadValidationListener;
import com.google.gerrit.server.git.validators.UploadValidators;
import com.google.gerrit.server.group.GroupModule;
import com.google.gerrit.server.index.ReindexAfterUpdate;
import com.google.gerrit.server.mail.AddReviewerSender;
import com.google.gerrit.server.mail.CreateChangeSender;
import com.google.gerrit.server.mail.EmailModule;
import com.google.gerrit.server.mail.FromAddressGenerator;
import com.google.gerrit.server.mail.FromAddressGeneratorProvider;
import com.google.gerrit.server.mail.MergeFailSender;
import com.google.gerrit.server.mail.MergedSender;
import com.google.gerrit.server.mail.RegisterNewEmailSender;
import com.google.gerrit.server.mail.ReplacePatchSetSender;
import com.google.gerrit.server.mail.VelocityRuntimeProvider;
import com.google.gerrit.server.mime.FileTypeRegistry;
import com.google.gerrit.server.mime.MimeUtilFileTypeRegistry;
import com.google.gerrit.server.notedb.NoteDbModule;
import com.google.gerrit.server.patch.PatchListCacheImpl;
import com.google.gerrit.server.patch.PatchScriptFactory;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.plugins.ReloadPluginListener;
import com.google.gerrit.server.project.AccessControlModule;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.CommentLinkInfo;
import com.google.gerrit.server.project.CommentLinkProvider;
import com.google.gerrit.server.project.PermissionCollection;
import com.google.gerrit.server.project.ProjectCacheImpl;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectNode;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.SectionSortCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ConflictsCacheImpl;
import com.google.gerrit.server.ssh.SshAddressesModule;
import com.google.gerrit.server.tools.ToolsCatalog;
import com.google.gerrit.server.util.IdGenerator;
import com.google.gerrit.server.util.SubmoduleSectionParser;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gerrit.server.validators.GroupCreationValidationListener;
import com.google.gerrit.server.validators.HashtagValidationListener;
import com.google.gerrit.server.validators.OutgoingEmailValidationListener;
import com.google.gerrit.server.validators.ProjectCreationValidationListener;
import com.google.inject.Inject;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.UniqueAnnotations;

import org.apache.velocity.runtime.RuntimeInstance;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.PreUploadHook;

import java.util.List;


/** Starts global state with standard dependencies. */
public class GerritGlobalModule extends FactoryModule {
  private final AuthModule authModule;

  @Inject
  GerritGlobalModule(AuthModule authModule) {
    this.authModule = authModule;
  }

  @Override
  protected void configure() {
    bind(EmailExpander.class).toProvider(EmailExpanderProvider.class).in(
        SINGLETON);

    bind(IdGenerator.class);
    bind(RulesCache.class);
    install(authModule);
    install(AccountByEmailCacheImpl.module());
    install(AccountCacheImpl.module());
    install(ChangeKindCacheImpl.module());
    install(ConflictsCacheImpl.module());
    install(GroupCacheImpl.module());
    install(GroupIncludeCacheImpl.module());
    install(MergeabilityCacheImpl.module());
    install(PatchListCacheImpl.module());
    install(ProjectCacheImpl.module());
    install(SectionSortCache.module());
    install(TagCache.module());

    install(new AccessControlModule());
    install(new CmdLineParserModule());
    install(new EmailModule());
    install(new GitModule());
    install(new GroupModule());
    install(new NoteDbModule());
    install(new PrologModule());
    install(new SshAddressesModule());
    install(ThreadLocalRequestContext.module());

    bind(AccountResolver.class);

    factory(AccountInfoCacheFactory.Factory.class);
    factory(AddReviewerSender.Factory.class);
    factory(CapabilityControl.Factory.class);
    factory(ChangeData.Factory.class);
    factory(CreateChangeSender.Factory.class);
    factory(GroupDetailFactory.Factory.class);
    factory(GroupInfoCacheFactory.Factory.class);
    factory(GroupMembers.Factory.class);
    factory(MergedSender.Factory.class);
    factory(MergeFailSender.Factory.class);
    factory(MergeUtil.Factory.class);
    factory(PatchScriptFactory.Factory.class);
    factory(PluginUser.Factory.class);
    factory(ProjectNode.Factory.class);
    factory(ProjectState.Factory.class);
    factory(RegisterNewEmailSender.Factory.class);
    factory(ReplacePatchSetSender.Factory.class);
    bind(PermissionCollection.Factory.class);
    bind(AccountVisibility.class)
        .toProvider(AccountVisibilityProvider.class)
        .in(SINGLETON);
    factory(ProjectOwnerGroupsProvider.Factory.class);
    bind(RepositoryConfig.class);

    bind(AuthBackend.class).to(UniversalAuthBackend.class).in(SINGLETON);
    DynamicSet.setOf(binder(), AuthBackend.class);

    bind(GroupControl.Factory.class).in(SINGLETON);
    bind(GroupControl.GenericFactory.class).in(SINGLETON);

    bind(FileTypeRegistry.class).to(MimeUtilFileTypeRegistry.class);
    bind(ToolsCatalog.class);
    bind(EventFactory.class);
    bind(TransferConfig.class);

    bind(GcConfig.class);

    bind(ApprovalsUtil.class);
    bind(ChangeMergeQueue.class).in(SINGLETON);
    bind(MergeQueue.class).to(ChangeMergeQueue.class).in(SINGLETON);

    bind(RuntimeInstance.class)
        .toProvider(VelocityRuntimeProvider.class)
        .in(SINGLETON);
    bind(FromAddressGenerator.class).toProvider(
        FromAddressGeneratorProvider.class).in(SINGLETON);
    bind(Boolean.class).annotatedWith(DisableReverseDnsLookup.class)
        .toProvider(DisableReverseDnsLookupProvider.class).in(SINGLETON);

    bind(PatchSetInfoFactory.class);
    bind(IdentifiedUser.GenericFactory.class).in(SINGLETON);
    bind(ChangeControl.GenericFactory.class);
    bind(ProjectControl.GenericFactory.class);
    bind(AccountControl.Factory.class);

    install(new AuditModule());
    install(new com.google.gerrit.server.access.Module());
    install(new com.google.gerrit.server.account.Module());
    install(new com.google.gerrit.server.api.Module());
    install(new com.google.gerrit.server.change.Module());
    install(new com.google.gerrit.server.config.Module());
    install(new com.google.gerrit.server.group.Module());
    install(new com.google.gerrit.server.project.Module());

    bind(GitReferenceUpdated.class);
    DynamicMap.mapOf(binder(), new TypeLiteral<Cache<?, ?>>() {});
    DynamicSet.setOf(binder(), CacheRemovalListener.class);
    DynamicMap.mapOf(binder(), CapabilityDefinition.class);
    DynamicSet.setOf(binder(), GitReferenceUpdatedListener.class);
    DynamicSet.setOf(binder(), ReceivePackInitializer.class);
    DynamicSet.setOf(binder(), PostReceiveHook.class);
    DynamicSet.setOf(binder(), PreUploadHook.class);
    DynamicSet.setOf(binder(), NewProjectCreatedListener.class);
    DynamicSet.setOf(binder(), ProjectDeletedListener.class);
    DynamicSet.setOf(binder(), GarbageCollectorListener.class);
    DynamicSet.setOf(binder(), HeadUpdatedListener.class);
    DynamicSet.setOf(binder(), UsageDataPublishedListener.class);
    DynamicSet.bind(binder(), GitReferenceUpdatedListener.class).to(ReindexAfterUpdate.class);
    DynamicSet.bind(binder(), GitReferenceUpdatedListener.class)
        .to(ProjectConfigEntry.UpdateChecker.class);
    DynamicSet.setOf(binder(), EventListener.class);
    DynamicSet.setOf(binder(), CommitValidationListener.class);
    DynamicSet.setOf(binder(), RefOperationValidationListener.class);
    DynamicSet.setOf(binder(), MergeValidationListener.class);
    DynamicSet.setOf(binder(), ProjectCreationValidationListener.class);
    DynamicSet.setOf(binder(), GroupCreationValidationListener.class);
    DynamicSet.setOf(binder(), HashtagValidationListener.class);
    DynamicSet.setOf(binder(), OutgoingEmailValidationListener.class);
    DynamicItem.itemOf(binder(), AvatarProvider.class);
    DynamicSet.setOf(binder(), LifecycleListener.class);
    DynamicSet.setOf(binder(), TopMenu.class);
    DynamicSet.setOf(binder(), MessageOfTheDay.class);
    DynamicMap.mapOf(binder(), DownloadScheme.class);
    DynamicMap.mapOf(binder(), DownloadCommand.class);
    DynamicMap.mapOf(binder(), ExternalIncludedIn.class);
    DynamicMap.mapOf(binder(), ProjectConfigEntry.class);
    DynamicSet.setOf(binder(), PatchSetWebLink.class);
    DynamicSet.setOf(binder(), FileWebLink.class);
    DynamicSet.setOf(binder(), DiffWebLink.class);
    DynamicSet.setOf(binder(), ProjectWebLink.class);
    DynamicSet.setOf(binder(), BranchWebLink.class);

    factory(UploadValidators.Factory.class);
    DynamicSet.setOf(binder(), UploadValidationListener.class);

    bind(AnonymousUser.class);

    factory(CommitValidators.Factory.class);
    factory(RefOperationValidators.Factory.class);
    factory(MergeValidators.Factory.class);
    factory(ProjectConfigValidator.Factory.class);
    factory(NotesBranchUtil.Factory.class);
    factory(SubmoduleSectionParser.Factory.class);

    bind(AccountManager.class);
    bind(ChangeUserName.CurrentUser.class);
    factory(ChangeUserName.Factory.class);

    bind(new TypeLiteral<List<CommentLinkInfo>>() {})
        .toProvider(CommentLinkProvider.class).in(SINGLETON);

    bind(ReloadPluginListener.class)
        .annotatedWith(UniqueAnnotations.create())
        .to(PluginConfigFactory.class);
  }
}
