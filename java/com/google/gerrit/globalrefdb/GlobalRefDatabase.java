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

package com.google.gerrit.globalrefdb;

import com.google.gerrit.common.UsedAt;
import com.google.gerrit.reviewdb.client.Project;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

@UsedAt(UsedAt.Project.GERRITFORGE)
public interface GlobalRefDatabase {

  /**
   * Check in global ref-db if ref is up-to-date
   *
   * @param project project name of the ref
   * @param ref to be checked against global ref-db
   * @return true if it is; false otherwise
   * @throws GlobalRefDbLockException implementation must handle operation atomicity if there was a
   *     problem with locking ref in the ref db exception will be thrown
   */
  boolean isUpToDate(Project.NameKey project, Ref ref) throws GlobalRefDbLockException;

  /**
   * Compare a reference, and put if it is up-to-date with the current.
   *
   * <p>Two reference match if and only if they satisfy the following:
   *
   * <ul>
   *   <li>If one reference is a symbolic ref, the other one should be a symbolic ref.
   *   <li>If both are symbolic refs, the target names should be same.
   *   <li>If both are object ID refs, the object IDs should be same.
   * </ul>
   *
   * Compare and put are executed as an atomic operation.
   *
   * @param project project name of the ref
   * @param currRef old value to compare to. If the reference is expected to not exist the old value
   *     has a storage of {@link org.eclipse.jgit.lib.Ref.Storage#NEW} and an ObjectId value of
   *     {@code null}.
   * @param newRefValue new reference to store.
   * @return true if the put was successful; false otherwise.
   * @throws java.io.IOException the reference cannot be put due to a system error.
   */
  boolean compareAndPut(Project.NameKey project, Ref currRef, ObjectId newRefValue)
      throws IOException;

  /**
   * Lock a reference.
   *
   * @param project project name
   * @param refName ref to lock
   * @return lock object
   * @throws GlobalRefDbLockException if the lock cannot be obtained
   */
  AutoCloseable lockRef(Project.NameKey project, String refName) throws GlobalRefDbLockException;

  /**
   * Verify if the DB contains a value for the specific project and ref name
   *
   * @param project
   * @param refName
   * @return true if the ref exists on the project
   */
  boolean exists(Project.NameKey project, String refName);

  /**
   * Clean project path from global-ref db
   *
   * @param project project name
   * @throws IOException
   */
  void remove(Project.NameKey project) throws IOException;
}
