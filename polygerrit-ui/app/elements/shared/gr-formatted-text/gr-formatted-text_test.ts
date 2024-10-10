/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
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
import {queryAndAssert, waitUntilObserved} from '../../../test/test-utils';
import {CommentLinks, EmailAddress} from '../../../api/rest-api';
import {testResolver} from '../../../test/common-test-setup';
import {GrAccountChip} from '../gr-account-chip/gr-account-chip';
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
    self.CANONICAL_PATH = 'http://localhost';
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
          FOO<a href="a.b.c" rel="noopener noreferrer" target="_blank">foo</a>
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
            <a href="bug/123" rel="noopener noreferrer" target="_blank">
              bug/123
            </a>
            <a href="bug/456" rel="noopener noreferrer" target="_blank">
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
            <a
              href="http://localhost/page?id=12"
              rel="noopener noreferrer"
              target="_blank"
            >
              Link 12
            </a>
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
      const checkLinking = async (url: string) => {
        element.content = url;
        await element.updateComplete;
        const a = queryAndAssert<HTMLElement>(element, 'a');
        assert.equal(a.getAttribute('href'), url);
        assert.equal(a.innerText, url);
      };

      await checkLinking('http://www.google.com');
      await checkLinking('https://www.google.com');
      await checkLinking('https://www.google.com/');
      await checkLinking('https://www.google.com/asdf~');
      await checkLinking('https://www.google.com/asdf-');
      await checkLinking('https://www.google.com/asdf-');
      // matches & part as well, even we first linkify and then htmlEscape
      await checkLinking(
        'https://google.com/traces/list?project=gerrit&tid=123'
      );
    });
  });

  suite('as markdown', () => {
    setup(async () => {
      element.markdown = true;
      await element.updateComplete;
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
            <marked-element>
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
                  <a
                    href="http://localhost/page?id=12"
                    rel="noopener noreferrer"
                    target="_blank"
                  >
                    Link 12
                  </a>
                </p>
              </div>
            </marked-element>
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
          <a
            href="http://localhost/page?id=12"
            rel="noopener noreferrer"
            target="_blank"
          >
            Link 12
          </a>
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
            <marked-element>
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
            </marked-element>
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
            <marked-element>
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
            </marked-element>
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
            <marked-element>
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
            </marked-element>
          </gr-endpoint-decorator>
        `
      );
    });

    test('does not render inline images into <img> tags', async () => {
      element.content = '![img](google.com/img.png)';
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="formatted-text-endpoint">
            <marked-element>
              <div slot="markdown-html" class="markdown-html">
                <p>![img](google.com/img.png)</p>
              </div>
            </marked-element>
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
            <marked-element>
              <div slot="markdown-html" class="markdown-html">
                <p>
                  <gr-account-chip></gr-account-chip>
                </p>
              </div>
            </marked-element>
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
            <marked-element>
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
            </marked-element>
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
            <marked-element>
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
            </marked-element>
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
            <marked-element>
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
            </marked-element>
          </gr-endpoint-decorator>
        `
      );
    });

    test('never renders typed html', async () => {
      element.content = `plain text <div>foo</div>
        \n\`inline code <div>foo</div>\`
        \n\`\`\`\nmultiline code <div>foo</div>\`\`\`
        \n> block quote <div>foo</div>
        \n[inline link <div>foo</div>](http://google.com)`;
      await element.updateComplete;

      const escapedDiv = '&lt;div&gt;foo&lt;/div&gt;';
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="formatted-text-endpoint">
            <marked-element>
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
            </marked-element>
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
            <marked-element>
              <div slot="markdown-html" class="markdown-html">
                <blockquote>
                  <blockquote>
                    <blockquote>
                      <p>block quote</p>
                    </blockquote>
                  </blockquote>
                </blockquote>
              </div>
            </marked-element>
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
            <marked-element>
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
            </marked-element>
          </gr-endpoint-decorator>
        `
      );
    });

    test('does default linking', async () => {
      const checkLinking = async (url: string) => {
        element.content = url;
        await element.updateComplete;
        const a = queryAndAssert<HTMLElement>(element, 'a');
        const p = queryAndAssert<HTMLElement>(element, 'p');
        assert.equal(a.getAttribute('href'), url);
        assert.equal(p.innerText, url);
      };

      await checkLinking('http://www.google.com');
      await checkLinking('https://www.google.com');
      await checkLinking('https://www.google.com/');
      // matches & part as well, even we first linkify and then htmlEscape
      await checkLinking(
        'https://google.com/traces/list?project=gerrit&tid=123'
      );
    });

    suite('user suggest fix', () => {
      setup(async () => {
        const flagsService = getAppContext().flagsService;
        sinon.stub(flagsService, 'isEnabled').returns(true);
      });

      test('renders', async () => {
        element.content = '```suggestion\nHello World```';
        await element.updateComplete;
        assert.shadowDom.equal(
          element,
          /* HTML */
          `
            <gr-endpoint-decorator name="formatted-text-endpoint">
              <marked-element>
                <div class="markdown-html" slot="markdown-html">
                  <gr-user-suggestion-fix>Hello World</gr-user-suggestion-fix>
                </div>
              </marked-element>
            </gr-endpoint-decorator>
          `
        );
      });
    });
  });
});
