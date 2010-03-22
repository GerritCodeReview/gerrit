package com.google.gerrit.server.patch;

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ReceiveCommits;
import com.google.gwtorm.client.OrmException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DepsBypasser {

  private class Module {
    private final String name;
    private final String sha1;
    private Change.Id changeId;

    private Module() {
      name = null;
      sha1 = null;
    }

    private Module(final String n, final String s) {
      name = n;
      sha1 = s;
    }
  }

  private GitRepositoryManager repoManager;
  private Module superModule;
  private List<Module> subModules;

  // singleton - use getInstance instead
  private DepsBypasser() {
    superModule = new Module();
  }

  private static DepsBypasser instance = null;

  public static DepsBypasser getInstance() {
    if (instance == null) instance = new DepsBypasser();
    return instance;
  }

  public static DepsBypasser getInstance(final GitRepositoryManager grm) {
    if (instance == null) instance = new DepsBypasser();
    instance.repoManager = grm;
    return instance;
  }

  public String getSuperModuleName() {
    return superModule.name;
  }

  public Change.Id getSuperModuleChangeId() {
    return superModule.changeId;
  }

  public void setSuperModule(final String n, final String s) {
    this.superModule = new Module(n, s);
  }

  public void addSubModule(final String n, final String s) {
    if (subModules == null) this.subModules = new ArrayList<Module>();
    this.subModules.add(new Module(n, s));
  }

  public void reset() {
    this.superModule = new Module();
    this.subModules = null;
  }

  private Change.Id getChangeId(Module m, final ReviewDb db) {
    Change.Id id = null;
    String changeId = "I" + m.sha1;
    Repository repo = null;

    try {
      repo = repoManager.openRepository(m.name);
      final RevWalk rw = new RevWalk(repo);
      final RevCommit rc = rw.parseCommit(ObjectId.fromString(m.sha1));
      final List<String> idList = rc.getFooterLines(ReceiveCommits.CHANGE_ID);

      if (!idList.isEmpty()) {
        changeId = idList.get(idList.size() - 1).trim();
      }
    } catch (RepositoryNotFoundException e) {
      // TODO handle, ignore or throw exception ?
    } catch (MissingObjectException e) {
      // TODO handle, ignore or throw exception ?
    } catch (IncorrectObjectTypeException e) {
      // TODO handle, ignore or throw exception ?
    } catch (IOException e) {
      // TODO handle, ignore or throw exception ?
    } finally {
      if (repo != null) repo.close();
    }

    try {
      List<Change> changes =
          db.changes().byKey(Change.Key.parse(changeId)).toList();

      if (!changes.isEmpty()) {
        // get the latest change for the required sha1, since one sha1 might
        // have multiple changes
        if (changes.size() > 1) {
          Collections.sort(changes, new Comparator<Change>() {
            public int compare(Change ch1, Change ch2) {
              return ch1.getCreatedOn().compareTo(ch2.getCreatedOn());
            }
          });
        }
        id = changes.get(changes.size() - 1).getId();
      }
    } catch (OrmException e) {
      // TODO handle, ignore or throw exception ?
    }

    return id;
  }

  public void validate(final ReviewDb db) {
    if (superModule.changeId == null && superModule.name != null) {
      superModule.changeId = getChangeId(superModule, db);

      if (superModule.changeId != null) {
        for (Module m : subModules) {
          m.changeId = getChangeId(m, db);
        }
      }
    }
  }

  public boolean isAtomicCommit() {
    return superModule.changeId != null && subModules != null
        && subModules.size() > 1;
  }

  public List<Change.Id> getAtomicMembers() {
    List<Change.Id> members = new ArrayList<Change.Id>();
    if (isAtomicCommit()) {
      members.add(superModule.changeId);
      for (Module m : subModules) {
        members.add(m.changeId);
      }
    }
    return members;
  }
}
