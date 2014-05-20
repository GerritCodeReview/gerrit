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

import com.google.common.base.Function;
import com.google.common.collect.EnumBiMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.ChangeStatus;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.server.api.accounts.AccountInfoMapper;
import com.google.gerrit.server.change.ChangeJson;

import java.util.List;
import java.util.Map;

public class ChangeInfoMapper
    implements Function<ChangeJson.ChangeInfo, ChangeInfo> {
  public static final ChangeInfoMapper INSTANCE = new ChangeInfoMapper();

  private final static EnumBiMap<Change.Status, ChangeStatus> STATUS_MAP =
      EnumBiMap.create(Change.Status.class, ChangeStatus.class);
  static {
    STATUS_MAP.put(Status.DRAFT, ChangeStatus.DRAFT);
    STATUS_MAP.put(Status.NEW, ChangeStatus.NEW);
    STATUS_MAP.put(Status.SUBMITTED, ChangeStatus.SUBMITTED);
    STATUS_MAP.put(Status.MERGED, ChangeStatus.MERGED);
    STATUS_MAP.put(Status.ABANDONED, ChangeStatus.ABANDONED);
  }

  public static Status changeStatus2Status(ChangeStatus status) {
    if (status != null) {
      return STATUS_MAP.inverse().get(status);
    }
    return Change.Status.NEW;
  }

  private ChangeInfoMapper() {
  }

  @Override
  public ChangeInfo apply(ChangeJson.ChangeInfo i) {
    ChangeInfo o = new ChangeInfo();
    mapCommon(i, o);
    mapLabels(i, o);
    mapMessages(i, o);
    o.revisions = i.revisions;
    o.actions = i.actions;
    return o;
  }

  private void mapCommon(ChangeJson.ChangeInfo i, ChangeInfo o) {
    o.id = i.id;
    o.project = i.project;
    o.branch = i.branch;
    o.topic = i.topic;
    o.changeId = i.changeId;
    o.subject = i.subject;
    o.status = STATUS_MAP.get(i.status);
    o.created = i.created;
    o.updated = i.updated;
    o.starred = i.starred;
    o.reviewed = i.reviewed;
    o.mergeable = i.mergeable;
    o.insertions = i.insertions;
    o.deletions = i.deletions;
    o.owner = AccountInfoMapper.fromAcountInfo(i.owner);
    o.currentRevision = i.currentRevision;
    o._number = i._number;
  }

  private void mapMessages(ChangeJson.ChangeInfo i, ChangeInfo o) {
    if (i.messages == null) {
      return;
    }
    List<ChangeMessageInfo> r =
        Lists.newArrayListWithCapacity(i.messages.size());
    for (ChangeJson.ChangeMessageInfo m : i.messages) {
      ChangeMessageInfo cmi = new ChangeMessageInfo();
      cmi.id = m.id;
      cmi.author = AccountInfoMapper.fromAcountInfo(m.author);
      cmi.date = m.date;
      cmi.message = m.message;
      cmi._revisionNumber = m._revisionNumber;
      r.add(cmi);
    }
    o.messages = r;
  }

  private void mapLabels(ChangeJson.ChangeInfo i, ChangeInfo o) {
    if (i.labels == null) {
      return;
    }
    Map<String, LabelInfo> r = Maps.newLinkedHashMap();
    for (Map.Entry<String, ChangeJson.LabelInfo> e : i.labels.entrySet()) {
      ChangeJson.LabelInfo li = e.getValue();
      LabelInfo lo = new LabelInfo();
      lo.approved = AccountInfoMapper.fromAcountInfo(li.approved);
      lo.rejected = AccountInfoMapper.fromAcountInfo(li.rejected);
      lo.recommended = AccountInfoMapper.fromAcountInfo(li.recommended);
      lo.disliked = AccountInfoMapper.fromAcountInfo(li.disliked);
      lo.value = li.value;
      lo.defaultValue = li.defaultValue;
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

  private static ApprovalInfo fromApprovalInfo(ChangeJson.ApprovalInfo ai) {
    ApprovalInfo ao = new ApprovalInfo();
    ao.value = ai.value;
    ao.date = ai.date;
    AccountInfoMapper.fromAccount(ai, ao);
    return ao;
  }
}
