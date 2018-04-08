// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.group.db;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.config.GerritServerId;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.notedb.NoteDbUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.RawParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** NoteDb reader for group audit log. */
@Singleton
public class AuditLogReader {
  private static final Logger log = LoggerFactory.getLogger(AuditLogReader.class);

  private final String serverId;

  @Inject
  public AuditLogReader(@GerritServerId String serverId) {
    this.serverId = serverId;
  }

  // Having separate methods for reading the two types of audit records mirrors the split in
  // ReviewDb. Once ReviewDb is gone, the audit record interface becomes more flexible and we can
  // revisit this, e.g. to do only a single walk, or even change the record types.

  public ImmutableList<AccountGroupMemberAudit> getMembersAudit(
      Repository repo, AccountGroup.UUID uuid) throws IOException, ConfigInvalidException {
    return getMembersAudit(getGroupId(repo, uuid), parseCommits(repo, uuid));
  }

  private ImmutableList<AccountGroupMemberAudit> getMembersAudit(
      AccountGroup.Id groupId, List<ParsedCommit> commits) {
    ListMultimap<MemberKey, AccountGroupMemberAudit> audits =
        MultimapBuilder.hashKeys().linkedListValues().build();
    ImmutableList.Builder<AccountGroupMemberAudit> result = ImmutableList.builder();
    for (ParsedCommit pc : commits) {
      for (Account.Id id : pc.addedMembers()) {
        MemberKey key = MemberKey.create(groupId, id);
        AccountGroupMemberAudit audit =
            new AccountGroupMemberAudit(
                new AccountGroupMemberAudit.Key(id, groupId, pc.when()), pc.authorId());
        audits.put(key, audit);
        result.add(audit);
      }
      for (Account.Id id : pc.removedMembers()) {
        List<AccountGroupMemberAudit> adds = audits.get(MemberKey.create(groupId, id));
        if (!adds.isEmpty()) {
          AccountGroupMemberAudit audit = adds.remove(0);
          audit.removed(pc.authorId(), pc.when());
        } else {
          // Match old behavior of DbGroupAuditListener and add a "legacy" add/remove pair.
          AccountGroupMemberAudit audit =
              new AccountGroupMemberAudit(
                  new AccountGroupMemberAudit.Key(id, groupId, pc.when()), pc.authorId());
          audit.removedLegacy();
          result.add(audit);
        }
      }
    }
    return result.build();
  }

  public ImmutableList<AccountGroupByIdAud> getSubgroupsAudit(
      Repository repo, AccountGroup.UUID uuid) throws IOException, ConfigInvalidException {
    return getSubgroupsAudit(getGroupId(repo, uuid), parseCommits(repo, uuid));
  }

  private ImmutableList<AccountGroupByIdAud> getSubgroupsAudit(
      AccountGroup.Id groupId, List<ParsedCommit> commits) {
    ListMultimap<SubgroupKey, AccountGroupByIdAud> audits =
        MultimapBuilder.hashKeys().linkedListValues().build();
    ImmutableList.Builder<AccountGroupByIdAud> result = ImmutableList.builder();
    for (ParsedCommit pc : commits) {
      for (AccountGroup.UUID uuid : pc.addedSubgroups()) {
        SubgroupKey key = SubgroupKey.create(groupId, uuid);
        AccountGroupByIdAud audit =
            new AccountGroupByIdAud(
                new AccountGroupByIdAud.Key(groupId, uuid, pc.when()), pc.authorId());
        audits.put(key, audit);
        result.add(audit);
      }
      for (AccountGroup.UUID uuid : pc.removedSubgroups()) {
        List<AccountGroupByIdAud> adds = audits.get(SubgroupKey.create(groupId, uuid));
        if (!adds.isEmpty()) {
          AccountGroupByIdAud audit = adds.remove(0);
          audit.removed(pc.authorId(), pc.when());
        } else {
          // Unlike members, DbGroupAuditListener didn't insert an add/remove pair here.
        }
      }
    }
    return result.build();
  }

