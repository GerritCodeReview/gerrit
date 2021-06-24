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
import {DraftInfo, UIDraft} from '../../utils/comment-util';
import {drafts$, updateState} from './comments-model';

export class CommentsService {
  private drafts: {[path: string]: UIDraft[]} = {};

  constructor() {
    drafts$.subscribe(drafts => {
      this.drafts = drafts;
    });
  }

  changeCommentsUpdate(changeComments: ChangeComments) {
    updateState(changeComments.drafts);
  }

  addDraft(draft: DraftInfo) {
    if (!draft.path) throw new Error('draft path undefined');
    const index = this.drafts[draft.path].findIndex(
      d => d.__draftID === draft.__draftID || d.id === draft.id
    );
    if (index !== -1) {
      this.drafts[draft.path][index] = draft;
    } else {
      this.drafts[draft.path].push(draft);
    }
    updateState(this.drafts);
  }

  deleteDraft(draft: DraftInfo) {
    if (!draft.path) throw new Error('draft path undefined');
    const index = this.drafts[draft.path].findIndex(
      d => d.__draftID === draft.__draftID || d.id === draft.id
    );
    if (index === -1) throw new Error('draft not found');
    this.drafts[draft.path].splice(index, 1);
    updateState(this.drafts);
  }
}
