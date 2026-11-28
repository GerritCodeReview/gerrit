/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import {assert, aTimeout, fixture, html} from '@open-wc/testing';
import {changeModelToken} from '../../../models/change/change-model';
import {
  ConfigModel,
  configModelToken,
} from '../../../models/config/config-model';
import {wrapInProvider} from '../../../models/di-provider-element';
import {getAppContext} from '../../../services/app-context';
import './gr-formatted-text';
import {GrFormattedText} from './gr-formatted-text';
import {createComment, createConfig} from '../../../test/test-data-generators';
import {
  mockPromise,
  query,
  queryAndAssert,
  waitUntil,
  waitUntilObserved,
} from '../../../test/test-utils';
import {CommentLinks, EmailAddress} from '../../../api/rest-api';
import {testResolver} from '../../../test/common-test-setup';
import {GrAccountChip} from '../gr-account-chip/gr-account-chip';
import {highlightServiceToken} from '../../../services/highlight/highlight-service';
import {SyntaxLayerLine} from '../../../types/syntax-worker-api';
import {userModelToken} from '../../../models/user/user-model';
import {createDefaultDiffPrefs} from '../../../constants/constants';
import {
  CommentModel,
  commentModelToken,
} from '../gr-comment-model/gr-comment-model';

suite('gr-formatted-text tests', () => {
  let element: GrFormattedText;
  let configModel: ConfigModel;

  async function setCommentLinks(commentlinks: CommentLinks) {
    configModel.updateRepoConfig({...createConfig(), commentlinks});
    await waitUntilObserved(
      configModel.repoCommentLinks$,
      links => links === commentlinks
    );
  }

  setup(async () => {
    configModel = new ConfigModel(
      testResolver(changeModelToken),
      getAppContext().restApiService
    );
    const commentModel = new CommentModel(getAppContext().restApiService);
    commentModel.updateState({
      comment: createComment(),
    });
    await setCommentLinks({
      customLinkRewrite: {
        match: '(LinkRewriteMe)',
        link: 'http://google.com/$1',
      },
      complexLinkRewrite: {
        match: '(^|\\s)A Link (\\d+)($|\\s)',
        link: '/page?id=$2',
        text: 'Link $2',
        prefix: '$1A ',
        suffix: '$3',
      },
    });
    self.CANONICAL_PATH = '';
    element = (
      await fixture(
        wrapInProvider(
          wrapInProvider(
            html`<gr-formatted-text></gr-formatted-text>`,
            configModelToken,
            configModel
          ),
          commentModelToken,
          commentModel
        )
      )
    ).querySelector('gr-formatted-text')!;
  });

  suite('as plaintext', () => {
    setup(async () => {
      element.markdown = false;
      await element.updateComplete;
    });

    test('does not apply rewrites within links', async () => {
      element.content = 'http://google.com/LinkRewriteMe';
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="formatted-text-endpoint">
            <pre class="plaintext">
            <a
              href="http://google.com/LinkRewriteMe"
              rel="noopener noreferrer"
              target="_blank"
            >
            http://google.com/LinkRewriteMe
            </a>
          </pre>
          </gr-endpoint-decorator>
        `
      );
    });

    test('does not apply rewrites on rewritten text', async () => {
      await setCommentLinks({
        capitalizeFoo: {
          match: 'foo',
          prefix: 'FOO',
          link: 'a.b.c',
        },
        lowercaseFoo: {
          match: 'FOO',
          prefix: 'foo',
          link: 'c.d.e',
        },
      });
      element.content = 'foo';
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="formatted-text-endpoint">
            <pre class="plaintext">
          FOO<a href="a.b.c">foo</a>
        </pre>
          </gr-endpoint-decorator>
        `
      );
    });

    test('supports overlapping rewrites', async () => {
      await setCommentLinks({
        bracketNum: {
          match: '(Start:) ([0-9]+)',
          prefix: '$1 ',
          link: 'bug/$2',
          text: 'bug/$2',
        },
        bracketNum2: {
          match: '(Start: [0-9]+) ([0-9]+)',
          prefix: '$1 ',
          link: 'bug/$2',
          text: 'bug/$2',
        },
      });
      element.content = 'Start: 123 456';
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="formatted-text-endpoint">
            <pre class="plaintext">
            Start:
            <a href="bug/123">
              bug/123
            </a>
            <a href="bug/456">
              bug/456
            </a>
          </pre>
          </gr-endpoint-decorator>
        `
      );
    });

    test('renders text with links and rewrites', async () => {
      element.content = `
        text with plain link: http://google.com
        text with config link: LinkRewriteMe
        text with complex link: A Link 12`;
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="formatted-text-endpoint">
            <pre class="plaintext">
          text with plain link:
          <a
            href="http://google.com"
            rel="noopener noreferrer"
            target="_blank"
          >
            http://google.com
          </a>
          text with config link:
            <a
              href="http://google.com/LinkRewriteMe"
              rel="noopener noreferrer"
              target="_blank"
            >
              LinkRewriteMe
            </a>
            text with complex link: A
            <a href="/page?id=12">Link 12</a>
          </pre>
          </gr-endpoint-decorator>
        `
      );
    });

    test('does not render typed html', async () => {
      element.content = 'plain text <div>foo</div>';
      await element.updateComplete;

      const escapedDiv = '&lt;div&gt;foo&lt;/div&gt;';
      assert.shadowDom.equal(
        element,
        /* HTML */ `<gr-endpoint-decorator name="formatted-text-endpoint">
          <pre class="plaintext">plain text ${escapedDiv}</pre>
        </gr-endpoint-decorator>`
      );
    });

    test('does not render markdown', async () => {
      element.content = '# A Markdown Heading';
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */
        `<gr-endpoint-decorator name="formatted-text-endpoint">
          <pre class="plaintext"># A Markdown Heading</pre>
        </gr-endpoint-decorator>`
      );
    });

    test('does default linking', async () => {
      const checkLinking = async (url: string, expectLinkified = true) => {
        element.content = url;
        await element.updateComplete;
        const a = query<HTMLElement>(element, 'a');

        if (expectLinkified) {
          assert.isDefined<HTMLElement | undefined>(a);
          // URLs without scheme are upgraded to https:// by the
          // ALWAYS_LINK_SCHEMELESS rule. URLs with http:// or https://
          // are preserved by the ALWAYS_LINK_HTTP rule.
          const isSchemeless =
            !url.startsWith('http://') &&
            !url.startsWith('https://') &&
            !url.startsWith('mailto:') &&
            !url.startsWith('/');
          const expectedHref = isSchemeless ? `http://${url}` : url;

          assert.equal(a.getAttribute('href'), expectedHref);
          assert.equal(a.innerText, url);
        } else {
          assert.isUndefined(a);
        }
      };

      const getLinkifiedUrl = async (url: string) => {
        element.content = url;
        await element.updateComplete;
        const a = query<HTMLElement>(element, 'a');

        assert.isDefined<HTMLElement | undefined>(a);
        return a.innerText;
      };

      await checkLinking('http://www.google.com');
      await checkLinking('https://www.google.com');
      await checkLinking('https://www.google.com/');
      await checkLinking('https://www.google.com/asdf~');
      await checkLinking('https://www.google.com/asdf-');
      // We DO NOT want to linkify URLs ending with `)`, even though the URL
      // spec does allow it. Links ending with `)` are extremely rare, and
      // users putting links into parenthesis happens extremely often. :-)
      assert.equal(
        await getLinkifiedUrl('https://www.google.com/asdf)'),
        'https://www.google.com/asdf'
      );
      assert.equal(
        await getLinkifiedUrl('hello gerrit.com/asdf,'),
        'gerrit.com/asdf'
      );
      assert.equal(
        await getLinkifiedUrl('hello gerrit.com/asdf.),'),
        'gerrit.com/asdf'
      );
      assert.equal(
        await getLinkifiedUrl('hello gerrit.com/asdf),'),
        'gerrit.com/asdf'
      );
      assert.equal(
        await getLinkifiedUrl('hello gerrit.com/foo,bar'),
        'gerrit.com/foo,bar'
      );
      // matches & part as well, even we first linkify and then htmlEscape
      await checkLinking(
        'https://google.com/traces/list?project=gerrit&tid=123'
      );

      await checkLinking('www.google.com/path');
      await checkLinking('www.google-foo.com/path');
      await checkLinking('google.co.uk/path?q=1#frag');

      // Do not linkify URLs without `/`.
      await checkLinking('google.com', false);
      await checkLinking('com.google.gerrit.server.Event', false);

      // Do not linkify URLs without a recognized TLD.
      await checkLinking('google.foogle/path', false);
      await checkLinking('google.com.blah/path', false);
    });
  });

  suite('as markdown', () => {
    setup(async () => {
      element.markdown = true;
      await element.updateComplete;
    });

    test('applies overflow-wrap: break-word to markdown-html', async () => {
      element.content = 'text';
      await element.updateComplete;
      const div = queryAndAssert<HTMLElement>(element, 'div.markdown-html');
      const style = window.getComputedStyle(div);
      assert.equal(style.overflowWrap, 'break-word');
    });

    test('indents lists on the list element instead of on every item', async () => {
      element.content = '- one\n  - nested\n- two';
      await element.updateComplete;

      const ul = queryAndAssert<HTMLUListElement>(element, 'ul');
      const nestedUl = queryAndAssert<HTMLUListElement>(element, 'ul ul');
      const li = queryAndAssert<HTMLLIElement>(element, 'li');
      const step = getComputedStyle(ul).paddingLeft;

      assert.isAbove(parseFloat(step), 0);
      assert.equal(getComputedStyle(nestedUl).paddingLeft, step);
      assert.equal(getComputedStyle(li).marginLeft, '0px');
    });

    test('renders text with links and rewrites', async () => {
      element.content = `text
        \ntext with plain link: http://google.com
        \ntext with config link: LinkRewriteMe
        \ntext without a link: NotA Link 15 cats
        \ntext with complex link: A Link 12`;
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="formatted-text-endpoint">
            <gr-marked-element>
              <div slot="markdown-html" class="markdown-html">
                <p>text</p>
                <p>
                  text with plain link:
                  <a
                    href="http://google.com"
                    rel="noopener noreferrer"
                    target="_blank"
                  >
                    http://google.com
                  </a>
                </p>
                <p>
                  text with config link:
                  <a
                    href="http://google.com/LinkRewriteMe"
                    rel="noopener noreferrer"
                    target="_blank"
                  >
                    LinkRewriteMe
                  </a>
                </p>
                <p>text without a link: NotA Link 15 cats</p>
                <p>
                  text with complex link: A
                  <a href="/page?id=12">Link 12</a>
                </p>
              </div>
            </gr-marked-element>
          </gr-endpoint-decorator>
        `
      );
    });

    test('does not render if too long', async () => {
      element.content = `text
        text with plain link: http://google.com
        text with config link: LinkRewriteMe
        text without a link: NotA Link 15 cats
        text with complex link: A Link 12`;
      element.MARKDOWN_LIMIT = 10;
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="formatted-text-endpoint">
            <pre class="plaintext">
          text
        text with plain link:
        <a
          href="http://google.com"
          rel="noopener noreferrer"
          target="_blank"
        >
          http://google.com
        </a>
        text with config link:
          <a
            href="http://google.com/LinkRewriteMe"
            rel="noopener noreferrer"
            target="_blank"
          >
            LinkRewriteMe
          </a>
        text without a link: NotA Link 15 cats
        text with complex link: A
          <a href="/page?id=12">Link 12</a>
        </pre>
          </gr-endpoint-decorator>
        `
      );
    });

    test('renders headings with links and rewrites', async () => {
      element.content = `# h1-heading
        \n## h2-heading
        \n### h3-heading
        \n#### h4-heading
        \n##### h5-heading
        \n###### h6-heading
        \n# heading with plain link: http://google.com
        \n# heading with config link: LinkRewriteMe`;
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="formatted-text-endpoint">
            <gr-marked-element>
              <div slot="markdown-html" class="markdown-html">
                <h1>h1-heading</h1>
                <h2>h2-heading</h2>
                <h3>h3-heading</h3>
                <h4>h4-heading</h4>
                <h5>h5-heading</h5>
                <h6>h6-heading</h6>
                <h1>
                  heading with plain link:
                  <a
                    href="http://google.com"
                    rel="noopener noreferrer"
                    target="_blank"
                  >
                    http://google.com
                  </a>
                </h1>
                <h1>
                  heading with config link:
                  <a
                    href="http://google.com/LinkRewriteMe"
                    rel="noopener noreferrer"
                    target="_blank"
                  >
                    LinkRewriteMe
                  </a>
                </h1>
              </div>
            </gr-marked-element>
          </gr-endpoint-decorator>
        `
      );
    });

    test('renders inline-code without linking or rewriting', async () => {
      element.content = `\`inline code\`
        \n\`inline code with plain link: google.com\`
        \n\`inline code with config link: LinkRewriteMe\``;
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="formatted-text-endpoint">
            <gr-marked-element>
              <div slot="markdown-html" class="markdown-html">
                <p>
                  <code>inline code</code>
                </p>
                <p>
                  <code>inline code with plain link: google.com</code>
                </p>
                <p>
                  <code>inline code with config link: LinkRewriteMe</code>
                </p>
              </div>
            </gr-marked-element>
          </gr-endpoint-decorator>
        `
      );
    });

    test('renders multiline-code without linking or rewriting', async () => {
      element.content = `\`\`\`\nmultiline code\n\`\`\`
        \n\`\`\`\nmultiline code with plain link: google.com\n\`\`\`
        \n\`\`\`\nmultiline code with config link: LinkRewriteMe\n\`\`\``;
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="formatted-text-endpoint">
            <gr-marked-element>
              <div slot="markdown-html" class="markdown-html">
                <pre>
              <code>multiline code</code>
            </pre>
                <pre>
              <code>multiline code with plain link: google.com</code>
            </pre>
                <pre>
              <code>multiline code with config link: LinkRewriteMe</code>
            </pre>
              </div>
            </gr-marked-element>
          </gr-endpoint-decorator>
        `
      );
    });

    test('highlights tagged fences and preserves the original code', async () => {
      const highlight = sinon.stub(
        testResolver(highlightServiceToken),
        'highlight'
      );
      highlight.resolves([]);
      highlight
        .withArgs('cpp')
        .resolves([
          {ranges: [{start: 3, length: 3, className: 'type'}]},
          {ranges: [{start: 0, length: 6, className: 'keyword'}]},
        ]);
      highlight
        .withArgs('python')
        .resolves([{ranges: [{start: 0, length: 6, className: 'keyword'}]}]);
      element.content = [
        '`inline`',
        '',
        '```',
        'plain',
        '```',
        '',
        '```cpp extra',
        '🐟 int x;',
        'return "<tag>";',
        '```',
        '',
        '> ~~~python',
        '> return 42',
        '> ~~~',
        '',
        '```unknown',
        '<tag>',
        '```',
        '',
        '```suggestion',
        'replacement',
        '```',
      ].join('\n');
      await waitUntil(
        () =>
          element.shadowRoot!.querySelectorAll('.gr-syntax-keyword').length ===
          2
      );
      const blocks = element.shadowRoot!.querySelectorAll('pre > code');
      assert.lengthOf(blocks, 4);
      assert.equal(blocks[1].textContent, '🐟 int x;\nreturn "<tag>";\n');
      assert.equal(
        blocks[1].querySelector('.gr-syntax-type')?.textContent,
        'int'
      );
      assert.equal(
        blocks[2].querySelector('.gr-syntax-keyword')?.textContent,
        'return'
      );
      assert.equal(blocks[3].textContent, '<tag>\n');
      assert.lengthOf(blocks[3].children, 0);
      assert.isNull(blocks[1].querySelector('tag'));
      assert.exists(query(element, 'gr-user-suggestion-fix'));
      assert.sameMembers(
        highlight.getCalls().map(call => call.args[0]),
        ['cpp', 'python', 'unknown']
      );
    });

    test('preserves a code-formatted comment link before a tagged fence', async () => {
      await setCommentLinks({
        issue: {
          match: 'ISSUE-42',
          link: '/q/42',
          prefix: '<pre><code>',
          suffix: '</code></pre>',
        },
      });
      const highlight = sinon
        .stub(testResolver(highlightServiceToken), 'highlight')
        .resolves([{ranges: [{start: 0, length: 5, className: 'keyword'}]}]);
      element.content = 'ISSUE-42\n\n~~~js\nconst x = 42;\n~~~';
      await waitUntil(() => !!query(element, '.gr-syntax-keyword'));
      assert.isTrue(
        highlight.calledOnceWithExactly('js', 'const x = 42;\n', {
          ignoreUnknownLanguage: true,
        })
      );
      assert.equal(
        queryAndAssert(element, 'pre code a').getAttribute('href'),
        '/q/42'
      );
      assert.equal(
        queryAndAssert(element, 'pre code .gr-syntax-keyword').textContent,
        'const'
      );
    });

    test('reuses unchanged fences and evicts removed ones', async () => {
      const highlight = sinon
        .stub(testResolver(highlightServiceToken), 'highlight')
        .resolves([{ranges: [{start: 0, length: 1, className: 'number'}]}]);
      async function show(content: string, count: number) {
        element.content = content;
        await element.updateComplete;
        await waitUntil(
          () =>
            element.shadowRoot!.querySelectorAll('.gr-syntax-number').length ===
            count
        );
      }
      const one = '~~~js\n1\n~~~';
      const two = '~~~js\n2\n~~~';
      const three = '~~~js\n3\n~~~';
      await show([one, one, two].join('\n\n'), 3);
      await show([one, one, two, 'more prose'].join('\n\n'), 3);
      await show([one, three].join('\n\n'), 2);
      await show([one, two].join('\n\n'), 2);
      await show('', 0);
      await show(one, 1);
      assert.deepEqual(
        highlight.args.map(args => args[1]),
        ['1\n', '2\n', '3\n', '2\n', '1\n']
      );
    });

    test('waits for a preference before touching an already rendered fence', async () => {
      const model = testResolver(userModelToken);
      model.updateState({diffPreferences: undefined});
      const highlight = sinon
        .stub(testResolver(highlightServiceToken), 'highlight')
        .resolves([{ranges: [{start: 0, length: 3, className: 'type'}]}]);
      const fresh = document.createElement('gr-formatted-text');
      fresh.markdown = true;
      fresh.content = '~~~cpp\nint i;\n~~~';
      element.parentElement!.appendChild(fresh);
      await fresh.updateComplete;
      const code = queryAndAssert(fresh, 'pre > code');
      assert.equal(code.textContent, 'int i;\n');
      assert.isFalse(highlight.called);
      model.setDiffPreferences({
        ...createDefaultDiffPrefs(),
        syntax_highlighting: false,
      });
      await fresh.updateComplete;
      assert.isFalse(highlight.called);
      model.setDiffPreferences({
        ...createDefaultDiffPrefs(),
        syntax_highlighting: true,
      });
      await waitUntil(() => !!code.querySelector('.gr-syntax-type'));
      assert.isTrue(highlight.calledOnce);
      model.setDiffPreferences({
        ...createDefaultDiffPrefs(),
        syntax_highlighting: false,
      });
      await waitUntil(() => !code.querySelector('.gr-syntax-type'));
      assert.strictEqual(queryAndAssert(fresh, 'pre > code'), code);
      assert.equal(code.textContent, 'int i;\n');
    });

    test('disabling highlighting discards an outstanding result', async () => {
      const pending = mockPromise<SyntaxLayerLine[]>();
      const highlight = sinon
        .stub(testResolver(highlightServiceToken), 'highlight')
        .returns(pending);
      element.content = '~~~cpp\nint x;\n~~~';
      await waitUntil(() => highlight.called);
      const code = queryAndAssert(element, 'pre > code');
      testResolver(userModelToken).setDiffPreferences({
        ...createDefaultDiffPrefs(),
        syntax_highlighting: false,
      });
      pending.resolve([{ranges: [{start: 0, length: 3, className: 'type'}]}]);
      await element.updateComplete;
      await aTimeout(0);
      assert.strictEqual(queryAndAssert(element, 'pre > code'), code);
      assert.equal(code.textContent, 'int x;\n');
      assert.lengthOf(code.children, 0);
    });

    test('submits a fence once on mount and when comment links arrive', async () => {
      const highlight = sinon
        .stub(testResolver(highlightServiceToken), 'highlight')
        .resolves([{ranges: [{start: 0, length: 5, className: 'keyword'}]}]);
      const fresh = document.createElement('gr-formatted-text');
      fresh.markdown = true;
      fresh.content = '~~~js\nconst x = 42;\n~~~';
      element.parentElement!.appendChild(fresh);
      await waitUntil(() => !!query(fresh, '.gr-syntax-keyword'));
      const block = queryAndAssert(fresh, 'pre > code');
      await setCommentLinks({});
      await waitUntil(() => query(fresh, 'pre > code') !== block);
      await waitUntil(() => !!query(fresh, '.gr-syntax-keyword'));
      assert.isTrue(highlight.calledOnce);
    });

    test('replacing a comment while highlighting leaves the replacement intact', async () => {
      const pending = mockPromise<SyntaxLayerLine[]>();
      const highlight = sinon
        .stub(testResolver(highlightServiceToken), 'highlight')
        .returns(pending);
      element.content = '```cpp\nint x;\n```';
      await waitUntil(() => highlight.called);
      const original = queryAndAssert(element, 'pre > code');
      element.content = '```\nnew text\n```';
      await element.updateComplete;
      pending.resolve([{ranges: [{start: 0, length: 3, className: 'type'}]}]);
      await pending;
      const block = queryAndAssert(element, 'pre > code');
      assert.lengthOf(original.children, 0);
      assert.equal(block.textContent, 'new text\n');
      assert.lengthOf(block.children, 0);
    });

    test('keeps a code selection while a worker result arrives', async () => {
      const pending = mockPromise<SyntaxLayerLine[]>();
      const highlight = sinon
        .stub(testResolver(highlightServiceToken), 'highlight')
        .returns(pending);
      element.content = '~~~cpp\nint x;\n~~~\n\n~~~cpp\nint y;\n~~~';
      await waitUntil(() => highlight.called);
      const code = queryAndAssert(element, 'pre > code');
      const range = document.createRange();
      range.setStart(code.firstChild!, 0);
      range.setEnd(code.firstChild!, 3);
      const selection = window.getSelection()!;
      selection.removeAllRanges();
      selection.setBaseAndExtent(
        range.startContainer,
        range.startOffset,
        range.endContainer,
        range.endOffset
      );
      try {
        assert.equal(selection.toString(), 'int');
        pending.resolve([{ranges: [{start: 0, length: 3, className: 'type'}]}]);
        await waitUntil(
          () => !!query(element, 'pre:nth-of-type(2) .gr-syntax-type')
        );
        assert.equal(selection.toString(), 'int');
        assert.lengthOf(code.children, 0);
        selection.removeAllRanges();
        await waitUntil(() => !!code.querySelector('.gr-syntax-type'));
        assert.equal(code.textContent, 'int x;\n');
        assert.equal(highlight.callCount, 2);
      } finally {
        selection.removeAllRanges();
      }
    });

    test('reports a rejected worker request once and keeps plain text', async () => {
      const error = new Error('Worker unavailable');
      const report = sinon.stub(getAppContext().reportingService, 'error');
      const highlight = sinon
        .stub(testResolver(highlightServiceToken), 'highlight')
        .rejects(error);
      element.content = '~~~cpp\nint x;\n~~~';
      await waitUntil(() => report.called);
      const first = queryAndAssert(element, 'pre > code');
      await setCommentLinks({});
      await waitUntil(() => query(element, 'pre > code') !== first);
      assert.isTrue(
        report.calledOnceWithExactly('Comment syntax highlighting', error)
      );
      assert.isTrue(highlight.calledOnce);
      const code = queryAndAssert(element, 'pre > code');
      assert.equal(code.textContent, 'int x;\n');
      assert.lengthOf(code.children, 0);
    });

    test('preserves a caret when adding and removing syntax spans', async () => {
      const pending = mockPromise<SyntaxLayerLine[]>();
      const highlight = sinon
        .stub(testResolver(highlightServiceToken), 'highlight')
        .returns(pending);
      element.content = '~~~js\nconst x = 42;\n~~~';
      await waitUntil(() => highlight.called);
      const code = queryAndAssert(element, 'pre > code');
      const selection = window.getSelection()!;
      const selectedOffset = () => {
        const range = selection.getComposedRanges({
          shadowRoots: [element.shadowRoot!],
        })[0];
        assert.isTrue(range.collapsed);
        assert.isTrue(code.contains(range.startContainer));
        const prefix = document.createRange();
        prefix.selectNodeContents(code);
        prefix.setEnd(range.startContainer, range.startOffset);
        return prefix.toString().length;
      };
      try {
        selection.collapse(code.firstChild, 8);
        assert.equal(selectedOffset(), 8);
        pending.resolve([
          {ranges: [{start: 0, length: 5, className: 'keyword'}]},
        ]);
        await waitUntil(() => !!code.querySelector('span'));
        assert.equal(selectedOffset(), 8);
        selection.collapse(code.querySelector('span')!.firstChild, 3);
        testResolver(userModelToken).setDiffPreferences({
          ...createDefaultDiffPrefs(),
          syntax_highlighting: false,
        });
        await waitUntil(() => !code.querySelector('span'));
        assert.equal(selectedOffset(), 3);
      } finally {
        selection.removeAllRanges();
      }
    });

    test('does not render inline images into <img> tags', async () => {
      element.content = '![img](google.com/img.png)';
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="formatted-text-endpoint">
            <gr-marked-element>
              <div slot="markdown-html" class="markdown-html">
                <p>![img](google.com/img.png)</p>
              </div>
            </gr-marked-element>
          </gr-endpoint-decorator>
        `
      );
    });

    test('handles @mentions', async () => {
      element.content = '@someone@google.com';
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="formatted-text-endpoint">
            <gr-marked-element>
              <div slot="markdown-html" class="markdown-html">
                <p>
                  <gr-account-chip></gr-account-chip>
                </p>
              </div>
            </gr-marked-element>
          </gr-endpoint-decorator>
        `
      );
      const accountChip = queryAndAssert<GrAccountChip>(
        element,
        'gr-account-chip'
      );
      assert.equal(
        accountChip.account?.email,
        'someone@google.com' as EmailAddress
      );
    });

    test('does not handle @mentions that is part of a code block', async () => {
      element.content = '`@`someone@google.com';
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="formatted-text-endpoint">
            <gr-marked-element>
              <div slot="markdown-html" class="markdown-html">
                <p>
                  <code>@</code>
                  <a
                    href="mailto:someone@google.com"
                    rel="noopener noreferrer"
                    target="_blank"
                  >
                    someone@google.com
                  </a>
                </p>
              </div>
            </gr-marked-element>
          </gr-endpoint-decorator>
        `
      );
    });

    test('renders inline links into <a> tags', async () => {
      const origin = window.location.origin;
      element.content = `[myLink1](https://www.google.com)
        [myLink2](/destiny)
        [myLink3](${origin}/destiny)
        [myLink4](google.com)
        [myLink5](http://google.com)
        [myLink6](mailto:google@google.com)
      `;
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="formatted-text-endpoint">
            <gr-marked-element>
              <div slot="markdown-html" class="markdown-html">
                <p>
                  <a
                    href="https://www.google.com"
                    rel="noopener noreferrer"
                    target="_blank"
                    >myLink1</a
                  >
                  <br />
                  <a href="/destiny">myLink2</a>
                  <br />
                  <a href="${origin}/destiny">myLink3</a>
                  <br />
                  <a
                    href="https://google.com"
                    rel="noopener noreferrer"
                    target="_blank"
                    >myLink4</a
                  >
                  <br />
                  <a
                    href="http://google.com"
                    rel="noopener noreferrer"
                    target="_blank"
                    >myLink5</a
                  >
                  <br />
                  <a
                    href="mailto:google@google.com"
                    rel="noopener noreferrer"
                    target="_blank"
                    >myLink6</a
                  >
                </p>
              </div>
            </gr-marked-element>
          </gr-endpoint-decorator>
        `
      );
    });

    test('renders block quotes with links and rewrites', async () => {
      element.content = `> block quote
        \n> block quote with plain link: http://google.com
        \n> block quote with config link: LinkRewriteMe`;
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="formatted-text-endpoint">
            <gr-marked-element>
              <div slot="markdown-html" class="markdown-html">
                <blockquote>
                  <p>block quote</p>
                </blockquote>
                <blockquote>
                  <p>
                    block quote with plain link:
                    <a
                      href="http://google.com"
                      rel="noopener noreferrer"
                      target="_blank"
                    >
                      http://google.com
                    </a>
                  </p>
                </blockquote>
                <blockquote>
                  <p>
                    block quote with config link:
                    <a
                      href="http://google.com/LinkRewriteMe"
                      rel="noopener noreferrer"
                      target="_blank"
                    >
                      LinkRewriteMe
                    </a>
                  </p>
                </blockquote>
              </div>
            </gr-marked-element>
          </gr-endpoint-decorator>
        `
      );
    });

    test('never renders typed html', async () => {
      element.content = `plain text <div>foo</div>
        \n\`inline code <div>foo</div>\`
        \n\`\`\`\nmultiline code <div>foo</div>\n\`\`\`
        \n> block quote <div>foo</div>
        \n[inline link <div>foo</div>](http://google.com)`;
      await element.updateComplete;

      const escapedDiv = '&lt;div&gt;foo&lt;/div&gt;';
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="formatted-text-endpoint">
            <gr-marked-element>
              <div slot="markdown-html" class="markdown-html">
                <p>plain text ${escapedDiv}</p>
                <p>
                  <code>inline code ${escapedDiv}</code>
                </p>
                <pre>
              <code>
                multiline code ${escapedDiv}
              </code>
            </pre>
                <blockquote>
                  <p>block quote ${escapedDiv}</p>
                </blockquote>
                <p>
                  <a
                    href="http://google.com"
                    rel="noopener noreferrer"
                    target="_blank"
                    >inline link ${escapedDiv}</a
                  >
                </p>
              </div>
            </gr-marked-element>
          </gr-endpoint-decorator>
        `
      );
    });

    test('treats HTML outside code blocks as text', async () => {
      element.content = `
<div>
  Hello
</div>

<div>
  Hello
</div>
with text
afterwards.


<div>Single-line block</div>

Text with <span>inline HTML</span>
and more text.

Some
<div>text in div</div>
on several lines.

Some
\\<div>text in escaped div</div>
on several lines.

<div>**markdown** and <b>HTML</b> in HTML blocks stay as-is</div>

An <a href="example.com">inline HTML link</a>.

An <a href="example.com">inline HTML link with **markup**</a>.

An <a href="example.com">inline HTML link with [markup link](http://google.com)</a>.
`;
      await element.updateComplete;

      const escapedHtml = '&lt;div&gt;<br>  Hello<br>&lt;/div&gt;';
      const escapedHtmlWithMixedContent =
        '&lt;div&gt;' +
        '**markdown** and &lt;b&gt;HTML&lt;/b&gt; in HTML blocks stay as-is' +
        '&lt;/div&gt;';
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="formatted-text-endpoint">
            <gr-marked-element>
              <div slot="markdown-html" class="markdown-html">
                <p>${escapedHtml}</p>
                <p>
                  ${escapedHtml}<br />
                  with text<br />
                  afterwards.
                </p>
                <p>&lt;div&gt;Single-line block&lt;/div&gt;</p>
                <p>
                  Text with &lt;span&gt;inline HTML&lt;/span&gt;<br />
                  and more text.
                </p>
                <p>Some</p>
                <p>
                  &lt;div&gt;text in div&lt;/div&gt;<br />
                  on several lines.
                </p>
                <p>
                  Some<br />
                  &lt;div&gt;text in escaped div&lt;/div&gt;<br />
                  on several lines.
                </p>
                <p>${escapedHtmlWithMixedContent}</p>
                <p>
                  An &lt;a href="example.com"&gt;inline HTML link&lt;/a&gt;.
                </p>
                <p>
                  An &lt;a href="example.com"&gt;inline HTML link with
                  <strong>markup</strong>&lt;/a&gt;.
                </p>
                <p>
                  An &lt;a href="example.com"&gt;inline HTML link with
                  <a
                    href="http://google.com"
                    rel="noopener noreferrer"
                    target="_blank"
                    >markup link</a
                  >&lt;/a&gt;.
                </p>
              </div>
            </gr-marked-element>
          </gr-endpoint-decorator>
        `
      );
    });

    test('renders nested block quotes', async () => {
      element.content = '> > > block quote';
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="formatted-text-endpoint">
            <gr-marked-element>
              <div slot="markdown-html" class="markdown-html">
                <blockquote>
                  <blockquote>
                    <blockquote>
                      <p>block quote</p>
                    </blockquote>
                  </blockquote>
                </blockquote>
              </div>
            </gr-marked-element>
          </gr-endpoint-decorator>
        `
      );
    });

    test('renders rewrites with an asterisk', async () => {
      await setCommentLinks({
        customLinkRewrite: {
          match: 'asterisks (\\*) rule',
          link: 'http://google.com',
        },
      });

      element.content = 'I think asterisks * rule';
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="formatted-text-endpoint">
            <gr-marked-element>
              <div slot="markdown-html" class="markdown-html">
                <p>
                  I think
                  <a
                    href="http://google.com"
                    rel="noopener noreferrer"
                    target="_blank"
                    >asterisks * rule</a
                  >
                </p>
              </div>
            </gr-marked-element>
          </gr-endpoint-decorator>
        `
      );
    });

    test('does default linking', async () => {
      const checkLinking = async (url: string, expectLinkified = true) => {
        element.content = url;
        await element.updateComplete;
        const a = query<HTMLElement>(element, 'a');
        const p = queryAndAssert<HTMLElement>(element, 'p');
        assert.equal(p.innerText, url);

        if (expectLinkified) {
          assert.isDefined<HTMLElement | undefined>(a);
          // URLs without scheme are upgraded to https:// by the
          // ALWAYS_LINK_SCHEMELESS rule. URLs with http:// or https://
          // are preserved by the ALWAYS_LINK_HTTP rule.
          const isSchemeless =
            !url.startsWith('http://') &&
            !url.startsWith('https://') &&
            !url.startsWith('mailto:') &&
            !url.startsWith('/');
          const expectedHref = isSchemeless ? `http://${url}` : url;

          assert.equal(a.getAttribute('href'), expectedHref);
        } else {
          assert.isUndefined(a);
        }
      };

      await checkLinking('http://www.google.com');
      await checkLinking('https://www.google.com');
      await checkLinking('https://www.google.com/');
      // matches & part as well, even we first linkify and then htmlEscape
      await checkLinking(
        'https://google.com/traces/list?project=gerrit&tid=123'
      );

      await checkLinking('www.google.com/path');
      await checkLinking('www.google-foo.com/path');
      await checkLinking('google.co.uk/path?q=1#frag');

      // Do not linkify URLs without `/`.
      await checkLinking('google.com', false);
      await checkLinking('com.google.gerrit.server.Event', false);

      // Do not linkify URLs without a recognized TLD.
      await checkLinking('google.foogle/path', false);
      await checkLinking('google.com.blah/path', false);
    });

    test('reproduce b/519426997: linkification in loose list', async () => {
      element.content = '1. A Link 1234\n\n2. aaa';
      await element.updateComplete;
      await new Promise<void>(resolve => {
        const listener = () => {
          element.removeEventListener('marked-render-complete', listener);
          resolve();
        };
        element.addEventListener('marked-render-complete', listener);
        setTimeout(() => {
          element.removeEventListener('marked-render-complete', listener);
          resolve();
        }, 100);
      });

      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="formatted-text-endpoint">
            <gr-marked-element>
              <div slot="markdown-html" class="markdown-html">
                <ol>
                  <li>
                    <p>
                      A
                      <a href="/page?id=1234">Link 1234</a>
                    </p>
                  </li>
                  <li>
                    <p>aaa</p>
                  </li>
                </ol>
              </div>
            </gr-marked-element>
          </gr-endpoint-decorator>
        `
      );
    });

    test('renders loose list of links without leaking html attributes', async () => {
      element.content = `
* [First Link](https://example.com/first)

* [Second Link](https://example.com/second)
      `;
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="formatted-text-endpoint">
            <gr-marked-element>
              <div slot="markdown-html" class="markdown-html">
                <ul>
                  <li>
                    <p>
                      <a
                        href="https://example.com/first"
                        rel="noopener noreferrer"
                        target="_blank"
                        >First Link</a
                      >
                    </p>
                  </li>
                  <li>
                    <p>
                      <a
                        href="https://example.com/second"
                        rel="noopener noreferrer"
                        target="_blank"
                        >Second Link</a
                      >
                    </p>
                  </li>
                </ul>
              </div>
            </gr-marked-element>
          </gr-endpoint-decorator>
        `
      );
    });

    test('renders nested loose list with links without leaking html attributes', async () => {
      element.content = `
* item 1
  * [Nested Link](https://example.com/nested)

* item 2
      `;
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="formatted-text-endpoint">
            <gr-marked-element>
              <div slot="markdown-html" class="markdown-html">
                <ul>
                  <li>
                    <p>item 1</p>
                    <ul>
                      <li>
                        <a
                          href="https://example.com/nested"
                          rel="noopener noreferrer"
                          target="_blank"
                          >Nested Link</a
                        >
                      </li>
                    </ul>
                  </li>
                  <li>
                    <p>item 2</p>
                  </li>
                </ul>
              </div>
            </gr-marked-element>
          </gr-endpoint-decorator>
        `
      );
    });

    suite('user suggest fix', () => {
      setup(async () => {
        const flagsService = getAppContext().flagsService;
        sinon.stub(flagsService, 'isEnabled').returns(true);
      });

      test('renders', async () => {
        element.content = '```suggestion\nHello World\n```';
        await element.updateComplete;
        assert.shadowDom.equal(
          element,
          /* HTML */
          `
            <gr-endpoint-decorator name="formatted-text-endpoint">
              <gr-marked-element>
                <div class="markdown-html" slot="markdown-html">
                  <gr-user-suggestion-fix>Hello World</gr-user-suggestion-fix>
                </div>
              </gr-marked-element>
            </gr-endpoint-decorator>
          `
        );
      });
    });
  });
});
