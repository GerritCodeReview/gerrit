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
import '../../test/common-test-setup-karma';
import {createDraft} from '../../test/test-data-generators';
import {UrlEncodedCommentId} from '../../types/common';
import {DraftInfo} from '../../utils/comment-util';
import './comments-model';
import {
  updateStateDeleteDraft,
  _testOnly_getState,
  _testOnly_setState,
} from './comments-model';

suite('comments model tests', () => {
  test('updateStateDeleteDraft', () => {
    const draft = createDraft();
    draft.id = '1' as UrlEncodedCommentId;
    _testOnly_setState({
      comments: {},
      robotComments: {},
      drafts: {
        [draft.path!]: [draft as DraftInfo],
      },
      portedComments: {},
      portedDrafts: {},
      discardedDrafts: [],
    });
    updateStateDeleteDraft(draft);
    assert.deepEqual(_testOnly_getState(), {
      comments: {},
      robotComments: {},
      drafts: {
        'abc.txt': [],
      },
      portedComments: {},
      portedDrafts: {},
      discardedDrafts: [{...draft}],
    });
  });
});
