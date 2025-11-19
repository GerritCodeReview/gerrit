// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.logging;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.server.logging.RunningOperations.RegistrationHandle;
import org.junit.Test;

/** Unit tests for {@link RunningOperations}. */
public class RunningOperationsTest {
  @Test
  @SuppressWarnings("unused")
  public void operationsAreOrdered() {
    RunningOperations runningOperations = new RunningOperations();
    var unused = runningOperations.add("foo", Metadata.empty());
    var unused2 = runningOperations.add("bar", Metadata.empty());
    var unused3 = runningOperations.add("baz", Metadata.empty());
    assertThat(runningOperations.toOperationNames()).containsExactly("foo", "bar", "baz").inOrder();
  }

  @Test
  public void removeOperationsInOrder() {
    RunningOperations runningOperations = new RunningOperations();
    RegistrationHandle fooHandle = runningOperations.add("foo", Metadata.empty());
    RegistrationHandle barHandle = runningOperations.add("bar", Metadata.empty());
    RegistrationHandle bazHandle = runningOperations.add("baz", Metadata.empty());
    assertThat(runningOperations.toOperationNames()).containsExactly("foo", "bar", "baz").inOrder();

    bazHandle.remove();
    assertThat(runningOperations.toOperationNames()).containsExactly("foo", "bar").inOrder();

    barHandle.remove();
    assertThat(runningOperations.toOperationNames()).containsExactly("foo");

    fooHandle.remove();
    assertThat(runningOperations.toOperationNames()).isEmpty();
  }

  @Test
  public void removeOperationsOutOfOrder() {
    RunningOperations runningOperations = new RunningOperations();
    RegistrationHandle fooHandle = runningOperations.add("foo", Metadata.empty());
    RegistrationHandle barHandle = runningOperations.add("bar", Metadata.empty());
    RegistrationHandle bazHandle = runningOperations.add("baz", Metadata.empty());
    assertThat(runningOperations.toOperationNames()).containsExactly("foo", "bar", "baz").inOrder();

    barHandle.remove();
    assertThat(runningOperations.toOperationNames()).containsExactly("foo", "baz").inOrder();

    fooHandle.remove();
    assertThat(runningOperations.toOperationNames()).containsExactly("baz");

    bazHandle.remove();
    assertThat(runningOperations.toOperationNames()).isEmpty();
  }

  @Test
  public void removeOperationTwice() {
    RunningOperations runningOperations = new RunningOperations();
    RegistrationHandle fooHandle = runningOperations.add("foo", Metadata.empty());
    assertThat(runningOperations.toOperationNames()).containsExactly("foo");

    fooHandle.remove();
    assertThat(runningOperations.toOperationNames()).isEmpty();

    fooHandle.remove();
    assertThat(runningOperations.toOperationNames()).isEmpty();
  }

  @Test
  @SuppressWarnings("unused")
  public void operationsWithTheSameName() {
    RunningOperations runningOperations = new RunningOperations();
    RegistrationHandle fooHandle = runningOperations.add("foo", Metadata.empty());
    var unused = runningOperations.add("bar", Metadata.empty());
    var unused2 = runningOperations.add("foo", Metadata.empty());
    assertThat(runningOperations.toOperationNames()).containsExactly("foo", "bar", "foo").inOrder();

    fooHandle.remove();
    assertThat(runningOperations.toOperationNames()).containsExactly("bar", "foo").inOrder();
  }

  @Test
  @SuppressWarnings("unused")
  public void operationNamesAreDecorated() {
    RunningOperations runningOperations = new RunningOperations();
    var unused = runningOperations.add("foo", Metadata.builder().thread("thread").build());
    var unused2 =
        runningOperations.add(
            "plugin/latency", Metadata.builder().pluginName("plugin").className("class").build());
    assertThat(runningOperations.toOperationNames())
        .containsExactly("[thread] foo", "plugin/latency (plugin:class)")
        .inOrder();
  }
}
