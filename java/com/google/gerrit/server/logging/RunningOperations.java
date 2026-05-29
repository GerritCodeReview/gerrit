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
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.ListIterator;

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

  /**
   * Adds a new operation.
   *
   * <p>Allows to retrieve the callers of the operation (aka parent operations) via the returned
   * registration handle (see {@link RegistrationHandle#parentOperations()}), for the purpose of
   * logging them.
   *
   * <p>Callers must remove the operation when it's done by calling {@link
   * RegistrationHandle#remove()} on the returned registration handle.
   *
   * @param operationName the name of the operation that is started
   * @param metadata the metadata that should be recorded/logged for the operation
   * @return registration handle that allows to retrieve the parent operations and that must be used
   *     to remove the operation when it is done
   */
  public RegistrationHandle add(String operationName, Metadata metadata) {
    // Remember the operations that were running at the moment when the new operation is added.
    ImmutableList<String> parentOperations = toOperationNames();

    int operationId = getOperationId();
    Operation operation = new Operation(operationId, operationName, metadata);
    operations.add(operation);

    return new RegistrationHandle() {
      @Override
      public ImmutableList<String> parentOperations() {
        return parentOperations;
      }

      @Override
      public void remove() {
        // In most cases it's the last operation that needs to be removed. Iterate over the list in
        // reverse order to find it faster.
        ListIterator<Operation> listIterator = operations.listIterator(operations.size());
        while (listIterator.hasPrevious()) {
          if (listIterator.previous().id() == operationId) {
            listIterator.remove();
            return;
          }
        }
      }
    };
  }

  private int getOperationId() {
    Operation lastOperation = Iterables.getLast(operations, null);
    return lastOperation != null ? lastOperation.id() + 1 : 0;
  }

  /** Returns the names of the currently running operations. */
  public ImmutableList<String> toOperationNames() {
    return operations.stream().map(Operation::getDecoratedOperationName).collect(toImmutableList());
  }

  public boolean isEmpty() {
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

  record Operation(int id, String operationName, Metadata metadata) {
    String getDecoratedOperationName() {
      return metadata.decorateOperation(operationName);
    }
  }
}
