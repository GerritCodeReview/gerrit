package com.google.gerrit.server;

import static com.google.gerrit.server.Sequences.NAME_GROUPS;

import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.RepoSequence;
import com.google.gwtorm.server.OrmException;
import org.eclipse.jgit.lib.Config;

/** Like Sequences for groups, but without injection to facilitate usage in SchemaCreator. */
public class GroupSequence {
  private final boolean readGroupSeqFromNoteDb;
  private final ReviewDb db;
  private final RepoSequence groupSeq;

  public GroupSequence(
      ReviewDb db,
      boolean readFromNoteDb,
      AllUsersName allUsers,
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated) {
    this.db = db;
    readGroupSeqFromNoteDb = readFromNoteDb;
    RepoSequence.Seed groupSeed = () -> nextGroupId(db);

    int groupBatchSize = 1;
    groupSeq =
        new RepoSequence(
            repoManager, gitRefUpdated, allUsers, NAME_GROUPS, groupSeed, groupBatchSize);
  }

  public static boolean readSetting(Config cfg) {
    return cfg.getBoolean("noteDb", "groups", "readSequenceFromNoteDb", false);
  }

  public int nextGroupId() throws OrmException {
    if (readGroupSeqFromNoteDb) {
      // TODO - latency stats?
      return groupSeq.next();
    }
    int groupId = nextGroupId(db);
    groupSeq.increaseTo(groupId + 1); // NoteDb stores next available account ID.
    return groupId;
  }

  @SuppressWarnings("deprecation")
  static int nextGroupId(ReviewDb db) throws OrmException {
    return db.nextAccountGroupId();
  }
}
