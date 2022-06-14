package com.google.gerrit.server.notedb;

import static com.google.gerrit.server.notedb.AbstractChangeUpdate.NO_OP_UPDATE;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.update.ChainedReceiveCommands;
import java.io.IOException;
import java.time.Instant;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

abstract public class ChangeUpdateSink {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  protected final PersonIdent serverIdent;
  protected final PersonIdent authorIdent;
  protected final Instant when;

  protected final ObjectId original;
  protected ObjectId curr;

  public ChangeUpdateSink(ObjectId curr) {
    this.curr = curr;
    this.serverIdent = null;
    this.authorIdent = null;
    this.when = null;
    this.original = null;
  }

  public String getRefName() {
    return "";
  }

  abstract protected CommitBuilder applyImpl(RevWalk rw, ObjectInserter ins) throws IOException;

  /** returns true if a new update is written. */
  boolean apply(RevWalk rw, ObjectInserter ins, Change.Id changeId, Project.NameKey project)
      throws IOException {
    logger.atFinest().log(
        "%s for change %s of project %s in %s (NoteDb)",
        getClass().getSimpleName(), changeId, project, getRefName());

    ObjectId z = ObjectId.zeroId();
    CommitBuilder cb = applyImpl(rw, ins);
    if (cb == null) {
      curr = z;
    } else if (cb == NO_OP_UPDATE) {
      return false; // Impl is a no-op.
    }
    cb.setAuthor(authorIdent);
    cb.setCommitter(new PersonIdent(serverIdent, when));
    setParentCommit(cb, curr);
    if (cb.getTreeId() == null) {
      if (curr.equals(z)) {
        cb.setTreeId(emptyTree(ins)); // No parent, assume empty tree.
      } else {
        RevCommit p = rw.parseCommit(curr);
        cb.setTreeId(p.getTree()); // Copy tree from parent.
      }
    }
    curr = ins.insert(cb);
    return true;
  }

  void appendRefUpdate(ChainedReceiveCommands cmds) {
    if (original != curr) {
      cmds.add(new ReceiveCommand(original, curr, getRefName()));
    }
  }

  protected void setParentCommit(CommitBuilder cb, ObjectId parentCommitId) {
    if (!parentCommitId.equals(ObjectId.zeroId())) {
      cb.setParentId(parentCommitId);
    } else {
      cb.setParentIds(); // Ref is currently nonexistent, commit has no parents.
    }
  }

  private static ObjectId emptyTree(ObjectInserter ins) throws IOException {
    return ins.insert(Constants.OBJ_TREE, new byte[] {});
  }
}
