// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.change.reviewer;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.mail.Address;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.BooleanProjectConfig;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.AccountResolver.InputFormat;
import com.google.gerrit.server.account.AccountResolver.UnresolvableAccountException;
import com.google.gerrit.server.account.GroupMembers;
import com.google.gerrit.server.change.ChangeMessages;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.group.GroupResolver;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.mail.send.OutgoingEmailValidator;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;

@Singleton
public class ReviewerAdder {
  public static final int DEFAULT_MAX_REVIEWERS = 20;
  public static final int DEFAULT_MAX_REVIEWERS_WITHOUT_CHECK = 10;

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static Module module() {
    return new FactoryModule() {
      @Override
      public void configure() {
        factory(AddReviewersEmailOp.Factory.class);
        factory(ReviewerAddition.Factory.class);
      }
    };
  }

  enum FailureStrategy {
    FAIL,
    IGNORE
  }

  @AutoValue
  public abstract static class Options {
    private static Builder builder() {
      return new AutoValue_ReviewerAdder_Options.Builder();
    }

    public static Options forRestApi() {
      return builder()
          .ignoreIfChangeOwner(false)
          .allowGroup(true)
          .allowByEmail(true)
          .failureStrategy(FailureStrategy.FAIL)
          .build();
    }

    public static Options forPush() {
      // TODO(dborowitz): Warn instead. Probably in both forPush and forRestApi.
      return builder()
          .ignoreIfChangeOwner(true)
          .allowGroup(true)
          .allowByEmail(true)
          .failureStrategy(FailureStrategy.FAIL)
          .build();
    }

    public static Options forAutoAddingGitIdentity() {
      return builder()
          .ignoreIfChangeOwner(true)
          .allowGroup(false)
          .allowByEmail(false)
          .failureStrategy(FailureStrategy.IGNORE)
          .build();
    }

    public static Options forAutoAddingUsersFromOtherChange() {
      return builder()
          .ignoreIfChangeOwner(true)
          .allowGroup(false)
          .allowByEmail(false)
          .failureStrategy(FailureStrategy.IGNORE)
          .build();
    }

    abstract boolean ignoreIfChangeOwner();

    abstract boolean allowGroup();

    /**
     * Whether the calling codepath allows adding reviewers-by-email.
     *
     * <p>This is not sufficient to actually add reviewers by email; the project must also be
     * configured to allow it, and the change must be visible to anonymous users.
     */
    abstract boolean allowByEmail();

    abstract FailureStrategy failureStrategy();

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder ignoreIfChangeOwner(boolean ignoreIfChangeOwner);

      abstract Builder allowGroup(boolean allowGroup);

      abstract Builder allowByEmail(boolean allowByEmail);

      abstract Builder failureStrategy(FailureStrategy failureStrategy);

