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

import '../test/common-test-setup-karma.js';
import {
  isUnresolved, getPatchRangeForCommentUrl,
} from './comment-util.js';
import {createComment} from '../test/test-data-generators.js';
import {CommentSide} from '../constants/constants.js';
import {ParentPatchSetNum} from '../types/common.js';

suite('comment-util', () => {
  test('isUnresolved', () => {
    assert.isFalse(isUnresolved(undefined));
    assert.isFalse(isUnresolved({comments: []}));
    assert.isTrue(isUnresolved({comments: [{unresolved: true}]}));
    assert.isFalse(isUnresolved({comments: [{unresolved: false}]}));
    assert.isTrue(isUnresolved(
        {comments: [{unresolved: false}, {unresolved: true}]}));
    assert.isFalse(isUnresolved(
        {comments: [{unresolved: true}, {unresolved: false}]}));
  });

  test('getPatchRangeForCommentUrl', () => {
    test('comment created with side=PARENT does not navigate to latest ps',
        () => {
          const comment = {
            ...createComment(),
            id: 'c4',
            line: 10,
            patch_set: 4,
            side: CommentSide.PARENT,
            path: '/COMMIT_MSG',
          };
          assert.deepEqual(getPatchRangeForCommentUrl(comment, 11), {
            basePatchNum: ParentPatchSetNum,
            patchNum: 4,
          });
        });
  });
});
