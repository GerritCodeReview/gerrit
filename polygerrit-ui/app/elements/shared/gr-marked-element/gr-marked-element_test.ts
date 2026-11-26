/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-marked-element';
import {assert, fixture, html} from '@open-wc/testing';
import {GrMarkedElement} from './gr-marked-element';
import {detailsExtension} from './details-extension';
import {Marked} from 'marked';

suite('gr-marked-element tests', () => {
  let element: GrMarkedElement;

  setup(async () => {
    element = await fixture<GrMarkedElement>(
      html`<gr-marked-element></gr-marked-element>`
    );
    await element.updateComplete;
  });

  test('details token consumes the full closing tag and trailing whitespace', () => {
    const markdown =
      '<details><summary>title</summary>body</details >   \n\nAfter';
    const tokens = new Marked({extensions: [detailsExtension]}).lexer(markdown);
    assert.equal(
      tokens[0].raw,
      '<details><summary>title</summary>body</details >   \n\n'
    );
    assert.equal(tokens[tokens.length - 1].raw, 'After');
  });

  test('details tags inside fenced code do not end or nest the block', () => {
    const marked = new Marked({extensions: [detailsExtension]});
    for (const fence of ['```', '~~~']) {
      for (const tag of ['</details>', '<details>']) {
        const markdown = `<details><summary>title</summary>\n\n${fence}html\n${tag}\n${fence}\n\nbody\n</details>\nAfter`;
        const tokens = marked.lexer(markdown);
        assert.equal(tokens[0].type, 'details');
        assert.include(tokens[0].raw, `${fence}html\n${tag}\n${fence}`);
        assert.include(tokens[0].raw, 'body\n</details>');
        assert.equal(tokens[tokens.length - 1].raw, 'After');
      }
    }
  });

  test('details tags inside inline code do not end or nest the block', () => {
    const marked = new Marked({extensions: [detailsExtension]});
    for (const tag of ['</details>', '<details>']) {
      const markdown = `<details><summary>title</summary>\n\n\`\`${tag}\`\` body\n</details>\nAfter`;
      const tokens = marked.lexer(markdown);
      assert.equal(tokens[0].type, 'details');
      assert.include(tokens[0].raw, `\`\`${tag}\`\` body\n</details>`);
      assert.equal(tokens[tokens.length - 1].raw, 'After');
    }
  });

  test('hyphenated custom elements do not nest details', () => {
    const tokens = new Marked({extensions: [detailsExtension]}).lexer(
      '<details><summary>title</summary>\n<details-foo>body</details>\nAfter'
    );
    assert.equal(tokens[0].type, 'details');
    assert.include(tokens[0].raw, '</details>');
    assert.equal(tokens[tokens.length - 1].raw, 'After');
  });

  test('details after a paragraph starts a block', () => {
    const tokens = new Marked({extensions: [detailsExtension]}).lexer(
      'intro\n<details><summary>title</summary>body</details>\nafter'
    );
    assert.deepEqual(
      tokens.filter(token => token.type !== 'space').map(token => token.type),
      ['paragraph', 'details', 'paragraph']
    );
  });

  test('inline details and custom elements do not start blocks', () => {
    const marked = new Marked({extensions: [detailsExtension]});
    const inline = marked.lexer(
      'a<details><summary>title</summary>body</details>'
    );
    assert.equal(inline.length, 1);
    assert.equal(inline[0].type, 'paragraph');
    assert.equal(
      inline[0].raw,
      'a<details><summary>title</summary>body</details>'
    );

    const custom = marked.lexer(
      'intro\n<details-foo><summary>title</summary>body</details-foo>'
    );
    assert.notInclude(
      custom.map(token => token.type),
      'details'
    );
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <slot name="markdown-html">
          <div id="content" slot="markdown-html"></div>
        </slot>
      `
    );
  });
});
