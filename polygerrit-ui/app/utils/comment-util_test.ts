/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../test/common-test-setup-karma';
import {
  isUnresolved,
  getPatchRangeForCommentUrl,
  createCommentThreads,
  sortComments,
} from './comment-util';
import {createComment, createCommentThread} from '../test/test-data-generators';
import {CommentSide} from '../constants/constants';
import {
  ParentPatchSetNum,
  PatchSetNum,
  RevisionPatchSetNum,
  Timestamp,
  UrlEncodedCommentId,
} from '../types/common';

suite('comment-util', () => {
  test('isUnresolved', () => {
    const thread = createCommentThread([createComment()]);

    assert.isFalse(isUnresolved(thread));

    assert.isTrue(
      isUnresolved({
        ...thread,
        comments: [{...createComment(), unresolved: true}],
      })
    );
    assert.isFalse(
      isUnresolved({
        ...thread,
        comments: [{...createComment(), unresolved: false}],
      })
    );
    assert.isTrue(
      isUnresolved({
        ...thread,
        comments: [
          {...createComment(), unresolved: false},
          {...createComment(), unresolved: true},
        ],
      })
    );
    assert.isFalse(
      isUnresolved({
        ...thread,
        comments: [
          {...createComment(), unresolved: true},
          {...createComment(), unresolved: false},
        ],
      })
    );
  });

  test('getPatchRangeForCommentUrl', () => {
    test('comment created with side=PARENT does not navigate to latest ps', () => {
      const comment = {
        ...createComment(),
        id: 'c4' as UrlEncodedCommentId,
        line: 10,
        patch_set: 4 as PatchSetNum,
        side: CommentSide.PARENT,
        path: '/COMMIT_MSG',
      };
      assert.deepEqual(
        getPatchRangeForCommentUrl(comment, 11 as RevisionPatchSetNum),
        {
          basePatchNum: ParentPatchSetNum,
          patchNum: 4 as PatchSetNum,
        }
      );
    });
  });

  test('comments sorting', () => {
    const comments = [
      {
        id: 'new_draft' as UrlEncodedCommentId,
        message: 'i do not like either of you',
        __draft: true,
        updated: '2015-12-20 15:01:20.396000000' as Timestamp,
      },
      {
        id: 'sallys_confession' as UrlEncodedCommentId,
        message: 'i like you, jack',
        updated: '2015-12-23 15:00:20.396000000' as Timestamp,
        line: 1,
      },
      {
        id: 'jacks_reply' as UrlEncodedCommentId,
        message: 'i like you, too',
        updated: '2015-12-24 15:01:20.396000000' as Timestamp,
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
          id: 'sallys_confession' as UrlEncodedCommentId,
          message: 'i like you, jack',
          updated: '2015-12-23 15:00:20.396000000' as Timestamp,
          line: 1,
          patch_set: 1 as PatchSetNum,
          path: 'some/path',
        },
        {
          id: 'jacks_reply' as UrlEncodedCommentId,
          message: 'i like you, too',
          updated: '2015-12-24 15:01:20.396000000' as Timestamp,
          line: 1,
          in_reply_to: 'sallys_confession' as UrlEncodedCommentId,
          patch_set: 1 as PatchSetNum,
          path: 'some/path',
        },
        {
          id: 'new_draft' as UrlEncodedCommentId,
          message: 'i do not like either of you' as UrlEncodedCommentId,
          __draft: true,
          updated: '2015-12-20 15:01:20.396000000' as Timestamp,
          patch_set: 1 as PatchSetNum,
          path: 'some/path',
        },
      ];

      const actualThreads = createCommentThreads(comments);

      assert.equal(actualThreads.length, 2);

      assert.equal(actualThreads[0].comments.length, 2);
      assert.deepEqual(actualThreads[0].comments[0], comments[0]);
      assert.deepEqual(actualThreads[0].comments[1], comments[1]);
      assert.equal(actualThreads[0].patchNum, 1 as PatchSetNum);
      assert.equal(actualThreads[0].line, 1);

      assert.equal(actualThreads[1].comments.length, 1);
      assert.deepEqual(actualThreads[1].comments[0], comments[2]);
      assert.equal(actualThreads[1].patchNum, 1 as PatchSetNum);
      assert.equal(actualThreads[1].line, 'FILE');
    });

    test('derives patchNum and range', () => {
      const comments = [
        {
          id: 'betsys_confession' as UrlEncodedCommentId,
          message: 'i like you, jack',
          updated: '2015-12-24 15:00:10.396000000' as Timestamp,
          range: {
            start_line: 1,
            start_character: 1,
            end_line: 1,
            end_character: 2,
          },
          patch_set: 5 as PatchSetNum,
          path: '/p',
          line: 1,
        },
      ];

      const expectedThreads = [
        {
          commentSide: CommentSide.REVISION,
          path: '/p',
          rootId: 'betsys_confession' as UrlEncodedCommentId,
          mergeParentNum: undefined,
          comments: [
            {
              id: 'betsys_confession' as UrlEncodedCommentId,
              path: '/p',
              message: 'i like you, jack',
              updated: '2015-12-24 15:00:10.396000000' as Timestamp,
              range: {
                start_line: 1,
                start_character: 1,
                end_line: 1,
                end_character: 2,
              },
              patch_set: 5 as PatchSetNum,
              line: 1,
            },
          ],
          patchNum: 5 as PatchSetNum,
          range: {
            start_line: 1,
            start_character: 1,
            end_line: 1,
            end_character: 2,
          },
          line: 1,
        },
      ];

      assert.deepEqual(createCommentThreads(comments), expectedThreads);
    });

    test('does not thread unrelated comments at same location', () => {
      const comments = [
        {
          id: 'sallys_confession' as UrlEncodedCommentId,
          message: 'i like you, jack',
          updated: '2015-12-23 15:00:20.396000000' as Timestamp,
          path: '/p',
        },
        {
          id: 'jacks_reply' as UrlEncodedCommentId,
          message: 'i like you, too',
          updated: '2015-12-24 15:01:20.396000000' as Timestamp,
          path: '/p',
        },
      ];
      assert.equal(createCommentThreads(comments).length, 2);
    });
  });
});
