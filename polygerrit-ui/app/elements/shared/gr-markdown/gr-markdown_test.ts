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
import './gr-markdown';
import {GrMarkdown} from './gr-markdown';
import {createConfig} from '../../../test/test-data-generators';
import {waitUntilObserved} from '../../../test/test-utils';
import {CommentLinks} from '../../../api/rest-api';
import {testResolver} from '../../../test/common-test-setup';

suite('gr-markdown tests', () => {
  let element: GrMarkdown;
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
    await setCommentLinks({
      customLinkRewrite: {
        match: '(LinkRewriteMe)',
        link: 'http://google.com/$1',
      },
      customHtmlRewrite: {
        match: 'HTMLRewriteMe',
        html: '<div>HTMLRewritten</div>',
      },
    });
    element = (
      await fixture(
        wrapInProvider(
          html`<gr-markdown></gr-markdown>`,
          configModelToken,
          configModel
        )
      )
    ).querySelector('gr-markdown')!;
  });

  suite('as plaintext', () => {
    setup(async () => {
      element.asMarkdown = false;
      await element.updateComplete;
    });

    test('renders text with links and rewrites', async () => {
      element.content = `text with plain link: google.com
        \ntext with config link: LinkRewriteMe
        \ntext with config html: HTMLRewriteMe`;
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <pre class="plaintext">
            text with plain link:
            <a href="http://google.com" rel="noopener" target="_blank">
              google.com
            </a>
            text with config link:
            <a
              href="http://google.com/LinkRewriteMe"
              rel="noopener"
              target="_blank"
            >
              LinkRewriteMe
            </a>
            text with config html:
            <div>HTMLRewritten</div>
          </pre>
        `
      );
    });

    test('does not render typed html', async () => {
      element.content = `plain text <div>foo</div>`;
      await element.updateComplete;

      const escapedDiv = '&lt;div&gt;foo&lt;/div&gt;';
      assert.shadowDom.equal(
        element,
        /* HTML */ `<pre class="plaintext">plain text ${escapedDiv}</pre>`
      );
    });

    test('does not render markdown', async () => {
      element.content = `# A Markdown Heading`;
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `<pre class="plaintext"># A Markdown Heading</pre>`
      );
    });
  });

  suite('as markdown', () => {
    setup(async () => {
      element.asMarkdown = true;
      await element.updateComplete;
    });
    test('renders text with links and rewrites', async () => {
      element.content = `text
        \ntext with plain link: google.com
        \ntext with config link: LinkRewriteMe
        \ntext with config html: HTMLRewriteMe`;
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <marked-element>
            <div slot="markdown-html">
              <p>text</p>
              <p>
                text with plain link:
                <a href="http://google.com" rel="noopener" target="_blank">
                  google.com
                </a>
              </p>
              <p>
                text with config link:
                <a
                  href="http://google.com/LinkRewriteMe"
                  rel="noopener"
                  target="_blank"
                >
                  LinkRewriteMe
                </a>
              </p>
              <p>text with config html:</p>
              <div>HTMLRewritten</div>
              <p></p>
            </div>
          </marked-element>
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
        \n# heading with plain link: google.com
        \n# heading with config link: LinkRewriteMe
        \n# heading with config html: HTMLRewriteMe`;
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <marked-element>
            <div slot="markdown-html">
              <h1 id="h1-heading">h1-heading</h1>
              <h2 id="h2-heading">h2-heading</h2>
              <h3 id="h3-heading">h3-heading</h3>
              <h4 id="h4-heading">h4-heading</h4>
              <h5 id="h5-heading">h5-heading</h5>
              <h6 id="h6-heading">h6-heading</h6>
              <h1 id="heading-with-plain-link-google-com">
                heading with plain link:
                <a href="http://google.com" rel="noopener" target="_blank">
                  google.com
                </a>
              </h1>
              <h1 id="heading-with-config-link-linkrewriteme">
                heading with config link:
                <a
                  href="http://google.com/LinkRewriteMe"
                  rel="noopener"
                  target="_blank"
                >
                  LinkRewriteMe
                </a>
              </h1>
              <h1 id="heading-with-config-html-htmlrewriteme">
                heading with config html:
                <div>HTMLRewritten</div>
              </h1>
            </div>
          </marked-element>
        `
      );
    });

    test('renders inline-code without linking or rewriting', async () => {
      element.content = `\`inline code\`
        \n\`inline code with plain link: google.com\`
        \n\`inline code with config link: LinkRewriteMe\`
        \n\`inline code with config html: HTMLRewriteMe\``;
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <marked-element>
            <div slot="markdown-html">
              <p>
                <code> inline code </code>
              </p>
              <p>
                <code> inline code with plain link: google.com </code>
              </p>
              <p>
                <code> inline code with config link: LinkRewriteMe </code>
              </p>
              <p>
                <code> inline code with config html: HTMLRewriteMe </code>
              </p>
            </div>
          </marked-element>
        `
      );
    });
    test('renders multiline-code without linking or rewriting', async () => {
      element.content = `\`\`\`\nmultiline code\n\`\`\`
        \n\`\`\`\nmultiline code with plain link: google.com\n\`\`\`
        \n\`\`\`\nmultiline code with config link: LinkRewriteMe\n\`\`\`
        \n\`\`\`\nmultiline code with config html: HTMLRewriteMe\n\`\`\``;
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <marked-element>
            <div slot="markdown-html">
              <pre>
              <code> multiline code </code>
            </pre>
              <pre>
              <code> multiline code with plain link: google.com </code>
            </pre>
              <pre>
              <code> multiline code with config link: LinkRewriteMe </code>
            </pre>
              <pre>
              <code> multiline code with config html: HTMLRewriteMe </code>
            </pre>
            </div>
          </marked-element>
        `
      );
    });

    test('does not render inline images into <img> tags', async () => {
      element.content = '![img](google.com/img.png)';
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <marked-element>
            <div slot="markdown-html">
              <p>![img](google.com/img.png)</p>
            </div>
          </marked-element>
        `
      );
    });

    test('renders inline links into <a> tags', async () => {
      element.content = '[myLink](https://www.google.com)';
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <marked-element>
            <div slot="markdown-html">
              <p>
                <a href="https://www.google.com">myLink</a>
              </p>
            </div>
          </marked-element>
        `
      );
    });

    test('renders block quotes with links and rewrites', async () => {
      element.content = `> block quote
        \n> block quote with plain link: google.com
        \n> block quote with config link: LinkRewriteMe
        \n> block quote with config html: HTMLRewriteMe`;
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <marked-element>
            <div slot="markdown-html">
              <blockquote>
                <p>block quote</p>
              </blockquote>
              <blockquote>
                <p>
                  block quote with plain link:
                  <a href="http://google.com" rel="noopener" target="_blank">
                    google.com
                  </a>
                </p>
              </blockquote>
              <blockquote>
                <p>
                  block quote with config link:
                  <a
                    href="http://google.com/LinkRewriteMe"
                    rel="noopener"
                    target="_blank"
                  >
                    LinkRewriteMe
                  </a>
                </p>
              </blockquote>
              <blockquote>
                <p>block quote with config html:</p>
                <div>HTMLRewritten</div>
                <p></p>
              </blockquote>
            </div>
          </marked-element>
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
          <marked-element>
            <div slot="markdown-html">
              <p>plain text ${escapedDiv}</p>
              <p>
                <code> inline code ${escapedDiv} </code>
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
                <a href="http://google.com"> inline link ${escapedDiv} </a>
              </p>
            </div>
          </marked-element>
        `
      );
    });
  });
});
