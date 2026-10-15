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

  suite('list diffing', () => {
    test('reproduction case: 7-point ordered list with nested sub-bullet added on item 7', () => {
      const docA = [
        '1. Point one',
        '2. Point two',
        '3. Point three',
        '4. Point four',
        '5. Point five',
        '6. Point six',
        '7. Point seven',
      ].join('\n');

      const docB = [
        '1. Point one',
        '2. Point two',
        '3. Point three',
        '4. Point four',
        '5. Point five',
        '6. Point six',
        '7. Point seven',
        '   - Nested sub-bullet under seven',
      ].join('\n');

      const tokensA = parseMarkdownBlocks(docA);
      const tokensB = parseMarkdownBlocks(docB);
      const rows = alignMarkdownTokens(tokensA, tokensB);

      // Verify that items 1 through 6 are aligned as status: 'unchanged'
      for (let i = 0; i < 6; i++) {
        assert.equal(
          rows[i].status,
          'unchanged',
          `Item ${i + 1} should be unchanged`
        );
        assert.isDefined(rows[i].leftToken);
        assert.isDefined(rows[i].rightToken);
      }

      // Verify item 7 is 'unchanged' (or 'modified')
      assert.isTrue(
        rows[6].status === 'unchanged' || rows[6].status === 'modified',
        `Item 7 status should be unchanged or modified, was: ${rows[6].status}`
      );
      assert.include(rows[6].rightHtml ?? '', 'Point seven');

      // Verify the sub-bullet is status: 'added'
      const addedSubBulletRow = rows.find(
        r =>
          r.status === 'added' &&
          (r.rightHtml?.includes('Nested sub-bullet') ?? false)
      );
      assert.isDefined(
        addedSubBulletRow,
        'Should have an added row for the nested sub-bullet'
      );
      assert.isUndefined(addedSubBulletRow.leftToken);
      assert.isDefined(addedSubBulletRow.rightToken);
    });

    test('number shift resilience: item inserted at #2 keeps subsequent items unchanged', () => {
      const docA = [
        '1. Alpha item',
        '2. Beta item',
        '3. Gamma item',
        '4. Delta item',
        '5. Epsilon item',
      ].join('\n');

      const docB = [
        '1. Alpha item',
        '2. Newly inserted item',
        '3. Beta item',
        '4. Gamma item',
        '5. Delta item',
        '6. Epsilon item',
      ].join('\n');

      const tokensA = parseMarkdownBlocks(docA);
      const tokensB = parseMarkdownBlocks(docB);
      const rows = alignMarkdownTokens(tokensA, tokensB);

      // Row 0: Alpha item (unchanged)
      assert.equal(rows[0].status, 'unchanged');
      assert.include(rows[0].leftHtml ?? '', 'Alpha item');
      assert.include(rows[0].rightHtml ?? '', 'Alpha item');

      // Row 1: Newly inserted item (added)
      assert.equal(rows[1].status, 'added');
      assert.isUndefined(rows[1].leftToken);
      assert.isDefined(rows[1].rightToken);
      assert.include(rows[1].rightHtml ?? '', 'Newly inserted item');

      // Rows for Beta, Gamma, Delta, Epsilon must remain unchanged
      // (items 2 through 5 of original list)
      const unchangedRemaining = rows.slice(2);
      assert.equal(unchangedRemaining.length, 4);
      for (const row of unchangedRemaining) {
        assert.equal(
          row.status,
          'unchanged',
          `Shifted item should remain unchanged, but had status: ${row.status}`
        );
      }
      assert.include(rows[2].rightHtml ?? '', 'Beta item');
      assert.include(rows[3].rightHtml ?? '', 'Gamma item');
      assert.include(rows[4].rightHtml ?? '', 'Delta item');
      assert.include(rows[5].rightHtml ?? '', 'Epsilon item');
    });

    test('number shift resilience: item deleted from middle keeps subsequent items unchanged', () => {
      const docA = [
        '1. Step one',
        '2. Step to remove',
        '3. Step three',
        '4. Step four',
        '5. Step five',
      ].join('\n');

      const docB = [
        '1. Step one',
        '2. Step three',
        '3. Step four',
        '4. Step five',
      ].join('\n');

      const tokensA = parseMarkdownBlocks(docA);
      const tokensB = parseMarkdownBlocks(docB);
      const rows = alignMarkdownTokens(tokensA, tokensB);

      assert.equal(rows[0].status, 'unchanged');
      assert.include(rows[0].leftHtml ?? '', 'Step one');

      assert.equal(rows[1].status, 'deleted');
      assert.isDefined(rows[1].leftToken);
      assert.isUndefined(rows[1].rightToken);
      assert.include(rows[1].leftHtml ?? '', 'Step to remove');

      // Shifted items 3-5 (now numbered 2-4 in docB) remain unchanged
      assert.equal(rows[2].status, 'unchanged');
      assert.include(rows[2].rightHtml ?? '', 'Step three');

      assert.equal(rows[3].status, 'unchanged');
      assert.include(rows[3].rightHtml ?? '', 'Step four');

      assert.equal(rows[4].status, 'unchanged');
      assert.include(rows[4].rightHtml ?? '', 'Step five');
    });

    test('inline text edit: modified words in ordered list item generate del and ins highlights', () => {
      const docA = [
        '1. The quick brown fox jumps over the dog',
        '2. Unchanged list item',
      ].join('\n');

      const docB = [
        '1. The fast brown fox leaps over the dog',
        '2. Unchanged list item',
      ].join('\n');

      const tokensA = parseMarkdownBlocks(docA);
      const tokensB = parseMarkdownBlocks(docB);
      const rows = alignMarkdownTokens(tokensA, tokensB);

      assert.equal(rows[0].status, 'modified');
      assert.isDefined(rows[0].leftHtml);
      assert.isDefined(rows[0].rightHtml);

      // Verify <del class="diff-highlight-del">
      assert.include(rows[0].leftHtml, '<del class="diff-highlight-del">');
      assert.include(rows[0].leftHtml, 'quick');
      assert.include(rows[0].leftHtml, 'jumps');

      // Verify <ins class="diff-highlight-add">
      assert.include(rows[0].rightHtml, '<ins class="diff-highlight-add">');
      assert.include(rows[0].rightHtml, 'fast');
      assert.include(rows[0].rightHtml, 'leaps');

      // Common text preserved
      assert.include(rows[0].leftHtml, 'brown fox');
      assert.include(rows[0].rightHtml, 'brown fox');

      assert.equal(rows[1].status, 'unchanged');
    });

    test('inline text edit: modified words in unordered bullet item generate del and ins highlights', () => {
      const docA = [
        '* Deploy service to staging cluster',
        '* Keep monitoring dashboard active',
      ].join('\n');

      const docB = [
        '* Deploy service to production cluster',
        '* Keep monitoring dashboard active',
      ].join('\n');

      const tokensA = parseMarkdownBlocks(docA);
      const tokensB = parseMarkdownBlocks(docB);
      const rows = alignMarkdownTokens(tokensA, tokensB);

      assert.equal(rows[0].status, 'modified');
      assert.include(
        rows[0].leftHtml!,
        '<del class="diff-highlight-del">staging</del>'
      );
      assert.include(
        rows[0].rightHtml!,
        '<ins class="diff-highlight-add">production</ins>'
      );
      assert.equal(rows[1].status, 'unchanged');
    });

    test('nested lists: sub-bullets align correctly with parent and sibling items', () => {
      const docA = [
        '- Top level section',
        '  - Nested item A',
        '  - Nested item B',
        '- Another top section',
      ].join('\n');

      const docB = [
        '- Top level section',
        '  - Nested item A',
        '  - Nested item B with extra notes',
        '  - Nested item C added',
        '- Another top section',
      ].join('\n');

      const tokensA = parseMarkdownBlocks(docA);
      const tokensB = parseMarkdownBlocks(docB);
      const rows = alignMarkdownTokens(tokensA, tokensB);

      // Top level section is unchanged
      assert.equal(rows[0].status, 'unchanged');
      assert.include(rows[0].rightHtml ?? '', 'Top level section');

      // Nested item A is unchanged
      assert.equal(rows[1].status, 'unchanged');
      assert.include(rows[1].rightHtml ?? '', 'Nested item A');

      // Nested item B was modified with extra notes
      assert.equal(rows[2].status, 'modified');
      assert.include(rows[2].rightHtml ?? '', 'diff-highlight-add');
      assert.include(rows[2].rightHtml ?? '', 'extra notes');

      // Nested item C was added
      assert.equal(rows[3].status, 'added');
      assert.isUndefined(rows[3].leftToken);
      assert.include(rows[3].rightHtml ?? '', 'Nested item C added');

      // Another top section is unchanged
      assert.equal(rows[4].status, 'unchanged');
      assert.include(rows[4].rightHtml ?? '', 'Another top section');
    });

    test('task lists: checking a task marks row as modified with diff highlight', () => {
      const docA = [
        '- [ ] Task 1: Write specifications',
        '- [ ] Task 2: Implement functionality',
        '- [ ] Task 3: Verify with unit tests',
      ].join('\n');

      const docB = [
        '- [ ] Task 1: Write specifications',
        '- [x] Task 2: Implement functionality',
        '- [ ] Task 3: Verify with unit tests',
      ].join('\n');

      const tokensA = parseMarkdownBlocks(docA);
      const tokensB = parseMarkdownBlocks(docB);
      const rows = alignMarkdownTokens(tokensA, tokensB);

      // Task 1 is unchanged
      assert.equal(rows[0].status, 'unchanged');
      assert.include(rows[0].rightHtml ?? '', 'Task 1');

      // Task 2 was checked: status should be modified
      assert.equal(rows[1].status, 'modified');
      assert.isDefined(rows[1].leftToken);
      assert.isDefined(rows[1].rightToken);

      // Task 3 is unchanged
      assert.equal(rows[2].status, 'unchanged');
      assert.include(rows[2].rightHtml ?? '', 'Task 3');
    });

    test('task lists: adding a new task item preserves alignment of existing tasks', () => {
      const docA = ['- [ ] Prepare environment', '- [x] Run test suite'].join(
        '\n'
      );

      const docB = [
        '- [ ] Prepare environment',
        '- [ ] Run static analysis',
        '- [x] Run test suite',
      ].join('\n');

      const tokensA = parseMarkdownBlocks(docA);
      const tokensB = parseMarkdownBlocks(docB);
      const rows = alignMarkdownTokens(tokensA, tokensB);

      assert.equal(rows.length, 3);
      assert.equal(rows[0].status, 'unchanged');
      assert.include(rows[0].rightHtml ?? '', 'Prepare environment');

      assert.equal(rows[1].status, 'added');
      assert.isUndefined(rows[1].leftToken);
      assert.include(rows[1].rightHtml ?? '', 'Run static analysis');

      assert.equal(rows[2].status, 'unchanged');
      assert.include(rows[2].rightHtml ?? '', 'Run test suite');
    });

    test('task lists: editing words inside a task generates del and ins highlights', () => {
      const docA = '- [ ] Fix regression in parser module';
      const docB = '- [ ] Fix regression in tokenizer module';

      const tokensA = parseMarkdownBlocks(docA);
      const tokensB = parseMarkdownBlocks(docB);
      const rows = alignMarkdownTokens(tokensA, tokensB);

      assert.equal(rows.length, 1);
      assert.equal(rows[0].status, 'modified');
      assert.include(
        rows[0].leftHtml!,
        '<del class="diff-highlight-del">parser</del>'
      );
      assert.include(
        rows[0].rightHtml!,
        '<ins class="diff-highlight-add">tokenizer</ins>'
      );
    });

    test('parseMarkdownBlocks: list items compute precise line bounds', () => {
      const md = [
        '# Header',
        '',
        '1. First item',
        '2. Second item',
        '   - Sub item',
        '3. Third item',
      ].join('\n');

      const tokens = parseMarkdownBlocks(md);
      const headerToken = tokens[0];
      assert.equal(headerToken.type, 'heading');
      assert.equal(headerToken.startLine, 1);
      assert.equal(headerToken.endLine, 1);

      const itemTokens = tokens.slice(1);
      assert.isAtLeast(itemTokens.length, 4);

      assert.equal(itemTokens[0].startLine, 3);
      assert.equal(itemTokens[0].endLine, 3);

      assert.equal(itemTokens[1].startLine, 4);

      assert.equal(itemTokens[2].startLine, 5);
      assert.equal(itemTokens[2].endLine, 5);

      assert.equal(itemTokens[3].startLine, 6);
      assert.equal(itemTokens[3].endLine, 6);
    });
  });
});
