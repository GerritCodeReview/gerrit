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

package com.google.gerrit.server.query.change;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.index.query.PostFilterPredicate;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData.StorageConstraint;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;

public class EqualsLabelPredicates {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class PostFilterEqualsLabelPredicate extends PostFilterPredicate<ChangeData> {
    private final Matcher matcher;

    public PostFilterEqualsLabelPredicate(
        LabelPredicate.Args args, String label, int expVal, @Nullable Integer count) {
      super(ChangeQueryBuilder.FIELD_LABEL, ChangeField.formatLabel(label, expVal, count));
      matcher = new Matcher(args, label, expVal, count);
    }

    @Override
    public boolean match(ChangeData object) {
      return matcher.match(object);
    }

    @Override
    public int getCost() {
      return 2;
    }
  }

  public static class IndexEqualsLabelPredicate extends ChangeIndexPostFilterPredicate {
    private final Matcher matcher;

    public IndexEqualsLabelPredicate(
        LabelPredicate.Args args, String label, int expVal, @Nullable Integer count) {
      this(args, label, expVal, null, count);
    }

    public IndexEqualsLabelPredicate(
        LabelPredicate.Args args,
        String label,
        int expVal,
        @Nullable Account.Id account,
        @Nullable Integer count) {
      super(ChangeField.LABEL_SPEC, ChangeField.formatLabel(label, expVal, account, count));
      this.matcher = new Matcher(args, label, expVal, account, count);
    }

    @Override
    public boolean match(ChangeData object) {
      return matcher.match(object);
    }

    @Override
    public int getCost() {
      return 1 + (matcher.group == null ? 0 : 1);
    }
  }

  private static class Matcher {
    protected final AccountResolver accountResolver;
    protected final ProjectCache projectCache;
    protected final PermissionBackend permissionBackend;
    protected final IdentifiedUser.GenericFactory userFactory;

    /** label name to be matched. */
    protected final String label;

    /** Expected vote value for the label. */
    protected final int expVal;

    /**
     * Number of times the value {@link #expVal} for label {@link #label} should occur. If null,
     * match with any count greater or equal to 1.
     */
    @Nullable protected final Integer count;

    /** Account ID that has voted on the label. */
    @Nullable protected final Account.Id account;

    protected final AccountGroup.UUID group;

    public Matcher(LabelPredicate.Args args, String label, int expVal, @Nullable Integer count) {
      this(args, label, expVal, null, count);
    }

    public Matcher(
        LabelPredicate.Args args,
        String label,
        int expVal,
        @Nullable Account.Id account,
        @Nullable Integer count) {
      this.permissionBackend = args.permissionBackend;
      this.accountResolver = args.accountResolver;
      this.projectCache = args.projectCache;
      this.userFactory = args.userFactory;
      this.group = args.group;
      this.label = label;
      this.expVal = expVal;
      this.account = account;
      this.count = count;
    }

    public boolean match(ChangeData cd) {
      Change c = cd.change();
      if (c == null) {
        // The change has disappeared.
        logger.atFine().log(
            "%s=%s doesn't match because the change has disappeared.", label, expVal);
        return false;
      }

      if (Integer.valueOf(0).equals(count)) {
        // We don't match against count=0 so that the computation is identical to the stored values
        // in the index. We do that since computing count=0 requires looping on all {label_type,
        // vote_value} for the change and storing a {count=0} format for it in the change index
        // which is computationally expensive.
        logger.atFine().log(
            "%s=%s doesn't match change %s because the count was specified as 0 which is not"
                + " supported.",
            label, expVal, cd.change().getChangeId());
        return false;
      }

      Optional<ProjectState> project = projectCache.get(c.getDest().project());
      if (!project.isPresent()) {
        // The project has disappeared.
        logger.atFine().log(
            "%s=%s doesn't match change %s because its project %s has disappeared.",
            label, expVal, cd.change().getChangeId(), c.getDest().project().get());
        return false;
      }

      LabelType labelType = type(project.get().getLabelTypes(), label);
      if (labelType == null) {
        logger.atFine().log(
            "%s=%s doesn't match change %s because the label is not defined by its project %s"
                + " (label type = %s)",
            label, expVal, cd.change().getChangeId(), project.get(), project.get().getLabelTypes());
        return false; // Label is not defined by this project.
      }

      boolean hasVote = false;
      int matchingVotes = 0;
      StorageConstraint currentStorageConstraint = cd.getStorageConstraint();
      cd.setStorageConstraint(ChangeData.StorageConstraint.INDEX_PRIMARY_NOTEDB_SECONDARY);
      for (PatchSetApproval psa : cd.currentApprovals()) {
        if (labelType.matches(psa)) {
          hasVote = true;
          if (match(cd, psa)) {
            matchingVotes += 1;
          }
        }
      }
      logger.atFine().log(
          "found %s matching votes for %s=%s on change %s (current approvals = %s)",
          matchingVotes, label, expVal, cd.change().getChangeId(), cd.currentApprovals());
      cd.setStorageConstraint(currentStorageConstraint);
      if (!hasVote && expVal == 0) {
        logger.atFine().log(
            "%s=%s matches change %s because there is no vote for label %s",
            label, expVal, cd.change().getChangeId(), label);
        return true;
      }

      if (count == null) {
        if (matchingVotes >= 1) {
          logger.atFine().log(
              "%s=%s matches change %s because there are %s matching votes (count was not"
                  + " specified, hence 1 or more votes are needed)",
              label, expVal, cd.change().getChangeId(), matchingVotes);
          return true;
        }
        logger.atFine().log(
            "%s=%s doesn't match change %s because there are no matching votes (count was not"
                + " specified, hence 1 or more votes are needed)",
            label, expVal, cd.change().getChangeId());
        return false;
      }

      if (matchingVotes == count) {
        logger.atFine().log(
            "%s=%s matches change %s because there are %s matching votes which matches the"
                + " expected count %s",
            label, expVal, cd.change().getChangeId(), matchingVotes, count);
        return true;
      }
      logger.atFine().log(
          "%s=%s doesn't match change %s because there are %s matching votes which doesn't match"
              + " the expected count %s",
          label, expVal, cd.change().getChangeId(), matchingVotes, count);
      return false;
    }

