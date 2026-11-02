/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-marked-element';
import {assert, fixture, html} from '@open-wc/testing';
import {GrMarkedElement} from './gr-marked-element';
import {Token} from 'marked';

suite('gr-marked-element tests', () => {
  let element: GrMarkedElement;

  setup(async () => {
    element = await fixture<GrMarkedElement>(
      html`<gr-marked-element></gr-marked-element>`
    );
    await element.updateComplete;
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

  test('emits tokens on marked-render-complete', async () => {
    let emittedTokens: Token[] = [];
    element.addEventListener('marked-render-complete', (e: Event) => {
      emittedTokens = (e as CustomEvent<{tokens: Token[]}>).detail.tokens;
    });
    element.markdown = 'Hello `world`';
    await element.updateComplete;

    assert.isTrue(emittedTokens.some(token => token.type === 'codespan'));
  });
});
