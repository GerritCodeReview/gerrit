/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import '../../../test/common-test-setup-karma.js';
import './gr-file-list-header.js';
import {FilesExpandedState} from '../gr-file-list-constants.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import {generateChange} from '../../../test/test-utils.js';
import 'lodash/lodash.js';

const basicFixture = fixtureFromElement('gr-file-list-header');

suite('gr-file-list-header tests', () => {
  let element;

  setup(() => {
    stub('gr-rest-api-interface', {
      getConfig() { return Promise.resolve({test: 'config'}); },
      getAccount() { return Promise.resolve(null); },
      _fetchSharedCacheURL() { return Promise.resolve({}); },
    });
    element = basicFixture.instantiate();
  });

  teardown(done => {
    flush(() => {
      done();
    });
  });

  test('Diff preferences hidden when no prefs or diffPrefsDisabled', () => {
    element.diffPrefsDisabled = true;
    flush();
    assert.isTrue(element.$.diffPrefsContainer.hidden);

    element.diffPrefsDisabled = false;
    flush();
    assert.isTrue(element.$.diffPrefsContainer.hidden);

    element.diffPrefsDisabled = true;
    element.diffPrefs = {font_size: '12'};
    flush();
    assert.isTrue(element.$.diffPrefsContainer.hidden);

    element.diffPrefsDisabled = false;
    flush();
    assert.isFalse(element.$.diffPrefsContainer.hidden);
  });

  test('_computeDescriptionReadOnly', () => {
    element.loggedIn = false;
    element.change = {owner: {_account_id: 1}};
    element.account = {_account_id: 1};
    assert.equal(element._descriptionReadOnly, true);

    element.loggedIn = true;
    element.change = {owner: {_account_id: 0}};
    element.account = {_account_id: 1};
    assert.equal(element._descriptionReadOnly, true);

    element.loggedIn = true;
    element.change = {owner: {_account_id: 1}};
    element.account = {_account_id: 1};
    assert.equal(element._descriptionReadOnly, false);
  });

  test('_computeDescriptionPlaceholder', () => {
    assert.equal(element._computeDescriptionPlaceholder(true),
        'No patchset description');
    assert.equal(element._computeDescriptionPlaceholder(false),
        'Add patchset description');
  });

  test('description editing', () => {
    const putDescStub = sinon.stub(element.$.restAPI, 'setDescription')
        .returns(Promise.resolve({ok: true}));

    element.changeNum = '42';
    element.basePatchNum = 'PARENT';
    element.patchNum = 1;

    element.change = {
      change_id: 'Iad9dc96274af6946f3632be53b106ef80f7ba6ca',
      revisions: {
        rev1: {_number: 1, description: 'test', commit: {commit: 'rev1'}},
      },
      current_revision: 'rev1',
      status: 'NEW',
      labels: {},
      actions: {},
      owner: {_account_id: 1},
    };
    element.account = {_account_id: 1};
    element.owner = {_account_id: 1};
    element.loggedIn = true;

    flush();

    // The element has a description, so the account chip should be visible
    element.owner = {_account_id: 1};
    // and the description label should not exist.
    const chip = element.root.querySelector('#descriptionChip');
    let label = element.root.querySelector('#descriptionLabel');

    assert.equal(chip.text, 'test');
    assert.isNotOk(label);

    // Simulate tapping the remove button, but call function directly so that
    // can determine what happens after the promise is resolved.
    return element._handleDescriptionRemoved()
        .then(() => {
          // The API stub should be called with an empty string for the new
          // description.
          assert.equal(putDescStub.lastCall.args[2], '');
          assert.equal(element.change.revisions.rev1.description, '');

          flush();
          // The editable label should now be visible and the chip hidden.
          label = element.root.querySelector('#descriptionLabel');
          assert.isOk(label);
          assert.equal(getComputedStyle(chip).display, 'none');
          assert.notEqual(getComputedStyle(label).display, 'none');
          assert.isFalse(label.readOnly);
          // Edit the label to have a new value of test2, and save.
          label.editing = true;
          label._inputText = 'test2';
          label._save();
          flush();
          // The API stub should be called with an `test2` for the new
          // description.
          assert.equal(putDescStub.callCount, 2);
          assert.equal(putDescStub.lastCall.args[2], 'test2');
        })
        .then(() => {
          flush();
          // The chip should be visible again, and the label hidden.
          assert.equal(element.change.revisions.rev1.description, 'test2');
          assert.equal(getComputedStyle(label).display, 'none');
          assert.notEqual(getComputedStyle(chip).display, 'none');
        });
  });

  test('expandAllDiffs called when expand button clicked', () => {
    element.shownFileCount = 1;
    flush();
    sinon.stub(element, '_expandAllDiffs');
    MockInteractions.tap(element.root.querySelector(
        '#expandBtn'));
    assert.isTrue(element._expandAllDiffs.called);
  });

  test('collapseAllDiffs called when expand button clicked', () => {
    element.shownFileCount = 1;
    flush();
    sinon.stub(element, '_collapseAllDiffs');
    MockInteractions.tap(element.root.querySelector(
        '#collapseBtn'));
    assert.isTrue(element._collapseAllDiffs.called);
  });

  test('show/hide diffs disabled for large amounts of files', done => {
    const computeSpy = sinon.spy(element, '_fileListActionsVisible');
    element._files = [];
    element.changeNum = '42';
    element.basePatchNum = 'PARENT';
    element.patchNum = '2';
    element.shownFileCount = 1;
    flush(() => {
      assert.isTrue(computeSpy.lastCall.returnValue);
      _.times(element._maxFilesForBulkActions + 1, () => {
        element.shownFileCount = element.shownFileCount + 1;
      });
      assert.isFalse(computeSpy.lastCall.returnValue);
      done();
    });
  });

  test('fileViewActions are properly hidden', () => {
    const actions = element.shadowRoot
        .querySelector('.fileViewActions');
    assert.equal(getComputedStyle(actions).display, 'none');
    element.filesExpanded = FilesExpandedState.SOME;
    flush();
    assert.notEqual(getComputedStyle(actions).display, 'none');
    element.filesExpanded = FilesExpandedState.ALL;
    flush();
    assert.notEqual(getComputedStyle(actions).display, 'none');
    element.filesExpanded = FilesExpandedState.NONE;
    flush();
    assert.equal(getComputedStyle(actions).display, 'none');
  });

  test('expand/collapse buttons are toggled correctly', () => {
    element.shownFileCount = 10;
    flush();
    const expandBtn = element.shadowRoot.querySelector('#expandBtn');
    const collapseBtn = element.shadowRoot.querySelector('#collapseBtn');
    assert.notEqual(getComputedStyle(expandBtn).display, 'none');
    assert.equal(getComputedStyle(collapseBtn).display, 'none');
    element.filesExpanded = FilesExpandedState.SOME;
    flush();
    assert.notEqual(getComputedStyle(expandBtn).display, 'none');
    assert.equal(getComputedStyle(collapseBtn).display, 'none');
    element.filesExpanded = FilesExpandedState.ALL;
    flush();
    assert.equal(getComputedStyle(expandBtn).display, 'none');
    assert.notEqual(getComputedStyle(collapseBtn).display, 'none');
    element.filesExpanded = FilesExpandedState.NONE;
    flush();
    assert.notEqual(getComputedStyle(expandBtn).display, 'none');
    assert.equal(getComputedStyle(collapseBtn).display, 'none');
  });

  test('navigateToChange called when range select changes', () => {
    const navigateToChangeStub = sinon.stub(GerritNav, 'navigateToChange');
    element.change = {
      change_id: 'Iad9dc96274af6946f3632be53b106ef80f7ba6ca',
      revisions: {
        rev2: {_number: 2},
        rev1: {_number: 1},
        rev13: {_number: 13},
        rev3: {_number: 3},
      },
      status: 'NEW',
      labels: {},
    };
    element.basePatchNum = 1;
    element.patchNum = 2;

    element._handlePatchChange({detail: {basePatchNum: 1, patchNum: 3}});
    assert.equal(navigateToChangeStub.callCount, 1);
    assert.isTrue(navigateToChangeStub.lastCall
        .calledWithExactly(element.change, 3, 1));
  });

  test('class is applied to file list on old patch set', () => {
    const allPatchSets = [{num: 4}, {num: 2}, {num: 1}];
    assert.equal(element._computePatchInfoClass('1', allPatchSets),
        'patchInfoOldPatchSet');
    assert.equal(element._computePatchInfoClass('2', allPatchSets),
        'patchInfoOldPatchSet');
    assert.equal(element._computePatchInfoClass('4', allPatchSets), '');
  });

  suite('editMode behavior', () => {
    setup(() => {
      element.diffPrefsDisabled = false;
      element.diffPrefs = {};
    });

    const isVisible = el => {
      assert.ok(el);
      return getComputedStyle(el).getPropertyValue('display') !== 'none';
    };

    test('patch specific elements', () => {
      element.editMode = true;
      element.allPatchSets = generateChange({revisionsCount: 2}).revisions;
      flush();

      assert.isFalse(isVisible(element.$.diffPrefsContainer));
      assert.isFalse(isVisible(element.shadowRoot
          .querySelector('.descriptionContainer')));

      element.editMode = false;
      flush();

      assert.isTrue(isVisible(element.shadowRoot
          .querySelector('.descriptionContainer')));
      assert.isTrue(isVisible(element.$.diffPrefsContainer));
    });

    test('edit-controls visibility', () => {
      element.editMode = false;
      flush();
      // on the first render, when editMode is false, editControls are not
      // in the DOM to reduce size of DOM and make first render faster.
      assert.isNull(element.shadowRoot
          .querySelector('#editControls'));

      element.editMode = true;
      flush();
      assert.isTrue(isVisible(element.shadowRoot
          .querySelector('#editControls').parentElement));

      element.editMode = false;
      flush();
      assert.isFalse(isVisible(element.shadowRoot
          .querySelector('#editControls').parentElement));
    });

    test('_computeUploadHelpContainerClass', () => {
      // Only show the upload helper button when an unmerged change is viewed
      // by its owner.
      const accountA = {_account_id: 1};
      const accountB = {_account_id: 2};
      assert.notInclude(element._computeUploadHelpContainerClass(
          {owner: accountA}, accountA), 'hide');
      assert.include(element._computeUploadHelpContainerClass(
          {owner: accountA}, accountB), 'hide');
    });
  });
});