      abstract Options build();
    }
  }

  @AutoValue
  public abstract static class Input {
    public static Input forAccount(Account.Id id, ReviewerStateInternal state, Options options) {
      return new AutoValue_ReviewerAdder_Input(
          id.toString(), InputFormat.ACCOUNT_ID, state, false, options);
    }

    public static Input fromJson(AddReviewerInput jsonInput, Options options) {
      return new AutoValue_ReviewerAdder_Input(
          jsonInput.reviewer,
          InputFormat.ANY,
          ReviewerStateInternal.fromReviewerState(jsonInput.state()),
          firstNonNull(jsonInput.confirmed, false),
          options);
    }

    public static Input fromUserInput(String input, ReviewerStateInternal state, Options options) {
      return new AutoValue_ReviewerAdder_Input(input, InputFormat.ANY, state, false, options);
    }

    public static Input fromPersonIdent(
        PersonIdent ident, ReviewerStateInternal state, Options options) {
      return new AutoValue_ReviewerAdder_Input(
          ident.toExternalString(), InputFormat.NAME_OR_EMAIL, state, false, options);
    }

    /**
     * Input string, possibly end-user-provided.
     *
     * <p>The total set of all possible formats for individual accounts is defined by {@link
     * com.google.gerrit.server.account.AccountResolver}. Which subset should be considered for this
     * operation is specified via {@link #format()}.
     *
     * <p>If {@link Options#allowByEmail()} is true and {@link #format()} is {@code ANY}, then the
     * input string may also be a group name.
     *
     * <p>Null is not a valid input, but it is allowed in this method because the behavior of {@link
     * #prepare(ChangeNotes, ImmutableList)} in the face of null is determined by {@link
     * Options#failureStrategy()}}.
     */
    @Nullable
    public abstract String input();

    abstract InputFormat format();

    abstract ReviewerStateInternal state();

    abstract boolean confirmedLargeGroup();

    abstract Options options();
  }

  @AutoValue
  public abstract static class Result {
    static Builder builder(Input input) {
      return new AutoValue_ReviewerAdder_Result.Builder()
          .input(input)
          .confirm(false)
          .reviewers(ImmutableList.of())
          .reviewersByEmail(ImmutableList.of())
          .ccs(ImmutableList.of())
          .ccsByEmail(ImmutableList.of());
    }

    public abstract Input input();

    public abstract Optional<String> error();

    public abstract boolean confirm();

    public boolean errorOrConfirm() {
      return error().isPresent() || confirm();
    }

    public boolean isEmpty() {
      return reviewers().isEmpty()
          && reviewersByEmail().isEmpty()
          && ccs().isEmpty()
          && ccsByEmail().isEmpty();
    }

    public abstract ImmutableList<Account.Id> reviewers();

    public abstract ImmutableList<Address> reviewersByEmail();

    public abstract ImmutableList<Account.Id> ccs();

    public abstract ImmutableList<Address> ccsByEmail();

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder input(Input input);

      abstract Input input();

      abstract Builder error(Optional<String> error);

      abstract Optional<String> error();

      abstract Builder confirm(boolean confirm);

      abstract boolean confirm();

      abstract Builder reviewers(Iterable<Account.Id> reviewers);

      abstract ImmutableList<Account.Id> reviewers();

      abstract Builder reviewersByEmail(Iterable<Address> reviewersByEmail);

      abstract ImmutableList<Address> reviewersByEmail();

      abstract Builder ccs(Iterable<Account.Id> ccs);

      abstract ImmutableList<Account.Id> ccs();

      abstract Builder ccsByEmail(Iterable<Address> ccsByEmail);

      abstract ImmutableList<Address> ccsByEmail();

      abstract Result build();

      private Builder set(ReviewerStateInternal state, ImmutableList<Account.Id> values) {
        switch (state) {
          case REVIEWER:
            return reviewers(values);
          case CC:
            return ccs(values);
          default:
            throw new IllegalStateException("invalid state: " + state);
        }
      }

      private Builder setByEmail(ReviewerStateInternal state, Address value) {
        switch (state) {
          case REVIEWER:
            return reviewersByEmail(ImmutableList.of(value));
          case CC:
            return ccsByEmail(ImmutableList.of(value));
          default:
            throw new IllegalStateException("invalid state: " + state);
        }
      }

      private Builder appendError(String error) {
        if (!error().isPresent()) {
          return error(Optional.of(error));
        }
        return error(error().map(e -> e + "\n" + error));
      }

      private Builder clearError() {
        return error(Optional.empty());
      }

      boolean errorOrConfirm() {
        return error().isPresent() || confirm();
      }

      boolean isEmpty() {
        return reviewers().isEmpty()
            && reviewersByEmail().isEmpty()
            && ccs().isEmpty()
            && ccsByEmail().isEmpty();
      }
    }
  }

  private final AccountResolver accountResolver;
  private final Config cfg;
  private final GroupMembers groupMembers;
  private final GroupResolver groupResolver;
  private final OutgoingEmailValidator validator;
  private final PermissionBackend permissionBackend;
  private final ProjectCache projectCache;
  private final Provider<AnonymousUser> anonymousProvider;
  private final ReviewerAddition.Factory additionFactory;

  @Inject
  ReviewerAdder(
      AccountResolver accountResolver,
      @GerritServerConfig Config cfg,
      GroupMembers groupMembers,
      GroupResolver groupResolver,
      OutgoingEmailValidator validator,
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      Provider<AnonymousUser> anonymousProvider,
      ReviewerAddition.Factory additionFactory) {
    this.accountResolver = accountResolver;
    this.cfg = cfg;
    this.groupMembers = groupMembers;
    this.groupResolver = groupResolver;
    this.validator = validator;
    this.permissionBackend = permissionBackend;
    this.anonymousProvider = anonymousProvider;
    this.projectCache = projectCache;
    this.additionFactory = additionFactory;
  }

  public ReviewerAddition prepare(ChangeNotes notes, ImmutableList<Input> inputs)
      throws NoSuchProjectException, ConfigInvalidException, IOException,
          PermissionBackendException {
    ImmutableList.Builder<Result.Builder> b = ImmutableList.builderWithExpectedSize(inputs.size());
    for (Input input : inputs) {
      Result.Builder resultBuilder = Result.builder(input);
      prepareImpl(notes, resultBuilder);
      if (resultBuilder.error().isPresent()
          && input.options().failureStrategy() == FailureStrategy.IGNORE) {
        resultBuilder
            .clearError()
            .reviewers(ImmutableList.of())
            .reviewersByEmail(ImmutableList.of())
            .ccs(ImmutableList.of())
            .ccsByEmail(ImmutableList.of());
      }
      b.add(resultBuilder);
    }
    return additionFactory.create(b.build());
  }

  private void prepareImpl(ChangeNotes notes, Result.Builder resultBuilder)
      throws NoSuchProjectException, ConfigInvalidException, IOException,
          PermissionBackendException {
    // General implementation notes:
    // * Each resolveAs* step returns true if it should short-circuit, false otherwise.
    // * Short-circuiting may occur either if there is a result, or if there is an error.
    // * If multiple steps fail, they may append multiple errors together.
    // * Thus a previous step fails and a subsequent one succeeds, it needs to clear any errors from
    //   previous steps.
    // * If failureStrategy == IGNORE, then the result is post-processed above to clear the error
    //   as well as any intermediate results.
    Options opts = resultBuilder.input().options();
    try {
      if (Strings.isNullOrEmpty(resultBuilder.input().input())) {
        resultBuilder.appendError("Empty input provided for reviewer");
        return;
      }
      if (resolveAsAccount(notes, resultBuilder)) {
        return;
      }
      if (resolveAsGroup(notes, resultBuilder)) {
        return;
      }
      if (resolveAsEmail(notes, resultBuilder)) {
        return;
      }
      checkState(resultBuilder.errorOrConfirm(), "no error set after resolution failed");
    } catch (ConfigInvalidException
        | StorageException
        | IOException
        | NoSuchProjectException
        | PermissionBackendException e) {
      if (opts.failureStrategy() == FailureStrategy.FAIL) {
        throw e;
      }
      logger.atWarning().withCause(e).log("Error resolving reviewer: " + resultBuilder.input());
    }
  }

  private boolean resolveAsAccount(ChangeNotes notes, Result.Builder resultBuilder)
      throws ConfigInvalidException, IOException, PermissionBackendException {
    Input input = resultBuilder.input();
    Account account;
    try {
      AccountResolver.Result resolved = accountResolver.resolve(input.input(), input.format());
      account = resolved.asUnique().getAccount();
    } catch (UnresolvableAccountException e) {
      resultBuilder.appendError(e.getMessage());
      if (e.isEmpty()) {
        // No account: fall through to next step,
        return false;
      }
      // Ambiguous account or illegal input: short-circuit.
      return true;
    }

    if (!reviewerCanSeeChange(notes.getChange().getDest(), account)) {
      resultBuilder.appendError(
          MessageFormat.format(ChangeMessages.get().reviewerCantSeeChange, input.input()));
      return true;
    }

    resultBuilder.clearError().set(input.state(), ImmutableList.of(account.getId()));
    return true;
  }

  private boolean resolveAsGroup(ChangeNotes notes, Result.Builder resultBuilder)
      throws NoSuchProjectException, IOException, PermissionBackendException {
    Input input = resultBuilder.input();
    if (!input.options().allowGroup()) {
      return false;
    }

    GroupDescription.Basic group;
    try {
      // TODO(dborowitz): This currently doesn't work in the push path because InternalGroupBackend
      // depends on the Provider<CurrentUser> which returns anonymous in that path.
      group = groupResolver.parseInternal(resultBuilder.input().input());
    } catch (UnprocessableEntityException e) {
      resultBuilder.appendError(e.getMessage());
      // Not a group: fall through to next step.
      return false;
    }

    if (!isLegalReviewerGroup(group.getGroupUUID())) {
      resultBuilder.appendError(
          MessageFormat.format(ChangeMessages.get().groupIsNotAllowed, input.input()));
      return true;
    }
    Set<Account> members = groupMembers.listAccounts(group.getGroupUUID(), notes.getProjectName());

    // If maxAllowed is set to 0, it is allowed to add any number of reviewers.
    int maxAllowed = cfg.getInt("addreviewer", "maxAllowed", DEFAULT_MAX_REVIEWERS);
    if (maxAllowed > 0 && members.size() > maxAllowed) {
      logger.atFine().log(
          "Adding %d group members is not allowed (maxAllowed = %d)", members.size(), maxAllowed);
      resultBuilder.appendError(
          MessageFormat.format(ChangeMessages.get().groupHasTooManyMembers, group.getName()));
      return true;
    }

    // If maxWithoutCheck is set to 0, we never ask for confirmation.
    int maxWithoutConfirmation =
        cfg.getInt("addreviewer", "maxWithoutConfirmation", DEFAULT_MAX_REVIEWERS_WITHOUT_CHECK);
    if (!input.confirmedLargeGroup()
        && maxWithoutConfirmation > 0
        && members.size() > maxWithoutConfirmation) {
      logger.atFine().log(
          "Adding %d group members as reviewer requires confirmation (maxWithoutConfirmation = %d)",
          members.size(), maxWithoutConfirmation);
      resultBuilder
          .confirm(true)
          .appendError(
              MessageFormat.format(
                  ChangeMessages.get().groupManyMembersConfirmation,
                  group.getName(),
                  members.size()));
      return true;
    }

    ImmutableList.Builder<Account.Id> reviewers =
        ImmutableList.builderWithExpectedSize(members.size());
    for (Account member : members) {
      if (reviewerCanSeeChange(notes.getChange().getDest(), member)) {
        reviewers.add(member.getId());
      }
    }
    // TODO(dborowitz): Corner case where no group member can see change (wasn't handled by old impl
    // either).
    resultBuilder.clearError().set(input.state(), reviewers.build());
    return true;
  }

  private boolean reviewerCanSeeChange(Branch.NameKey branch, Account member)
      throws PermissionBackendException {
    try {
      // Check ref permission instead of change permission, since change permissions take into
      // account the private bit, whereas adding a user as a reviewer is explicitly allowing them to
      // see private changes.
      permissionBackend.absentUser(member.getId()).ref(branch).check(RefPermission.READ);
      return true;
    } catch (AuthException e) {
      return false;
    }
  }

  private boolean resolveAsEmail(ChangeNotes notes, Result.Builder resultBuilder)
      throws IOException, PermissionBackendException {
    Input input = resultBuilder.input();
    if (!input.options().allowByEmail()) {
      return false;
    }
    if (!projectCache
        .checkedGet(notes.getProjectName())
        .is(BooleanProjectConfig.ENABLE_REVIEWER_BY_EMAIL)) {
      resultBuilder.appendError("Adding reviewers by email is not enabled on this project");
      return false;
    }

    Address addr = Address.tryParse(input.input());
    if (addr == null) {
      resultBuilder.appendError(
          "'" + input.input() + "' does not appear to contain an email address");
      return false;
    }
    if (!validator.isValid(addr.getEmail())) {
      resultBuilder.appendError("'" + addr.getEmail() + "' is not a valid email address");
      return false;
    }

    try {
      permissionBackend.user(anonymousProvider.get()).change(notes).check(ChangePermission.READ);
    } catch (AuthException e) {
      resultBuilder.appendError(
          "Reviewers may not be added by email because the change is not visible to anonymous"
              + " users");
      return true;
    }

    resultBuilder.clearError().setByEmail(input.state(), addr);
    return true;
  }

  public static boolean isLegalReviewerGroup(AccountGroup.UUID groupUUID) {
    return !SystemGroupBackend.isSystemGroup(groupUUID);
  }
}