  private Optional<ParsedCommit> parse(AccountGroup.UUID uuid, RevCommit c) {
    Optional<Account.Id> authorId = NoteDbUtil.parseIdent(c.getAuthorIdent(), serverId);
    if (!authorId.isPresent()) {
      // Only report audit events from identified users, since this is a non-nullable field in
      // ReviewDb. May be revisited after groups are fully migrated to NoteDb.
      return Optional.empty();
    }

    List<Account.Id> addedMembers = new ArrayList<>();
    List<AccountGroup.UUID> addedSubgroups = new ArrayList<>();
    List<Account.Id> removedMembers = new ArrayList<>();
    List<AccountGroup.UUID> removedSubgroups = new ArrayList<>();

    for (FooterLine line : c.getFooterLines()) {
      if (line.matches(GroupConfigCommitMessage.FOOTER_ADD_MEMBER)) {
        parseAccount(uuid, c, line).ifPresent(addedMembers::add);
      } else if (line.matches(GroupConfigCommitMessage.FOOTER_REMOVE_MEMBER)) {
        parseAccount(uuid, c, line).ifPresent(removedMembers::add);
      } else if (line.matches(GroupConfigCommitMessage.FOOTER_ADD_GROUP)) {
        parseGroup(uuid, c, line).ifPresent(addedSubgroups::add);
      } else if (line.matches(GroupConfigCommitMessage.FOOTER_REMOVE_GROUP)) {
        parseGroup(uuid, c, line).ifPresent(removedSubgroups::add);
      }
    }
    return Optional.of(
        new AutoValue_AuditLogReader_ParsedCommit(
            authorId.get(),
            new Timestamp(c.getAuthorIdent().getWhen().getTime()),
            ImmutableList.copyOf(addedMembers),
            ImmutableList.copyOf(removedMembers),
            ImmutableList.copyOf(addedSubgroups),
            ImmutableList.copyOf(removedSubgroups)));
  }

  private Optional<Account.Id> parseAccount(AccountGroup.UUID uuid, RevCommit c, FooterLine line) {
    Optional<Account.Id> result =
        Optional.ofNullable(RawParseUtils.parsePersonIdent(line.getValue()))
            .flatMap(ident -> NoteDbUtil.parseIdent(ident, serverId));
    if (!result.isPresent()) {
      logInvalid(uuid, c, line);
    }
    return result;
  }

  private static Optional<AccountGroup.UUID> parseGroup(
      AccountGroup.UUID uuid, RevCommit c, FooterLine line) {
    PersonIdent ident = RawParseUtils.parsePersonIdent(line.getValue());
    if (ident == null) {
      logInvalid(uuid, c, line);
      return Optional.empty();
    }
    return Optional.of(new AccountGroup.UUID(ident.getEmailAddress()));
  }

  private static void logInvalid(AccountGroup.UUID uuid, RevCommit c, FooterLine line) {
    log.debug(
        "Invalid footer line in commit {} while parsing audit log for group {}: {}",
        c.name(),
        uuid,
        line);
  }

  private ImmutableList<ParsedCommit> parseCommits(Repository repo, AccountGroup.UUID uuid)
      throws IOException {
    try (RevWalk rw = new RevWalk(repo)) {
      Ref ref = repo.exactRef(RefNames.refsGroups(uuid));
      if (ref == null) {
        return ImmutableList.of();
      }

      rw.reset();
      rw.markStart(rw.parseCommit(ref.getObjectId()));
      rw.setRetainBody(true);
      rw.sort(RevSort.COMMIT_TIME_DESC, true);
      rw.sort(RevSort.REVERSE, true);

      ImmutableList.Builder<ParsedCommit> result = ImmutableList.builder();
      RevCommit c;
      while ((c = rw.next()) != null) {
        parse(uuid, c).ifPresent(result::add);
      }
      return result.build();
    }
  }

  private AccountGroup.Id getGroupId(Repository repo, AccountGroup.UUID uuid)
      throws ConfigInvalidException, IOException {
    // TODO(dborowitz): This re-walks all commits just to find createdOn, which we don't need.
    return GroupConfig.loadForGroup(repo, uuid).getLoadedGroup().get().getId();
  }

  @AutoValue
  abstract static class MemberKey {
    static MemberKey create(AccountGroup.Id groupId, Account.Id memberId) {
      return new AutoValue_AuditLogReader_MemberKey(groupId, memberId);
    }

    abstract AccountGroup.Id groupId();

    abstract Account.Id memberId();
  }

  @AutoValue
  abstract static class SubgroupKey {
    static SubgroupKey create(AccountGroup.Id groupId, AccountGroup.UUID subgroupUuid) {
      return new AutoValue_AuditLogReader_SubgroupKey(groupId, subgroupUuid);
    }

    abstract AccountGroup.Id groupId();

    abstract AccountGroup.UUID subgroupUuid();
  }

  @AutoValue
  abstract static class ParsedCommit {
    abstract Account.Id authorId();

    abstract Timestamp when();

    abstract ImmutableList<Account.Id> addedMembers();

    abstract ImmutableList<Account.Id> removedMembers();

    abstract ImmutableList<AccountGroup.UUID> addedSubgroups();

    abstract ImmutableList<AccountGroup.UUID> removedSubgroups();
  }
}
