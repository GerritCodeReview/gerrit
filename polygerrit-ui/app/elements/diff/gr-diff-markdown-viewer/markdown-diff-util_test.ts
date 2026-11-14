/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import {render, TemplateResult} from 'lit';
import {marked, Tokens} from 'marked';
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
  alignHomologousItems,
  alignMarkdownTokens,
  attachThreadsToRows,
  coalesceInlineDiffSegments,
  computeLinearExactAnchors,
  computeNormalizedEditDistance,
  computeSequenceDiff,
  parseMarkdownBlocks,
  reconstructFileContent,
  renderBlockquoteDiff,
  renderCodeBlockDiff,
  renderInlineCodeSpans,
  renderInlineMarkdown,
  renderInlineTextDiff,
  renderListDiff,
  renderTableDiff,
  renderTokenToHtml,
  sanitizeCodeLanguage,
  sanitizeMarkdownUrl,
  tokenizeCodeWords,
  tokenizeWords,
} from './markdown-diff-util';

function renderToHtmlString(template?: TemplateResult): string {
  if (!template) return '';
  const container = document.createElement('div');
  render(template, container);
  return container.innerHTML.replace(/<!--[\s\S]*?-->/g, '');
}

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

  test('tokenizeWords keeps markdown links and inline code spans atomic and separates adjacent punctuation', () => {
    const words = tokenizeWords(
      'Located at [`scripts/run_experiment.py`](scripts/run_experiment.py) (`<SKILL_DIR>`, `--workspace-root`, `` `code` ``).'
    );
    assert.include(
      words,
      '[`scripts/run_experiment.py`](scripts/run_experiment.py)'
    );
    assert.include(words, '`<SKILL_DIR>`');
    assert.include(words, '`--workspace-root`');
    assert.include(words, '`` `code` ``');
    assert.equal(
      words[4],
      '[`scripts/run_experiment.py`](scripts/run_experiment.py)'
    );
  });

  test('renderInlineCodeSpans converts backtick blocks to <code> and escapes HTML', () => {
    const rendered = renderToHtmlString(
      renderInlineCodeSpans(
        'Relative to `SKILL.md` (`<SKILL_DIR>`), `--workspace-root`, `$ANDROID_BUILD_TOP`, `.repo/`, and `` `code` ``.'
      )
    );
    assert.equal(
      rendered,
      'Relative to <code>SKILL.md</code> (<code>&lt;SKILL_DIR&gt;</code>), <code>--workspace-root</code>, <code>$ANDROID_BUILD_TOP</code>, <code>.repo/</code>, and <code>`code`</code>.'
    );
  });

  test('tokenizeWords and renderInlineTextDiff handle autolinks and nested links/code inside formatted text', () => {
    const words = tokenizeWords(
      'Check **old `flag` and [Docs](https://example.com)** and <https://auto.example.com>.'
    );
    assert.include(words, '`flag`');
    assert.include(words, '[Docs](https://example.com)');
    assert.include(words, '<https://auto.example.com>');

    const {leftHtml, rightHtml} = renderInlineTextDiff(
      'Check **old `flag` and [Docs](https://example.com)** and <https://auto.example.com/v1>.',
      'Check **new `flag` and [Docs](https://example.com)** and <https://auto.example.com/v2>.',
      'p'
    );
    assert.include(
      renderToHtmlString(leftHtml),
      '<strong><del class="diff-highlight-del">old</del> <code>flag</code> and <a target="_blank" rel="noopener noreferrer" href="https://example.com">Docs</a></strong>'
    );
    assert.include(
      renderToHtmlString(rightHtml),
      '<strong><ins class="diff-highlight-add">new</ins> <code>flag</code> and <a target="_blank" rel="noopener noreferrer" href="https://example.com">Docs</a></strong>'
    );
    assert.include(
      renderToHtmlString(leftHtml),
      '<del class="diff-highlight-del"><a target="_blank" rel="noopener noreferrer" href="https://auto.example.com/v1">https://auto.example.com/v1</a></del>'
    );
    assert.include(
      renderToHtmlString(rightHtml),
      '<ins class="diff-highlight-add"><a target="_blank" rel="noopener noreferrer" href="https://auto.example.com/v2">https://auto.example.com/v2</a></ins>'
    );
  });

  test('renderInlineMarkdown converts markdown links and code spans to HTML and escapes attributes', () => {
    const rendered = renderToHtmlString(
      renderInlineMarkdown(
        'Located at [`scripts/run_experiment.py`](scripts/run_experiment.py) and [Docs](https://example.com/docs "Documentation") (relative to `SKILL.md`).'
      )
    );
    assert.equal(
      rendered,
      'Located at <a rel="noopener noreferrer" href="scripts/run_experiment.py"><code>scripts/run_experiment.py</code></a> and <a target="_blank" rel="noopener noreferrer" href="https://example.com/docs">Docs</a> (relative to <code>SKILL.md</code>).'
    );
  });

  test('renderInlineMarkdown handles parenthesized URLs, angle-bracket URLs, bracketed code labels, and ignores images', () => {
    const rendered = renderToHtmlString(
      renderInlineMarkdown(
        'See [Wiki](https://en.wikipedia.org/wiki/Fn_(math)), [Angle](<https://example.com/path>), [`arr[0]`](https://example.com/arr), and ![alt](https://example.com/img.png).'
      )
    );
    assert.equal(
      rendered,
      'See <a target="_blank" rel="noopener noreferrer" href="https://en.wikipedia.org/wiki/Fn_(math)">Wiki</a>, <a target="_blank" rel="noopener noreferrer" href="https://example.com/path">Angle</a>, <a target="_blank" rel="noopener noreferrer" href="https://example.com/arr"><code>arr[0]</code></a>, and ![alt](https://example.com/img.png).'
    );
  });

  test('sanitizeMarkdownUrl and Lit templates harden against XSS and unsafe URL schemes', () => {
    assert.equal(
      sanitizeMarkdownUrl('javascript:alert(1)'),
      'about:invalid#zClosurez'
    );
    assert.equal(
      sanitizeMarkdownUrl('  JAVASCRIPT:alert(1) '),
      'about:invalid#zClosurez'
    );
    assert.equal(
      sanitizeMarkdownUrl('data:text/html,<script>alert(1)</script>'),
      'about:invalid#zClosurez'
    );
    assert.equal(
      sanitizeMarkdownUrl('file:///etc/passwd'),
      'about:invalid#zClosurez'
    );
    assert.equal(
      sanitizeMarkdownUrl('http://example.com/safe'),
      'http://example.com/safe'
    );
    assert.equal(
      sanitizeMarkdownUrl('https://example.com/safe'),
      'https://example.com/safe'
    );
    assert.equal(
      sanitizeMarkdownUrl('scripts/run_experiment.py'),
      'scripts/run_experiment.py'
    );

    const rendered = renderToHtmlString(
      renderInlineMarkdown(
        'Click [evil](javascript:alert(1)) or <img src=x onerror=alert(1)>'
      )
    );
    assert.include(
      rendered,
      '<a rel="noopener noreferrer" href="about:invalid#zClosurez">evil</a>'
    );
    assert.include(rendered, '&lt;img src=x onerror=alert(1)&gt;');
  });

  test('renderInlineTextDiff renders single backtick code blocks and markdown links inside modified paragraphs', () => {
    const textA =
      'The generalized runner script is located at [run_experiment.py](https://example.com/tools/build_experimentation/scripts/run_experiment.py).';
    const textB =
      'The generalized runner script is located at [`scripts/run_experiment.py`](scripts/run_experiment.py) (relative to this `SKILL.md` directory, `<SKILL_DIR>`). It automatically resolves the workspace root from `--workspace-root`, `workspace_root` in the JSON config, `$ANDROID_BUILD_TOP`, or the nearest ancestor directory containing `.repo/`.';

    const {leftHtml, rightHtml} = renderInlineTextDiff(textA, textB, 'p');
    const leftStr = renderToHtmlString(leftHtml);
    const rightStr = renderToHtmlString(rightHtml);
    assert.include(
      leftStr,
      '<a target="_blank" rel="noopener noreferrer" href="https://example.com/tools/build_experimentation/scripts/run_experiment.py">run_experiment.py</a>'
    );
    assert.include(
      rightStr,
      '<a rel="noopener noreferrer" href="scripts/run_experiment.py"><code>scripts/run_experiment.py</code></a>'
    );
    assert.include(rightStr, '<code>SKILL.md</code>');
    assert.include(rightStr, '<code>&lt;SKILL_DIR&gt;</code>');
    assert.include(rightStr, '<code>--workspace-root</code>');
    assert.include(rightStr, '<code>workspace_root</code>');
    assert.include(rightStr, '<code>$ANDROID_BUILD_TOP</code>');
    assert.include(rightStr, '<code>.repo/</code>');
  });

  test('computeNormalizedEditDistance and coalesceInlineDiffSegments follow delta-style edit inference', () => {
    assert.equal(
      computeNormalizedEditDistance(
        tokenizeWords('same sentence'),
        tokenizeWords('same sentence')
      ),
      0
    );
    assert.equal(
      computeNormalizedEditDistance(
        tokenizeWords('alpha beta'),
        tokenizeWords('gamma delta')
      ),
      1
    );

    const rawSegments = computeSequenceDiff(
      tokenizeWords('Run old legacy command now.'),
      tokenizeWords('Run new modern command now.')
    );
    const coalesced = coalesceInlineDiffSegments(rawSegments);
    // The space between 'old'/'legacy' and 'new'/'modern' is coalesced into a single edit span.
    assert.deepEqual(coalesced, [
      {text: 'Run ', type: 'common'},
      {text: 'old legacy', type: 'deleted'},
      {text: 'new modern', type: 'added'},
      {text: ' command now.', type: 'common'},
    ]);
  });

  test('alignHomologousItems pairs modified items across insertions', () => {
    const itemsA = ['Configure timeout in default.yaml'];
    const itemsB = [
      'Brand new prerequisite step',
      'Configure timeout in production.yaml',
    ];
    const aligned = alignHomologousItems(itemsA, itemsB);
    assert.equal(aligned.length, 2);
    assert.equal(aligned[0].type, 'added');
    assert.equal(aligned[1].type, 'modified');
    if (aligned[1].type === 'modified') {
      assert.equal(aligned[1].textA, 'Configure timeout in default.yaml');
      assert.equal(aligned[1].textB, 'Configure timeout in production.yaml');
    }
  });

  test('renderCodeBlockDiff performs within-line token diffing on homologous code lines', () => {
    const tokensA = parseMarkdownBlocks(
      '```bash\npython3 tools/build_experimentation/scripts/run_experiment.py --config path/to/config.json\n```'
    );
    const tokensB = parseMarkdownBlocks(
      '```bash\npython3 <SKILL_DIR>/scripts/run_experiment.py --config path/to/config.json\n```'
    );
    assert.deepEqual(tokenizeCodeWords('python3 <SKILL_DIR>/run.py --flag'), [
      'python3',
      ' ',
      '<SKILL_DIR>',
      '/',
      'run',
      '.',
      'py',
      ' ',
      '--flag',
    ]);

    const {leftHtml, rightHtml} = renderCodeBlockDiff(
      tokensA[0] as never,
      tokensB[0] as never
    );
    const leftStr = renderToHtmlString(leftHtml);
    const rightStr = renderToHtmlString(rightHtml);
    assert.include(leftStr, 'class="diff-code-line-del"');
    assert.include(
      leftStr,
      '<del class="diff-highlight-del">tools/build_experimentation</del>'
    );
    assert.include(rightStr, 'class="diff-code-line-add"');
    assert.include(
      rightStr,
      '<ins class="diff-highlight-add">&lt;SKILL_DIR&gt;</ins>'
    );
  });

  test('renderListDiff, renderBlockquoteDiff, and renderTableDiff highlight within-element changes and emit placeholders', () => {
    const listA = parseMarkdownBlocks(
      '- Enable `--legacy-auth` on startup\n- Unchanged item'
    )[0];
    const listB = parseMarkdownBlocks(
      '- Enable `--oauth2-auth` on startup\n- Unchanged item\n- Added item'
    )[0];
    const listDiff = renderListDiff(listA as never, listB as never);
    const listLeftStr = renderToHtmlString(listDiff.leftHtml);
    const listRightStr = renderToHtmlString(listDiff.rightHtml);
    assert.include(
      listLeftStr,
      '<del class="diff-highlight-del"><code>--legacy-auth</code></del>'
    );
    assert.include(
      listLeftStr,
      '<li class="diff-list-item-placeholder" aria-hidden="true"></li>'
    );
    assert.include(
      listRightStr,
      '<ins class="diff-highlight-add"><code>--oauth2-auth</code></ins>'
    );
    assert.include(listRightStr, 'class="diff-list-item-add"');

    const quoteA = parseMarkdownBlocks('> Removed in Q3.')[0];
    const quoteB = parseMarkdownBlocks('> Removed in Q4.')[0];
    const quoteDiff = renderBlockquoteDiff(quoteA as never, quoteB as never);
    assert.include(
      renderToHtmlString(quoteDiff.leftHtml),
      '<blockquote><p>Removed in <del class="diff-highlight-del">Q3</del>.</p></blockquote>'
    );
    assert.include(
      renderToHtmlString(quoteDiff.rightHtml),
      '<blockquote><p>Removed in <ins class="diff-highlight-add">Q4</ins>.</p></blockquote>'
    );

    const tableA = parseMarkdownBlocks(
      '| Flag | Default |\n| --- | --- |\n| `--threads` | `4` |'
    )[0];
    const tableB = parseMarkdownBlocks(
      '| Flag | Default | Notes |\n| --- | --- | --- |\n| `--threads` | `8` | Fast |\n| `--verbose` | `true` | Logs |'
    )[0];
    const tableDiff = renderTableDiff(tableA as never, tableB as never);
    const tableLeftStr = renderToHtmlString(tableDiff.leftHtml);
    const tableRightStr = renderToHtmlString(tableDiff.rightHtml);
    assert.include(
      tableLeftStr,
      '<th class="diff-table-cell-placeholder" aria-hidden="true"></th>'
    );
    assert.include(tableLeftStr, '<td><code>--threads</code></td>');
    assert.include(
      tableLeftStr,
      '<td><del class="diff-highlight-del"><code>4</code></del></td>'
    );
    assert.include(
      tableLeftStr,
      '<td class="diff-table-cell-placeholder" aria-hidden="true"></td>'
    );
    assert.include(
      tableLeftStr,
      '<tr class="diff-table-row-placeholder" aria-hidden="true">'
    );
    assert.include(
      tableRightStr,
      '<td><ins class="diff-highlight-add"><code>8</code></ins></td>'
    );
    assert.include(tableRightStr, '<tr class="diff-table-row-add">');
  });

  test('handles combined bold+italic delimiters, backslash escapes, nested lists, ordered list start, and multi-paragraph blockquotes', () => {
    assert.deepEqual(tokenizeWords('***old italic***'), [
      '*',
      '**',
      'old',
      ' ',
      'italic',
      '**',
      '*',
    ]);
    const boldItalicDiff = renderInlineTextDiff(
      'Prefix ***old italic*** suffix.',
      'Prefix ***new italic*** suffix.',
      'p'
    );
    assert.include(
      renderToHtmlString(boldItalicDiff.leftHtml),
      '<em><strong><del class="diff-highlight-del">old</del> italic</strong></em>'
    );
    assert.include(
      renderToHtmlString(boldItalicDiff.rightHtml),
      '<em><strong><ins class="diff-highlight-add">new</ins> italic</strong></em>'
    );

    assert.equal(
      renderToHtmlString(
        renderInlineMarkdown('Escaped \\*star\\* and \\[brackets\\]')
      ),
      'Escaped *star* and [brackets]'
    );

    const nestedListA = parseMarkdownBlocks(
      '3. Parent item\n   - Old child\n4. Second item'
    )[0];
    const nestedListB = parseMarkdownBlocks(
      '3. Parent item\n   - New child\n4. Second item'
    )[0];
    const nestedListDiff = renderListDiff(
      nestedListA as never,
      nestedListB as never
    );
    const nestedLeftStr = renderToHtmlString(nestedListDiff.leftHtml);
    const nestedRightStr = renderToHtmlString(nestedListDiff.rightHtml);
    assert.include(nestedLeftStr, '<ol start="3">');
    assert.match(
      nestedLeftStr,
      /<ul>\s*<li><del class="diff-highlight-del">Old<\/del> child<\/li>\s*<\/ul>/
    );
    assert.match(
      nestedRightStr,
      /<ul>\s*<li><ins class="diff-highlight-add">New<\/ins> child<\/li>\s*<\/ul>/
    );

    const multiQuoteA = parseMarkdownBlocks(
      '> First paragraph old.\n>\n> Second paragraph.'
    )[0];
    const multiQuoteB = parseMarkdownBlocks(
      '> First paragraph new.\n>\n> Second paragraph.'
    )[0];
    const multiQuoteDiff = renderBlockquoteDiff(
      multiQuoteA as never,
      multiQuoteB as never
    );
    assert.include(
      renderToHtmlString(multiQuoteDiff.leftHtml),
      '<blockquote><p>First paragraph <del class="diff-highlight-del">old</del>.</p><p>Second paragraph.</p></blockquote>'
    );
    assert.include(
      renderToHtmlString(multiQuoteDiff.rightHtml),
      '<blockquote><p>First paragraph <ins class="diff-highlight-add">new</ins>.</p><p>Second paragraph.</p></blockquote>'
    );
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
      assert.include(
        renderToHtmlString(rows[1].rightHtml),
        'Inserted paragraph'
      );
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
      assert.include(renderToHtmlString(rows[1].leftHtml), 'Deleted paragraph');
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
      assert.include(
        renderToHtmlString(rows[0].leftHtml),
        'Follow this structure'
      );
      assert.include(
        renderToHtmlString(rows[0].rightHtml),
        'diff-highlight-add'
      );
      assert.include(
        renderToHtmlString(rows[0].rightHtml),
        'Only the first block is required'
      );
    });

    test('modified code block generates line diff highlights', () => {
      const docA = '```bash\nline 1\nold code\nline 3\n```';
      const docB = '```bash\nline 1\nnew code\nline 3\n```';
      const tokensA = parseMarkdownBlocks(docA);
      const tokensB = parseMarkdownBlocks(docB);

      const rows = alignMarkdownTokens(tokensA, tokensB);
      assert.equal(rows.length, 1);
      assert.equal(rows[0].status, 'modified');
      assert.include(
        renderToHtmlString(rows[0].leftHtml),
        'diff-highlight-del'
      );
      assert.include(renderToHtmlString(rows[0].leftHtml), 'old code');
      assert.include(
        renderToHtmlString(rows[0].rightHtml),
        'diff-highlight-add'
      );
      assert.include(renderToHtmlString(rows[0].rightHtml), 'new code');
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

    test('does not cross-pair unrelated blocks when uA === uB > 1', () => {
      const docA =
        'Alpha beta gamma.\n\n```bash\npython3 run_experiment.py --old-flag --config a.json\n```';
      const docB =
        '```bash\npython3 run_experiment.py --new-flag --config a.json\n```\n\nDelta epsilon zeta.';
      const tokensA = parseMarkdownBlocks(docA);
      const tokensB = parseMarkdownBlocks(docB);

      const rows = alignMarkdownTokens(tokensA, tokensB);
      assert.equal(rows.length, 3);
      assert.equal(rows[0].status, 'deleted');
      assert.equal(rows[1].status, 'modified');
      assert.equal(rows[1].leftToken?.type, 'code');
      assert.equal(rows[1].rightToken?.type, 'code');
      assert.equal(rows[2].status, 'added');
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

  suite('adversarial security and DoS hardening', () => {
    test('sanitizeCodeLanguage blocks CSS class injection and Shadow DOM selector hijacking', () => {
      assert.equal(
        sanitizeCodeLanguage('lean selection-action-box diff-cell'),
        'language-lean'
      );
      assert.equal(sanitizeCodeLanguage('c++'), 'language-c++');
      assert.equal(sanitizeCodeLanguage('ts_2.0-beta'), 'language-ts_2.0-beta');
      assert.isUndefined(sanitizeCodeLanguage('   '));
      assert.equal(sanitizeCodeLanguage('"><script>'), 'language-script');
      assert.isUndefined(sanitizeCodeLanguage('"><!@$%^'));
      assert.equal(sanitizeCodeLanguage('a'.repeat(100))?.length, 9 + 40);

      const tokens = parseMarkdownBlocks(
        '```lean selection-action-box diff-cell hygiene-pill verified\n#eval 1\n```'
      );
      const rendered = renderToHtmlString(renderTokenToHtml(tokens[0]));
      assert.include(rendered, '<code class="language-lean">');
      assert.notInclude(rendered, 'selection-action-box');
      assert.notInclude(rendered, 'diff-cell');
      assert.notInclude(rendered, 'hygiene-pill');

      const codeTokenA = parseMarkdownBlocks(
        '```lean selection-action-box\na\n```'
      )[0] as Tokens.Code;
      const codeTokenB = parseMarkdownBlocks(
        '```lean diff-cell\nb\n```'
      )[0] as Tokens.Code;
      const {leftHtml, rightHtml} = renderCodeBlockDiff(codeTokenA, codeTokenB);
      assert.include(
        renderToHtmlString(leftHtml),
        '<code class="language-lean">'
      );
      assert.notInclude(renderToHtmlString(leftHtml), 'selection-action-box');
      assert.include(
        renderToHtmlString(rightHtml),
        '<code class="language-lean">'
      );
      assert.notInclude(renderToHtmlString(rightHtml), 'diff-cell');
    });

    test('sanitizeMarkdownUrl blocks protocol-relative, backslash, control-char, entity-encoded, and exotic schemes', () => {
      const blockedUrls = [
        '//evil.com/phish',
        '///evil.com/phish',
        '/\\evil.com/phish',
        '\\/evil.com/phish',
        '\\\\evil.com/phish',
        '\\evil.com/phish',
        'java\tscript:alert(1)',
        'java\nscript:alert(1)',
        'java\rscript:alert(1)',
        'javascript\u0000:alert(1)',
        '\u0000//evil.com',
        'ht\ttps://evil.com',
        '&#106;avascript:alert(1)',
        '&#x6a;avascript:alert(1)',
        'javascript&colon;alert(1)',
        'http:evil.com',
        'https:evil.com',
        'vbscript:msgbox(1)',
        'blob:https://example.com/123',
        'javascript :alert(1)',
        'mailto:user@example.com',
        'ftp://example.com/file',
      ];
      for (const bad of blockedUrls) {
        assert.equal(
          sanitizeMarkdownUrl(bad),
          'about:invalid#zClosurez',
          `Expected URL to be blocked: ${JSON.stringify(bad)}`
        );
      }
      assert.equal(sanitizeMarkdownUrl(''), '');
      assert.equal(sanitizeMarkdownUrl('   '), '');

      // Safe absolute and relative URLs still pass
      assert.equal(
        sanitizeMarkdownUrl('https://lean-lang.org/doc/reference/latest/'),
        'https://lean-lang.org/doc/reference/latest/'
      );
      assert.equal(
        sanitizeMarkdownUrl('https://example.com/?a=1&amp;b=2'),
        'https://example.com/?a=1&amp;b=2'
      );
      assert.equal(sanitizeMarkdownUrl('./Spec.lean#L42'), './Spec.lean#L42');
      assert.equal(sanitizeMarkdownUrl('#section-1'), '#section-1');
    });

    test('inline markdown lexing is isolated from global marked.setOptions mutations', () => {
      const prevDefaults = marked.defaults;
      try {
        marked.setOptions({pedantic: true, gfm: false});
        const rendered = renderToHtmlString(
          renderInlineMarkdown('~~old~~ and [Safe](https://example.com)')
        );
        assert.include(rendered, '<del>old</del>');
        assert.include(
          rendered,
          '<a target="_blank" rel="noopener noreferrer" href="https://example.com">Safe</a>'
        );
      } finally {
        marked.setOptions(prevDefaults);
      }
    });

    test('diff and alignment utilities stay bounded on large adversarial inputs', () => {
      const start = performance.now();

      const bigA = Array.from({length: 1000}, (_, i) => `a_${i}`);
      const bigB = Array.from({length: 1000}, (_, i) => `b_${i}`);
      const seqOps = computeSequenceDiff(bigA, bigB);
      assert.equal(seqOps.length, 2000);

      const dist = computeNormalizedEditDistance(
        bigA.slice(0, 400),
        bigB.slice(0, 400)
      );
      assert.equal(dist, 1);

      const alternating = Array.from({length: 2000}, (_, i) => {
        return {
          text: i % 2 === 0 ? 'x' : ' ',
          type: i % 2 === 0 ? ('deleted' as const) : ('common' as const),
        };
      });
      const coalesced = coalesceInlineDiffSegments(alternating);
      assert.isAtLeast(coalesced.length, 1);

      const itemsA = Array.from({length: 200}, (_, i) => `item_a_${i}`);
      const itemsB = Array.from({length: 200}, (_, i) => `item_b_${i}`);
      const aligned = alignHomologousItems(itemsA, itemsB, s => [s]);
      assert.equal(aligned.length, 400);

      const elapsedMs = performance.now() - start;
      assert.isBelow(
        elapsedMs,
        1000,
        `Bounded diff utilities took too long: ${elapsedMs}ms`
      );
    });

    test('computeLinearExactAnchors and alignHomologousItems use Patience anchoring on >100-line proofs with repeated lines', () => {
      // Deleting a leading repeated line (`simp`) that also occurs at EOF must
      // not jump the cursor to the bottom and mark all middle lines changed.
      const middle = Array.from({length: 120}, (_, i) => `  have h${i} := rfl`);
      const base = ['  simp', ...middle, '  simp'];
      const rev = [...middle, '  simp'];

      const aligned = alignHomologousItems(base, rev, s => s.split(/\s+/));
      const unchangedCount = aligned.filter(a => a.type === 'unchanged').length;
      const deletedCount = aligned.filter(a => a.type === 'deleted').length;
      const addedCount = aligned.filter(a => a.type === 'added').length;

      assert.equal(unchangedCount, 121);
      assert.equal(deletedCount, 1);
      assert.equal(addedCount, 0);

      // Large input with 5,000 repeated lines must run in linear time.
      const start = performance.now();
      const repA = Array.from({length: 5000}, (_, i) =>
        i % 3 === 0 ? '' : i % 3 === 1 ? 'end' : 'simp'
      );
      const repB = ['extra', ...repA.slice(1)];
      const anchors = computeLinearExactAnchors(repA, repB);
      assert.isAbove(anchors.length, 4900);
      assert.isBelow(performance.now() - start, 500);
    });

    test('computeNormalizedEditDistance preserves Unicode identifiers and supports custom content filters', () => {
      assert.equal(
        computeNormalizedEditDistance(['α', '=', 'β'], ['α', '=', 'β']),
        0
      );
      assert.isAbove(
        computeNormalizedEditDistance(['have', 'α', 'β'], ['have', 'γ', 'δ']),
        0.5
      );
      const withMath = computeNormalizedEditDistance(
        ['∀', 'α', '≤', 'β'],
        ['∃', 'α', '≥', 'β'],
        t => /[\p{L}\p{N}_]/u.test(t) || '∀∃≤≥'.includes(t)
      );
      assert.equal(withMath, 0.5);
    });
  });
});
