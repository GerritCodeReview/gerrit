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

import '../../../test/common-test-setup-karma';
import './gr-file-list-header';
import {FilesExpandedState} from '../gr-file-list-constants';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import 'lodash/lodash';
import {createChange, createRevision} from '../../../test/test-data-generators';
import {query, queryAndAssert, stubRestApi} from '../../../test/test-utils';
import {GrFileListHeader} from './gr-file-list-header';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {
  BasePatchSetNum,
  ChangeId,
  NumericChangeId,
  PatchSetNum,
} from '../../../types/common.js';
import {ChangeInfo, ChangeStatus} from '../../../api/rest-api.js';
import {PatchSet} from '../../../utils/patch-set-util';
import {createDefaultDiffPrefs} from '../../../constants/constants.js';

const basicFixture = fixtureFromElement('gr-file-list-header');

suite('gr-file-list-header tests', () => {
  let element: GrFileListHeader;

  setup(() => {
    stubRestApi('getAccount').returns(Promise.resolve(undefined));
    element = basicFixture.instantiate();
  });

  teardown(async () => {
    await flush();
  });

  test('Diff preferences hidden when no prefs', async () => {
    assert.isTrue(
      queryAndAssert<HTMLElement>(element, '#diffPrefsContainer').hidden
    );

    element.diffPrefs = createDefaultDiffPrefs();
    element.loggedIn = true;
    await flush();
    assert.isFalse(
      queryAndAssert<HTMLElement>(element, '#diffPrefsContainer').hidden
    );
  });

  test('expandAllDiffs called when expand button clicked', async () => {
    element.shownFileCount = 1;
    await flush();
    const expandAllDiffsStub = sinon.stub(element, '_expandAllDiffs');
    MockInteractions.tap(queryAndAssert(element, '#expandBtn'));
    assert.isTrue(expandAllDiffsStub.called);
  });

  test('collapseAllDiffs called when collapse button clicked', async () => {
    element.shownFileCount = 1;
    await flush();
    const collapseAllDiffsStub = sinon.stub(element, '_collapseAllDiffs');
    MockInteractions.tap(queryAndAssert(element, '#collapseBtn'));
    assert.isTrue(collapseAllDiffsStub.called);
  });

  test('show/hide diffs disabled for large amounts of files', async () => {
    const computeSpy = sinon.spy(element, '_fileListActionsVisible');
    element.changeNum = 42 as NumericChangeId;
    element.basePatchNum = 'PARENT' as BasePatchSetNum;
    element.patchNum = '2' as PatchSetNum;
    element.shownFileCount = 1;
    await flush();
    assert.isTrue(computeSpy.lastCall.returnValue);
    _.times(element._maxFilesForBulkActions + 1, () => {
      element.shownFileCount = element.shownFileCount! + 1;
    });
    assert.isFalse(computeSpy.lastCall.returnValue);
  });

  test('fileViewActions are properly hidden', async () => {
    const actions = queryAndAssert(element, '.fileViewActions');
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
    const expandBtn = queryAndAssert(element, '#expandBtn');
    const collapseBtn = queryAndAssert(element, '#collapseBtn');
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
      ...createChange(),
      change_id: 'Iad9dc96274af6946f3632be53b106ef80f7ba6ca' as ChangeId,
      revisions: {
        rev2: createRevision(2),
        rev1: createRevision(1),
        rev13: createRevision(13),
        rev3: createRevision(3),
      },
      status: 'NEW' as ChangeStatus,
      labels: {},
    } as ChangeInfo;
    element.basePatchNum = 1 as BasePatchSetNum;
    element.patchNum = 2 as PatchSetNum;

    element._handlePatchChange({
      detail: {basePatchNum: 1, patchNum: 3},
    } as CustomEvent);
    assert.equal(navigateToChangeStub.callCount, 1);
    assert.isTrue(
      navigateToChangeStub.lastCall.calledWithExactly(element.change, {
        patchNum: 3 as PatchSetNum,
        basePatchNum: 1 as BasePatchSetNum,
      })
    );
  });

  test('class is applied to file list on old patch set', () => {
    const allPatchSets: PatchSet[] = [
      {num: 4 as PatchSetNum, desc: undefined, sha: ''},
      {num: 2 as PatchSetNum, desc: undefined, sha: ''},
      {num: 1 as PatchSetNum, desc: undefined, sha: ''},
    ];
    assert.equal(
      element._computePatchInfoClass(1 as PatchSetNum, allPatchSets),
      'patchInfoOldPatchSet'
    );
    assert.equal(
      element._computePatchInfoClass(2 as PatchSetNum, allPatchSets),
      'patchInfoOldPatchSet'
    );
    assert.equal(
      element._computePatchInfoClass(4 as PatchSetNum, allPatchSets),
      ''
    );
  });

  suite('editMode behavior', () => {
    setup(() => {
      element.loggedIn = true;
      element.diffPrefs = createDefaultDiffPrefs();
    });

    const isVisible = (el: HTMLElement) => {
      assert.ok(el);
      return getComputedStyle(el).getPropertyValue('display') !== 'none';
    };

    test('patch specific elements', async () => {
      element.editMode = true;
      element.allPatchSets = [
        {num: 1 as PatchSetNum, desc: undefined, sha: ''},
        {num: 2 as PatchSetNum, desc: undefined, sha: ''},
        {num: 3 as PatchSetNum, desc: undefined, sha: ''},
      ];
      await flush();

      assert.isFalse(
        isVisible(queryAndAssert<HTMLElement>(element, '#diffPrefsContainer'))
      );

      element.editMode = false;
      await flush();

      assert.isTrue(
        isVisible(queryAndAssert<HTMLElement>(element, '#diffPrefsContainer'))
      );
    });

    test('edit-controls visibility', async () => {
      element.editMode = false;
      await flush();
      // on the first render, when editMode is false, editControls are not
      // in the DOM to reduce size of DOM and make first render faster.
      assert.isUndefined(query(element, '#editControls'));

      element.editMode = true;
      await flush();
      queryAndAssert<HTMLElement>(element, '#editControls').parentElement;
      assert.isTrue(
        isVisible(
          queryAndAssert<HTMLElement>(element, '#editControls').parentElement!
        )
      );

      element.editMode = false;
      await flush();
      assert.isFalse(
        isVisible(
          queryAndAssert<HTMLElement>(element, '#editControls').parentElement!
        )
      );
    });
  });
});
