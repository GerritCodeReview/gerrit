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
import org.eclipse.jgit.transport.ReceiveCommand;

import java.util.ArrayList;
import java.util.List;

/** Send notice about change being pushed directly into the repository. */
public class DirectPushSender extends NotificationEmail {

  public static interface Factory {
    DirectPushSender create(Project project, ReceiveCommand cmd,
        RevCommit commit);
  }

  protected final ReceiveCommand cmd;
  protected final RevCommit commit;

  @Inject
  protected DirectPushSender(EmailArguments ea,
      @AnonymousCowardName String anonymousCowardName, SshInfo si,
      @Assisted Project project, @Assisted ReceiveCommand cmd,
      @Assisted RevCommit commit) {
    super(ea, anonymousCowardName, "direct-push", project.getNameKey(),
        new Branch.NameKey(project.getNameKey(), cmd.getRefName()));
    this.cmd = cmd;
    this.commit = commit;
    setSshInfo(si);
  }

  public String getCommitId() {
    return commit.name();
  }

  public String getSubject() {
    return commit.getShortMessage();
  }

  public String getFullMessage() {
    return commit.getFullMessage();
  }

  @Override
  protected Watchers getWatchers(NotifyType type) throws OrmException {
    Change c = new Change(new Change.Key("I00000000"), new Change.Id(0),
        new Account.Id(0), branch);
    DirectPushWatch watch = new DirectPushWatch(args,
        project, args.projectCache.get(project), new ChangeData(c));
    return watch.getWatchers(type);
  }

  /** Format the message body by calling {@link #appendText(String)}. */
  @Override
  protected void format() throws EmailException {
    appendText(velocifyFile("DirectPush.vm"));
  }

  /** Setup the message headers and envelope (TO, CC, BCC). */
  @Override
  protected void init() throws EmailException {

    super.init();

    includeWatchers(NotifyType.DIRECT_PUSHES);
    setSubjectHeader();
    setCommitIdHeader();
  }

  private void setSubjectHeader() throws EmailException {
    setHeader("Subject", velocifyFile("ChangeSubject.vm"));
  }

  private void setCommitIdHeader() {
    setHeader("X-Gerrit-Commit", getCommitId());
  }

  @Override
  protected void setupVelocityContext() {
    super.setupVelocityContext();
  }

  private static class DirectPushWatch extends ProjectWatch {
    public DirectPushWatch(EmailArguments args, Project.NameKey project,
    ProjectState projectState, ChangeData changeData) {
      super(args, project, projectState, changeData);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected boolean filterMatch(CurrentUser user, String filter)
        throws OrmException, QueryParseException {
      ChangeQueryBuilder qb = args.queryBuilder.create(user);
      Predicate<ChangeData> p = null;

      if (!(user instanceof AnonymousUser)) {
        p = qb.is_visible();
      }

      if (filter != null) {
        qb.setAllowFile(true);
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
