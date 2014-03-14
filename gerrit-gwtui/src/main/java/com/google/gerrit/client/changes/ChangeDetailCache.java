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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.actions.ActionInfo;
import com.google.gerrit.client.changes.ChangeInfo.CommitInfo;
import com.google.gerrit.client.changes.ChangeInfo.GitPerson;
import com.google.gerrit.client.changes.ChangeInfo.MessageInfo;
import com.google.gerrit.client.changes.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.client.ui.ListenableValue;
import com.google.gerrit.common.data.AccountInfo;
import com.google.gerrit.common.data.AccountInfoCache;
import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.common.data.PatchSetDetail;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.common.data.UiCommandDetail;
import com.google.gerrit.extensions.common.ListChangesOption;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.PatchSetInfo.ParentInfo;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.client.UserIdentity;
import com.google.gwt.core.client.JsArray;
import com.google.gwtjsonrpc.common.AsyncCallback;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class ChangeDetailCache extends ListenableValue<ChangeDetail> {
  public static class NewGerritCallback extends
      com.google.gerrit.client.rpc.GerritCallback<ChangeInfo> {
    @Override
    public void onSuccess(ChangeInfo detail) {
      setChangeDetail(reverse(detail));
    }
  }

  public static class IgnoreErrorCallback implements AsyncCallback<ChangeDetail> {
    @Override
    public void onSuccess(ChangeDetail info) {
      setChangeDetail(info);
    }

    @Override
    public void onFailure(Throwable caught) {
    }
  }

  public static ChangeDetail reverse(ChangeInfo info) {
    info.revisions().copyKeysIntoChildren("name");
    RevisionInfo rev = current(info);

    ChangeDetail r = new ChangeDetail();
    r.setAllowsAnonymous(rev.has_fetch() && rev.fetch().containsKey("http"));
    r.setCanAbandon(can(info.actions(), "abandon"));
    r.setCanEditCommitMessage(can(rev.actions(), "message"));
    r.setCanCherryPick(can(rev.actions(), "cherrypick"));
    r.setCanPublish(can(rev.actions(), "publish"));
    r.setCanRebase(can(rev.actions(), "rebase"));
    r.setCanRestore(can(info.actions(), "restore"));
    r.setCanRevert(can(info.actions(), "revert"));
    r.setCanDeleteDraft(can(rev.actions(), "/"));
    r.setCanEditTopicName(can(info.actions(), "topic"));
    r.setCanSubmit(can(rev.actions(), "submit"));
    r.setCanEdit(true);
    r.setChange(toChange(info));
    r.setStarred(info.starred());
    r.setPatchSets(toPatchSets(info));
    r.setMessages(toMessages(info));
    r.setAccounts(users(info));
    r.setCurrentPatchSetId(new PatchSet.Id(info.legacy_id(), rev._number()));
    r.setCurrentPatchSetDetail(toPatchSetDetail(info));
    r.setSubmitRecords(new ArrayList<SubmitRecord>());

    // Obtained later in ChangeScreen.
    r.setSubmitTypeRecord(new SubmitTypeRecord());
    r.getSubmitTypeRecord().status = SubmitTypeRecord.Status.RULE_ERROR;
    r.setPatchSetsWithDraftComments(new HashSet<PatchSet.Id>());
    r.setDependsOn(new ArrayList<com.google.gerrit.common.data.ChangeInfo>());
    r.setNeededBy(new ArrayList<com.google.gerrit.common.data.ChangeInfo>());
    return r;
  }

  private static PatchSetDetail toPatchSetDetail(ChangeInfo info) {
    RevisionInfo rev = current(info);
    PatchSetDetail p = new PatchSetDetail();
    p.setPatchSet(toPatchSet(info, rev));
    p.setProject(info.project_name_key());
    p.setInfo(new PatchSetInfo(p.getPatchSet().getId()));
    p.getInfo().setRevId(rev.name());
    p.getInfo().setParents(new ArrayList<ParentInfo>());
    p.getInfo().setAuthor(toUser(rev.commit().author()));
    p.getInfo().setCommitter(toUser(rev.commit().committer()));
    p.getInfo().setSubject(rev.commit().subject());
    p.getInfo().setMessage(rev.commit().message());
    if (rev.commit().parents() != null) {
      for (CommitInfo c : Natives.asList(rev.commit().parents())) {
        p.getInfo().getParents().add(new ParentInfo(
            new RevId(c.commit()),
            c.subject()));
      }
    }
    p.setPatches(new ArrayList<Patch>());
    p.setCommands(new ArrayList<UiCommandDetail>());

    rev.files();
    return p;
  }

  private static UserIdentity toUser(GitPerson p) {
    UserIdentity u = new UserIdentity();
    u.setName(p.name());
    u.setEmail(p.email());
    u.setDate(p.date());
    return u;
  }

  public static AccountInfoCache users(ChangeInfo info) {
    Map<Integer, AccountInfo> r = new HashMap<>();
    add(r, info.owner());
    if (info.messages() != null) {
      for (MessageInfo m : Natives.asList(info.messages())) {
        add(r, m.author());
      }
    }
    return new AccountInfoCache(r.values());
  }

  private static void add(Map<Integer, AccountInfo> r,
      com.google.gerrit.client.account.AccountInfo user) {
    if (user != null && !r.containsKey(user._account_id())) {
      AccountInfo a = new AccountInfo(new Account.Id(user._account_id()));
      a.setPreferredEmail(user.email());
      a.setFullName(user.name());
      r.put(user._account_id(), a);
    }
  }

  private static boolean can(NativeMap<ActionInfo> m, String n) {
    return m != null && m.containsKey(n) && m.get(n).enabled();
  }

  private static List<ChangeMessage> toMessages(ChangeInfo info) {
    List<ChangeMessage> msgs = new ArrayList<>();
    for (MessageInfo m : Natives.asList(info.messages())) {
      ChangeMessage o = new ChangeMessage(
          new ChangeMessage.Key(
              info.legacy_id(),
              m.date().toString()),
          m.author() != null
            ? new Account.Id(m.author()._account_id())
            : null,
          m.date(),
          m._revisionNumber() > 0
            ? new PatchSet.Id(info.legacy_id(), m._revisionNumber())
            : null);
      o.setMessage(m.message());
      msgs.add(o);
    }
    return msgs;
  }

  private static List<PatchSet> toPatchSets(ChangeInfo info) {
    JsArray<RevisionInfo> all = info.revisions().values();
    RevisionInfo.sortRevisionInfoByNumber(all);

    List<PatchSet> r = new ArrayList<>(all.length());
    for (RevisionInfo rev : Natives.asList(all)) {
      r.add(toPatchSet(info, rev));
    }
    return r;
  }

  private static PatchSet toPatchSet(ChangeInfo info, RevisionInfo rev) {
    PatchSet p = new PatchSet(
        new PatchSet.Id(info.legacy_id(), rev._number()));
    p.setCreatedOn(rev.commit().committer().date());
    p.setDraft(rev.draft());
    p.setRevision(new RevId(rev.name()));
    return p;
  }

  public static Change toChange(ChangeInfo info) {
    RevisionInfo rev = current(info);
    PatchSetInfo p = new PatchSetInfo(
      new PatchSet.Id(
          info.legacy_id(),
          rev._number()));
    p.setSubject(info.subject());
    Change c = new Change(
        new Change.Key(info.change_id()),
        info.legacy_id(),
        new Account.Id(info.owner()._account_id()),
        new Branch.NameKey(
            info.project_name_key(),
            info.branch()),
        info.created());
    c.setTopic(info.topic());
    c.setStatus(info.status());
    c.setCurrentPatchSet(p);
    c.setLastUpdatedOn(info.updated());
    c.setMergeable(info.mergeable());
    return c;
  }

  private static RevisionInfo current(ChangeInfo info) {
    RevisionInfo rev = info.revision(info.current_revision());
    if (rev == null) {
      JsArray<RevisionInfo> all = info.revisions().values();
      RevisionInfo.sortRevisionInfoByNumber(all);
      rev = all.get(all.length() - 1);
    }
    return rev;
  }

  public static void setChangeDetail(ChangeDetail detail) {
    Change.Id chgId = detail.getChange().getId();
    ChangeCache.get(chgId).getChangeDetailCache().set(detail);
    StarredChanges.fireChangeStarEvent(chgId, detail.isStarred());
  }

  private final Change.Id changeId;

  public ChangeDetailCache(final Change.Id chg) {
    changeId = chg;
  }

  public void refresh() {
    RestApi call = ChangeApi.detail(changeId.get());
    ChangeList.addOptions(call, EnumSet.of(
      ListChangesOption.CURRENT_ACTIONS,
      ListChangesOption.ALL_REVISIONS,
      ListChangesOption.ALL_COMMITS));
    call.get(new NewGerritCallback());
  }
}
