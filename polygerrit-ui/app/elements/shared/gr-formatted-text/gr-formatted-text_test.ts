/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
<<<<<<< PATCH SET (77e628 Replace gr-formatted-text with gr-markdown)
=======
import {fixture, html, assert} from '@open-wc/testing';
>>>>>>> BASE      (380bee Enable markdown renderer by default)
import '../../../test/common-test-setup';
<<<<<<< PATCH SET (77e628 Replace gr-formatted-text with gr-markdown)
import {assert, fixture, html} from '@open-wc/testing';
import {changeModelToken} from '../../../models/change/change-model';
=======
import './gr-formatted-text';
>>>>>>> BASE      (380bee Enable markdown renderer by default)
import {
  ConfigModel,
  configModelToken,
} from '../../../models/config/config-model';
import {wrapInProvider} from '../../../models/di-provider-element';
import {getAppContext} from '../../../services/app-context';
import './gr-formatted-text';
import {GrFormattedText} from './gr-formatted-text';
import {createConfig} from '../../../test/test-data-generators';
import {waitUntilObserved} from '../../../test/test-utils';
import {CommentLinks} from '../../../api/rest-api';
import {testResolver} from '../../../test/common-test-setup';

suite('gr-formatted-text tests', () => {
  let element: GrFormattedText;
<<<<<<< PATCH SET (77e628 Replace gr-formatted-text with gr-markdown)
  let configModel: ConfigModel;
=======
>>>>>>> BASE      (380bee Enable markdown renderer by default)

  async function setCommentLinks(commentlinks: CommentLinks) {
    configModel.updateRepoConfig({...createConfig(), commentlinks});
    await waitUntilObserved(
      configModel.repoCommentLinks$,
      links => links === commentlinks
    );
  }

  setup(async () => {
<<<<<<< PATCH SET (77e628 Replace gr-formatted-text with gr-markdown)
    configModel = new ConfigModel(
      testResolver(changeModelToken),
      getAppContext().restApiService
    );
    await setCommentLinks({
      customLinkRewrite: {
        match: '(LinkRewriteMe)',
        link: 'http://google.com/$1',
=======
    element = await fixture(html`<gr-formatted-text></gr-formatted-text>`);
  });

  test('parse empty', () => {
    assert.lengthOf(element._computeBlocks(''), 0);
  });

  test('render', async () => {
    element.content = 'text `code`';
    await element.updateComplete;

    assert.shadowDom.equal(element, /* HTML */ ` <gr-markdown></gr-markdown> `);
  });

  for (const text of [
    'Para1',
    'Para 1\nStill para 1',
    'Para 1\n\nPara 2\n\nPara 3',
  ]) {
    test('parse simple', () => {
      const comment = {type: 'text', text} as TextSpan;
      const result = element._computeBlocks(text);
      assert.lengthOf(result, 1);
      assertTextBlock(result[0], [comment]);
    });
  }

  test('parse link', () => {
    const comment = '[text](url)';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 1);
    assertTextBlock(result[0], [{type: 'link', text: 'text', url: 'url'}]);
  });

  test('parse inline code', () => {
    const comment = 'text `code`';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 1);
    assertTextBlock(result[0], [
      {type: 'text', text: 'text '},
      {type: 'code', text: 'code'},
    ]);
  });

  test('parse quote', () => {
    const comment = '> Quote text';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 1);
    const quoteBlock = assertQuoteBlock(result[0]);
    assert.lengthOf(quoteBlock.blocks, 1);
    assertSimpleTextBlock(quoteBlock.blocks[0], 'Quote text');
  });

  test('parse quote lead space', () => {
    const comment = ' > Quote text';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 1);
    const quoteBlock = assertQuoteBlock(result[0]);
    assert.lengthOf(quoteBlock.blocks, 1);
    assertSimpleTextBlock(quoteBlock.blocks[0], 'Quote text');
  });

  test('parse multiline quote', () => {
    const comment = '> Quote line 1\n> Quote line 2\n > Quote line 3\n';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 1);
    const quoteBlock = assertQuoteBlock(result[0]);
    assert.lengthOf(quoteBlock.blocks, 1);
    assertSimpleTextBlock(
      quoteBlock.blocks[0],
      'Quote line 1\nQuote line 2\nQuote line 3'
    );
  });

  test('parse pre', () => {
    const comment = '    Four space indent.';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 1);
    assertPreBlock(result[0], comment);
  });

  test('one space is not a pre', () => {
    const comment = ' One space indent.\n Another line.';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 1);
    assertSimpleTextBlock(result[0], comment);
  });

  test('parse multi-line space pre', () => {
    const comment = '    One space indent.\n    Another line.';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 1);
    assertPreBlock(result[0], comment);
  });

  test('parse tab pre', () => {
    const comment = '\tOne tab indent.\n\tAnother line.\n    Yet another!';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 1);
    assertPreBlock(result[0], comment);
  });

  test('parse star list', () => {
    const comment = '* Item 1\n* Item 2\n* Item 3';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 1);
    assertListBlock(result[0], [
      {spans: [{type: 'text', text: 'Item 1'}]},
      {spans: [{type: 'text', text: 'Item 2'}]},
      {spans: [{type: 'text', text: 'Item 3'}]},
    ]);
  });

  test('parse dash list', () => {
    const comment = '- Item 1\n- Item 2\n- Item 3';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 1);
    assertListBlock(result[0], [
      {spans: [{type: 'text', text: 'Item 1'}]},
      {spans: [{type: 'text', text: 'Item 2'}]},
      {spans: [{type: 'text', text: 'Item 3'}]},
    ]);
  });

  test('parse mixed list', () => {
    const comment = '- Item 1\n* Item 2\n- Item 3\n* Item 4';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 1);
    assertListBlock(result[0], [
      {spans: [{type: 'text', text: 'Item 1'}]},
      {spans: [{type: 'text', text: 'Item 2'}]},
      {spans: [{type: 'text', text: 'Item 3'}]},
      {spans: [{type: 'text', text: 'Item 4'}]},
    ]);
  });

  test('parse mixed block types', () => {
    const comment =
      'Paragraph\nacross\na\nfew\nlines.' +
      '\n\n' +
      '> Quote\n> across\n> not many lines.' +
      '\n\n' +
      'Another paragraph' +
      '\n\n' +
      '* Series\n* of\n* list\n* items' +
      '\n\n' +
      'Yet another paragraph' +
      '\n\n' +
      '\tPreformatted text.' +
      '\n\n' +
      'Parting words.';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 7);
    assertSimpleTextBlock(result[0], 'Paragraph\nacross\na\nfew\nlines.\n');

    const quoteBlock = assertQuoteBlock(result[1]);
    assert.lengthOf(quoteBlock.blocks, 1);
    assertSimpleTextBlock(
      quoteBlock.blocks[0],
      'Quote\nacross\nnot many lines.'
    );

    assertSimpleTextBlock(result[2], 'Another paragraph\n');
    assertListBlock(result[3], [
      {spans: [{type: 'text', text: 'Series'}]},
      {spans: [{type: 'text', text: 'of'}]},
      {spans: [{type: 'text', text: 'list'}]},
      {spans: [{type: 'text', text: 'items'}]},
    ]);
    assertSimpleTextBlock(result[4], 'Yet another paragraph\n');
    assertPreBlock(result[5], '\tPreformatted text.');
    assertSimpleTextBlock(result[6], 'Parting words.');
  });

  test('bullet list 1', () => {
    const comment = 'A\n\n* line 1';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 2);
    assertSimpleTextBlock(result[0], 'A\n');
    assertListBlock(result[1], [{spans: [{type: 'text', text: 'line 1'}]}]);
  });

  test('bullet list 2', () => {
    const comment = 'A\n\n* line 1\n* 2nd line';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 2);
    assertSimpleTextBlock(result[0], 'A\n');
    assertListBlock(result[1], [
      {spans: [{type: 'text', text: 'line 1'}]},
      {spans: [{type: 'text', text: '2nd line'}]},
    ]);
  });

  test('bullet list 3', () => {
    const comment = 'A\n* line 1\n* 2nd line\n\nB';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 3);
    assertSimpleTextBlock(result[0], 'A');
    assertListBlock(result[1], [
      {spans: [{type: 'text', text: 'line 1'}]},
      {spans: [{type: 'text', text: '2nd line'}]},
    ]);
    assertSimpleTextBlock(result[2], 'B');
  });

  test('bullet list 4', () => {
    const comment = '* line 1\n* 2nd line\n\nB';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 2);
    assertListBlock(result[0], [
      {spans: [{type: 'text', text: 'line 1'}]},
      {spans: [{type: 'text', text: '2nd line'}]},
    ]);
    assertSimpleTextBlock(result[1], 'B');
  });

  test('bullet list 5', () => {
    const comment =
      'To see this bug, you have to:\n' +
      '* Be on IMAP or EAS (not on POP)\n' +
      '* Be very unlucky\n';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 2);
    assertSimpleTextBlock(result[0], 'To see this bug, you have to:');
    assertListBlock(result[1], [
      {spans: [{type: 'text', text: 'Be on IMAP or EAS (not on POP)'}]},
      {spans: [{type: 'text', text: 'Be very unlucky'}]},
    ]);
  });

  test('bullet list 6', () => {
    const comment =
      'To see this bug,\n' +
      'you have to:\n' +
      '* Be on IMAP or EAS (not on POP)\n' +
      '* Be very unlucky\n';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 2);
    assertSimpleTextBlock(result[0], 'To see this bug,\nyou have to:');
    assertListBlock(result[1], [
      {spans: [{type: 'text', text: 'Be on IMAP or EAS (not on POP)'}]},
      {spans: [{type: 'text', text: 'Be very unlucky'}]},
    ]);
  });

  test('dash list 1', () => {
    const comment = 'A\n- line 1\n- 2nd line';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 2);
    assertSimpleTextBlock(result[0], 'A');
    assertListBlock(result[1], [
      {spans: [{type: 'text', text: 'line 1'}]},
      {spans: [{type: 'text', text: '2nd line'}]},
    ]);
  });

  test('dash list 2', () => {
    const comment = 'A\n- line 1\n- 2nd line\n\nB';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 3);
    assertSimpleTextBlock(result[0], 'A');
    assertListBlock(result[1], [
      {spans: [{type: 'text', text: 'line 1'}]},
      {spans: [{type: 'text', text: '2nd line'}]},
    ]);
    assertSimpleTextBlock(result[2], 'B');
  });

  test('dash list 3', () => {
    const comment = '- line 1\n- 2nd line\n\nB';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 2);
    assertListBlock(result[0], [
      {spans: [{type: 'text', text: 'line 1'}]},
      {spans: [{type: 'text', text: '2nd line'}]},
    ]);
    assertSimpleTextBlock(result[1], 'B');
  });

  test('list with links', () => {
    const comment = '- [text](http://url)\n- 2nd line';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 1);
    assertListBlock(result[0], [
      {
        spans: [{type: 'link', text: 'text', url: 'http://url'}],
>>>>>>> BASE      (380bee Enable markdown renderer by default)
      },
      customHtmlRewrite: {
        match: 'HTMLRewriteMe',
        html: '<div>HTMLRewritten</div>',
      },
    });
    element = (
      await fixture(
        wrapInProvider(
          html`<gr-formatted-text></gr-formatted-text>`,
          configModelToken,
          configModel
        )
      )
    ).querySelector('gr-formatted-text')!;
  });

  suite('as plaintext', () => {
    setup(async () => {
      element.markdown = false;
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
      element.content = 'plain text <div>foo</div>';
      await element.updateComplete;

      const escapedDiv = '&lt;div&gt;foo&lt;/div&gt;';
      assert.shadowDom.equal(
        element,
        /* HTML */ `<pre class="plaintext">plain text ${escapedDiv}</pre>`
      );
    });

    test('does not render markdown', async () => {
      element.content = '# A Markdown Heading';
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ '<pre class="plaintext"># A Markdown Heading</pre>'
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
              <h1>h1-heading</h1>
              <h2>h2-heading</h2>
              <h3>h3-heading</h3>
              <h4>h4-heading</h4>
              <h5>h5-heading</h5>
              <h6>h6-heading</h6>
              <h1>
                heading with plain link:
                <a href="http://google.com" rel="noopener" target="_blank">
                  google.com
                </a>
              </h1>
              <h1>
                heading with config link:
                <a
                  href="http://google.com/LinkRewriteMe"
                  rel="noopener"
                  target="_blank"
                >
                  LinkRewriteMe
                </a>
              </h1>
              <h1>
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
                <code>inline code</code>
              </p>
              <p>
                <code>inline code with plain link: google.com</code>
              </p>
              <p>
                <code>inline code with config link: LinkRewriteMe</code>
              </p>
              <p>
                <code>inline code with config html: HTMLRewriteMe</code>
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
              <code>multiline code</code>
            </pre>
              <pre>
              <code>multiline code with plain link: google.com</code>
            </pre>
              <pre>
              <code>multiline code with config link: LinkRewriteMe</code>
            </pre>
              <pre>
              <code>multiline code with config html: HTMLRewriteMe</code>
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
                <a href="http://google.com">inline link ${escapedDiv}</a>
              </p>
            </div>
          </marked-element>
        `
      );
    });
  });
});
