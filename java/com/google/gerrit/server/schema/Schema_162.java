package com.google.gerrit.server.schema;

import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.RepoSequence;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.sql.SQLException;

/** Create group sequence in NoteDb */
public class Schema_162 extends SchemaVersion {
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;

  @Inject
  Schema_162(
      Provider<Schema_161> prior, GitRepositoryManager repoManager, AllUsersName allUsersName) {
    super(prior);
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
    @SuppressWarnings("deprecation")
    RepoSequence.Seed groupSeed = () -> db.nextAccountGroupId();
    RepoSequence groupSeq =
        new RepoSequence(
            repoManager,
            GitReferenceUpdated.DISABLED,
            allUsersName,
            Sequences.NAME_GROUPS,
            groupSeed,
            1);

    // consume one account ID to ensure that the account sequence is initialized in NoteDb
    groupSeq.next();
  }
}
