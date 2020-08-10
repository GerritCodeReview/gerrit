// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.update;

import java.util.Map;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * Handler of a submission, a set of reference updates over one or more repos that are being
 * submitted together.
 *
 * <p>The submission represented in this interfaces has 3 stages 1. Announce the work that is going
 * to be done 2. Update the state of work during its execution 3. Indicate the submission is
 * complete
 *
 * <p>The work to be done (step #1) is declared with the builder, that returns the submission
 * handler.
 *
 * <p>The submission handler provides the SubmissionContext that is passed to the ref updates before
 * execution. The context has the information the update needs to report the result of its execution
 * (step #2).
 *
 * <p>Finally, submitter invokes {@link #completed()} in the handler to indicate the submission is
 * done.
 */
public interface Submission {

  /**
   * Information passed to BatchRefUpdate before execution.
   *
   * @return an instance of the submission context
   */
  SubmissionContext getSubmissionContext();

  /**
   * Indicates the caller is done with this submission.
   *
   * <p>Implementors should protect agains this method not being called (e.g. on server crash).
   */
  void completed();

  interface Builder {

    /**
     * Announce work to be done as part of the submission.
     *
     * @param repository repository to be updated
     * @param refUpdates collection of update commands, grouped by ref name that are going to be
     *     executed during the submission.
     */
    void addEntry(Repository repository, Map<String, ReceiveCommand> refUpdates);

    /**
     * Invoked once all the work has been announced.
     *
     * @return a Submission handler
     */
    Submission done();
  }
}
