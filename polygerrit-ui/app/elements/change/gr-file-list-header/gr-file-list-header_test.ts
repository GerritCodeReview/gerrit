/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-file-list-header';
import {FilesExpandedState} from '../gr-file-list-constants';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {createChange, createRevision} from '../../../test/test-data-generators';
import {
  isVisible,
  query,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import {GrFileListHeader} from './gr-file-list-header';
import {
  BasePatchSetNum,
  ChangeId,
  NumericChangeId,
  PatchSetNum,
} from '../../../types/common.js';
import {ChangeInfo, ChangeStatus} from '../../../api/rest-api.js';
import {PatchSet} from '../../../utils/patch-set-util';
import {createDefaultDiffPrefs} from '../../../constants/constants.js';
import {fixture, html} from '@open-wc/testing-helpers';
import {GrButton} from '../../shared/gr-button/gr-button';

suite('gr-file-list-header tests', () => {
  let element: GrFileListHeader;
  const change: ChangeInfo = {
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
  };

  setup(async () => {
    stubRestApi('getAccount').resolves(undefined);
    element = await fixture(
      html`<gr-file-list-header
        .change=${change}
        .shownFileCount=${3}
      ></gr-file-list-header>`
    );
    element.diffPrefs = createDefaultDiffPrefs();
  });

  test('Diff preferences hidden when no prefs', async () => {
    assert.isTrue(
      queryAndAssert<HTMLElement>(element, '#diffPrefsContainer').hidden
    );

    element.diffPrefs = createDefaultDiffPrefs();
    element.loggedIn = true;
    await element.updateComplete;

    assert.isFalse(
      queryAndAssert<HTMLElement>(element, '#diffPrefsContainer').hidden
    );
  });

  test('expandAllDiffs called when expand button clicked', async () => {
    const expandDiffsListener = sinon.stub();
    element.addEventListener('expand-diffs', expandDiffsListener);

    queryAndAssert<GrButton>(element, 'gr-button#expandBtn').click();
    await element.updateComplete;

    assert.isTrue(expandDiffsListener.called);
  });

  test('collapseAllDiffs called when collapse button clicked', async () => {
    const collapseAllDiffsListener = sinon.stub();
    element.addEventListener('collapse-diffs', collapseAllDiffsListener);

    queryAndAssert<GrButton>(element, 'gr-button#collapseBtn').click();
    await element.updateComplete;

    assert.isTrue(collapseAllDiffsListener.called);
  });

  test('show/hide diffs disabled for large amounts of files', async () => {
    element.changeNum = 42 as NumericChangeId;
    element.basePatchNum = 'PARENT' as BasePatchSetNum;
    element.patchNum = '2' as PatchSetNum;
    element.shownFileCount = 1;
    await element.updateComplete;

    queryAndAssert(element, 'gr-button#expandBtn');
    queryAndAssert(element, 'gr-button#collapseBtn');
    assert.isNotOk(query(element, '.warning'));

    element.shownFileCount = 226; // more than element.maxFilesForBulkActions
    await element.updateComplete;

    assert.isNotOk(query(element, 'gr-button#expandBtn'));
    assert.isNotOk(query(element, 'gr-button#collapseBtn'));
    queryAndAssert(element, '.warning');
  });

  test('fileViewActions are properly hidden', async () => {
    const actions = queryAndAssert(element, '.fileViewActions');
    assert.equal(getComputedStyle(actions).display, 'none');
    element.filesExpanded = FilesExpandedState.SOME;
    await element.updateComplete;
    assert.notEqual(getComputedStyle(actions).display, 'none');
    element.filesExpanded = FilesExpandedState.ALL;
    await element.updateComplete;
    assert.notEqual(getComputedStyle(actions).display, 'none');
    element.filesExpanded = FilesExpandedState.NONE;
    await element.updateComplete;
    assert.equal(getComputedStyle(actions).display, 'none');
  });

  test('expand/collapse buttons are toggled correctly', async () => {
    // Only the expand button should be visible in the initial state when
    // NO files are expanded.
    element.shownFileCount = 10;
    await element.updateComplete;
    const expandBtn = queryAndAssert(element, '#expandBtn');
    const collapseBtn = queryAndAssert(element, '#collapseBtn');
    assert.notEqual(getComputedStyle(expandBtn).display, 'none');
    assert.equal(getComputedStyle(collapseBtn).display, 'none');

    // Both expand and collapse buttons should be visible when SOME files are
    // expanded.
    element.filesExpanded = FilesExpandedState.SOME;
    await element.updateComplete;
    assert.notEqual(getComputedStyle(expandBtn).display, 'none');
    assert.notEqual(getComputedStyle(collapseBtn).display, 'none');

    // Only the collapse button should be visible when ALL files are expanded.
    element.filesExpanded = FilesExpandedState.ALL;
    await element.updateComplete;
    assert.equal(getComputedStyle(expandBtn).display, 'none');
    assert.notEqual(getComputedStyle(collapseBtn).display, 'none');

    // Only the expand button should be visible when NO files are expanded.
    element.filesExpanded = FilesExpandedState.NONE;
    await element.updateComplete;
    assert.notEqual(getComputedStyle(expandBtn).display, 'none');
    assert.equal(getComputedStyle(collapseBtn).display, 'none');
  });

  test('navigateToChange called when range select changes', async () => {
    const navigateToChangeStub = sinon.stub(GerritNav, 'navigateToChange');
    element.basePatchNum = 1 as BasePatchSetNum;
    element.patchNum = 2 as PatchSetNum;
    await element.updateComplete;

    element.handlePatchChange({
      detail: {basePatchNum: 1, patchNum: 3},
    } as CustomEvent);
    await element.updateComplete;

    assert.equal(navigateToChangeStub.callCount, 1);
    assert.isTrue(
      navigateToChangeStub.lastCall.calledWithExactly(change, {
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
      element.computePatchInfoClass(1 as PatchSetNum, allPatchSets),
      'patchInfoOldPatchSet'
    );
    assert.equal(
      element.computePatchInfoClass(2 as PatchSetNum, allPatchSets),
      'patchInfoOldPatchSet'
    );
    assert.equal(
      element.computePatchInfoClass(4 as PatchSetNum, allPatchSets),
      ''
    );
  });

  suite('editMode behavior', () => {
    setup(async () => {
      element.loggedIn = true;
      await element.updateComplete;
    });

    test('patch specific elements', async () => {
      element.editMode = true;
      element.allPatchSets = [
        {num: 1 as PatchSetNum, desc: undefined, sha: ''},
        {num: 2 as PatchSetNum, desc: undefined, sha: ''},
        {num: 3 as PatchSetNum, desc: undefined, sha: ''},
      ];
      await element.updateComplete;

      assert.isFalse(
        isVisible(queryAndAssert<HTMLElement>(element, '#diffPrefsContainer'))
      );

      element.editMode = false;
      await element.updateComplete;

      assert.isTrue(
        isVisible(queryAndAssert<HTMLElement>(element, '#diffPrefsContainer'))
      );
    });

    test('edit-controls visibility', async () => {
      element.editMode = false;
      await element.updateComplete;

      assert.isNotOk(query(element, '#editControls'));

      element.editMode = true;
      await element.updateComplete;

      assert.isTrue(
        isVisible(queryAndAssert<HTMLElement>(element, '#editControls'))
      );

      element.editMode = false;
      await element.updateComplete;

      assert.isNotOk(query<HTMLElement>(element, '#editControls'));
    });
  });
});
