// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.plugins.checkers.acceptance.testsuite;

import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * An aggregation of operations on checkers for test purposes.
 *
 * <p>To execute the operations, no Gerrit permissions are necessary.
 *
 * <p><strong>Note:</strong> This interface is not implemented using the REST or extension API.
 * Hence, it cannot be used for testing those APIs.
 */
public interface CheckerOperations {
  /**
   * Starts the fluent chain for querying or modifying a checker. Please see the methods of {@link
   * PerCheckerOperations} for details on possible operations.
   *
   * @return an aggregation of operations on a specific checker
   */
  PerCheckerOperations checker(String checkerUuid);

  /**
   * Starts the fluent chain to create a checker. The returned builder can be used to specify the
   * attributes of the new checker. To create the checker for real, {@link
   * TestCheckerCreation.Builder#create()} must be called.
   *
   * <p>Example:
   *
   * <pre>
   * String createdCheckerUuid = checkerOperations
   *     .newChecker()
   *     .name("my-checker")
   *     .description("A simple checker.")
   *     .create();
   * </pre>
   *
   * <p><strong>Note:</strong> If another checker with the provided name already exists, the
   * creation of the checker will succeed since checker names are not unique.
   *
   * @return a builder to create the new checker
   */
  TestCheckerCreation.Builder newChecker();

  /** An aggregation of methods on a specific checker. */
  interface PerCheckerOperations {

    /**
     * Checks whether the checker exists.
     *
     * @return {@code true} if the checker exists
     */
    boolean exists();

    /**
     * Retrieves the checker.
     *
     * <p><strong>Note:</strong> This call will fail with an exception if the requested checker
     * doesn't exist. If you want to check for the existence of a checker, use {@link #exists()}
     * instead.
     *
     * @return the corresponding {@code TestChecker}
     */
    TestChecker get();

    /**
     * Retrieves the tip commit of the checker ref.
     *
     * <p><strong>Note:</strong>This call will fail with an exception if the checker doesn't exist.
     *
     * @return the tip commit of the checker ref
     * @throws IOException if reading the commit fails
     */
    RevCommit commit() throws IOException;

    /**
     * Retrieves the checker config as text.
     *
     * <p>This call reads the checker config from the checker ref and returns it as text.
     *
     * <p><strong>Note:</strong>This call will fail with an exception if the checker doesn't exist.
     *
     * @return the checker config as text
     * @throws IOException if reading the checker config fails
     * @throws ConfigInvalidException if the checker config is invalid
     */
    String configText() throws IOException, ConfigInvalidException;
  }
}
