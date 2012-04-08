// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import static com.google.gerrit.common.changes.ListChangesOption.ALL_COMMITS;
import static com.google.gerrit.common.changes.ListChangesOption.ALL_FILES;
import static com.google.gerrit.common.changes.ListChangesOption.ALL_REVISIONS;
import static com.google.gerrit.common.changes.ListChangesOption.CURRENT_COMMIT;
import static com.google.gerrit.common.changes.ListChangesOption.CURRENT_FILES;
import static com.google.gerrit.common.changes.ListChangesOption.CURRENT_REVISION;
import static com.google.gerrit.common.changes.ListChangesOption.LABELS;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.common.changes.ListChangesOption;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.PatchSetInfo.ParentInfo;
import com.google.gerrit.reviewdb.client.UserIdentity;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.events.AccountAttribute;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gson.reflect.TypeToken;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import com.jcraft.jsch.HostKey;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.URIish;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.io.Writer;
import java.net.URISyntaxException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public class ListChanges {
  @Singleton
  static class Urls {
    final String git;
    final String http;

    @Inject
    Urls(@GerritServerConfig Config cfg) {
      this.git = ensureSlash(cfg.getString("gerrit", null, "canonicalGitUrl"));
      this.http = ensureSlash(cfg.getString("gerrit", null, "gitHttpUrl"));
    }

    private static String ensureSlash(String in) {
      if (in != null && !in.endsWith("/")) {
        return in + "/";
      }
      return in;
    }
  }

  private final QueryProcessor imp;
  private final Provider<ReviewDb> db;
  private final AccountCache accountCache;
  private final CurrentUser user;
  private final AnonymousUser anonymous;
  private final ChangeControl.Factory changeControlFactory;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final PatchListCache patchListCache;
  private final SshInfo sshInfo;
  private final Provider<String> urlProvider;
  private final Urls urls;
  private boolean reverse;
  private String defaultField;
  private Map<Account.Id, AccountAttribute> accounts = Maps.newHashMap();
  private EnumSet<ListChangesOption> options =
      EnumSet.noneOf(ListChangesOption.class);

  @Option(name = "--format", metaVar = "FMT", usage = "Output display format")
  private OutputFormat format = OutputFormat.TEXT;

  @Option(name = "--query", aliases = {"-q"}, metaVar = "QUERY", multiValued = true, usage = "Query string")
  private List<String> queries;

  @Option(name = "--limit", aliases = {"-n"}, metaVar = "CNT", usage = "Maximum number of results to return")
  void setLimit(int limit) {
    imp.setLimit(limit);
  }

  @Option(name = "-o", multiValued = true, usage = "Output options per change")
  void addOption(ListChangesOption o) {
    options.add(o);
  }

  @Option(name = "-O", usage = "Output option flags, in hex")
  void setOptionFlagsHex(String hex) {
    options.addAll(ListChangesOption.fromBits(Integer.parseInt(hex, 16)));
  }

  @Option(name = "-P", metaVar = "SORTKEY", usage = "Previous changes before SORTKEY")
  void setSortKeyAfter(String key) {
    // Querying for the prior page of changes requires sortkey_after predicate.
    // Changes are shown most recent->least recent. The previous page of
    // results contains changes that were updated after the given key.
    imp.setSortkeyAfter(key);
    reverse = true;
  }

  @Option(name = "-N", metaVar = "SORTKEY", usage = "Next changes after SORTKEY")
  void setSortKeyBefore(String key) {
    // Querying for the next page of changes requires sortkey_before predicate.
    // Changes are shown most recent->least recent. The next page contains
    // changes that were updated before the given key.
    imp.setSortkeyBefore(key);
  }

  @Inject
  ListChanges(QueryProcessor qp,
      Provider<ReviewDb> db,
      AccountCache ac,
      CurrentUser u,
      AnonymousUser au,
      ChangeControl.Factory cf,
      PatchSetInfoFactory psi,
      PatchListCache plc,
      SshInfo sshInfo,
      @CanonicalWebUrl Provider<String> curl,
      Urls urls) {
    this.imp = qp;
    this.db = db;
    this.accountCache = ac;
    this.user = u;
    this.anonymous = au;
    this.changeControlFactory = cf;
    this.patchSetInfoFactory = psi;
    this.patchListCache = plc;
    this.sshInfo = sshInfo;
    this.urlProvider = curl;
    this.urls = urls;
  }

  public OutputFormat getFormat() {
    return format;
  }

  public ListChanges setFormat(OutputFormat fmt) {
    this.format = fmt;
    return this;
  }

  /**
   * Add a default field string that will be combined with the rest of the query
   * using a boolean AND operator.
   *
   * @param defaultField single term to parse using the defaultField logic.
   *        Typically this is a project name.
   * @return {@code this}
   */
  public ListChanges andDefaultField(String defaultField) {
    this.defaultField = defaultField;
    return this;
  }

  public void query(Writer out)
      throws OrmException, QueryParseException, IOException {
    if (imp.isDisabled()) {
      throw new QueryParseException("query disabled");
    }

    if (queries == null) {
      queries = Collections.singletonList("status:open");
    }

    List<List<ChangeInfo>> res = Lists.newArrayListWithCapacity(queries.size());
    for (String query : queries) {
      List<ChangeData> changes = imp.queryChanges(query, defaultField);
      boolean moreChanges = imp.getLimit() > 0 && changes.size() > imp.getLimit();
      if (moreChanges) {
        if (reverse) {
          changes = changes.subList(1, changes.size());
        } else {
          changes = changes.subList(0, imp.getLimit());
        }
      }

      List<ChangeInfo> info = Lists.newArrayListWithCapacity(changes.size());
      for (ChangeData cd : changes) {
        info.add(toChangeInfo(cd));
      }
      if (moreChanges && !info.isEmpty()) {
        if (reverse) {
          info.get(0)._moreChanges = true;
        } else {
          info.get(info.size() - 1)._moreChanges = true;
        }
      }
      res.add(info);
    }

    if (format.isJson()) {
      format.newGson().toJson(
          res.size() == 1 ? res.get(0) : res,
          new TypeToken<List<ChangeInfo>>() {}.getType(),
          out);
      out.write('\n');
    } else {
      for (List<ChangeInfo> info : res) {
        for (ChangeInfo c : info) {
          String id = new Change.Key(c.id).abbreviate();
          String subject = c.subject;
          if (subject.length() + id.length() > 80) {
            subject = subject.substring(0, 80 - id.length());
          }
          out.write(id);
          out.write(' ');
          out.write(subject.replace('\n', ' '));
          out.write('\n');
        }
      }
    }
  }

  private ChangeInfo toChangeInfo(ChangeData cd) throws OrmException {
    ChangeInfo out = new ChangeInfo();
    Change in = cd.change(db);
    out.project = in.getProject().get();
    out.branch = in.getDest().getShortName();
    out.topic = in.getTopic();
    out.id = in.getKey().get();
    out.subject = in.getSubject();
    out.status = in.getStatus();
    out.owner = asAccountAttribute(in.getOwner());
    out.created = in.getCreatedOn();
    out.updated = in.getLastUpdatedOn();
    out._number = in.getId().get();
    out._sortkey = in.getSortKey();
    out.starred = user.getStarredChanges().contains(in.getId()) ? true : null;
    out.labels = options.contains(LABELS) ? labelsFor(cd) : null;

    if (options.contains(ALL_REVISIONS) || options.contains(CURRENT_REVISION)) {
      out.revisions = revisions(cd);
      for (String commit : out.revisions.keySet()) {
        if (out.revisions.get(commit).isCurrent) {
          out.current_revision = commit;
          break;
        }
      }
    }

    return out;
  }

  private AccountAttribute asAccountAttribute(Account.Id user) {
    if (user == null) {
      return null;
    } else if (accounts.containsKey(user)) {
      return accounts.get(user);
    }

    AccountState state = accountCache.get(user);
    String name = state.getAccount().getFullName();
    if (Strings.isNullOrEmpty(name)) {
      accounts.put(user, null);
      return null;
    }

    AccountAttribute a = new AccountAttribute();
    a.name = name;
    accounts.put(user, a);
    return a;
  }

  private Map<String, LabelInfo> labelsFor(ChangeData cd) throws OrmException {
    Change in = cd.change(db);
    ChangeControl ctl = cd.changeControl();
    if (ctl == null || ctl.getCurrentUser() != user) {
      try {
        ctl = changeControlFactory.controlFor(in);
      } catch (NoSuchChangeException e) {
        return null;
      }
    }

    Map<String, LabelInfo> labels = Maps.newLinkedHashMap();
    for (SubmitRecord rec : ctl.canSubmit(db.get(), in.currentPatchSetId())) {
      for (SubmitRecord.Label r : rec.labels) {
        LabelInfo p = labels.get(r.label);
        if (p == null || p._status.compareTo(r.status) < 0) {
          LabelInfo n = new LabelInfo();
          n._status = r.status;
          switch (r.status) {
            case OK:
              n.approved = asAccountAttribute(r.appliedBy);
              break;
            case REJECT:
              n.rejected = asAccountAttribute(r.appliedBy);
              break;
          }
          labels.put(r.label, n);
        }
      }
    }
    return labels;
  }

  private Map<String, RevisionInfo> revisions(ChangeData cd) throws OrmException {
    ChangeControl ctl = cd.changeControl();
    if (ctl == null || ctl.getCurrentUser() != user) {
      try {
        ctl = changeControlFactory.controlFor(cd.change(db));
      } catch (NoSuchChangeException e) {
        return Collections.emptyMap();
      }
    }

    Collection<PatchSet> src;
    if (options.contains(ALL_REVISIONS)) {
      src = cd.patches(db);
    } else {
      src = Collections.singletonList(cd.currentPatchSet(db));
    }
    Map<String, RevisionInfo> res = Maps.newLinkedHashMap();
    for (PatchSet in : src) {
      if (ctl.isPatchVisible(in, db.get())) {
        res.put(in.getRevision().get(), toRevisionInfo(cd, in));
      }
    }
    return res;
  }

  private RevisionInfo toRevisionInfo(ChangeData cd, PatchSet in)
      throws OrmException {
    RevisionInfo out = new RevisionInfo();
    out.isCurrent = in.getId().equals(cd.change(db).currentPatchSetId());
    out._number = in.getId().get();
    out.draft = in.isDraft() ? true : null;
    out.fetch = makeFetchMap(cd, in);

    if (options.contains(ALL_COMMITS)
        || (out.isCurrent && options.contains(CURRENT_COMMIT))) {
      try {
        PatchSetInfo info = patchSetInfoFactory.get(db.get(), in.getId());
        out.commit = new CommitInfo();
        out.commit.parents = Maps.newLinkedHashMap();
        out.commit.author = toGitPerson(info.getAuthor());
        out.commit.committer = toGitPerson(info.getCommitter());
        out.commit.message = info.getMessage();

        for (ParentInfo p1 : info.getParents()) {
          CommitInfo p2 = new CommitInfo();
          p2.subject = p1.shortMessage;
          out.commit.parents.put(p1.id.get(), p2);
        }
      } catch (PatchSetInfoNotAvailableException e) {
      }
    }

    if (options.contains(ALL_FILES)
        || (out.isCurrent && options.contains(CURRENT_FILES))) {
      PatchList list = patchListCache.get(cd.change(db), in);
      if (list != null) {
        out.files = Lists.newArrayListWithCapacity(list.getPatches().size());
        for (PatchListEntry e : list.getPatches()) {
          if (Patch.COMMIT_MSG.equals(e.getNewName())) {
            continue;
          }
          FileInfo o = new FileInfo();
          o.status = e.getChangeType() != Patch.ChangeType.MODIFIED
              ? e.getChangeType().getCode()
              : null;
          if (e.getOldName() == null && e.getNewName() != null) {
            o.path = e.getNewName();
          } else {
            o.oldPath = e.getOldName();
            o.newPath = e.getNewName();
          }
          if (e.getPatchType() == Patch.PatchType.BINARY) {
            o.binary = true;
          } else {
            o.linesInserted = e.getInsertions();
            o.linesDeleted = e.getDeletions();
          }
          out.files.add(o);
        }
      }
    }
    return out;
  }

  private Map<String, String> makeFetchMap(ChangeData cd, PatchSet in) throws OrmException {
    Map<String, String> r = Maps.newLinkedHashMap();
    boolean anon = false;
    ChangeControl ctl = cd.changeControl();
    if (ctl == null || ctl.getCurrentUser() != user) {
      try {
        ctl = changeControlFactory.controlFor(cd.change(db));
      } catch (NoSuchChangeException e) {
        ctl = null;
      }
    }
    if (ctl != null && ctl.forUser(anonymous).isPatchVisible(in, db.get())) {
      anon = true;
    }

    if (anon && urls.git != null) {
      r.put(urls.git + cd.change(db).getProject().get(), in.getRefName());
    }
    if (anon && urls.http != null) {
      r.put(urls.http + cd.change(db).getProject().get(), in.getRefName());
    }

    String http = urlProvider.get();
    if (anon && !Strings.isNullOrEmpty(http)) {
      r.put(http + cd.change(db).getProject().get(), in.getRefName());
    }

    if (user instanceof IdentifiedUser) {
      String username = ((IdentifiedUser)user).getUserName();
      if (!Strings.isNullOrEmpty(username)) {
        if (!sshInfo.getHostKeys().isEmpty()) {
          HostKey host = sshInfo.getHostKeys().get(0);
          r.put(String.format(
              "ssh://%s@%s/%s",
              username, host.getHost(), cd.change(db).getProject().get()),
              in.getRefName());
        }

        if (!Strings.isNullOrEmpty(http)) {
          try {
            r.put(new URIish(http + cd.change(db).getProject().get())
                  .setUser(username)
                  .toPrivateString(),
                in.getRefName());
          } catch (URISyntaxException e) {
          }
        }
      }
    }

    return r;
  }

  private static GitPerson toGitPerson(UserIdentity committer) {
    GitPerson p = new GitPerson();
    p.name = committer.getName();
    p.email = committer.getEmail();
    p.date = committer.getDate();
    p.tz = committer.getTimeZone();
    return p;
  }

  static class ChangeInfo {
    String project;
    String branch;
    String topic;
    String id;
    String subject;
    Change.Status status;
    Timestamp created;
    Timestamp updated;
    Boolean starred;

    String _sortkey;
    int _number;

    AccountAttribute owner;
    Map<String, LabelInfo> labels;
    String current_revision;
    Map<String, RevisionInfo> revisions;

    Boolean _moreChanges;
  }

  static class RevisionInfo {
    private transient boolean isCurrent;
    Boolean draft;
    int _number;
    Map<String, String> fetch;
    CommitInfo commit;
    List<FileInfo> files;
  }

  static class GitPerson {
    String name;
    String email;
    Timestamp date;
    int tz;
  }

  static class CommitInfo {
    Map<String, CommitInfo> parents;
    GitPerson author;
    GitPerson committer;
    String subject;
    String message;
  }

  static class FileInfo {
    Character status;
    Boolean binary;
    String path;
    String oldPath;
    String newPath;
    Integer linesInserted;
    Integer linesDeleted;
  }

  static class LabelInfo {
    transient SubmitRecord.Label.Status _status;
    AccountAttribute approved;
    AccountAttribute rejected;
  }
}
