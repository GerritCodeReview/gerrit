package com.google.gerrit.server.notedb;

import static com.google.gerrit.reviewdb.client.RefNames.changeMetaRef;

import com.google.gerrit.reviewdb.client.BranchNameKey;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;

public class ChangeNotesStatesLoader extends AbstractChangeNotes<ChangeNotesStatesLoader> {

  @Singleton
  public static class Factory {
    private final Args args;

    @Inject
    public Factory(Args args) {
      this.args = args;
    }

    public ChangeNotesStatesLoader createChecked(Change c) {
      return createChecked(c.getProject(), c.getId());
    }

    public ChangeNotesStatesLoader createChecked(Project.NameKey project, Change.Id changeId) {
      Change change = newChange(project, changeId);
      return new ChangeNotesStatesLoader(args, change).load();
    }

    public static Change newChange(Project.NameKey project, Change.Id changeId) {
      return new Change(
          null, changeId, null, BranchNameKey.create(project, "INVALID_NOTE_DB_ONLY"), null);
    }
  }

  private final Change change;
  private ChangeNotesState state;

  public ChangeNotesStatesLoader(Args args, Change change) {
    super(args, change.getId());
    this.change = change;
  }

  @Override
  public String getRefName() {
    return changeMetaRef(getChangeId());
  }

  @Override
  protected void loadDefaults() {
    state = ChangeNotesState.empty(change);
  }

  @Override
  public NameKey getProjectName() {
    return change.getProject();
  }

  @Override
  protected void onLoad(LoadHandle handle)
      throws NoSuchChangeException, IOException, ConfigInvalidException {
    ObjectId rev = handle.id();
    if (rev == null) {
      throw new NoSuchChangeException(getChangeId());
    }

    ChangeNotesCache.Value v =
        args.cache.get().get(getProjectName(), getChangeId(), rev, handle::walk);
    state = v.state();
  }

  public ChangeNotesState state() {
    return state;
  }
}
