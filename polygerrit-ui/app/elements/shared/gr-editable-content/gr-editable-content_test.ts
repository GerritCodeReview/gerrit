/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-editable-content';
import {GrEditableContent} from './gr-editable-content';
import {query, queryAndAssert, waitUntil} from '../../../test/test-utils';
import {GrButton} from '../gr-button/gr-button';
import {fixture, html, assert} from '@open-wc/testing';
import {StorageService} from '../../../services/storage/gr-storage';
import {storageServiceToken} from '../../../services/storage/gr-storage_impl';
import {testResolver} from '../../../test/common-test-setup';
import {GrDropdownList} from '../gr-dropdown-list/gr-dropdown-list';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {
  EmailAddress,
  NumericChangeId,
  RepoName,
  RevisionPatchSetNum,
} from '../../../api/rest-api';
import {changeViewModelToken} from '../../../models/views/change';
import {IronAutogrowTextareaElement} from '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';

const emails = [
  {
    email: 'primary@example.com' as EmailAddress,
    preferred: true,
  },
  {
    email: 'secondary@example.com' as EmailAddress,
    preferred: false,
  },
];

suite('gr-editable-content tests', () => {
  let element: GrEditableContent;
  let storageService: StorageService;

  setup(async () => {
    element = await fixture(html`<gr-editable-content></gr-editable-content>`);
    await element.updateComplete;
    storageService = testResolver(storageServiceToken);
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
              <span>Show All</span>
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

    suite('in editMode', () => {
      test('click opens edit url', async () => {
        const editUrlStub = sinon.stub(
          testResolver(changeViewModelToken),
          'editUrl'
        );
        editUrlStub.returns('fakeUrl');
        const setUrlStub = sinon.stub(testResolver(navigationToken), 'setUrl');
        element.editMode = true;
        element.changeNum = 42 as NumericChangeId;
        element.repoName = 'Test Repo' as RepoName;
        element.patchNum = '1' as RevisionPatchSetNum;
        await element.updateComplete;
        const editButton = queryAndAssert<GrButton>(
          element,
          'gr-button.edit-commit-message'
        );
        editButton.click();
        assert.isTrue(setUrlStub.called);
        assert.equal(setUrlStub.lastCall.args[0], 'fakeUrl');
      });
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
      sinon.stub(storageService, 'getEditableContentItem').returns({
        message: 'stored content',
        updated: 0,
      });
      element.editing = true;
      await element.updateComplete;
      assert.equal(element.newContent, 'stored content');
      assert.isTrue(dispatchSpy.called);
      assert.equal(dispatchSpy.lastCall.args[0].type, 'show-alert');
    });

    test('editing toggled to true, has no stored data', async () => {
      sinon.stub(storageService, 'getEditableContentItem').returns(null);
      element.editing = true;

      await element.updateComplete;

      assert.equal(element.newContent, 'current content');
      assert.equal(dispatchSpy.firstCall.args[0].type, 'editing-changed');
    });

    test('edits are cached', async () => {
      const storeStub = sinon.stub(storageService, 'setEditableContentItem');
      const eraseStub = sinon.stub(storageService, 'eraseEditableContentItem');
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

  suite('edit with committer email', () => {
    test('hide email dropdown when user has one email', async () => {
      element.emails = emails.slice(0, 1);
      element.editing = true;
      await element.updateComplete;
      assert.notExists(query(element, '#editMessageEmailDropdown'));
    });

    test('show email dropdown when user has more than one email', async () => {
      element.emails = emails;
      element.editing = true;
      await element.updateComplete;
      const editMessageEmailDropdown = queryAndAssert(
        element,
        '#editMessageEmailDropdown'
      );
      assert.dom.equal(
        editMessageEmailDropdown,
        `<div class="email-dropdown" id="editMessageEmailDropdown">Committer Email
        <gr-dropdown-list></gr-dropdown-list>
        <span></span>
        </div>`
      );
      const emailDropdown = queryAndAssert<GrDropdownList>(
        editMessageEmailDropdown,
        'gr-dropdown-list'
      );
      assert.deepEqual(
        emailDropdown.items?.map(e => e.value),
        emails.map(e => e.email)
      );
    });
  });

  suite('format button', () => {
    let element: GrEditableContent;

    setup(async () => {
      element = await fixture(
        html`<gr-editable-content></gr-editable-content>`
      );
      element.editing = true;
      await element.updateComplete;
    });

    test('toggles between Format and Undo', async () => {
      const formatButton = queryAndAssert<GrButton>(
        element,
        'gr-button.format-button'
      );

      // Initially shows "Format"
      assert.equal(formatButton.textContent?.trim(), 'Format');

      // Set some content that needs formatting
      element.newContent = 'line1    \n\nline2     \n\nline3';
      await element.updateComplete;
      element.updateFormatState(/* skipDebounce= */ true);
      await element.updateComplete;

      // Click format
      formatButton.click();
      await element.updateComplete;

      // Button should now show "Undo"
      assert.equal(formatButton.textContent?.trim(), 'Undo');

      // Content should be formatted
      assert.equal(element.newContent, 'line1\n\nline2\n\nline3');

      // Click undo
      formatButton.click();
      await element.updateComplete;

      // Button should show "Format" again
      // assert.equal(formatButton.textContent?.trim(), 'Format');

      // Content should be back to original
      assert.equal(element.newContent, 'line1    \n\nline2     \n\nline3');
    });

    test('reverts to Format when content is modified after formatting', async () => {
      const formatButton = queryAndAssert<GrButton>(
        element,
        'gr-button.format-button'
      );

      // Set content and format it
      element.newContent = 'line1    \nline2     \nline3';
      await element.updateComplete;
      element.updateFormatState(/* skipDebounce= */ true);
      await element.updateComplete;

      formatButton.click();
      await element.updateComplete;

      assert.equal(formatButton.textContent?.trim(), 'Undo');

      // Modify the content
      element.newContent = 'line1\nline2\nline3\nline4';
      await element.updateComplete;

      // Button should show "Format" again
      assert.equal(formatButton.textContent?.trim(), 'Format');
    });

    test('format button tooltip changes for Format/Undo states', async () => {
      const formatButton = queryAndAssert<GrButton>(
        element,
        'gr-button.format-button'
      );

      // Set content that needs formatting
      element.newContent = 'line1    \nline2     \nline3';
      await element.updateComplete;
      element.updateFormatState(/* skipDebounce= */ true);
      await element.updateComplete;

      // Initial Format tooltip
      assert.include(formatButton.title, 'Automatically fixes formatting');

      // Click format
      formatButton.click();
      await element.updateComplete;

      // Undo tooltip
      assert.equal(formatButton.title, 'Undo formatting changes');

      // Click undo
      formatButton.click();
      await element.updateComplete;
      element.updateFormatState(/* skipDebounce= */ true);
      await element.updateComplete;

      // Back to Format tooltip
      assert.include(formatButton.title, 'Automatically fixes formatting');
    });

    test('disables format button when only current line needs formatting', async () => {
      const formatButton = queryAndAssert<GrButton>(
        element,
        'gr-button.format-button'
      );

      element.newContent = 'line1\nline2    \nline3';
      const textarea = queryAndAssert<IronAutogrowTextareaElement>(
        element,
        'iron-autogrow-textarea'
      ).textarea;

      textarea.setSelectionRange(7, 7); // Position cursor after "line2"
      await element.updateComplete;
      element.updateFormatState(/* skipDebounce= */ false);
      await element.updateComplete;
      await waitUntil(() => !!formatButton?.disabled);

      // Format button should be disabled because only current line needs formatting
      assert.isTrue(formatButton.disabled);
      assert.include(formatButton.title, 'No format changes needed');
    });

    test('enables format button when other lines need formatting', async () => {
      const formatButton = queryAndAssert<GrButton>(
        element,
        'gr-button.format-button'
      );

      element.newContent = 'line1    \nline2    \nline3    ';
      const textarea = queryAndAssert<IronAutogrowTextareaElement>(
        element,
        'iron-autogrow-textarea'
      ).textarea;

      textarea.setSelectionRange(7, 7); // Position cursor after "line2"
      await element.updateComplete;
      element.updateFormatState(/* skipDebounce= */ false);
      await element.updateComplete;
      await waitUntil(() => !formatButton?.disabled);

      // Format button should be enabled because other lines need formatting
      assert.isFalse(formatButton.disabled);
      assert.include(formatButton.title, 'Automatically fixes formatting');
    });
  });
});
