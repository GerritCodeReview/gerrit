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

import {NumericChangeId, PatchSetNum, RevisionId} from '../../types/common';
import {DraftInfo} from '../../utils/comment-util';
import {fireAlert} from '../../utils/event-util';
import {CURRENT} from '../../utils/patch-set-util';
import {RestApiService} from '../gr-rest-api/gr-rest-api';
import {
  updateStateAddDraft,
  updateStateDeleteDraft,
  updateStateUpdateDraft,
  updateStateComments,
  updateStateRobotComments,
  updateStateDrafts,
  updateStatePortedComments,
  updateStatePortedDrafts,
  updateStateUndoDiscardedDraft,
  discardedDrafts$,
} from './comments-model';

export class CommentsService {
  private discardedDrafts?: DraftInfo[] = [];

  constructor(readonly restApiService: RestApiService) {
    discardedDrafts$.subscribe(
      discardedDrafts => (this.discardedDrafts = discardedDrafts)
    );
  }

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

  restoreDraft(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    draftID: string
  ) {
    const draft = {...this.discardedDrafts?.find(d => d.id === draftID)};
    if (!draft) throw new Error('discarded draft not found');
    // delete draft ID since we want to treat this as a new draft creation
    delete draft.id;
    this.restApiService
      .saveDiffDraft(changeNum, patchNum, draft)
      .then(result => {
        if (!result.ok) {
          fireAlert(document, 'Unable to restore draft');
          return;
        }
        this.restApiService.getResponseObject(result).then(obj => {
          const resComment = obj as unknown as DraftInfo;
          resComment.patch_set = draft.patch_set;
          updateStateAddDraft(resComment);
          updateStateUndoDiscardedDraft(draftID);
        });
      });
  }

  addDraft(draft: DraftInfo) {
    updateStateAddDraft(draft);
  }

  cancelDraft(draft: DraftInfo) {
    updateStateUpdateDraft(draft);
  }

  editDraft(draft: DraftInfo) {
    updateStateUpdateDraft(draft);
  }

  deleteDraft(draft: DraftInfo) {
    updateStateDeleteDraft(draft);
  }
}
