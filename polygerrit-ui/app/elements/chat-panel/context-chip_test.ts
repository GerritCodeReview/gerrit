/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import {ContextItem} from '../../api/ai-code-review';
import sinon from 'sinon';
import './context-chip';
import {ContextChip} from './context-chip';
import {testResolver} from '../../test/common-test-setup';
import {pluginLoaderToken} from '../shared/gr-js-api-interface/gr-plugin-loader';
import {chatProvider, createChange} from '../../test/test-data-generators';
import {changeModelToken} from '../../models/change/change-model';
import {ParsedChangeInfo} from '../../types/types';

suite('context-chip tests', () => {
  let element: ContextChip;

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

    element = await fixture(html`<context-chip></context-chip>`);
  });

  test('renders with default properties', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <md-filter-chip class="context-chip " removable="" title="" has-icon>
          <gr-icon class="" icon="" slot="icon"> </gr-icon>
        </md-filter-chip>
      `
    );
  });

  test('renders with text', async () => {
    element.text = 'test text';
    await element.updateComplete;
    const chip = element.shadowRoot?.querySelector('md-filter-chip');
    assert.equal(chip?.label, 'test text');
  });

  test('renders with subtext', async () => {
    element.subText = 'sub text';
    await element.updateComplete;
    const subtext = element.shadowRoot?.querySelector('.subtext');
    assert.isOk(subtext);
    assert.dom.equal(subtext, '<span class="subtext">: sub text</span>');
  });

  test('renders as suggestion', async () => {
    element.isSuggestion = true;
    await element.updateComplete;
    const icon = element.shadowRoot?.querySelector('md-icon');
    assert.isOk(icon);
    assert.equal(icon.textContent?.trim(), 'add');
  });

  test('renders as custom action', async () => {
    element.isCustomAction = true;
    await element.updateComplete;
    const chip = element.shadowRoot?.querySelector('md-filter-chip');
    assert.isTrue(chip?.classList.contains('custom-action-chip'));
  });

  test('renders with tooltip', async () => {
    element.tooltip = 'test tooltip';
    await element.updateComplete;
    const chip = element.shadowRoot?.querySelector('md-filter-chip');
    assert.equal(chip?.title, 'test tooltip');
  });

  test('is removable', async () => {
    element.isRemovable = true;
    element.isSuggestion = false;
    await element.updateComplete;
    const chip = element.shadowRoot?.querySelector('md-filter-chip');
    assert.isTrue(chip?.removable);
  });

  test('is not removable when suggestion', async () => {
    element.isRemovable = true;
    element.isSuggestion = true;
    await element.updateComplete;
    const chip = element.shadowRoot?.querySelector('md-filter-chip');
    assert.isFalse(chip?.removable);
  });

  test('fires remove-context-chip event', async () => {
    const spy = sinon.spy();
    element.addEventListener('remove-context-chip', spy);
    const chip = element.shadowRoot?.querySelector('md-filter-chip');
    chip?.dispatchEvent(new Event('remove'));
    assert.isTrue(spy.called);
  });

  test('fires accept-context-item-suggestion event', async () => {
    element.isSuggestion = true;
    await element.updateComplete;
    const spy = sinon.spy();
    element.addEventListener('accept-context-item-suggestion', spy);
    const trailingIcon = element.shadowRoot?.querySelector('md-icon');
    trailingIcon?.click();
    assert.isTrue(spy.called);
  });

  test('navigates to url', async () => {
    const openSpy = sinon.spy(window, 'open');
    const contextItem: ContextItem = {
      type_id: 'file',
      title: 'test.ts',
      link: ' gerrit.test/test.ts ',
    };
    element.contextItem = contextItem;
    await element.updateComplete;
    const chip = element.shadowRoot?.querySelector('md-filter-chip');
    chip?.click();
    assert.isTrue(openSpy.calledWith('http://gerrit.test/test.ts', '_blank'));
  });
});
