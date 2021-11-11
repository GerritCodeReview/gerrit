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
import 'lodash/lodash.js';
import {createRevisions} from '../../../test/test-data-generators.js';
import {stubRestApi} from '../../../test/test-utils.js';

const basicFixture = fixtureFromElement('gr-file-list-header');

suite('gr-file-list-header tests', () => {
  let element;

  setup(() => {
    stubRestApi('getConfig').returns(Promise.resolve({test: 'config'}));
    stubRestApi('getAccount').returns(Promise.resolve(null));
    element = basicFixture.instantiate();
  });

  teardown(async () => {
    await flush();
  });

  test('Diff preferences hidden when no prefs', () => {
    assert.isTrue(element.$.diffPrefsContainer.hidden);

    element.diffPrefs = {font_size: '12'};
    element.loggedIn = true;
    flush();
    assert.isFalse(element.$.diffPrefsContainer.hidden);
  });

  test('expandAllDiffs called when expand button clicked', () => {
    element.shownFileCount = 1;
    flush();
    sinon.stub(element, '_expandAllDiffs');
    MockInteractions.tap(element.root.querySelector(
        '#expandBtn'));
    assert.isTrue(element._expandAllDiffs.called);
  });

  test('collapseAllDiffs called when collapse button clicked', () => {
    element.shownFileCount = 1;
    flush();
    sinon.stub(element, '_collapseAllDiffs');
    MockInteractions.tap(element.root.querySelector(
        '#collapseBtn'));
    assert.isTrue(element._collapseAllDiffs.called);
  });

  test('show/hide diffs disabled for large amounts of files', async () => {
    const computeSpy = sinon.spy(element, '_fileListActionsVisible');
    element._files = [];
    element.changeNum = '42';
    element.basePatchNum = 'PARENT';
    element.patchNum = '2';
    element.shownFileCount = 1;
    await flush();
    assert.isTrue(computeSpy.lastCall.returnValue);
    _.times(element._maxFilesForBulkActions + 1, () => {
      element.shownFileCount = element.shownFileCount + 1;
    });
    assert.isFalse(computeSpy.lastCall.returnValue);
  });

  test('fileViewActions are properly hidden', async () => {
    const actions = element.shadowRoot
        .querySelector('.fileViewActions');
    assert.equal(getComputedStyle(actions).display, 'none');
    element.filesExpanded = FilesExpandedState.SOME;
    await flush();
    assert.notEqual(getComputedStyle(actions).display, 'none');
    element.filesExpanded = FilesExpandedState.ALL;
    await flush();
    assert.notEqual(getComputedStyle(actions).display, 'none');
    element.filesExpanded = FilesExpandedState.NONE;
    await flush();
    assert.equal(getComputedStyle(actions).display, 'none');
  });

  test('expand/collapse buttons are toggled correctly', async () => {
    // Only the expand button should be visible in the initial state when
    // NO files are expanded.
    element.shownFileCount = 10;
    await flush();
    const expandBtn = element.shadowRoot.querySelector('#expandBtn');
    const collapseBtn = element.shadowRoot.querySelector('#collapseBtn');
    assert.notEqual(getComputedStyle(expandBtn).display, 'none');
    assert.equal(getComputedStyle(collapseBtn).display, 'none');

    // Both expand and collapse buttons should be visible when SOME files are
    // expanded.
    element.filesExpanded = FilesExpandedState.SOME;
    await flush();
    assert.notEqual(getComputedStyle(expandBtn).display, 'none');
    assert.notEqual(getComputedStyle(collapseBtn).display, 'none');

    // Only the collapse button should be visible when ALL files are expanded.
    element.filesExpanded = FilesExpandedState.ALL;
    await flush();
    assert.equal(getComputedStyle(expandBtn).display, 'none');
    assert.notEqual(getComputedStyle(collapseBtn).display, 'none');

    // Only the expand button should be visible when NO files are expanded.
    element.filesExpanded = FilesExpandedState.NONE;
    await flush();
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
        .calledWithExactly(element.change, {patchNum: 3, basePatchNum: 1}));
  });

  test('class is applied to file list on old patch set', () => {
    const allPatchSets = [{num: 4}, {num: 2}, {num: 1}];
    assert.equal(element._computePatchInfoClass(1, allPatchSets),
        'patchInfoOldPatchSet');
    assert.equal(element._computePatchInfoClass(2, allPatchSets),
        'patchInfoOldPatchSet');
    assert.equal(element._computePatchInfoClass(4, allPatchSets), '');
  });

  suite('editMode behavior', () => {
    setup(() => {
      element.loggedIn = true;
      element.diffPrefs = {};
    });

    const isVisible = el => {
      assert.ok(el);
      return getComputedStyle(el).getPropertyValue('display') !== 'none';
    };

    test('patch specific elements', () => {
      element.editMode = true;
      element.allPatchSets = createRevisions(2);
      flush();

      assert.isFalse(isVisible(element.$.diffPrefsContainer));

      element.editMode = false;
      flush();

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
  });
});

