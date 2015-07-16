// Copyright (C) 2013 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.mail;

import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Branch;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.AccountProjectWatch.NotifyType;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.mail.ProjectWatch.Watchers;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.AndPredicate;
import com.google.gerrit.server.query.NotPredicate;
import com.google.gerrit.server.query.OrPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.RefPredicate;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.IsVisibleToPredicate;
import com.google.gerrit.server.query.change.RegexRefPredicate;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.io.IOException;
import java.util.List;

/** Send email for each change being pushed directly into the repository. */
public class BranchUpdateSender extends NotificationEmail {
  private static final Logger log = LoggerFactory.getLogger(BranchUpdateSender.class);

  protected static ChangeData newChangeData(EmailArguments ea, Change.Id id) {
    return ea.changeDataFactory.create(ea.db.get(), id);
  }

  public static interface Factory {
    BranchUpdateSender create(Project project, ReceiveCommand cmd);
  }

  protected final ReceiveCommand cmd;
  protected final Project.NameKey project;

  @Inject
  protected BranchUpdateSender(EmailArguments ea,
      @AnonymousCowardName String anonymousCowardName,
      @Assisted Project project, @Assisted ReceiveCommand cmd) {
    super(ea, "ref-update", new Branch.NameKey(project.getNameKey(), cmd.getRefName()));
    this.cmd = cmd;
    this.project = project.getNameKey();
  }

  public String getNewId() {
    return cmd.getNewId().getName();
  }

  public String getShortNewId() {
    return cmd.getNewId().abbreviate(12).toString();
  }

  public String getOldId() {
    return cmd.getOldId().getName();
  }

  public String getShortOldId() {
    return cmd.getOldId().abbreviate(12).toString();
  }

  public String getRefName() {
    return cmd.getRefName();
  }

  public String getCommitSummary() {
    try (Repository git = args.server.openRepository(project)) {
      try {
        RevWalk rw = new RevWalk(git);
        RevWalk merged = new RevWalk(git);
        RevCommit oldId = rw.parseCommit(cmd.getOldId());
        RevCommit newId = rw.parseCommit(cmd.getNewId());

        rw.reset();

        rw.setRevFilter(RevFilter.MERGE_BASE);
        rw.markStart(rw.parseCommit(oldId));
        rw.markStart(rw.parseCommit(newId));

        // Find a merge base
        RevCommit mergeBase = rw.next();
        rw.reset();

        rw.markStart(oldId);
        rw.markStart(newId);
        rw.markUninteresting(mergeBase);

        RevCommit c;
        String result = "";
        while ((c = rw.next()) != null) {
          rw.parseBody(c);

          if (merged.isMergedInto(c, newId)) {
            result += "+" + c.abbreviate(12) + " " + c.getShortMessage() + "\n";
          } else {
            result += "-" + c.abbreviate(12) + " " + c.getShortMessage() + "\n";
          }
        }

        return result;
      } catch (IOException e) {
        log.error("Cannot find commit to create summary", e);
        return "";
      }
    } catch (IOException e) {
      log.error("Cannot open repository to read log", e);
      return "";
    }
  }

  @Override
  protected Watchers getWatchers(NotifyType type) throws OrmException {
    Change.Id id = new Change.Id(0);
    BranchUpdateWatch watch = new BranchUpdateWatch(args,
        project, args.projectCache.get(project), newChangeData(args, id));
    return watch.getWatchers(type);
  }

  /** Format the message body by calling {@link #appendText(String)}. */
  @Override
  protected void format() throws EmailException {
    appendText(velocifyFile("BranchUpdate.vm"));
  }

  /** Setup the message headers and envelope (TO, CC, BCC, FROM). */
  @Override
  protected void init() throws EmailException {

    super.init();

    includeWatchers(NotifyType.REF_UPDATES);
    setSubjectHeader();
  }

  private void setSubjectHeader() throws EmailException {
    setHeader("Subject", velocifyFile("BranchUpdateSubject.vm"));
  }

  @Override
  protected void setupVelocityContext() {
    super.setupVelocityContext();
  }

  private static class BranchUpdateWatch extends ProjectWatch {
    public BranchUpdateWatch(EmailArguments args, Project.NameKey project,
    ProjectState projectState, ChangeData changeData) {
      super(args, project, projectState, changeData);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected boolean filterMatch(CurrentUser user, String filter)
        throws OrmException, QueryParseException {
      ChangeQueryBuilder qb = args.queryBuilder.asUser(user);
      Predicate<ChangeData> p = null;

      if (!(user instanceof AnonymousUser)) {
        p = qb.is_visible();
      }

      if (filter != null) {
        Predicate<ChangeData> filterPredicate = qb.parse(filter);
        if (p == null) {
          p = filterPredicate;
        } else {
          p = Predicate.and(filterPredicate, p);
        }
        p = removeIgnoredPredicate(p);
      }
      return p == null ? true : p.match(changeData);
    }

    private Predicate<ChangeData> removeIgnoredPredicate(Predicate<ChangeData> in) {
      if (in == null) {
        return null;
      }

      if (in.getClass() == AndPredicate.class ||
          in.getClass() == OrPredicate.class) {
        return rewriteChild(in);
      } else if (in.getClass() == NotPredicate.class) {
        Predicate<ChangeData> p = removeIgnoredPredicate(in.getChild(0));
        return p == null ? null : Predicate.not(p);
      } else if (isSupported(in)) {
        return in;
      } else {
        return null;
      }
    }

    private Predicate<ChangeData> rewriteChild(Predicate<ChangeData> in) {
      List<Predicate<ChangeData>> newPredicats =
          new ArrayList<Predicate<ChangeData>>(in.getChildCount());

      for (Predicate<ChangeData> child : in.getChildren()) {
        Predicate<ChangeData> p = removeIgnoredPredicate(child);
        if (p != null) {
          newPredicats.add(p);
        }
      }

      if (newPredicats.size() == 0) {
        return null;
      }
      if (in.getClass() == AndPredicate.class) {
        return Predicate.and(newPredicats);
      } else {
        return Predicate.or(newPredicats);
      }
    }

    private boolean isSupported(Predicate<ChangeData> p) {
      Class<?> clazz = p.getClass();
      if (clazz == RefPredicate.class ||
          clazz == RegexRefPredicate.class ||
          clazz == IsVisibleToPredicate.class) {
        return true;
      }
      return false;
    }
  }
}