    private boolean match(ChangeData cd, PatchSetApproval psa) {
      if (psa.value() != expVal) {
        logger.atFine().log(
            "vote %s on change %s doesn't match expected value %s",
            psa, cd.change().getChangeId(), expVal);
        return false;
      }
      Account.Id approver = psa.accountId();

      if (account != null) {
        // case when account in query is numeric
        if (!account.equals(approver) && !isMagicUser()) {
          logger.atFine().log(
              "vote %s on change %s doesn't match expected approver %s",
              psa, cd.change().getChangeId(), account);
          return false;
        }

        // case when account in query = owner
        if (account.equals(ChangeQueryBuilder.OWNER_ACCOUNT_ID)
            && !cd.change().getOwner().equals(approver)) {
          logger.atFine().log(
              "vote %s on change %s doesn't match since it is not from the change owner %s",
              psa, cd.change().getChangeId(), cd.change().getOwner());
          return false;
        }

        // case when account in query = non_uploader
        if (account.equals(ChangeQueryBuilder.NON_UPLOADER_ACCOUNT_ID)
            && cd.currentPatchSet().uploader().equals(approver)) {
          logger.atFine().log(
              "vote %s on change %s doesn't match since it is not from the uploader %s",
              psa, cd.change().getChangeId(), cd.currentPatchSet().uploader());
          return false;
        }

        if (account.equals(ChangeQueryBuilder.NON_CONTRIBUTOR_ACCOUNT_ID)) {
          if ((cd.currentPatchSet().uploader().equals(approver)
              || matchAccount(cd.getCommitter().getEmailAddress(), approver)
              || matchAccount(cd.getAuthor().getEmailAddress(), approver))) {
            logger.atFine().log(
                "vote %s on change %s doesn't match since it is not from a contributor"
                    + " (uploader: %s, committer: %s, author: %s)",
                psa,
                cd.change().getChangeId(),
                cd.currentPatchSet().uploader(),
                cd.getCommitter().getEmailAddress(),
                cd.getAuthor().getEmailAddress());
            return false;
          }
        }
      }

      IdentifiedUser reviewer = userFactory.create(approver);
      if (group != null && !reviewer.getEffectiveGroups().contains(group)) {
        logger.atFine().log(
            "vote %s on change %s doesn't match since the approver %s is not a member of the"
                + " expected group %s",
            psa, cd.change().getChangeId(), approver, group);
        return false;
      }

      // Check the user has 'READ' permission.
      try {
        PermissionBackend.ForChange perm = permissionBackend.absentUser(approver).change(cd);
        if (!projectCache.get(cd.project()).map(ProjectState::statePermitsRead).orElse(false)) {
          logger.atFine().log(
              "vote %s on change %s doesn't match since the project %s doesn't permit read",
              psa, cd.change().getChangeId(), cd.project().get());
          return false;
        }

        perm.check(ChangePermission.READ);
        logger.atFine().log("vote %s on change %s matches", psa, cd.change().getChangeId());
        return true;
      } catch (PermissionBackendException | AuthException e) {
        logger.atFine().log(
            "vote %s on change %s doesn't match because the approver %s has no read access",
            psa, cd.change().getChangeId(), approver);
        return false;
      }
    }

    /**
     * Returns true if the {@code email} parameter belongs to the account identified by the {@code
     * accountId} parameter.
     */
    private boolean matchAccount(String email, Account.Id accountId) {
      try {
        ImmutableList<AccountState> accountsList = accountResolver.resolve(email).asList();
        return accountsList.stream().anyMatch(c -> c.account().id().equals(accountId));
      } catch (ConfigInvalidException | IOException e) {
        logger.atWarning().withCause(e).log("Failed to resolve account %s", email);
      }
      return false;
    }

    private boolean isMagicUser() {
      return account != null
          && (account.equals(ChangeQueryBuilder.OWNER_ACCOUNT_ID)
              || account.equals(ChangeQueryBuilder.NON_UPLOADER_ACCOUNT_ID)
              || account.equals(ChangeQueryBuilder.NON_CONTRIBUTOR_ACCOUNT_ID));
    }
  }

  @Nullable
  public static LabelType type(LabelTypes types, String toFind) {
    if (types.byLabel(toFind).isPresent()) {
      return types.byLabel(toFind).get();
    }

    for (LabelType lt : types.getLabelTypes()) {
      if (toFind.equalsIgnoreCase(lt.getName())) {
        return lt;
      }
    }
    return null;
  }
}
