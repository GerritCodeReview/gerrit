/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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

import {ChangeComments} from '../../elements/diff/gr-comment-api/gr-comment-api';
import {
  NumericChangeId,
  PatchSetNum,
  PathToRobotCommentsInfoMap,
} from '../../types/common';
import {DraftInfo} from '../../utils/comment-util';
import {CURRENT} from '../../utils/patch-set-util';
import {appContext} from '../app-context';
import {changeNum$} from '../change/change-model';
import {updateState} from './comments-model';

export class CommentsService {
  private changeNum?: NumericChangeId;

  private changeComments?: ChangeComments;

  private readonly restApiService = appContext.restApiService;

  constructor() {
    changeNum$.subscribe(changeNum => {
      this.changeNum = changeNum;
    });
  }

  /**
   * Load all comments (with drafts and robot comments) for the given change
   * number. The returned promise resolves when the comments have loaded, but
   * does not yield the comment data.
   */
  loadAll(changeNum: NumericChangeId, patchNum?: PatchSetNum) {
    if (!changeNum) throw new Error('changenum undefined');
    const revision = patchNum || CURRENT;
    const commentsPromise = [
      this.restApiService.getDiffComments(changeNum),
      this.restApiService.getDiffRobotComments(changeNum),
      this.restApiService.getDiffDrafts(changeNum),
      this.restApiService.getPortedComments(changeNum, revision),
      this.restApiService.getPortedDrafts(changeNum, revision),
    ];

    return Promise.all(commentsPromise).then(
      ([comments, robotComments, drafts, portedComments, portedDrafts]) => {
        this.changeComments = new ChangeComments(
          comments,
          // TS 4.0.5 fails without 'as'
          robotComments as PathToRobotCommentsInfoMap | undefined,
          drafts,
          portedComments,
          portedDrafts
        );
        updateState(this.changeComments);
        return this.changeComments;
      }
    );
  }

  addDraft(draft: DraftInfo) {
    if (!draft.path) throw new Error('draft path undefined');
    if (!this.changeNum) throw new Error('change num undefined');
    if (!this.changeComments) {
      this.loadAll(this.changeNum);
      return;
    }
    const drafts = this.changeComments.drafts;
    if (!drafts[draft.path]) drafts[draft.path] = [];
    const index = drafts[draft.path].findIndex(
      d => d.__draftID === draft.__draftID || d.id === draft.id
    );
    if (index !== -1) {
      drafts[draft.path][index] = draft;
    } else {
      drafts[draft.path].push(draft);
    }
    this.changeComments = this.changeComments.cloneWithUpdatedDrafts(drafts);
    updateState(this.changeComments);
  }

  deleteDraft(draft: DraftInfo) {
    if (!draft.path) throw new Error('draft path undefined');
    if (!this.changeNum) throw new Error('change num undefined');
    if (!this.changeComments) {
      this.loadAll(this.changeNum);
      return;
    }
    const drafts = this.changeComments.drafts;
    const index = drafts[draft.path].findIndex(
      d => d.__draftID === draft.__draftID || d.id === draft.id
    );
    if (index === -1) return;
    this.changeComments = this.changeComments.cloneWithUpdatedDrafts(drafts);
    updateState(this.changeComments);
  }
}
