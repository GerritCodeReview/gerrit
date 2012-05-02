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

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.events.AccountAttribute;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gson.reflect.TypeToken;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.io.Writer;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ListChanges {
  private final QueryProcessor imp;
  private final Provider<ReviewDb> db;
  private final AccountCache accountCache;
  private final ApprovalTypes approvalTypes;
  private final CurrentUser user;
  private final ChangeControl.Factory changeControlFactory;
  private boolean reverse;
  private Map<Account.Id, AccountAttribute> accounts;

  @Option(name = "--format", metaVar = "FMT", usage = "Output display format")
  private OutputFormat format = OutputFormat.TEXT;

  @Option(name = "--query", aliases = {"-q"}, metaVar = "QUERY", multiValued = true, usage = "Query string")
  private List<String> queries;

  @Option(name = "--limit", aliases = {"-n"}, metaVar = "CNT", usage = "Maximum number of results to return")
  void setLimit(int limit) {
    imp.setLimit(limit);
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
      ApprovalTypes at,
      CurrentUser u,
      ChangeControl.Factory cf) {
    this.imp = qp;
    this.db = db;
    this.accountCache = ac;
    this.approvalTypes = at;
    this.user = u;
    this.changeControlFactory = cf;

    accounts = Maps.newHashMap();
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
    out.labels = labelsFor(cd);
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

    PatchSet ps = cd.currentPatchSet(db);
    Map<String, LabelInfo> labels = Maps.newLinkedHashMap();
    for (SubmitRecord rec : ctl.canSubmit(db.get(), ps, cd)) {
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
    Boolean _moreChanges;
  }

  static class LabelInfo {
    transient SubmitRecord.Label.Status _status;
    AccountAttribute approved;
    AccountAttribute rejected;

    AccountAttribute recommended;
    AccountAttribute disliked;
    Short value;
  }
}
