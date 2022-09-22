/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-editable-content';
import {GrEditableContent} from './gr-editable-content';
import {query, queryAndAssert, stubStorage} from '../../../test/test-utils';
import {GrButton} from '../gr-button/gr-button';
import {fixture, html, assert} from '@open-wc/testing';
import {EventType} from '../../../types/events';

suite('gr-editable-content tests', () => {
  let element: GrEditableContent;

  setup(async () => {
    element = await fixture(html`<gr-editable-content></gr-editable-content>`);
    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `<gr-endpoint-decorator name="commit-message">
        <gr-endpoint-param name="editing"> </gr-endpoint-param>
        <div class="collapsed viewer">
          <slot> </slot>
        </div>
        <div class="show-all-container font-normal">
          <gr-button
            aria-disabled="false"
            class="show-all-button"
            link=""
            role="button"
            tabindex="0"
          >
            <div>
              <gr-icon icon="expand_more" small></gr-icon>
              <span>Show all</span>
            </div>
          </gr-button>
          <div class="flex-space"></div>
          <gr-button
            aria-disabled="false"
            class="edit-commit-message"
            link=""
            role="button"
            tabindex="0"
            title="Edit commit message"
          >
            <div>
              <gr-icon icon="edit" filled small></gr-icon>
              <span>Edit</span>
            </div>
          </gr-button>
        </div>
        <gr-endpoint-slot name="above-actions"> </gr-endpoint-slot>
      </gr-endpoint-decorator> `
    );
  });

  test('show-all-container visibility', async () => {
    element.editing = false;
    element.commitCollapsible = false;
    element.hideEditCommitMessage = true;
    await element.updateComplete;
    assert.isNotOk(query(element, '.show-all-container'));

    element.hideEditCommitMessage = false;
    await element.updateComplete;
    assert.isOk(query(element, '.show-all-container'));

    element.hideEditCommitMessage = true;
    element.editing = true;
    await element.updateComplete;
    assert.isOk(query(element, '.show-all-container'));

    element.editing = false;
    element.commitCollapsible = true;
    await element.updateComplete;
    assert.isOk(query(element, '.show-all-container'));
  });

  test('save event', async () => {
    element.content = '';
    // Needed because contentChanged resets newContent
    // We want contentChanged observer to finish before newContentChanged is
    // called
    await element.updateComplete;

    element.newContent = 'foo';
    element.disabled = false;
    element.editing = true;
    const handler = sinon.spy();
    element.addEventListener('editable-content-save', handler);

    await element.updateComplete;

    queryAndAssert<GrButton>(element, 'gr-button[primary]').click();

    await element.updateComplete;

    assert.isTrue(handler.called);
    assert.equal(handler.lastCall.args[0].detail.content, 'foo');
  });

  test('cancel event', async () => {
    const handler = sinon.spy();
    element.editing = true;
    await element.updateComplete;
    element.addEventListener('editable-content-cancel', handler);

    queryAndAssert<GrButton>(element, 'gr-button.cancel-button').click();

    assert.isTrue(handler.called);
  });

  test('enabling editing keeps old content', async () => {
    element.content = 'current content';

    // Needed because contentChanged resets newContent
    // We want contentChanged observer to finish before newContentChanged is
    // called
    await element.updateComplete;

    element.newContent = 'old content';
    element.editing = true;

    await element.updateComplete;

    assert.equal(element.newContent, 'old content');
  });

  test('disabling editing does not update edit field contents', () => {
    element.content = 'current content';
    element.editing = true;
    element.newContent = 'stale content';
    element.editing = false;
    assert.equal(element.newContent, 'stale content');
  });

  suite('editing', () => {
    setup(async () => {
      element.content = 'current content';
      // Needed because contentChanged resets newContent
      // contentChanged updates newContent as well so wait for that observer
      // to finish before setting editing=true.
      await element.updateComplete;
      element.editing = true;
      await element.updateComplete;
    });

    test('save button is disabled initially', () => {
      assert.isTrue(
        queryAndAssert<GrButton>(element, 'gr-button[primary]').disabled
      );
    });

    test('save button is enabled when content changes', async () => {
      element.newContent = 'new content';
      await element.updateComplete;
      assert.isFalse(
        queryAndAssert<GrButton>(element, 'gr-button[primary]').disabled
      );
    });
  });

  suite('storageKey and related behavior', () => {
    let dispatchSpy: sinon.SinonSpy;

    setup(async () => {
      element.content = 'current content';
      await element.updateComplete;
      element.storageKey = 'test';
      dispatchSpy = sinon.spy(element, 'dispatchEvent');
    });

    test('editing toggled to true, has stored data', async () => {
      stubStorage('getEditableContentItem').returns({
        message: 'stored content',
        updated: 0,
      });
      element.editing = true;
      await element.updateComplete;
      assert.equal(element.newContent, 'stored content');
      assert.isTrue(dispatchSpy.called);
      assert.equal(dispatchSpy.lastCall.args[0].type, EventType.SHOW_ALERT);
    });

    test('editing toggled to true, has no stored data', async () => {
      stubStorage('getEditableContentItem').returns(null);
      element.editing = true;

      await element.updateComplete;

      assert.equal(element.newContent, 'current content');
      assert.equal(dispatchSpy.firstCall.args[0].type, 'editing-changed');
    });

    test('edits are cached', async () => {
      const storeStub = stubStorage('setEditableContentItem');
      const eraseStub = stubStorage('eraseEditableContentItem');
      element.editing = true;

      // Needed because editingChanged resets newContent
      // We want editingChanged() to finish before triggering newContentChanged
      await element.updateComplete;

      element.newContent = 'new content';

      await element.updateComplete;

      element.storeTask?.flush();

      assert.isTrue(storeStub.called);
      assert.deepEqual(
        [element.storageKey, element.newContent],
        storeStub.lastCall.args
      );

      element.newContent = '';

      await element.updateComplete;

      element.storeTask?.flush();

      assert.isTrue(eraseStub.called);
      assert.deepEqual([element.storageKey], eraseStub.lastCall.args);
    });
  });
});
