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

import {ChangeComments} from '../../elements/diff/gr-comment-api/gr-comment-api';
import {
  NumericChangeId,
  PatchSetNum,
  PathToRobotCommentsInfoMap,
} from '../../types/common';
import {CURRENT} from '../../utils/patch-set-util';
import {appContext} from '../app-context';
import {changeCommentsUpdateState} from './comments-model';

export class CommentsService {

  private changeComments?: ChangeComments;

  private readonly restApiService = appContext.restApiService;

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
        changeCommentsUpdateState(this.changeComments);
        return this.changeComments;
      }
    );
  }
}
