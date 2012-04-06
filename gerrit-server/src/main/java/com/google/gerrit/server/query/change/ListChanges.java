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
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.events.AccountAttribute;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gson.reflect.TypeToken;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.io.Writer;
import java.sql.Timestamp;
import java.util.List;

public class ListChanges {
  private final QueryProcessor imp;
  private final Provider<ReviewDb> db;
  private final AccountCache accountCache;
  private final CurrentUser user;
  private boolean reverse;

  @Option(name = "--format", metaVar = "FMT", usage = "Output display format")
  private OutputFormat format = OutputFormat.TEXT;

  @Option(name = "--limit", metaVar = "CNT", aliases = {"-n"},
      usage = "Maximum number of results to return.")
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
  ListChanges(QueryProcessor qp, Provider<ReviewDb> db, AccountCache ac,
      CurrentUser u) {
    this.imp = qp;
    this.db = db;
    this.accountCache = ac;
    this.user = u;
  }

  public OutputFormat getFormat() {
    return format;
  }

  public ListChanges setFormat(OutputFormat fmt) {
    this.format = fmt;
    return this;
  }

  public void query(String queryString, Writer out)
      throws OrmException, QueryParseException, IOException {
    if (imp.isDisabled()) {
      throw new QueryParseException("query disabled");
    }

    List<ChangeData> changes = imp.queryChanges(queryString);
    boolean moreChanges = changes.size() > imp.getLimit();
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

    if (format.isJson()) {
      format.newGson().toJson(
          info,
          new TypeToken<List<ChangeInfo>>() {}.getType(),
          out);
      out.write('\n');
    } else {
      for (ChangeInfo c : info) {
        out.write(c.id);
        out.write('\n');
      }
    }
  }

  private ChangeInfo toChangeInfo(ChangeData cd) throws OrmException {
    ChangeInfo out = new ChangeInfo();
    Change in = cd.change(db);
    if (user.getStarredChanges().contains(in.getId())) {
      out.starred = true;
    }
    out.project = in.getProject().get();
    out.branch = in.getDest().getShortName();
    out.topic = in.getTopic();
    out.id = in.getKey().get();
    out.subject = in.getSubject();
    out.status = in.getStatus();
    out.owner = asAccountAttribute(in.getOwner());
    out.updated = in.getLastUpdatedOn();
    out._number = in.getId().get();
    out._sortkey = in.getSortKey();
    return out;
  }

  private AccountAttribute asAccountAttribute(Account.Id owner) {
    AccountState state = accountCache.get(owner);
    String name = state.getAccount().getFullName();
    if (Strings.isNullOrEmpty(name)) {
      return null;
    }

    AccountAttribute a = new AccountAttribute();
    a.name = name;
    return a;
  }

  static class ChangeInfo {
    String project;
    String branch;
    String topic;
    String id;
    String subject;
    Change.Status status;
    AccountAttribute owner;
    Timestamp updated;
    Boolean starred;

    String _sortkey;
    int _number;
    Boolean _moreChanges;
  }
}
