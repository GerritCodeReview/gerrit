package com.google.gerrit.sshd.commands;

import com.google.common.base.Objects;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountInfo;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.group.ListMembers;
import com.google.gerrit.server.ioutil.ColumnFormatter;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;

import org.kohsuke.args4j.Option;

import java.io.PrintWriter;
import java.util.List;

import javax.inject.Inject;

@CommandMetaData(name = "ls-group-members", descr = "Lists the members of a given group")
public class ListGroupMembersCommand extends SshCommand {

  @Inject
  AccountCache accounts;

  @Inject
  ListMembers memberFetcher;

  @Inject
  GroupCache groupCache;

  @Option(required = true, name = "--name", usage = "the name of the group", metaVar = "GROUPNAME", aliases = {"-n"})
  String name;

  @Override
  protected void run() throws UnloggedFailure, Failure, Exception {
    parseCommandLine();
    AccountGroup group = groupCache.get(new AccountGroup.NameKey(name));
    PrintWriter writer = toPrintWriter(out);

    if (group == null) {
      writer.write("That group does not exist.\n");
      writer.flush();
      return;
    }

    try {
      List<AccountInfo> members = memberFetcher.apply(group.getGroupUUID());
      ColumnFormatter formatter = new ColumnFormatter(writer, '\t');
      formatter.addColumn("userid");
      formatter.addColumn("name");
      formatter.addColumn("email");
      formatter.nextLine();
      for (AccountInfo member : members) {
        if (member == null) {
          continue;
        }

        AccountState account = accounts.get(member._id);
        formatter.addColumn(Objects.firstNonNull(
            account != null ? account.getUserName() : null, "n/a"));
        formatter.addColumn(Objects.firstNonNull(member.name, "n/a"));
        formatter.addColumn(Objects.firstNonNull(member.email, "n/a"));
        formatter.nextLine();
      }

      formatter.finish();
    } catch (MethodNotAllowedException e) {
      writer.write("This group is not visible to you.\n");
      writer.flush();
    }
  }
}
