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
import {DraftInfo, UIDraft} from '../../utils/comment-util';
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
  updateStateReset,
} from './comments-model';
import {changeNum$, currentPatchNum$} from '../change/change-model';
import {combineLatest} from 'rxjs';

export class CommentsService {
  private discardedDrafts?: UIDraft[] = [];

  private changeNum?: NumericChangeId;

  private patchNum?: PatchSetNum;

  constructor(readonly restApiService: RestApiService) {
    discardedDrafts$.subscribe(
      discardedDrafts => (this.discardedDrafts = discardedDrafts)
    );
    changeNum$.subscribe(changeNum => {
      this.changeNum = changeNum;
      updateStateReset();
      this.reloadAllComments();
    });
    combineLatest([changeNum$, currentPatchNum$]).subscribe(
      ([changeNum, patchNum]) => {
        this.changeNum = changeNum;
        this.patchNum = patchNum;
        this.reloadAllPortedComments();
      }
    );
    document.addEventListener('reload', () => {
      this.reloadAllComments();
      this.reloadAllPortedComments();
    });
  }

  reloadAllComments() {
    if (!this.changeNum) return;
    this.reloadComments(this.changeNum);
    this.reloadRobotComments(this.changeNum);
    this.reloadDrafts(this.changeNum);
  }

  reloadAllPortedComments() {
    if (!this.changeNum) return;
    if (!this.patchNum) return;
    this.reloadPortedComments(this.changeNum, this.patchNum);
    this.reloadPortedDrafts(this.changeNum, this.patchNum);
  }

  reloadComments(changeNum: NumericChangeId): Promise<void> {
    return this.restApiService
      .getDiffComments(changeNum)
      .then(comments => updateStateComments(comments));
  }

  reloadRobotComments(changeNum: NumericChangeId): Promise<void> {
    return this.restApiService
      .getDiffRobotComments(changeNum)
      .then(robotComments => updateStateRobotComments(robotComments));
  }

  reloadDrafts(changeNum: NumericChangeId): Promise<void> {
    return this.restApiService
      .getDiffDrafts(changeNum)
      .then(drafts => updateStateDrafts(drafts));
  }

  reloadPortedComments(
    changeNum: NumericChangeId,
    patchNum = CURRENT as RevisionId
  ): Promise<void> {
    return this.restApiService
      .getPortedComments(changeNum, patchNum)
      .then(portedComments => updateStatePortedComments(portedComments));
  }

  reloadPortedDrafts(
    changeNum: NumericChangeId,
    patchNum = CURRENT as RevisionId
  ): Promise<void> {
    return this.restApiService
      .getPortedDrafts(changeNum, patchNum)
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
