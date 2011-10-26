package com.google.gerrit.sshd.commands;

import com.google.gerrit.common.data.GroupMemberResult;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.server.account.AddGroupInclude;
import com.google.gerrit.server.account.AddGroupMember;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.account.RemoveGroupInclude;
import com.google.gerrit.server.account.RemoveGroupMember;
import com.google.gerrit.sshd.BaseCommand;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

public class SetMembersCommand extends BaseCommand {
  private static final Logger log =
    LoggerFactory.getLogger(SetMembersCommand.class);

  @Option(name = "--add", aliases = {"-a"}, metaVar = "USER", usage = "user that should be added as group member")
  private List<Account.Id> accountsToAdd = new ArrayList<Account.Id>();

  @Option(name = "--remove", aliases = {"-r"}, metaVar = "USER", usage = "user that should be removed from the group")
  private List<Account.Id> accountsToRemove = new ArrayList<Account.Id>();

  @Option(name = "--include", aliases = {"-i"}, metaVar = "GROUP", usage = "group that should be included as group member")
  private List<AccountGroup.Id> groupsToInclude = new ArrayList<AccountGroup.Id>();

  @Option(name = "--exclude", aliases = {"-e"}, metaVar = "GROUP", usage = "group that should be excluded from the group")
  private List<AccountGroup.Id> groupsToExclude = new ArrayList<AccountGroup.Id>();

  @Argument(index = 0, required = true, multiValued = true, metaVar = "GROUP", usage = "groups to modify")
  private List<AccountGroup.Id> groups = new ArrayList<AccountGroup.Id>();

  @Inject
  private GroupControl.Factory groupControlFactory;

  @Inject
  private AddGroupMember.Factory addGroupMemberFactory;

  @Inject
  private RemoveGroupMember.Factory removeGroupMemberFactory;

  @Inject
  private AddGroupInclude.Factory addGroupIncludeFactory;

  @Inject
  private RemoveGroupInclude.Factory removeGroupIncludeFactory;

  @Override
  public void start(final Environment env) throws IOException {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Failure {
        parseCommandLine();

        boolean ok = true;
        for (final AccountGroup.Id groupId : groups) {
          try {
            ok &= modifyOne(groupId);
          } catch (Exception err) {
            ok = false;
            log.error("Error updating members of group " + groupId, err);
            writeError("fatal", "internal error while updating " + groupId);
          }
        }

        if (!ok) {
          throw die("one or more updates failed; review output above");
        }
      }
    });
  }

  private boolean modifyOne(final AccountGroup.Id groupId) throws Exception {
    groupControlFactory.validateFor(groupId);

    GroupMemberResult result;
    boolean ok = true;

    // Remove group members
    //
    result = removeGroupMemberFactory.create(groupId, accountsToRemove).call();
    ok &= processResultOfRemoval(groupId, result);

    // Remove included groups
    //
    result = removeGroupIncludeFactory.create(groupId, groupsToExclude).call();
    ok &= processResultOfRemoval(groupId, result);

    // Add group members
    //
    result = addGroupMemberFactory.create(groupId, accountsToAdd).call();
    ok &= processResultOfAddition(groupId, result);

    // Add included groups
    //
    result = addGroupIncludeFactory.create(groupId, groupsToInclude).call();
    ok &= processResultOfAddition(groupId, result);

    return ok;
  }

  private boolean processResultOfRemoval(final AccountGroup.Id groupId,
      final GroupMemberResult result) {
    final boolean ok = result.getErrors().isEmpty();
    for (final GroupMemberResult.Error resultError : result.getErrors()) {
      String message;
      switch (resultError.getType()) {
        case REMOVE_NOT_PERMITTED:
          message = "not permitted to remove {0} from {1}";
          break;
        default:
          message = "could not remove {0} from {1}: {2}";
      }
      writeError("error", MessageFormat.format(message, resultError.getName(),
          groupId, resultError.getType()));
    }
    return ok;
  }

  private boolean processResultOfAddition(final AccountGroup.Id groupId,
      final GroupMemberResult result) {
    final boolean ok = result.getErrors().isEmpty();
    for (GroupMemberResult.Error resultError : result.getErrors()) {
      String message;
      switch (resultError.getType()) {
        case ADD_NOT_PERMITTED:
          message = "not permitted to add {0} to {1}";
          break;
        case ACCOUNT_INACTIVE:
          message = "account {0} inactive";
          break;
        default:
          message = "could not add {0} to {1}: {2}";
      }
      writeError("error", MessageFormat.format(message,
          resultError.getName(), groupId, resultError.getType()));
    }
    return ok;
  }

  private void writeError(String type, String msg) {
    try {
      err.write((type + ": " + msg + "\n").getBytes(ENC));
    } catch (IOException e) {
    }
  }
}
