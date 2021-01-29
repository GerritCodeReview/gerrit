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
  isUnresolved, getPatchRangeForCommentUrl, createCommentThreads, sortComments,
} from './comment-util.js';
import {createComment} from '../test/test-data-generators.js';
import {CommentSide, Side} from '../constants/constants.js';
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

  test('comments sorting', () => {
    const comments = [
      {
        id: 'new_draft',
        message: 'i do not like either of you',
        diffSide: Side.LEFT,
        __draft: true,
        updated: '2015-12-20 15:01:20.396000000',
      },
      {
        id: 'sallys_confession',
        message: 'i like you, jack',
        updated: '2015-12-23 15:00:20.396000000',
        line: 1,
        diffSide: Side.LEFT,
      }, {
        id: 'jacks_reply',
        message: 'i like you, too',
        updated: '2015-12-24 15:01:20.396000000',
        diffSide: Side.LEFT,
        line: 1,
        in_reply_to: 'sallys_confession',
      },
    ];
    const sortedComments = sortComments(comments);
    assert.equal(sortedComments[0], comments[1]);
    assert.equal(sortedComments[1], comments[2]);
    assert.equal(sortedComments[2], comments[0]);
  });

  suite('createCommentThreads', () => {
    test('creates threads from individual comments', () => {
      const comments = [
        {
          id: 'sallys_confession',
          message: 'i like you, jack',
          updated: '2015-12-23 15:00:20.396000000',
          line: 1,
          patch_set: 1,
          path: 'some/path',
        }, {
          id: 'jacks_reply',
          message: 'i like you, too',
          updated: '2015-12-24 15:01:20.396000000',
          line: 1,
          in_reply_to: 'sallys_confession',
          patch_set: 1,
          path: 'some/path',
        },
        {
          id: 'new_draft',
          message: 'i do not like either of you',
          __draft: true,
          updated: '2015-12-20 15:01:20.396000000',
          patch_set: 1,
          path: 'some/path',
        },
      ];

      const actualThreads = createCommentThreads(comments,
          {basePatchNum: 1, patchNum: 4});

      assert.equal(actualThreads.length, 2);

      assert.equal(actualThreads[0].diffSide, Side.LEFT);
      assert.equal(actualThreads[0].comments.length, 2);
      assert.deepEqual(actualThreads[0].comments[0], comments[0]);
      assert.deepEqual(actualThreads[0].comments[1], comments[1]);
      assert.equal(actualThreads[0].patchNum, 1);
      assert.equal(actualThreads[0].line, 1);

      assert.equal(actualThreads[1].diffSide, Side.LEFT);
      assert.equal(actualThreads[1].comments.length, 1);
      assert.deepEqual(actualThreads[1].comments[0], comments[2]);
      assert.equal(actualThreads[1].patchNum, 1);
      assert.equal(actualThreads[1].line, 'FILE');
    });

    test('derives patchNum and range', () => {
      const comments = [{
        id: 'betsys_confession',
        message: 'i like you, jack',
        updated: '2015-12-24 15:00:10.396000000',
        range: {
          start_line: 1,
          start_character: 1,
          end_line: 1,
          end_character: 2,
        },
        patch_set: 5,
        path: '/p',
        line: 1,
      }];

      const expectedThreads = [
        {
          diffSide: Side.LEFT,
          commentSide: CommentSide.REVISION,
          path: '/p',
          rootId: 'betsys_confession',
          mergeParentNum: undefined,
          comments: [{
            id: 'betsys_confession',
            path: '/p',
            message: 'i like you, jack',
            updated: '2015-12-24 15:00:10.396000000',
            range: {
              start_line: 1,
              start_character: 1,
              end_line: 1,
              end_character: 2,
            },
            patch_set: 5,
            line: 1,
          }],
          patchNum: 5,
          range: {
            start_line: 1,
            start_character: 1,
            end_line: 1,
            end_character: 2,
          },
          line: 1,
        },
      ];

      assert.deepEqual(
          createCommentThreads(comments, {basePatchNum: 5, patchNum: 10}),
          expectedThreads);
    });

    test('does not thread unrelated comments at same location', () => {
      const comments = [
        {
          id: 'sallys_confession',
          message: 'i like you, jack',
          updated: '2015-12-23 15:00:20.396000000',
          diffSide: Side.LEFT,
          path: '/p',
        }, {
          id: 'jacks_reply',
          message: 'i like you, too',
          updated: '2015-12-24 15:01:20.396000000',
          diffSide: Side.LEFT,
          path: '/p',
        },
      ];
      assert.equal(createCommentThreads(comments).length, 2);
    });
  });
});
