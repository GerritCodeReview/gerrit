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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Optional;

/**
 * Class to keep track of operations which are currently running. Allows to get the callers (aka
 * parent operations) of an operation for logging.
 *
 * <p>This class is not thread safe.
 */
public class RunningOperations {
  public interface RegistrationHandle {
    ImmutableList<String> parentOperations();

    /** Delete this registration. */
    void remove();
  }

  private final ArrayList<Operation> operations;

  public RunningOperations() {
    this.operations = new ArrayList<>();
  }

  public RegistrationHandle add(String operationName, Metadata metadata) {
    // Remember the operations that were running at the moment when the new operation is added.
    ImmutableList<String> parentOperations = toOperationNames();

    Operation operation = new Operation(operationName, Optional.of(metadata));
    operations.add(operation);

    return new RegistrationHandle() {
      @Override
      public ImmutableList<String> parentOperations() {
        return parentOperations;
      }

      @Override
      public void remove() {
        operations.remove(operation);
      }
    };
  }

  public ImmutableList<String> toOperationNames() {
    return operations.stream().map(Operation::toString).collect(toImmutableList());
  }

  public boolean isEmtpy() {
    return operations.isEmpty();
  }

  /** Makes a copy of this instance to be used in other threads. */
  public RunningOperations copy() {
    RunningOperations runningOperations = new RunningOperations();
    runningOperations.operations.addAll(operations);
    return runningOperations;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("operations", toOperationNames()).toString();
  }
}
