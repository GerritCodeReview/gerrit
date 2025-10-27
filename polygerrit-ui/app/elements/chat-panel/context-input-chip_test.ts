/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import './context-input-chip';
import {assert, fixture, html} from '@open-wc/testing';
import {ContextInputChip} from './context-input-chip';
import {customElement} from 'lit/decorators.js';
import sinon from 'sinon';
import {ContextItem, ContextItemType} from '../../api/ai-code-review';
import {BehaviorSubject} from 'rxjs';

@customElement('test-context-input-chip')
class TestContextInputChip extends ContextInputChip {
  constructor() {
    super();
    const fakeChatModel = {
      contextItemTypes$: new BehaviorSubject<ContextItemType[]>([]),
    };
    (this as any).getChatModel = () => fakeChatModel;
  }
}

suite('context-input-chip tests', () => {
  let element: TestContextInputChip;

  setup(async () => {
    element = await fixture(
      html`<test-context-input-chip></test-context-input-chip>`
    );
  });

  test('renders the add context chip', () => {
    const chip = element.shadowRoot?.querySelector('md-assist-chip');
    assert.isOk(chip);
  });

  test('opens the menu when the chip is clicked', async () => {
    const chip = element.shadowRoot?.querySelector('md-assist-chip');
    const menu = element.shadowRoot?.querySelector('md-menu');
    assert.isFalse(menu?.open);
    chip?.click();
    await element.updateComplete;
    assert.isTrue(menu?.open);
  });

  test('shows link dialog when menu item is clicked', async () => {
    const contextMenuItems: ContextItemType[] = [
      {
        id: 'link',
        name: 'Link',
        icon: 'link',
        placeholder: 'Paste link here',
        regex: /.*/,
        parse: (input: string) =>
          ({
            type_id: 'link',
            title: input,
            name: input,
            link: input,
          } as ContextItem),
      },
    ];
    const chatModel = (element as any).getChatModel();
    chatModel.contextItemTypes$.next(contextMenuItems);
    await element.updateComplete;

    const menuItem = element.shadowRoot?.querySelector('md-menu-item');
    menuItem?.click();
    await element.updateComplete;

    assert.isTrue((element as any).addLinkDialogOpened);
    const input = element.shadowRoot?.querySelector('.add-link-input');
    assert.isOk(input);
  });

  test('fires context-item-added event on enter', async () => {
    const spy = sinon.spy();
    element.addEventListener('context-item-added', spy);

    const contextMenuItem: ContextItemType = {
      id: 'link',
      name: 'Link',
      icon: 'link',
      placeholder: 'Paste link here',
      regex: /.*/,
      parse: (input: string) =>
        ({
          type_id: 'link',
          title: input,
          link: input,
        } as ContextItem),
    };
    (element as any).showLinkDialogInput(contextMenuItem);
    await element.updateComplete;

    (element as any).linkInputText = 'http://example.com';
    const input = element.shadowRoot?.querySelector(
      '.add-link-input'
    ) as HTMLInputElement;
    input.dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter'}));

    assert.isTrue(spy.called);
    const event = spy.args[0][0] as CustomEvent<ContextItem>;
    assert.deepEqual(event.detail, {
      type_id: 'link',
      title: 'http://example.com',
      link: 'http://example.com',
    });
  });
});

declare global {
  interface HTMLElementTagNameMap {
    'test-context-input-chip': TestContextInputChip;
  }
}
