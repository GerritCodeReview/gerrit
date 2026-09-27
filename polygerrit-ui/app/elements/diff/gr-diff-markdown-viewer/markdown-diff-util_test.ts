/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import '../../../test/common-test-setup';
import {DiffInfo} from '../../../types/diff';
import {CommentSide, Side} from '../../../constants/constants';
import {
  createComment,
  createCommentThread,
  createDiff,
} from '../../../test/test-data-generators';
import {CommentThread, UrlEncodedCommentId} from '../../../types/common';
import {
  alignMarkdownTokens,
  attachThreadsToRows,
  computeSequenceDiff,
  parseMarkdownBlocks,
  reconstructFileContent,
  tokenizeWords,
} from './markdown-diff-util';

suite('markdown-diff-util tests', () => {
  test('reconstructFileContent', () => {
    const diff: DiffInfo = {
      ...createDiff(),
      content: [
        {ab: ['# Header', '']},
        {a: ['Old line'], b: ['New line']},
        {ab: ['', 'Footer']},
      ],
    };

    assert.equal(
      reconstructFileContent(diff, Side.LEFT),
      '# Header\n\nOld line\n\nFooter'
    );
    assert.equal(
      reconstructFileContent(diff, Side.RIGHT),
      '# Header\n\nNew line\n\nFooter'
    );
  });

  test('parseMarkdownBlocks', () => {
    const md = '# Title\n\nParagraph text.\n\n```js\nconsole.log(1);\n```';
    const tokens = parseMarkdownBlocks(md);
    assert.equal(tokens.length, 3);
    assert.equal(tokens[0].type, 'heading');
    assert.equal(tokens[1].type, 'paragraph');
    assert.equal(tokens[2].type, 'code');
  });

  test('tokenizeWords', () => {
    const words = tokenizeWords('Hello world! How are you?');
    assert.deepEqual(words, [
      'Hello',
      ' ',
      'world',
      '!',
      ' ',
      'How',
      ' ',
      'are',
      ' ',
      'you',
      '?',
    ]);
  });

  test('computeSequenceDiff', () => {
    const seqA = ['a', 'b', 'c'];
    const seqB = ['a', 'x', 'c'];
    const diff = computeSequenceDiff(seqA, seqB);
    assert.deepEqual(diff, [
      {text: 'a', type: 'common'},
      {text: 'b', type: 'deleted'},
      {text: 'x', type: 'added'},
      {text: 'c', type: 'common'},
    ]);
  });

  suite('alignMarkdownTokens', () => {
    test('identical documents produce unchanged rows', () => {
      const doc = '# Title\n\nParagraph 1.\n\nParagraph 2.';
      const tokensA = parseMarkdownBlocks(doc);
      const tokensB = parseMarkdownBlocks(doc);

      const rows = alignMarkdownTokens(tokensA, tokensB);
      assert.equal(rows.length, 3);
      assert.isTrue(rows.every(r => r.status === 'unchanged'));
    });

    test('inserted block creates added row with empty left', () => {
      const docA = '# Title\n\nFooter.';
      const docB = '# Title\n\nInserted paragraph.\n\nFooter.';
      const tokensA = parseMarkdownBlocks(docA);
      const tokensB = parseMarkdownBlocks(docB);

      const rows = alignMarkdownTokens(tokensA, tokensB);
      assert.equal(rows.length, 3);
      assert.equal(rows[0].status, 'unchanged');
      assert.equal(rows[1].status, 'added');
      assert.isUndefined(rows[1].leftToken);
      assert.isDefined(rows[1].rightToken);
      assert.include(rows[1].rightHtml!, 'Inserted paragraph');
      assert.equal(rows[2].status, 'unchanged');
    });

    test('deleted block creates deleted row with empty right', () => {
      const docA = '# Title\n\nDeleted paragraph.\n\nFooter.';
      const docB = '# Title\n\nFooter.';
      const tokensA = parseMarkdownBlocks(docA);
      const tokensB = parseMarkdownBlocks(docB);

      const rows = alignMarkdownTokens(tokensA, tokensB);
      assert.equal(rows.length, 3);
      assert.equal(rows[0].status, 'unchanged');
      assert.equal(rows[1].status, 'deleted');
      assert.isDefined(rows[1].leftToken);
      assert.isUndefined(rows[1].rightToken);
      assert.include(rows[1].leftHtml!, 'Deleted paragraph');
      assert.equal(rows[2].status, 'unchanged');
    });

    test('modified paragraph generates inline diff highlights', () => {
      const docA = 'Follow this structure:';
      const docB = 'Follow this structure. Only the first block is required:';
      const tokensA = parseMarkdownBlocks(docA);
      const tokensB = parseMarkdownBlocks(docB);

      const rows = alignMarkdownTokens(tokensA, tokensB);
      assert.equal(rows.length, 1);
      assert.equal(rows[0].status, 'modified');
      assert.include(rows[0].leftHtml!, 'Follow this structure');
      assert.include(rows[0].rightHtml!, 'diff-highlight-add');
      assert.include(rows[0].rightHtml!, 'Only the first block is required');
    });

    test('modified code block generates line diff highlights', () => {
      const docA = '```bash\nline 1\nold code\nline 3\n```';
      const docB = '```bash\nline 1\nnew code\nline 3\n```';
      const tokensA = parseMarkdownBlocks(docA);
      const tokensB = parseMarkdownBlocks(docB);

      const rows = alignMarkdownTokens(tokensA, tokensB);
      assert.equal(rows.length, 1);
      assert.equal(rows[0].status, 'modified');
      assert.include(rows[0].leftHtml!, 'diff-highlight-del');
      assert.include(rows[0].leftHtml!, 'old code');
      assert.include(rows[0].rightHtml!, 'diff-highlight-add');
      assert.include(rows[0].rightHtml!, 'new code');
    });

    test('line numbers are correctly assigned to tokens and rows', () => {
      const docA = '# Header\n\nLine 1\nLine 2';
      const docB = '# Header\n\nLine 1\nLine 2 modified';
      const tokensA = parseMarkdownBlocks(docA);
      const tokensB = parseMarkdownBlocks(docB);

      assert.equal(tokensA[0].startLine, 1);
      assert.equal(tokensA[0].endLine, 1);
      assert.equal(tokensA[1].startLine, 3);
      assert.equal(tokensA[1].endLine, 4);

      const rows = alignMarkdownTokens(tokensA, tokensB);
      assert.equal(rows[0].leftStartLine, 1);
      assert.equal(rows[0].leftEndLine, 1);
      assert.equal(rows[0].rightStartLine, 1);
      assert.equal(rows[0].rightEndLine, 1);

      assert.equal(rows[1].leftStartLine, 3);
      assert.equal(rows[1].leftEndLine, 4);
      assert.equal(rows[1].rightStartLine, 3);
      assert.equal(rows[1].rightEndLine, 4);
    });
  });

  suite('attachThreadsToRows', () => {
    test('attaches line threads to matching rows and separates file-level threads', () => {
      const docA = '# Header\n\nParagraph 1\n\nParagraph 2';
      const docB = '# Header\n\nParagraph 1 modified\n\nParagraph 2';
      const tokensA = parseMarkdownBlocks(docA);
      const tokensB = parseMarkdownBlocks(docB);
      const rows = alignMarkdownTokens(tokensA, tokensB);

      const fileThread: CommentThread = createCommentThread([
        {
          ...createComment(),
          id: 'file-1' as UrlEncodedCommentId,
          line: undefined,
          side: CommentSide.REVISION,
        },
      ]);
      const rightThread: CommentThread = createCommentThread([
        {
          ...createComment(),
          id: 'right-1' as UrlEncodedCommentId,
          line: 3,
          side: CommentSide.REVISION,
        },
      ]);
      const leftThread: CommentThread = createCommentThread([
        {
          ...createComment(),
          id: 'left-1' as UrlEncodedCommentId,
          line: 3,
          side: CommentSide.PARENT,
        },
      ]);

      const result = attachThreadsToRows(rows, [
        fileThread,
        rightThread,
        leftThread,
      ]);
      assert.equal(result.fileLevelThreads.length, 1);
      assert.equal(result.fileLevelThreads[0].rootId, 'file-1');

      // Row 1 is "Paragraph 1 modified" which spans lines 3-3
      assert.equal(result.rowsWithThreads[1].rightThreads.length, 1);
      assert.equal(result.rowsWithThreads[1].rightThreads[0].rootId, 'right-1');

      assert.equal(result.rowsWithThreads[1].leftThreads.length, 1);
      assert.equal(result.rowsWithThreads[1].leftThreads[0].rootId, 'left-1');
    });
  });
});
