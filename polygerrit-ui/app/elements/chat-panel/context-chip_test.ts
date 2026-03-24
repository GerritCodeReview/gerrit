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
import {MdFilterChip} from '@material/web/chips/filter-chip';

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
        <md-filter-chip class="context-chip no-link" removable="" title="">
          <div class="context-chip-container">
            <span class="context-chip-title"> </span>
          </div>
        </md-filter-chip>
      `
    );
  });

  test('renders with text', async () => {
    element.text = 'test text';
    await element.updateComplete;
    const chip = element.shadowRoot?.querySelector('md-filter-chip');
    assert.equal(chip?.textContent?.trim(), 'test text');
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
    const icon = element.shadowRoot?.querySelector('gr-icon');
    assert.isOk(icon);
    assert.equal(icon.getAttribute('icon'), 'add');
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

  test('renders with gerrit change icon', async () => {
    const contextItem: ContextItem = {
      type_id: 'gerrit_change',
      title: 'This Change',
      link: '',
      tooltip: 'File diffs (against base), commit message, and comments.',
    };
    element.contextItem = contextItem;
    await element.updateComplete;

    // Should use gr-icon element with commit
    const icon = element.shadowRoot?.querySelector('gr-icon');

    assert.isOk(icon, 'Expected gr-icon to be rendered');
    assert.equal(icon.getAttribute('icon'), 'commit');
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

  test('fires accept-context-item-suggestion event on chip click', async () => {
    element.isSuggestion = true;
    await element.updateComplete;
    const spy = sinon.spy();
    element.addEventListener('accept-context-item-suggestion', spy);
    const chip = element.shadowRoot?.querySelector('md-filter-chip');
    chip?.click();
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

  test('has no-link class when no link', async () => {
    const contextItem: ContextItem = {
      type_id: 'file',
      title: 'test.ts',
      link: '',
    };
    element.contextItem = contextItem;
    await element.updateComplete;
    const chip = element.shadowRoot?.querySelector('md-filter-chip');
    assert.isTrue(chip?.classList.contains('no-link'));
  });

  test('does not have no-link class when has link', async () => {
    const contextItem: ContextItem = {
      type_id: 'file',
      title: 'test.ts',
      link: ' http://gerrit.test/test.ts ',
    };
    element.contextItem = contextItem;
    await element.updateComplete;
    const chip = element.shadowRoot?.querySelector('md-filter-chip');
    assert.isFalse(chip?.classList.contains('no-link'));
  });

  test('does not navigate on click when no link', async () => {
    const openSpy = sinon.spy(window, 'open');
    const contextItem: ContextItem = {
      type_id: 'file',
      title: 'test.ts',
      link: '',
    };
    element.contextItem = contextItem;
    await element.updateComplete;
    const chip = element.shadowRoot?.querySelector('md-filter-chip');
    chip?.click();
    assert.isFalse(openSpy.called);
  });

  test('shortens long context item titles', async () => {
    const contextItem: ContextItem = {
      type_id: 'file',
      title: 'very/long/path/to/some/deeply/nested/file.ts',
      link: '...',
    };
    element.contextItem = contextItem;
    await element.updateComplete;
    const chip =
      element.shadowRoot?.querySelector<MdFilterChip>('md-filter-chip');
    assert.equal(chip?.textContent?.trim(), '\u2026/nested/file.ts');
    assert.equal(chip?.title, 'very/long/path/to/some/deeply/nested/file.ts');
  });

  test('does not shorten short context item titles', async () => {
    const contextItem: ContextItem = {
      type_id: 'file',
      title: 'short/file.ts',
      link: '...',
    };
    element.contextItem = contextItem;
    await element.updateComplete;
    const chip =
      element.shadowRoot?.querySelector<MdFilterChip>('md-filter-chip');
    assert.equal(chip?.textContent?.trim(), 'short/file.ts');
  });
});
