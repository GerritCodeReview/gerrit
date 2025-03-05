// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.restapi.account;

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_MAILTO;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.GroupDescription;
import com.google.gerrit.exceptions.InvalidSshKeyException;
import com.google.gerrit.exceptions.NoSuchGroupException;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.auth.AuthTokenInput;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestCollectionCreateView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.account.AccountExternalIdCreator;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.AuthToken;
import com.google.gerrit.server.account.AuthTokenAccessor;
import com.google.gerrit.server.account.InvalidAuthTokenException;
import com.google.gerrit.server.account.VersionedAuthorizedKeys;
import com.google.gerrit.server.account.externalids.DuplicateExternalIdKeyException;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdFactory;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.group.GroupResolver;
import com.google.gerrit.server.group.db.GroupDelta;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.mail.send.OutgoingEmailValidator;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * REST endpoint for creating a new account.
 *
 * <p>This REST endpoint handles {@code PUT /accounts/<account-identifier>} requests if the
 * specified account doesn't exist yet. If it already exists, the request is handled by {@link
 * PutAccount}.
 */
@RequiresCapability(GlobalCapability.CREATE_ACCOUNT)
@Singleton
public class CreateAccount
    implements RestCollectionCreateView<TopLevelResource, AccountResource, AccountInput> {
  private final Sequences seq;
  private final GroupResolver groupResolver;
  private final VersionedAuthorizedKeys.Accessor authorizedKeys;
  private final AuthTokenAccessor tokensAccessor;
  private final SshKeyCache sshKeyCache;
  private final Provider<AccountsUpdate> accountsUpdateProvider;
  private final AccountLoader.Factory infoLoader;
  private final PluginSetContext<AccountExternalIdCreator> externalIdCreators;
  private final Provider<GroupsUpdate> groupsUpdate;
  private final OutgoingEmailValidator validator;
  private final AuthConfig authConfig;
  private final ExternalIdFactory externalIdFactory;

  @Inject
  CreateAccount(
      Sequences seq,
      GroupResolver groupResolver,
      VersionedAuthorizedKeys.Accessor authorizedKeys,
      AuthTokenAccessor tokensAccessor,
      SshKeyCache sshKeyCache,
      @UserInitiated Provider<AccountsUpdate> accountsUpdateProvider,
      AccountLoader.Factory infoLoader,
      PluginSetContext<AccountExternalIdCreator> externalIdCreators,
      @UserInitiated Provider<GroupsUpdate> groupsUpdate,
      OutgoingEmailValidator validator,
      AuthConfig authConfig,
      ExternalIdFactory externalIdFactory) {
    this.seq = seq;
    this.groupResolver = groupResolver;
    this.authorizedKeys = authorizedKeys;
    this.tokensAccessor = tokensAccessor;
    this.sshKeyCache = sshKeyCache;
    this.accountsUpdateProvider = accountsUpdateProvider;
    this.infoLoader = infoLoader;
    this.externalIdCreators = externalIdCreators;
    this.groupsUpdate = groupsUpdate;
    this.validator = validator;
    this.authConfig = authConfig;
    this.externalIdFactory = externalIdFactory;
  }

  @Override
  public Response<AccountInfo> apply(
      TopLevelResource rsrc, IdString id, @Nullable AccountInput input)
      throws BadRequestException,
          ResourceConflictException,
          UnprocessableEntityException,
          IOException,
          ConfigInvalidException,
          PermissionBackendException,
          InvalidAuthTokenException {
    return apply(id, input != null ? input : new AccountInput());
  }

  public Response<AccountInfo> apply(IdString id, AccountInput input)
      throws BadRequestException,
          ResourceConflictException,
          UnprocessableEntityException,
          IOException,
          ConfigInvalidException,
          PermissionBackendException,
          InvalidAuthTokenException {
    String username = applyCaseOfUsername(id.get());
    if (input.username != null && !username.equals(applyCaseOfUsername(input.username))) {
      throw new BadRequestException("username must match URL");
    }
    if (!ExternalId.isValidUsername(username)) {
      throw new BadRequestException("Invalid username '" + username + "'");
    }

    if (input.name == null) {
      input.name = input.username;
    }

    Set<AccountGroup.UUID> groups = parseGroups(input.groups);

    Account.Id accountId = Account.id(seq.nextAccountId());
    List<ExternalId> extIds = new ArrayList<>();

    if (input.email != null) {
      if (!validator.isValid(input.email)) {
        throw new BadRequestException("invalid email address");
      }
      extIds.add(externalIdFactory.createEmail(accountId, input.email));
    }

    extIds.add(externalIdFactory.createUsername(username, accountId));
    externalIdCreators.runEach(c -> extIds.addAll(c.create(accountId, username, input.email)));

    try {
      accountsUpdateProvider
          .get()
          .insert(
              "Create Account via API",
              accountId,
              u -> u.setFullName(input.name).setPreferredEmail(input.email).addExternalIds(extIds));
    } catch (DuplicateExternalIdKeyException e) {
      if (e.getDuplicateKey().isScheme(SCHEME_USERNAME)) {
        throw new ResourceConflictException(
            "username '" + e.getDuplicateKey().id() + "' already exists");
      } else if (e.getDuplicateKey().isScheme(SCHEME_MAILTO)) {
        throw new UnprocessableEntityException(
            "email '" + e.getDuplicateKey().id() + "' already exists");
      } else {
        // AccountExternalIdCreator returned an external ID that already exists
        throw e;
      }
    }

    for (AccountGroup.UUID groupUuid : groups) {
      try {
        addGroupMember(groupUuid, accountId);
      } catch (NoSuchGroupException e) {
        throw new UnprocessableEntityException(String.format("Group %s not found", groupUuid), e);
      }
    }

    if (input.sshKey != null) {
      try {
        authorizedKeys.addKey(accountId, input.sshKey);
        sshKeyCache.evict(username);
      } catch (InvalidSshKeyException e) {
        throw new BadRequestException(e.getMessage());
      }
    }

    List<AuthToken> tokens = new ArrayList<>();
    if (input.tokens != null) {
      for (AuthTokenInput token : input.tokens) {
        tokens.add(AuthToken.createWithPlainToken(token.id, token.token));
      }
    }

    if (input.httpPassword != null) {
      tokens.add(AuthToken.createWithPlainToken("legacy", input.httpPassword));
    }

    if (!tokens.isEmpty()) {
      tokensAccessor.addTokens(accountId, tokens);
    }

    AccountLoader loader = infoLoader.create(true);
    AccountInfo info = loader.get(accountId);
    loader.fill();
    return Response.created(info);
  }

  private String applyCaseOfUsername(String username) {
    return authConfig.isUserNameToLowerCase() ? username.toLowerCase(Locale.US) : username;
  }

  private Set<AccountGroup.UUID> parseGroups(List<String> groups)
      throws UnprocessableEntityException {
    Set<AccountGroup.UUID> groupUuids = new HashSet<>();
    if (groups != null) {
      for (String g : groups) {
        GroupDescription.Internal internalGroup = groupResolver.parseInternal(g);
        groupUuids.add(internalGroup.getGroupUUID());
      }
    }
    return groupUuids;
  }

  private void addGroupMember(AccountGroup.UUID groupUuid, Account.Id accountId)
      throws IOException, NoSuchGroupException, ConfigInvalidException {
    GroupDelta groupDelta =
        GroupDelta.builder()
            .setMemberModification(memberIds -> Sets.union(memberIds, ImmutableSet.of(accountId)))
            .build();
    groupsUpdate.get().updateGroup(groupUuid, groupDelta);
  }
}
