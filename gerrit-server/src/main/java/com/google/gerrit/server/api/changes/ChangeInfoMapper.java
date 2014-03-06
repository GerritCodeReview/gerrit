// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.api.changes;

import static com.google.gerrit.extensions.common.ListChangesOption.ALL_REVISIONS;
import static com.google.gerrit.extensions.common.ListChangesOption.CURRENT_ACTIONS;
import static com.google.gerrit.extensions.common.ListChangesOption.CURRENT_REVISION;
import static com.google.gerrit.extensions.common.ListChangesOption.DETAILED_LABELS;
import static com.google.gerrit.extensions.common.ListChangesOption.LABELS;
import static com.google.gerrit.extensions.common.ListChangesOption.MESSAGES;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.ChangeStatus;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.common.ListChangesOption;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.server.change.ChangeJson;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

class ChangeInfoMapper {
  private final static ImmutableMap<Change.Status, ChangeStatus> MAP =
      Maps.immutableEnumMap(ImmutableMap.of(
          Status.DRAFT, ChangeStatus.DRAFT,
          Status.NEW, ChangeStatus.NEW,
          Status.SUBMITTED, ChangeStatus.SUBMITTED,
          Status.MERGED, ChangeStatus.MERGED,
          Status.ABANDONED, ChangeStatus.ABANDONED));

  private final EnumSet<ListChangesOption> s;

  ChangeInfoMapper(EnumSet<ListChangesOption> s) {
    this.s = s;
  }

  ChangeInfo map(ChangeJson.ChangeInfo i) {
    ChangeInfo o = new ChangeInfo();
    mapCommon(i, o);
    if (has(LABELS) || has(DETAILED_LABELS)) {
      mapLabels(i, o);
    }
    if (has(MESSAGES)) {
      mapMessages(i, o);
    }
    if (has(ALL_REVISIONS) || has(CURRENT_REVISION)) {
      o.revisions = i.revisions;
    }
    if (has(CURRENT_ACTIONS)) {
      o.actions = i.actions;
    }
    return o;
  }

  private void mapCommon(ChangeJson.ChangeInfo i, ChangeInfo o) {
    o.id = i.id;
    o.project = i.project;
    o.branch = i.branch;
    o.topic = i.topic;
    o.changeId = i.changeId;
    o.subject = i.subject;
    o.status = MAP.get(i.status);
    o.created = i.created;
    o.updated = i.updated;
    o.starred = i.starred;
    o.reviewed = i.reviewed;
    o.mergeable = i.mergeable;
    o.insertions = i.insertions;
    o.deletions = i.deletions;
    o.owner = fromAcountInfo(i.owner);
    o.currentRevision = i.currentRevision;
  }

  private void mapMessages(ChangeJson.ChangeInfo i, ChangeInfo o) {
    List<ChangeMessageInfo> r =
        Lists.newArrayListWithCapacity(i.messages.size());
    for (ChangeJson.ChangeMessageInfo m : i.messages) {
      ChangeMessageInfo cmi = new ChangeMessageInfo();
      cmi.id = m.id;
      cmi.author = fromAcountInfo(m.author);
      cmi.date = m.date;
      cmi.message = m.message;
      cmi._revisionNumber = m._revisionNumber;
      r.add(cmi);
    }
    o.messages = r;
  }

  private void mapLabels(ChangeJson.ChangeInfo i, ChangeInfo o) {
    Map<String, LabelInfo> r = Maps.newLinkedHashMap();
    for (Map.Entry<String, ChangeJson.LabelInfo> e : i.labels.entrySet()) {
      ChangeJson.LabelInfo li = e.getValue();
      LabelInfo lo = new LabelInfo();
      lo.approved = fromAcountInfo(li.approved);
      lo.rejected = fromAcountInfo(li.rejected);
      lo.recommended = fromAcountInfo(li.recommended);
      lo.disliked = fromAcountInfo(li.disliked);
      lo.value = li.value;
      lo.optional = li.optional;
      lo.blocking = li.blocking;
      lo.values = li.values;
      if (li.all != null) {
        lo.all = Lists.newArrayListWithExpectedSize(li.all.size());
        for (ChangeJson.ApprovalInfo ai : li.all) {
          lo.all.add(fromApprovalInfo(ai));
        }
      }
      r.put(e.getKey(), lo);
    }
    o.labels = r;
  }

  private boolean has(ListChangesOption o) {
    return s.contains(o);
  }

  private static ApprovalInfo fromApprovalInfo(ChangeJson.ApprovalInfo ai) {
    ApprovalInfo ao = new ApprovalInfo();
    ao.value = ai.value;
    ao.date = ai.date;
    fromAccount(ai, ao);
    return ao;
  }

  private static AccountInfo fromAcountInfo(
      com.google.gerrit.server.account.AccountInfo i) {
    if (i == null) {
      return null;
    }
    AccountInfo ai = new AccountInfo();
    fromAccount(i, ai);
    return ai;
  }

  private static void fromAccount(
      com.google.gerrit.server.account.AccountInfo i, AccountInfo ai) {
    ai._accountId = i._accountId;
    ai.email = i.email;
    ai.name = i.name;
    ai.username = i.username;
  }
}
