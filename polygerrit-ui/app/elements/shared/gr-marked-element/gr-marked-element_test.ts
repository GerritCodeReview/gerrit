/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-marked-element';
import {assert, fixture, html} from '@open-wc/testing';
import {GrMarkedElement} from './gr-marked-element';

suite('gr-marked-element tests', () => {
  let element: GrMarkedElement;

  setup(async () => {
    element = await fixture<GrMarkedElement>(
      html`<gr-marked-element></gr-marked-element>`
    );
    await element.updateComplete;
  });

  test('renders slotted Markdown once on mount and again on reconnect', async () => {
    const container = await fixture<HTMLElement>(html`<div></div>`);
    const marked = document.createElement('gr-marked-element');
    const output = document.createElement('div');
    output.slot = 'markdown-html';
    marked.append(output);
    marked.markdown = 'one';
    let renders = 0;
    marked.addEventListener('marked-render-complete', () => renders++);
    container.append(marked);
    await marked.updateComplete;
    assert.equal(output.querySelector('p')?.textContent, 'one');
    assert.equal(renders, 1);

    marked.remove();
    marked.markdown = 'two';
    await marked.updateComplete;
    container.append(marked);
    await marked.updateComplete;
    assert.equal(output.querySelector('p')?.textContent, 'two');
    assert.equal(renders, 2);

    marked.markdown = '';
    await marked.updateComplete;
    assert.equal(output.textContent, '');
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
