package com.google.gerrit.server.patch;

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gwtorm.client.OrmException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DepsBypasser {

  private String superSha1;
  private Change.Id superChange;
  private List<String> subSha1s;
  private List<Change.Id> subChanges;

  // singleton - use getInstance instead
  private DepsBypasser() {
  }

  private static DepsBypasser instance = null;

  public static DepsBypasser getInstance() {
    if (instance == null) instance = new DepsBypasser();

    return instance;
  }

  public String getSuperSha1() {
    return superSha1;
  }

  public Change.Id getSuperChange() {
    return superChange;
  }

  public void setSuperSha1(String superSha1) {
    this.superSha1 = superSha1;
  }

  public void addSubSha1(String subSha1) {
    if (subSha1s == null) this.subSha1s = new ArrayList<String>();
    this.subSha1s.add(subSha1);
  }

  public void reset() {
    this.superSha1 = null;
    this.subSha1s = null;
    this.superChange = null;
    this.subChanges = null;
  }

  public void validate(final ReviewDb db) {
    if (superChange == null && superSha1 != null) {
      try {
        List<Change> changes =
            db.changes().byKey(Change.Key.parse("I" + superSha1)).toList();

        if (!changes.isEmpty()) {
          // get the latest change for the required sha1, since one sha1 may
          // have
          // multiple changes
          if (changes.size() > 1) {
            Collections.sort(changes, new Comparator<Change>() {
              public int compare(Change ch1, Change ch2) {
                return ch1.getCreatedOn().compareTo(ch2.getCreatedOn());
              }
            });
          }
          superChange = changes.get(changes.size() - 1).getId();

          if (subChanges == null && subSha1s != null) {
            for (String sha1 : subSha1s) {
              List<Change> subch =
                  db.changes().byKey(Change.Key.parse("I" + sha1)).toList();

              // Since the atomic commit is cross gits we might have more than
              // one change with the given revId. Can we just ignore this ?
              // Creating a dependency would not be correct.
              // We should only create dependency to submodule changes
              // with status NEW. This will prevent 200+ dependencies
              // when we initially create our superproject.
              if (subch.size() == 1
                  && subch.get(0).getStatus() == Change.Status.NEW) {
                if (subChanges == null)
                  this.subChanges = new ArrayList<Change.Id>();
                subChanges.add(subch.get(0).getId());
              }
            }
          }
        }
      } catch (OrmException e) {
        // TODO handle, ignore or throw exception ?
      }
    }
  }

  public boolean isAtomicCommit() {
    return superChange != null && subChanges != null;
  }

  public List<Change.Id> getAtomicMembers() {
    List<Change.Id> members = new ArrayList<Change.Id>(subChanges.size() + 1);
    if (subChanges != null) members.addAll(subChanges);
    if (superChange != null) members.add(superChange);
    return members;
  }
}
