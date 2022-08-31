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
  USER_SUGGESTION_START_PATTERN,
  hasUserSuggestion,
  getUserSuggestion,
  getContentInCommentRange,
  createUserFixSuggestion,
  PROVIDED_FIX_ID,
  getMentionedThreads,
} from './comment-util';
import {
  createAccountWithEmail,
  createComment,
  createCommentThread,
} from '../test/test-data-generators';
import {CommentSide} from '../constants/constants';
import {
  PARENT,
  RevisionPatchSetNum,
  Timestamp,
  UrlEncodedCommentId,
} from '../types/common';
import {assert} from '@open-wc/testing';

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

  suite('getPatchRangeForCommentUrl', () => {
    test('comment created with side=PARENT does not navigate to latest ps', () => {
      const comment = {
        ...createComment(),
        id: 'c4' as UrlEncodedCommentId,
        line: 10,
        patch_set: 4 as RevisionPatchSetNum,
        side: CommentSide.PARENT,
        path: '/COMMIT_MSG',
      };
      assert.deepEqual(
        getPatchRangeForCommentUrl(comment, 11 as RevisionPatchSetNum),
        {
          basePatchNum: PARENT,
          patchNum: 4 as RevisionPatchSetNum,
        }
      );
    });
  });

  test('getMentionedThreads', () => {
    const account = createAccountWithEmail('abcd@def.com');
    const threads = [
      createCommentThread([
        {
          ...createComment(),
          message: 'random text with no emails',
        },
      ]),
      createCommentThread([
        {
          ...createComment(),
          message: '@abcd@def.com please take a look',
        },
        {
          ...createComment(),
          message: '@abcd@def.com please take a look again at this',
        },
      ]),
      createCommentThread([
        {
          ...createComment(),
          message: '@abcd@def.com this is important',
        },
      ]),
    ];
    assert.deepEqual(getMentionedThreads(threads, account), [
      threads[1],
      threads[2],
    ]);

    assert.deepEqual(
      getMentionedThreads(threads, createAccountWithEmail('xyz@def.com')),
      []
    );
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
          patch_set: 1 as RevisionPatchSetNum,
          path: 'some/path',
        },
        {
          id: 'jacks_reply' as UrlEncodedCommentId,
          message: 'i like you, too',
          updated: '2015-12-24 15:01:20.396000000' as Timestamp,
          line: 1,
          in_reply_to: 'sallys_confession' as UrlEncodedCommentId,
          patch_set: 1 as RevisionPatchSetNum,
          path: 'some/path',
        },
        {
          id: 'new_draft' as UrlEncodedCommentId,
          message: 'i do not like either of you' as UrlEncodedCommentId,
          __draft: true,
          updated: '2015-12-20 15:01:20.396000000' as Timestamp,
          patch_set: 1 as RevisionPatchSetNum,
          path: 'some/path',
        },
      ];

      const actualThreads = createCommentThreads(comments);

      assert.equal(actualThreads.length, 2);

      assert.equal(actualThreads[0].comments.length, 2);
      assert.deepEqual(actualThreads[0].comments[0], comments[0]);
      assert.deepEqual(actualThreads[0].comments[1], comments[1]);
      assert.equal(actualThreads[0].patchNum, 1 as RevisionPatchSetNum);
      assert.equal(actualThreads[0].line, 1);

      assert.equal(actualThreads[1].comments.length, 1);
      assert.deepEqual(actualThreads[1].comments[0], comments[2]);
      assert.equal(actualThreads[1].patchNum, 1 as RevisionPatchSetNum);
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
          patch_set: 5 as RevisionPatchSetNum,
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
              patch_set: 5 as RevisionPatchSetNum,
              line: 1,
            },
          ],
          patchNum: 5 as RevisionPatchSetNum,
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

  test('hasUserSuggestion', () => {
    const comment = {
      ...createComment(),
      message: `${USER_SUGGESTION_START_PATTERN}${'test'}${'\n```'}`,
    };
    assert.isTrue(hasUserSuggestion(comment));
  });

  test('getUserSuggestion', () => {
    const suggestion = 'test';
    const comment = {
      ...createComment(),
      message: `${USER_SUGGESTION_START_PATTERN}${suggestion}${'\n```'}`,
    };
    assert.equal(getUserSuggestion(comment), suggestion);
  });

  suite('getContentInCommentRange', () => {
    test('one line', () => {
      const comment = {
        ...createComment(),
        line: 1,
      };
      const content = 'line1\nline2\nline3';
      assert.equal(getContentInCommentRange(content, comment), 'line1');
    });

    test('multi line', () => {
      const comment = {
        ...createComment(),
        line: 3,
        range: {
          start_line: 1,
          start_character: 5,
          end_line: 3,
          end_character: 39,
        },
      };
      const selectedText =
        '   * Examples:\n' +
        '      * Acknowledge/Dismiss, Delete, Report a bug, Report as not useful,\n' +
        '      * Make blocking, Downgrade severity.';
      const content = `${selectedText}\n`;
      assert.equal(getContentInCommentRange(content, comment), selectedText);
    });
  });

  suite('createUserFixSuggestion', () => {
    test('one line', () => {
      const comment = {
        ...createComment(),
        line: 1,
        path: 'abc.txt',
      };
      const line = 'lane1';
      const replacement = 'line1';
      assert.deepEqual(createUserFixSuggestion(comment, line, replacement), [
        {
          fix_id: PROVIDED_FIX_ID,
          description: 'User suggestion',
          replacements: [
            {
              path: 'abc.txt',
              range: {
                start_line: 1,
                start_character: 0,
                end_line: 1,
                end_character: line.length,
              },
              replacement,
            },
          ],
        },
      ]);
    });

    test('multiline', () => {
      const comment = {
        ...createComment(),
        line: 3,
        range: {
          start_line: 1,
          start_character: 5,
          end_line: 3,
          end_character: 39,
        },
        path: 'abc.txt',
      };
      const line =
        '   * Examples:\n' +
        '      * Acknowledge/Dismiss, Delete, Report a bug, Report as not useful,\n' +
        '      * Make blocking, Downgrade severity.';
      const replacement =
        '   - Examples:\n' +
        '      - Acknowledge/Dismiss, Delete, Report a bug, Report as not useful,\n' +
        '      - Make blocking, Downgrade severity.';
      assert.deepEqual(createUserFixSuggestion(comment, line, replacement), [
        {
          fix_id: PROVIDED_FIX_ID,
          description: 'User suggestion',
          replacements: [
            {
              path: 'abc.txt',
              range: {
                start_line: 1,
                start_character: 0,
                end_line: 3,
                end_character: 42,
              },
              replacement,
            },
          ],
        },
      ]);
    });
  });
});
