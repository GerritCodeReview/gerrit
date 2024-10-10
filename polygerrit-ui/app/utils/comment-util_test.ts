/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup';
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
  isNewThread,
  createNew,
  getUserSuggestionFromString,
} from './comment-util';
import {
  createAccountWithEmail,
  createComment,
  createCommentThread,
} from '../test/test-data-generators';
import {CommentSide, SpecialFilePath} from '../constants/constants';
import {
  Comment,
  SavingState,
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

  test('isNewThread', () => {
    let thread = createCommentThread([createComment()]);
    assert.isFalse(isNewThread(thread));

    thread = createCommentThread([createComment(), createNew()]);
    assert.isFalse(isNewThread(thread));

    thread = createCommentThread([createNew()]);
    assert.isTrue(isNewThread(thread));
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
    const updated = '2023-12-24 12:00:00.123000000' as Timestamp;
    const comments: Comment[] = [
      {
        id: 'pslevel' as UrlEncodedCommentId,
        path: SpecialFilePath.PATCHSET_LEVEL_COMMENTS,
        updated,
      },
      {
        id: 'commit-message' as UrlEncodedCommentId,
        path: SpecialFilePath.COMMIT_MESSAGE,
        updated,
      },
      {
        id: 'path2-id1-updated-earlier' as UrlEncodedCommentId,
        path: 'path2',
        updated: '2023-12-23 12:00:00.123000000' as Timestamp,
      },
      {
        id: 'path2-id1' as UrlEncodedCommentId,
        path: 'path2',
        updated,
      },
      {
        id: 'path2-id2' as UrlEncodedCommentId,
        path: 'path2',
        updated,
      },
      {
        id: 'path2-id1-updated-later' as UrlEncodedCommentId,
        path: 'path2',
        updated: '2023-12-24 15:55:00.123000000' as Timestamp,
      },
      {
        id: 'path2-line1' as UrlEncodedCommentId,
        path: 'path2',
        line: 1,
        updated,
      },
      {
        id: 'path2-line2' as UrlEncodedCommentId,
        path: 'path2',
        line: 2,
        updated,
      },
      {
        id: 'range-1-0-2-0' as UrlEncodedCommentId,
        path: 'path2',
        line: 2,
        range: {
          start_line: 1,
          start_character: 0,
          end_line: 2,
          end_character: 0,
        },
        updated,
      },
      {
        id: 'range-1-0-3-0' as UrlEncodedCommentId,
        path: 'path2',
        line: 2,
        range: {
          start_line: 1,
          start_character: 0,
          end_line: 3,
          end_character: 0,
        },
        updated,
      },
      {
        id: 'range-2-0-3-0' as UrlEncodedCommentId,
        path: 'path2',
        line: 2,
        range: {
          start_line: 2,
          start_character: 0,
          end_line: 3,
          end_character: 0,
        },
        updated,
      },
      {
        id: 'range-2-0-3-5' as UrlEncodedCommentId,
        path: 'path2',
        line: 2,
        range: {
          start_line: 2,
          start_character: 0,
          end_line: 3,
          end_character: 5,
        },
        updated,
      },
      {
        id: 'range-2-5-3-5' as UrlEncodedCommentId,
        path: 'path2',
        line: 2,
        range: {
          start_line: 2,
          start_character: 5,
          end_line: 3,
          end_character: 5,
        },
        updated,
      },
      {
        id: 'path2-line2-ps1' as UrlEncodedCommentId,
        path: 'path2',
        line: 2,
        patch_set: 1 as RevisionPatchSetNum,
        updated,
      },
      {
        id: 'path2-line2-ps2' as UrlEncodedCommentId,
        path: 'path2',
        line: 2,
        patch_set: 2 as RevisionPatchSetNum,
        updated,
      },
      {
        id: 'path2-line2-ps2-updated-later' as UrlEncodedCommentId,
        path: 'path2',
        line: 2,
        patch_set: 2 as RevisionPatchSetNum,
        updated,
      },
      {
        client_id: 'new1' as UrlEncodedCommentId,
        client_created_ms: 1,
        savingState: SavingState.OK,
        path: 'path2',
        line: 2,
        patch_set: 2 as RevisionPatchSetNum,
      },
      {
        client_id: 'new2' as UrlEncodedCommentId,
        client_created_ms: 2,
        savingState: SavingState.OK,
        path: 'path2',
        line: 2,
        patch_set: 2 as RevisionPatchSetNum,
      },
      {
        client_id: 'new2-sort-by-id' as UrlEncodedCommentId,
        client_created_ms: 2,
        savingState: SavingState.OK,
        path: 'path2',
        line: 2,
        patch_set: 2 as RevisionPatchSetNum,
      },
    ];
    const shuffled = [...comments].sort(() => Math.random() - 0.5);
    const sorted = sortComments(shuffled);
    assert.sameOrderedMembers(comments, sorted);
  });

  suite('createCommentThreads', () => {
    test('creates threads from individual comments', () => {
      const comments: Comment[] = [
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
          savingState: SavingState.OK,
          updated: '2015-12-20 15:01:20.396000000' as Timestamp,
          line: 1,
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
      assert.equal(actualThreads[1].line, 1);
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

  suite('getUserSuggestionFromString', () => {
    const createSuggestionContent = (suggestions: string[]) =>
      suggestions
        .map(s => `${USER_SUGGESTION_START_PATTERN}${s}\n\`\`\``)
        .join('\n');

    test('returns empty string for content without suggestions', () => {
      const content = 'This is a comment without any suggestions.';
      assert.equal(getUserSuggestionFromString(content), '');
    });

    test('returns first suggestion when no index is provided', () => {
      const content = createSuggestionContent(['First suggestion']);
      assert.equal(getUserSuggestionFromString(content), 'First suggestion');
    });

    test('returns correct suggestion for given index', () => {
      const content = createSuggestionContent([
        'First suggestion',
        'Second suggestion',
        'Third suggestion',
      ]);
      assert.equal(
        getUserSuggestionFromString(content, 1),
        'Second suggestion'
      );
    });

    test('returns last suggestion when index is out of bounds', () => {
      const content = createSuggestionContent([
        'First suggestion',
        'Second suggestion',
      ]);
      assert.equal(
        getUserSuggestionFromString(content, 5),
        'Second suggestion'
      );
    });

    test('handles suggestion without closing backticks', () => {
      const content = `${USER_SUGGESTION_START_PATTERN}Unclosed suggestion`;
      assert.equal(getUserSuggestionFromString(content), 'Unclosed suggestion');
    });

    test('handles multiple suggestions with varying content', () => {
      const content = createSuggestionContent([
        'First\nMultiline\nSuggestion',
        'Second suggestion',
        'Third suggestion with `backticks`',
      ]);
      assert.equal(
        getUserSuggestionFromString(content, 0),
        'First\nMultiline\nSuggestion'
      );
      assert.equal(
        getUserSuggestionFromString(content, 1),
        'Second suggestion'
      );
      assert.equal(
        getUserSuggestionFromString(content, 2),
        'Third suggestion with `backticks`'
      );
    });
  });
});
