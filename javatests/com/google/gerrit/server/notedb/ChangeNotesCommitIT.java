package com.google.gerrit.server.notedb;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.api.change.ChangeIT;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.notedb.ChangeNotesCommit.ChangeNotesRevWalk;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;

public class ChangeNotesCommitIT extends ChangeIT {

  @After
  public void assertChangeNotes() throws RestApiException, IOException {
    List<ChangeInfo> allChanges = gApi.changes().query().get();
    // There is a case when inactive user is added to cc by email.
    List<AccountState> allAccounts =
        accounts.all().stream()
            .filter(u -> u.account().isActive())
            .collect(ImmutableList.toImmutableList());
    for (ChangeInfo change : allChanges) {
      try (Repository repo = repoManager.openRepository(Project.nameKey(change.project))) {
        String metaRefName =
            RefNames.changeMetaRef(Change.Id.tryParse(change._number.toString()).get());
        ObjectId currTip = repo.getRefDatabase().exactRef(metaRefName).getObjectId();
        ChangeNotesRevWalk revWalk = ChangeNotesCommit.newRevWalk(repo);

        revWalk.reset();
        revWalk.markStart(revWalk.parseCommit(currTip));
        ChangeNotesCommit commit;
        while ((commit = revWalk.next()) != null) {
          String fullMessage = commit.getFullMessage();
          for (AccountState accountState : allAccounts) {
            Account account = accountState.account();
            assertThat(fullMessage).doesNotContain(account.getName());
            if (account.fullName() != null) {
              assertThat(fullMessage).doesNotContain(account.fullName());
            }
            if (account.displayName() != null) {
              assertThat(fullMessage).doesNotContain(account.displayName());
            }
            if (account.preferredEmail() != null) {
              assertThat(fullMessage).doesNotContain(account.preferredEmail());
            }
            if (accountState.userName().isPresent()) {
              assertThat(fullMessage).doesNotContain(accountState.userName().get());
            }
            Stream<String> allEmails =
                accountState.externalIds().stream().map(ExternalId::email).filter(Objects::nonNull);
            allEmails.forEach(email -> assertThat(fullMessage).doesNotContain(email));
          }
        }
      }
    }
  }
}
