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

package com.google.gerrit.server.restapi;

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.gpg.PublicKeyStore;
import com.google.gerrit.gpg.UnimplementedPublicKeyStoreProvider;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.change.AddReviewersOp;
import com.google.gerrit.server.change.AddToAttentionSetOp;
import com.google.gerrit.server.change.BlockUserOp;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.DeleteChangeOp;
import com.google.gerrit.server.change.DeleteReviewerByEmailOp;
import com.google.gerrit.server.change.DeleteReviewerOp;
import com.google.gerrit.server.change.EmailReviewComments;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.change.RebaseChangeOp;
import com.google.gerrit.server.change.RemoveFromAttentionSetOp;
import com.google.gerrit.server.change.ReviewerResource;
import com.google.gerrit.server.change.SetCherryPickOp;
import com.google.gerrit.server.change.SetCustomKeyedValuesOp;
import com.google.gerrit.server.change.SetHashtagsOp;
import com.google.gerrit.server.change.SetPrivateOp;
import com.google.gerrit.server.change.SetTopicOp;
import com.google.gerrit.server.change.WorkInProgressOp;
import com.google.gerrit.server.comment.CommentContextLoader;
import com.google.gerrit.server.config.GerritConfigListener;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.project.RefValidationHelper;
import com.google.gerrit.server.restapi.change.DeleteVoteOp;
import com.google.gerrit.server.restapi.change.PostReviewOp;
import com.google.gerrit.server.restapi.change.PreviewFix;
import com.google.gerrit.server.restapi.project.CreateProject;
import com.google.gerrit.server.restapi.project.ProjectNode;
import com.google.gerrit.server.restapi.project.SetParent;
import com.google.gerrit.server.util.AttentionSetEmail;
import com.google.gerrit.server.validators.ProjectCreationValidationListener;
import com.google.inject.Provides;
import com.google.inject.multibindings.OptionalBinder;

/**
 * Module to bind classes that are needed but the REST layer, but which are not REST endpoints.
 *
 * <p>REST endpoints should be bound in {@link RestApiModule}.
 */
public class RestModule extends FactoryModule {
  @Override
  protected void configure() {
    factory(AccountLoader.Factory.class);
    factory(AddReviewersOp.Factory.class);
    factory(AddToAttentionSetOp.Factory.class);
    factory(AttentionSetEmail.Factory.class);
    factory(ChangeInserter.Factory.class);
    factory(ChangeResource.Factory.class);
    factory(CommentContextLoader.Factory.class);
    factory(DeleteChangeOp.Factory.class);
    factory(DeleteReviewerByEmailOp.Factory.class);
    factory(DeleteReviewerOp.Factory.class);
    factory(BlockUserOp.Factory.class);
    factory(DeleteVoteOp.Factory.class);
    factory(EmailReviewComments.Factory.class);
    factory(GroupsUpdate.Factory.class);
    factory(PatchSetInserter.Factory.class);
    factory(PostReviewOp.Factory.class);
    factory(PreviewFix.Factory.class);
    factory(ProjectNode.Factory.class);
    factory(RebaseChangeOp.Factory.class);
    factory(RefValidationHelper.Factory.class);
    factory(RemoveFromAttentionSetOp.Factory.class);
    factory(ReviewerResource.Factory.class);
    factory(SetCherryPickOp.Factory.class);
    factory(SetCustomKeyedValuesOp.Factory.class);
    factory(SetHashtagsOp.Factory.class);
    factory(SetPrivateOp.Factory.class);
    factory(SetTopicOp.Factory.class);
    factory(WorkInProgressOp.Factory.class);

    DynamicSet.bind(binder(), GerritConfigListener.class).to(SetParent.class);
    DynamicSet.bind(binder(), ProjectCreationValidationListener.class)
        .to(CreateProject.ValidBranchListener.class);

    OptionalBinder.newOptionalBinder(binder(), PublicKeyStore.class)
        .setDefault()
        .toProvider(UnimplementedPublicKeyStoreProvider.class);
  }

  @Provides
  @ServerInitiated
  AccountsUpdate provideServerInitiatedAccountsUpdate(
      @AccountsUpdate.AccountsUpdateLoader.WithReindex
          AccountsUpdate.AccountsUpdateLoader accountsUpdateFactory) {
    return accountsUpdateFactory.createWithServerIdent();
  }

  @Provides
  @UserInitiated
  AccountsUpdate provideUserInitiatedAccountsUpdate(
      @AccountsUpdate.AccountsUpdateLoader.WithReindex
          AccountsUpdate.AccountsUpdateLoader accountsUpdateFactory,
      IdentifiedUser currentUser) {
    return accountsUpdateFactory.create(currentUser);
  }

  @Provides
  @ServerInitiated
  GroupsUpdate provideServerInitiatedGroupsUpdate(GroupsUpdate.Factory groupsUpdateFactory) {
    return groupsUpdateFactory.createWithServerIdent();
  }

  @Provides
  @UserInitiated
  GroupsUpdate provideUserInitiatedGroupsUpdate(
      GroupsUpdate.Factory groupsUpdateFactory, IdentifiedUser currentUser) {
    return groupsUpdateFactory.create(currentUser);
  }
}
