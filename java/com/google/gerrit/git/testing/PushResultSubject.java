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

import static com.google.common.truth.Truth.assertAbout;
import static java.util.stream.Collectors.joining;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import com.google.common.truth.Truth8;
import com.google.gerrit.common.Nullable;
import java.util.Arrays;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;

public class PushResultSubject extends Subject<PushResultSubject, PushResult> {
  public static PushResultSubject assertThat(PushResult actual) {
    return assertAbout(PushResultSubject::new).that(actual);
  }

  private PushResultSubject(FailureMetadata metadata, PushResult actual) {
    super(metadata, actual);
  }

  public void hasMessages(String... expectedLines) {
    isNotNull();
    Truth.assertThat(trimMessages()).isEqualTo(Arrays.stream(expectedLines).collect(joining("\n")));
  }

  private String trimMessages() {
    return trimMessages(actual().getMessages());
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
    ImmutableMap<String, Integer> actual;
    String messages = actual().getMessages();
    try {
      actual = parseProcessed(messages);
    } catch (RuntimeException e) {
      Truth.assert_()
          .fail(
              "failed to parse \"Processing changes\" line from messages: %s\n%s",
              messages, Throwables.getStackTraceAsString(e));
      return;
    }
    Truth.assertThat(actual)
        .named("processed commands")
        .containsExactlyEntriesIn(expected)
        .inOrder();
  }

  @VisibleForTesting
  static ImmutableMap<String, Integer> parseProcessed(@Nullable String messages) {
    if (messages == null) {
      return ImmutableMap.of();
    }
    String prefix = "Processing changes:";
    int idx = messages.lastIndexOf(prefix);
    if (idx < 0) {
      return ImmutableMap.of();
    }
    return ImmutableMap.copyOf(
        Maps.transformValues(
            Splitter.on(',')
                .trimResults()
                .withKeyValueSeparator(':')
                .split(messages.substring(idx + prefix.length()).replace(", done", "")),
            // trimResults() doesn't trim values in the map.
            v -> Integer.parseInt(v.trim())));
  }

  public RemoteRefUpdateSubject ref(String refName) {
    return assertAbout(
            (FailureMetadata m, RemoteRefUpdate a) -> new RemoteRefUpdateSubject(refName, m, a))
        .that(actual().getRemoteUpdate(refName));
  }

  public RemoteRefUpdateSubject onlyRef(String refName) {
    Truth8.assertThat(actual().getRemoteUpdates().stream().map(RemoteRefUpdate::getRemoteName))
        .named("set of refs")
        .containsExactly(refName);
    return ref(refName);
  }

  public static class RemoteRefUpdateSubject
      extends Subject<RemoteRefUpdateSubject, RemoteRefUpdate> {
    private final String refName;

    private RemoteRefUpdateSubject(
        String refName, FailureMetadata metadata, RemoteRefUpdate actual) {
      super(metadata, actual);
      this.refName = refName;
      named("ref update for %s", refName).isNotNull();
    }

    public void hasStatus(RemoteRefUpdate.Status status) {
      Truth.assertThat(actual().getStatus())
          .named("status of ref update for %s", refName)
          .isEqualTo(status);
    }

    public void hasNoMessage() {
      Truth.assertThat(actual().getMessage())
          .named("message of ref update for %s", refName)
          .isNull();
    }

    public void hasMessage(String expected) {
      Truth.assertThat(actual().getMessage())
          .named("message of ref update for %s", refName)
          .isEqualTo(expected);
    }

    public void isOk() {
      hasStatus(RemoteRefUpdate.Status.OK);
    }

    public void isRejected(String expectedMessage) {
      hasStatus(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
      hasMessage(expectedMessage);
    }
  }
}
