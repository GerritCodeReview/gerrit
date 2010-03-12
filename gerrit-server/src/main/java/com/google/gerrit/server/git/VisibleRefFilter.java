package com.google.gerrit.server.git;

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.RefControl;
import com.google.gwtorm.client.OrmException;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.RefFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VisibleRefFilter implements RefFilter {

  private static final Logger log =
    LoggerFactory.getLogger(VisibleRefFilter.class);

  private static final Pattern CHANGE_ID =
    Pattern.compile("^refs/changes/(?:[0-9][0-9]/)?([1-9][0-9]*)(?:/.*)?$");

  private final ProjectControl projectControl;
  private final ReviewDb reviewDb;

  public VisibleRefFilter(final ProjectControl projectControl,
      final ReviewDb reviewDb) {
    this.projectControl = projectControl;
    this.reviewDb = reviewDb;
  }

  @Override
  public Map<String, Ref> filter(Map<String,Ref> refs) {
    Map<String, Ref> result = new HashMap<String, Ref>();
    Map<Change.Id, Ref> changeRefs = new HashMap<Change.Id, Ref>();

    for (Ref ref : refs.values()) {
      final Matcher m = CHANGE_ID.matcher(ref.getName());
      if (m.matches()) {
        // This is a reference to a change in Gerrit:
        // We need to keep the Ref and its Change.Id for later: we must first
        // figure out which changes are visible to make a decision on that
        // Ref.
        System.out.println("??:" + ref.getName());
        changeRefs.put(Change.Id.parse(m.group(1)), ref);
      } else {
        RefControl ctl = projectControl.controlForRef(ref.getName());
        if (ctl.isVisible()) {
          result.put(ref.getName(), ref);
        }
      }
    }

    Set<Change.Id> visibleChanges = new HashSet<Change.Id>();
    try {
      Iterable<Change> changes =
        reviewDb.changes().byProject(projectControl.getProject().getNameKey());
      for (Change change : changes) {
        ChangeControl ctl = projectControl.controlFor(change);
        if (ctl.isVisible()) {
          visibleChanges.add(change.getId());
        }
      }
    } catch (OrmException e) {
      log.error("Cannot load changes for project "
          + projectControl.getProject().getNameKey().get(), e);
    }

    for (Change.Id changeId : changeRefs.keySet()) {
      if (visibleChanges.contains(changeId)) {
        Ref ref = changeRefs.get(changeId);
        System.out.println("OK:" + ref.getName());
        result.put(ref.getName(), ref);
      }
    }

    return result;
  }
}
