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

package com.google.gerrit.git.testing;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.truth.Fact.fact;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.gerrit.git.testing.PushResultSubject.RemoteRefUpdateSubject.refs;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.StreamSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.common.Nullable;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;

public class PushResultSubject extends Subject {
  public static PushResultSubject assertThat(PushResult actual) {
    return assertAbout(PushResultSubject::new).that(actual);
  }

  private final PushResult pushResult;

  private PushResultSubject(FailureMetadata metadata, PushResult pushResult) {
    super(metadata, pushResult);
    this.pushResult = pushResult;
  }

  public void hasNoMessages() {
    isNotNull();
    check("hasNoMessages()").that(Strings.nullToEmpty(getTrimmedMessages())).isEqualTo("");
  }

  public void hasMessages(String... expectedLines) {
    checkArgument(expectedLines.length > 0, "use hasNoMessages()");
    isNotNull();
    check("getTrimmedMessages()")
        .that(getTrimmedMessages())
        .isEqualTo(String.join("\n", expectedLines));
  }

  public void containsMessages(String... expectedLines) {
    checkArgument(expectedLines.length > 0, "use hasNoMessages()");
    isNotNull();
    Iterable<String> got = Splitter.on("\n").split(getTrimmedMessages());
    check("getTrimmedMessages()").that(got).containsAtLeastElementsIn(expectedLines).inOrder();
  }

  private String getTrimmedMessages() {
    return trimMessages(pushResult.getMessages());
  }

  @VisibleForTesting
  @Nullable
  static String trimMessages(@Nullable String msg) {
    if (msg == null) {
      return null;
    }
    int idx = msg.indexOf("Processing changes:");
    if (idx >= 0) {
      msg = msg.substring(0, idx);
    }
    return msg.trim();
  }

  public void hasProcessed(ImmutableMap<String, Integer> expected) {
    isNotNull();
    ImmutableMap<String, Integer> actual;
    String messages = pushResult.getMessages();
    try {
      actual = parseProcessed(messages);
    } catch (RuntimeException e) {
      failWithActual(
          fact(
              "failed to parse \"Processing changes\" line from messages, reason:",
              Throwables.getStackTraceAsString(e)));
      return;
    }
    check("processedCommands()").that(actual).containsExactlyEntriesIn(expected).inOrder();
  }

  @VisibleForTesting
  static ImmutableMap<String, Integer> parseProcessed(@Nullable String messages) {
    if (messages == null) {
      return ImmutableMap.of();
    }
    String toSplit = messages.trim();
    String prefix = "Processing changes: ";
    int idx = toSplit.lastIndexOf(prefix);
    if (idx < 0) {
      return ImmutableMap.of();
    }
    toSplit = toSplit.substring(idx + prefix.length());
    if (toSplit.equals("done")) {
      return ImmutableMap.of();
    }
    String done = ", done";
    if (toSplit.endsWith(done)) {
      toSplit = toSplit.substring(0, toSplit.length() - done.length());
    }
    return ImmutableMap.copyOf(
        Maps.transformValues(
            Splitter.on(',').trimResults().withKeyValueSeparator(':').split(toSplit),
            // trimResults() doesn't trim values in the map.
            v -> Integer.parseInt(v.trim())));
  }

  public RemoteRefUpdateSubject ref(String refName) {
    isNotNull();
    return check("getRemoteUpdate(%s)", refName)
        .about(refs())
        .that(pushResult.getRemoteUpdate(refName));
  }

  public RemoteRefUpdateSubject onlyRef(String refName) {
    isNotNull();
    check("setOfRefs()")
        .about(StreamSubject.streams())
        .that(pushResult.getRemoteUpdates().stream().map(RemoteRefUpdate::getRemoteName))
        .containsExactly(refName);
    return ref(refName);
  }

  public static class RemoteRefUpdateSubject extends Subject {
    private final RemoteRefUpdate remoteRefUpdate;

    private RemoteRefUpdateSubject(FailureMetadata metadata, RemoteRefUpdate remoteRefUpdate) {
      super(metadata, remoteRefUpdate);
      this.remoteRefUpdate = remoteRefUpdate;
    }

    static Factory<RemoteRefUpdateSubject, RemoteRefUpdate> refs() {
      return RemoteRefUpdateSubject::new;
    }

    public void hasStatus(RemoteRefUpdate.Status status) {
      isNotNull();
      RemoteRefUpdate u = remoteRefUpdate;
      check("getStatus()")
          .withMessage(
              "status message: %s", u.getMessage() != null ? ": " + u.getMessage() : "<emtpy>")
          .that(u.getStatus())
          .isEqualTo(status);
    }

    public void hasNoMessage() {
      isNotNull();
      check("getMessage()").that(remoteRefUpdate.getMessage()).isNull();
    }

    public void hasMessage(String expected) {
      isNotNull();
      check("getMessage()").that(remoteRefUpdate.getMessage()).isEqualTo(expected);
    }

    public void isOk() {
      isNotNull();
      hasStatus(RemoteRefUpdate.Status.OK);
    }

    public void isRejected(String expectedMessage) {
      isNotNull();
      hasStatus(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
      hasMessage(expectedMessage);
    }
  }
}
