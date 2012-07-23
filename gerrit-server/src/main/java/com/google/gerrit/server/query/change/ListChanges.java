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
import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.PatchSetInfo.ParentInfo;
import com.google.gerrit.reviewdb.client.UserIdentity;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.events.AccountAttribute;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
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
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public class ListChanges {
  private static final Logger log = LoggerFactory.getLogger(ListChanges.class);

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
  private final ApprovalTypes approvalTypes;
  private final CurrentUser user;
  private final AnonymousUser anonymous;
  private final ChangeControl.Factory changeControlFactory;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final PatchListCache patchListCache;
  private final SshInfo sshInfo;
  private final Provider<String> urlProvider;
  private final Urls urls;
  private boolean reverse;
  private Map<Account.Id, AccountAttribute> accounts;
  private Map<Change.Id, ChangeControl> controls;
  private EnumSet<ListChangesOption> options;

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
      ApprovalTypes at,
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
    this.approvalTypes = at;
    this.user = u;
    this.anonymous = au;
    this.changeControlFactory = cf;
    this.patchSetInfoFactory = psi;
    this.patchListCache = plc;
    this.sshInfo = sshInfo;
    this.urlProvider = curl;
    this.urls = urls;

    accounts = Maps.newHashMap();
    controls = Maps.newHashMap();
    options = EnumSet.noneOf(ListChangesOption.class);
  }

  public OutputFormat getFormat() {
    return format;
  }

  public ListChanges setFormat(OutputFormat fmt) {
    this.format = fmt;
    return this;
  }

  public void query(Writer out)
      throws OrmException, QueryParseException, IOException {
    if (imp.isDisabled()) {
      throw new QueryParseException("query disabled");
    }
    if (queries == null || queries.isEmpty()) {
      queries = Collections.singletonList("status:open");
    } else if (queries.size() > 10) {
      // Hard-code a default maximum number of queries to prevent
      // users from submitting too much to the server in a single call.
      throw new QueryParseException("limit of 10 queries");
    }

    List<List<ChangeInfo>> res = Lists.newArrayListWithCapacity(queries.size());
    for (String query : queries) {
      List<ChangeData> changes = imp.queryChanges(query);
      boolean moreChanges = imp.getLimit() > 0 && changes.size() > imp.getLimit();
      if (moreChanges) {
        if (reverse) {
          changes = changes.subList(1, changes.size());
        } else {
          changes = changes.subList(0, imp.getLimit());
        }
      }
      ChangeData.ensureChangeLoaded(db, changes);
      ChangeData.ensureCurrentPatchSetLoaded(db, changes);
      ChangeData.ensureCurrentApprovalsLoaded(db, changes);

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

    if (!accounts.isEmpty()) {
      for (Account account : db.get().accounts().get(accounts.keySet())) {
        AccountAttribute a = accounts.get(account.getId());
        a.name = Strings.emptyToNull(account.getFullName());
      }
    }

    if (format.isJson()) {
      format.newGson().toJson(
          res.size() == 1 ? res.get(0) : res,
          new TypeToken<List<ChangeInfo>>() {}.getType(),
          out);
      out.write('\n');
    } else {
      boolean firstQuery = true;
      for (List<ChangeInfo> info : res) {
        if (firstQuery) {
          firstQuery = false;
        } else {
          out.write('\n');
        }
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
    out.reviewed = in.getStatus().isOpen() && isChangeReviewed(cd) ? true : null;
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
    }
    AccountAttribute a = accounts.get(user);
    if (a == null) {
      a = new AccountAttribute();
      accounts.put(user, a);
    }
    return a;
  }

  private ChangeControl control(ChangeData cd) throws OrmException {
    ChangeControl ctrl = cd.changeControl();
    if (ctrl != null && ctrl.getCurrentUser() == user) {
      return ctrl;
    }

    ctrl = controls.get(cd.getId());
    if (ctrl != null) {
      return ctrl;
    }

    try {
      ctrl = changeControlFactory.controlFor(cd.change(db));
    } catch (NoSuchChangeException e) {
      return null;
    }
    controls.put(cd.getId(), ctrl);
    return ctrl;
  }

  private Map<String, LabelInfo> labelsFor(ChangeData cd) throws OrmException {
    ChangeControl ctl = control(cd);
    if (ctl == null) {
      return Collections.emptyMap();
    }

    PatchSet ps = cd.currentPatchSet(db);
    Map<String, LabelInfo> labels = Maps.newLinkedHashMap();
    for (SubmitRecord rec : ctl.canSubmit(db.get(), ps, cd, true, false)) {
      if (rec.labels == null) {
        continue;
      }
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
          n.optional = n._status == SubmitRecord.Label.Status.MAY ? true : null;
          labels.put(r.label, n);
        }
      }
    }

    Collection<PatchSetApproval> approvals = null;
    for (Map.Entry<String, LabelInfo> e : labels.entrySet()) {
      if (e.getValue().approved != null || e.getValue().rejected != null) {
        continue;
      }

      ApprovalType type = approvalTypes.byLabel(e.getKey());
      if (type == null || type.getMin() == null || type.getMax() == null) {
        // Unknown or misconfigured type can't have intermediate scores.
        continue;
      }

      short min = type.getMin().getValue();
      short max = type.getMax().getValue();
      if (-1 <= min && max <= 1) {
        // Types with a range of -1..+1 can't have intermediate scores.
        continue;
      }

      if (approvals == null) {
        approvals = cd.currentApprovals(db);
      }
      for (PatchSetApproval psa : approvals) {
        short val = psa.getValue();
        if (val != 0 && min < val && val < max
            && psa.getCategoryId().equals(type.getCategory().getId())) {
          if (0 < val) {
            e.getValue().recommended = asAccountAttribute(psa.getAccountId());
            e.getValue().value = val != 1 ? val : null;
          } else {
            e.getValue().disliked = asAccountAttribute(psa.getAccountId());
            e.getValue().value = val != -1 ? val : null;
          }
        }
      }
    }
    return labels;
  }

  private boolean isChangeReviewed(ChangeData cd) throws OrmException {
    if (user instanceof IdentifiedUser) {
      PatchSet currentPatchSet = cd.currentPatchSet(db);
      if (currentPatchSet == null) {
        return false;
      }

      List<ChangeMessage> messages =
          db.get().changeMessages().byPatchSet(currentPatchSet.getId()).toList();

      if (messages.isEmpty()) {
        return false;
      }

      // Sort messages to let the most recent ones at the beginning.
      Collections.sort(messages, new Comparator<ChangeMessage>() {
        @Override
        public int compare(ChangeMessage a, ChangeMessage b) {
          return b.getWrittenOn().compareTo(a.getWrittenOn());
        }
      });

      Account.Id currentUserId = ((IdentifiedUser) user).getAccountId();
      Account.Id changeOwnerId = cd.change(db).getOwner();
      for (ChangeMessage cm : messages) {
        if (currentUserId.equals(cm.getAuthor())) {
          return true;
        } else if (changeOwnerId.equals(cm.getAuthor())) {
          return false;
        }
      }
    }
    return false;
  }

  private Map<String, RevisionInfo> revisions(ChangeData cd) throws OrmException {
    ChangeControl ctl = control(cd);
    if (ctl == null) {
      return Collections.emptyMap();
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
        out.commit.parents = Lists.newArrayListWithCapacity(info.getParents().size());
        out.commit.author = toGitPerson(info.getAuthor());
        out.commit.committer = toGitPerson(info.getCommitter());
        out.commit.subject = info.getSubject();
        out.commit.message = info.getMessage();

        for (ParentInfo parent : info.getParents()) {
          CommitInfo i = new CommitInfo();
          i.commit = parent.id.get();
          i.subject = parent.shortMessage;
          out.commit.parents.add(i);
        }
      } catch (PatchSetInfoNotAvailableException e) {
        log.warn("Cannot load PatchSetInfo " + in.getId(), e);
      }
    }

    if (options.contains(ALL_FILES)
        || (out.isCurrent && options.contains(CURRENT_FILES))) {
      PatchList list;
      try {
        list = patchListCache.get(cd.change(db), in);
      } catch (PatchListNotAvailableException e) {
        log.warn("Cannot load PatchList " + in.getId(), e);
        list = null;
      }
      if (list != null) {
        out.files = Maps.newTreeMap();
        for (PatchListEntry e : list.getPatches()) {
          if (Patch.COMMIT_MSG.equals(e.getNewName())) {
            continue;
          }

          FileInfo d = new FileInfo();
          d.status = e.getChangeType() != Patch.ChangeType.MODIFIED
              ? e.getChangeType().getCode()
              : null;
          d.oldPath = e.getOldName();
          if (e.getPatchType() == Patch.PatchType.BINARY) {
            d.binary = true;
          } else {
            d.linesInserted = e.getInsertions() > 0 ? e.getInsertions() : null;
            d.linesDeleted = e.getDeletions() > 0 ? e.getDeletions() : null;
          }

          FileInfo o = out.files.put(e.getNewName(), d);
          if (o != null) {
            // This should only happen on a delete-add break created by JGit
            // when the file was rewritten and too little content survived. Write
            // a single record with data from both sides.
            d.status = Patch.ChangeType.REWRITE.getCode();
            if (o.binary != null && o.binary) {
              d.binary = true;
            }
            if (o.linesInserted != null) {
              d.linesInserted = o.linesInserted;
            }
            if (o.linesDeleted != null) {
              d.linesDeleted = o.linesDeleted;
            }
          }
        }
      }
    }
    return out;
  }

  private Map<String, FetchInfo> makeFetchMap(ChangeData cd, PatchSet in)
      throws OrmException {
    Map<String, FetchInfo> r = Maps.newLinkedHashMap();
    String refName = in.getRefName();
    ChangeControl ctl = control(cd);
    if (ctl != null && ctl.forUser(anonymous).isPatchVisible(in, db.get())) {
      if (urls.git != null) {
        r.put("git", new FetchInfo(urls.git
            + cd.change(db).getProject().get(), refName));
      }
    }
    if (urls.http != null) {
      r.put("http", new FetchInfo(urls.http
          + cd.change(db).getProject().get(), refName));
    } else {
      String http = urlProvider.get();
      if (!Strings.isNullOrEmpty(http)) {
        r.put("http", new FetchInfo(http
            + cd.change(db).getProject().get(), refName));
      }
    }
    if (!sshInfo.getHostKeys().isEmpty()) {
      HostKey host = sshInfo.getHostKeys().get(0);
      r.put("ssh", new FetchInfo(String.format(
          "ssh://%s/%s",
          host.getHost(), cd.change(db).getProject().get()),
          refName));
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
    Boolean reviewed;

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
    Map<String, FetchInfo> fetch;
    CommitInfo commit;
    Map<String, FileInfo> files;
  }

  static class FetchInfo {
    String url;
    String ref;

    FetchInfo(String url, String ref) {
      this.url = url;
      this.ref = ref;
    }
  }

  static class GitPerson {
    String name;
    String email;
    Timestamp date;
    int tz;
  }

  static class CommitInfo {
    String commit;
    List<CommitInfo> parents;
    GitPerson author;
    GitPerson committer;
    String subject;
    String message;
  }

  static class FileInfo {
    Character status;
    Boolean binary;
    String oldPath;
    Integer linesInserted;
    Integer linesDeleted;
  }

  static class LabelInfo {
    transient SubmitRecord.Label.Status _status;
    AccountAttribute approved;
    AccountAttribute rejected;

    AccountAttribute recommended;
    AccountAttribute disliked;
    Short value;
    Boolean optional;
  }
}
