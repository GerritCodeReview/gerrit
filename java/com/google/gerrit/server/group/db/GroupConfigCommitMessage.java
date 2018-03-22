// Copyright (C) 2018 The Android Open Source Project
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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.gerrit.server.group.InternalGroup;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import org.eclipse.jgit.revwalk.FooterKey;

/**
 * A parsable commit message for a NoteDb commit of a group.
 *
 * <p>For group creations, it's sufficient to simply call the constructor of this class. For
 * updates, {@link #setOriginalGroup(InternalGroup)} has to be called as well.
 */
class GroupConfigCommitMessage {
  static final FooterKey FOOTER_ADD_MEMBER = new FooterKey("Add");
  static final FooterKey FOOTER_REMOVE_MEMBER = new FooterKey("Remove");
  static final FooterKey FOOTER_ADD_GROUP = new FooterKey("Add-group");
  static final FooterKey FOOTER_REMOVE_GROUP = new FooterKey("Remove-group");

  private final AuditLogFormatter auditLogFormatter;
  private final InternalGroup updatedGroup;
  private Optional<InternalGroup> originalGroup = Optional.empty();

  GroupConfigCommitMessage(AuditLogFormatter auditLogFormatter, InternalGroup updatedGroup) {
    this.auditLogFormatter = auditLogFormatter;
    this.updatedGroup = updatedGroup;
  }

  public void setOriginalGroup(InternalGroup originalGroup) {
    this.originalGroup = Optional.of(originalGroup);
  }

  public String create() {
    String summaryLine = originalGroup.isPresent() ? "Update group" : "Create group";

    StringJoiner footerJoiner = new StringJoiner("\n", "\n\n", "");
    footerJoiner.setEmptyValue("");
    Streams.concat(
            Streams.stream(getFooterForRename()),
            getFootersForMemberModifications(),
            getFootersForSubgroupModifications())
        .sorted()
        .forEach(footerJoiner::add);
    String footer = footerJoiner.toString();

    return summaryLine + footer;
  }

  private Optional<String> getFooterForRename() {
    if (!originalGroup.isPresent()) {
      return Optional.empty();
    }

    String originalName = originalGroup.get().getName();
    String newName = updatedGroup.getName();
    if (originalName.equals(newName)) {
      return Optional.empty();
    }
    return Optional.of("Rename from " + originalName + " to " + newName);
  }

  private Stream<String> getFootersForMemberModifications() {
    return getFooters(
        InternalGroup::getMembers,
        AuditLogFormatter::getParsableAccount,
        FOOTER_ADD_MEMBER,
        FOOTER_REMOVE_MEMBER);
  }

  private Stream<String> getFootersForSubgroupModifications() {
    return getFooters(
        InternalGroup::getSubgroups,
        AuditLogFormatter::getParsableGroup,
        FOOTER_ADD_GROUP,
        FOOTER_REMOVE_GROUP);
  }

  private <T> Stream<String> getFooters(
      Function<InternalGroup, Set<T>> getElements,
      BiFunction<AuditLogFormatter, T, String> toParsableString,
      FooterKey additionFooterKey,
      FooterKey removalFooterKey) {
    Set<T> oldElements = originalGroup.map(getElements).orElseGet(ImmutableSet::of);
    Set<T> newElements = getElements.apply(updatedGroup);

    Function<T, String> toString = element -> toParsableString.apply(auditLogFormatter, element);

    Stream<String> removedElements =
        Sets.difference(oldElements, newElements)
            .stream()
            .map(toString)
            .map((removalFooterKey.getName() + ": ")::concat);
    Stream<String> addedElements =
        Sets.difference(newElements, oldElements)
            .stream()
            .map(toString)
            .map((additionFooterKey.getName() + ": ")::concat);
    return Stream.concat(removedElements, addedElements);
  }
}
