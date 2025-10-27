/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import './context-input-chip';
import {assert, fixture, html} from '@open-wc/testing';
import {ContextInputChip} from './context-input-chip';
import sinon from 'sinon';
import {ContextItem} from '../../api/ai-code-review';
import {testResolver} from '../../test/common-test-setup';
import {pluginLoaderToken} from '../shared/gr-js-api-interface/gr-plugin-loader';
import {chatProvider, createChange} from '../../test/test-data-generators';
import {changeModelToken} from '../../models/change/change-model';
import {ParsedChangeInfo} from '../../types/types';

suite('context-input-chip tests', () => {
  let element: ContextInputChip;

  setup(async () => {
    const pluginLoader = testResolver(pluginLoaderToken);
    pluginLoader.pluginsModel.aiCodeReviewRegister({
      pluginName: 'test-plugin',
      provider: chatProvider,
    });

    const changeModel = testResolver(changeModelToken);
    changeModel.updateState({
      change: createChange() as ParsedChangeInfo,
    });

    element = await fixture(html`<context-input-chip></context-input-chip>`);
    await element.updateComplete;
  });

  test('renders the add context chip', () => {
    const chip = element.shadowRoot?.querySelector('md-assist-chip');
    assert.isOk(chip);
    assert.equal(chip?.label, 'Add Context');
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
    const menuItem = element.shadowRoot?.querySelector('md-menu-item');
    menuItem?.click();
    await element.updateComplete;

    assert.isTrue(element.addLinkDialogOpened);
    const input = element.shadowRoot?.querySelector('.add-link-input');
    assert.isOk(input);
  });

  test('fires context-item-added event on enter', async () => {
    const spy = sinon.spy();
    element.addEventListener('context-item-added', spy);

    const menuItem = element.shadowRoot?.querySelector('md-menu-item');
    menuItem?.click();
    await element.updateComplete;

    const link = 'http://www.google.com';
    const input = element.shadowRoot?.querySelector(
      '.add-link-input'
    ) as HTMLInputElement;
    input.value = link;
    input.dispatchEvent(new Event('input'));
    await element.updateComplete;

    input.dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter'}));
    await element.updateComplete;

    assert.isTrue(spy.called);
    const event = spy.args[0][0] as CustomEvent<ContextItem>;
    assert.deepEqual(event.detail, {
      type_id: 'google',
      identifier: 'google-id',
      link,
      title: 'google-title',
    });
  });
});
