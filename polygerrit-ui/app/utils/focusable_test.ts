/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../test/common-test-setup-karma';
import {getFocusableElements} from './focusable';
import {html, render} from 'lit';
import {fixture} from '@open-wc/testing-helpers';



suite('focusable', () => {
  let container: HTMLElement;
  setup(async () => {
    container = await fixture<HTMLDivElement>(html`<div></div>`);
    const shadow = container.attachShadow({mode: 'open'});
    render(html`
      <a href="" id="first"></a>
      <slot></slot>
      <button id="third"></button>
      <button class="not" style="display: none"></button>
      <button class="not" style="visibility: hidden"></button>
      <span id="fourth" tabindex="0"></span>
      <textarea id="fifth"></textarea>
    `, shadow);
    const slottedContent = document.createElement('div');
    render(html`
      <textarea id="second"></textarea>
    `, slottedContent)
    const slot: HTMLSlotElement | null = shadow.querySelector('slot');
    //
    (slot! as any).assign(slottedContent);
  });

  test('Finds all focusables in-order', () => {
    const results = [...getFocusableElements(container)];
    expect(results.map(e => e.id)).to.have.ordered.members(
      ["first", "second", "third", "fourth", "fifth"]
    );
  })
});
