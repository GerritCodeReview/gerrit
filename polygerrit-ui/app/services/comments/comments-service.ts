/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {NumericChangeId, RevisionId} from '../../types/common';
import {DraftInfo} from '../../utils/comment-util';
import {CURRENT} from '../../utils/patch-set-util';
import {RestApiService} from '../gr-rest-api/gr-rest-api';
import {
  updateStateAddDraft,
  updateStateDeleteDraft,
  updateStateComments,
  updateStateRobotComments,
  updateStateDrafts,
  updateStatePortedComments,
  updateStatePortedDrafts,
} from './comments-model';

export class CommentsService {
  constructor(readonly restApiService: RestApiService) {}

  /**
   * Load all comments (with drafts and robot comments) for the given change
   * number. The returned promise resolves when the comments have loaded, but
   * does not yield the comment data.
   */
  // TODO(dhruvsri): listen to changeNum changes or reload event to update
  // automatically
  loadAll(changeNum: NumericChangeId, patchNum = CURRENT as RevisionId) {
    const revision = patchNum;
    this.restApiService
      .getDiffComments(changeNum)
      .then(comments => updateStateComments(comments));
    this.restApiService
      .getDiffRobotComments(changeNum)
      .then(robotComments => updateStateRobotComments(robotComments));
    this.restApiService
      .getDiffDrafts(changeNum)
      .then(drafts => updateStateDrafts(drafts));
    this.restApiService
      .getPortedComments(changeNum, revision)
      .then(portedComments => updateStatePortedComments(portedComments));
    this.restApiService
      .getPortedDrafts(changeNum, revision)
      .then(portedDrafts => updateStatePortedDrafts(portedDrafts));
  }

  addDraft(draft: DraftInfo) {
    updateStateAddDraft(draft);
  }

  deleteDraft(draft: DraftInfo) {
    updateStateDeleteDraft(draft);
  }
}
