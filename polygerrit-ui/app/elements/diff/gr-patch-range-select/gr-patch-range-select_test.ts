/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import '../../shared/revision-info/revision-info';
import './gr-patch-range-select';
import {GrPatchRangeSelect} from './gr-patch-range-select';
import {RevisionInfo as RevisionInfoClass} from '../../shared/revision-info/revision-info';
import {ChangeComments} from '../gr-comment-api/gr-comment-api';
import {stubReporting} from '../../../test/test-utils';
import {
  BasePatchSetNum,
  EDIT,
  RevisionPatchSetNum,
  PARENT,
  PatchSetNum,
  PatchSetNumber,
  RevisionInfo,
  Timestamp,
  UrlEncodedCommentId,
  PathToCommentsInfoMap,
} from '../../../types/common';
import {EditRevisionInfo, ParsedChangeInfo} from '../../../types/types';
import {SpecialFilePath} from '../../../constants/constants';
import {
  createChangeViewState,
  createEditRevision,
  createParsedChange,
  createRevision,
  createRevisions,
} from '../../../test/test-data-generators';
import {PatchSet} from '../../../utils/patch-set-util';
import {
  DropdownItem,
  GrDropdownList,
} from '../../shared/gr-dropdown-list/gr-dropdown-list';
import {queryAndAssert} from '../../../test/test-utils';
import {fire} from '../../../utils/event-util';
import {fixture, html, assert} from '@open-wc/testing';
import {testResolver} from '../../../test/common-test-setup';
import {changeViewModelToken} from '../../../models/views/change';
import {changeModelToken} from '../../../models/change/change-model';

type RevIdToRevisionInfo = {
  [revisionId: string]: RevisionInfo | EditRevisionInfo;
};

suite('gr-patch-range-select tests', () => {
  let element: GrPatchRangeSelect;

  function getInfo(revisions: (RevisionInfo | EditRevisionInfo)[]) {
    const revisionObj: Partial<RevIdToRevisionInfo> = {};
    for (let i = 0; i < revisions.length; i++) {
      revisionObj[i] = revisions[i];
    }
    return new RevisionInfoClass({revisions: revisionObj} as ParsedChangeInfo);
  }

  setup(async () => {
    // Element must be wrapped in an element with direct access to the
    // comment API.
    element = await fixture(
      html`<gr-patch-range-select></gr-patch-range-select>`
    );

    const viewModel = testResolver(changeViewModelToken);
    viewModel.setState({
      ...createChangeViewState(),
      patchNum: 1 as RevisionPatchSetNum,
      basePatchNum: PARENT,
    });
    const changeModel = testResolver(changeModelToken);
    changeModel.updateStateChange({
      ...createParsedChange(),
      revisions: createRevisions(5),
    });
    // Stub methods on the changeComments object after changeComments has
    // been initialized.
    element.changeComments = new ChangeComments();
    await element.updateComplete;
  });

  test('render', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <h3 class="assistive-tech-only">Patchset Range Selection</h3>
        <span aria-label="patch range starts with" class="patchRange">
          <gr-dropdown-list id="basePatchDropdown"> </gr-dropdown-list>
        </span>
        <span aria-hidden="true" class="arrow"> → </span>
        <span aria-label="patch range ends with" class="patchRange">
          <gr-dropdown-list id="patchNumDropdown"> </gr-dropdown-list>
        </span>
      `
    );
  });

  test('enabled/disabled options', async () => {
    element.sortedRevisions = [
      createRevision(3),
      createEditRevision(2),
      createRevision(2),
      createRevision(1),
    ];
    await element.updateComplete;

    for (const patchNum of [1, 2, 3]) {
      assert.isFalse(
        element.computeRightDisabled(PARENT, patchNum as PatchSetNumber)
      );
    }
    for (const basePatchNum of [1, 2]) {
      const base = basePatchNum as PatchSetNum;
      assert.isFalse(element.computeLeftDisabled(base, 3 as PatchSetNum));
    }
    assert.isTrue(
      element.computeLeftDisabled(3 as PatchSetNum, 3 as PatchSetNum)
    );

    assert.isTrue(
      element.computeLeftDisabled(3 as PatchSetNum, 3 as PatchSetNum)
    );
  });

  test('computeBaseDropdownContent', async () => {
    element.availablePatches = [
      {num: EDIT, sha: '1'} as PatchSet,
      {num: 3, sha: '2'} as PatchSet,
      {num: 2, sha: '3'} as PatchSet,
      {num: 1, sha: '4'} as PatchSet,
    ];
    element.sortedRevisions = [
      createRevision(4),
      createRevision(3),
      createRevision(2),
      createRevision(1),
    ];
    element.revisionInfo = getInfo(element.sortedRevisions);
    const expectedResult: DropdownItem[] = [
      {
        disabled: true,
        triggerText: 'Patchset edit',
        text: 'Patchset edit | 1',
        mobileText: EDIT,
        bottomText: '',
        value: EDIT,
      },
      {
        disabled: true,
        triggerText: 'Patchset 3',
        text: 'Patchset 3 | 2',
        mobileText: '3',
        bottomText: '',
        value: 3,
        date: '2020-02-01 01:02:03.000000000' as Timestamp,
      } as DropdownItem,
      {
        disabled: true,
        triggerText: 'Patchset 2',
        text: 'Patchset 2 | 3',
        mobileText: '2',
        bottomText: '',
        value: 2,
        date: '2020-02-01 01:02:03.000000000' as Timestamp,
      } as DropdownItem,
      {
        disabled: true,
        triggerText: 'Patchset 1',
        text: 'Patchset 1 | 4',
        mobileText: '1',
        bottomText: '',
        value: 1,
        date: '2020-02-01 01:02:03.000000000' as Timestamp,
      } as DropdownItem,
      {
        text: 'Base',
        value: PARENT,
      } as DropdownItem,
    ];
    element.patchNum = 1 as PatchSetNumber;
    element.basePatchNum = PARENT;
    await element.updateComplete;

    assert.deepEqual(element.computeBaseDropdownContent(), expectedResult);
  });

  test('computeBaseDropdownContent called when patchNum updates', async () => {
    element.sortedRevisions = [
      createRevision(4),
      createRevision(3),
      createRevision(2),
      createRevision(1),
    ];
    element.revisionInfo = getInfo(element.sortedRevisions);
    element.availablePatches = [
      {num: 1, sha: '1'} as PatchSet,
      {num: 2, sha: '2'} as PatchSet,
      {num: 3, sha: '3'} as PatchSet,
      {num: EDIT, sha: '4'} as PatchSet,
    ];
    element.patchNum = 2 as PatchSetNumber;
    element.basePatchNum = PARENT as BasePatchSetNum;
    await element.updateComplete;

    const baseDropDownStub = sinon.stub(element, 'computeBaseDropdownContent');

    // Should be recomputed for each available patch
    element.patchNum = 1 as PatchSetNumber;
    await element.updateComplete;
    assert.equal(baseDropDownStub.callCount, 1);
  });

  test('computeBaseDropdownContent called when changeComments update', async () => {
    element.sortedRevisions = [
      createRevision(4),
      createRevision(3),
      createRevision(2),
      createRevision(1),
    ];
    element.revisionInfo = getInfo(element.sortedRevisions);
    element.availablePatches = [
      {num: 3, sha: '2'} as PatchSet,
      {num: 2, sha: '3'} as PatchSet,
      {num: 1, sha: '4'} as PatchSet,
    ];
    element.patchNum = 2 as PatchSetNumber;
    element.basePatchNum = PARENT as BasePatchSetNum;
    await element.updateComplete;

    // Should be recomputed for each available patch
    const baseDropDownStub = sinon.stub(element, 'computeBaseDropdownContent');
    assert.equal(baseDropDownStub.callCount, 0);
    element.changeComments = new ChangeComments();
    await element.updateComplete;
    assert.equal(baseDropDownStub.callCount, 1);
  });

  test('computePatchDropdownContent called when basePatchNum updates', async () => {
    element.sortedRevisions = [
      createRevision(2),
      createRevision(3),
      createRevision(1),
      createRevision(4),
    ];
    element.revisionInfo = getInfo(element.sortedRevisions);
    element.availablePatches = [
      {num: 1, sha: '1'} as PatchSet,
      {num: 2, sha: '2'} as PatchSet,
      {num: 3, sha: '3'} as PatchSet,
      {num: EDIT, sha: '4'} as PatchSet,
    ];
    element.patchNum = 2 as PatchSetNumber;
    element.basePatchNum = PARENT as BasePatchSetNum;
    await element.updateComplete;

    // Should be recomputed for each available patch
    const baseDropDownStub = sinon.stub(element, 'computePatchDropdownContent');
    element.basePatchNum = 1 as BasePatchSetNum;
    await element.updateComplete;
    assert.equal(baseDropDownStub.callCount, 1);
  });

  test('computePatchDropdownContent', async () => {
    element.availablePatches = [
      {num: EDIT, sha: '1'} as PatchSet,
      {num: 3, sha: '2'} as PatchSet,
      {num: 2, sha: '3'} as PatchSet,
      {num: 1, sha: '4'} as PatchSet,
    ];
    element.basePatchNum = 1 as BasePatchSetNum;
    element.sortedRevisions = [
      createRevision(3),
      createEditRevision(2),
      createRevision(2, 'description'),
      createRevision(1),
    ];
    await element.updateComplete;

    const expectedResult: DropdownItem[] = [
      {
        disabled: false,
        triggerText: EDIT,
        text: 'edit | 1',
        mobileText: EDIT,
        bottomText: '',
        value: EDIT,
      },
      {
        disabled: false,
        triggerText: 'Patchset 3',
        text: 'Patchset 3 | 2',
        mobileText: '3',
        bottomText: '',
        value: 3,
        date: '2020-02-01 01:02:03.000000000' as Timestamp,
      } as DropdownItem,
      {
        disabled: false,
        triggerText: 'Patchset 2',
        text: 'Patchset 2 | 3',
        mobileText: '2 description',
        bottomText: 'description',
        value: 2,
        date: '2020-02-01 01:02:03.000000000' as Timestamp,
      } as DropdownItem,
      {
        disabled: true,
        triggerText: 'Patchset 1',
        text: 'Patchset 1 | 4',
        mobileText: '1',
        bottomText: '',
        value: 1,
        date: '2020-02-01 01:02:03.000000000' as Timestamp,
      } as DropdownItem,
    ];

    assert.deepEqual(element.computePatchDropdownContent(), expectedResult);
  });

  test('filesWeblinks', async () => {
    element.filesWeblinks = {
      meta_a: [
        {
          name: 'foo',
          url: 'f.oo',
        },
      ],
      meta_b: [
        {
          name: 'bar',
          url: 'ba.r',
        },
      ],
    };
    await element.updateComplete;
    assert.equal(
      queryAndAssert(element, 'a[href="f.oo"]').textContent!.trim(),
      'foo'
    );
    assert.equal(
      queryAndAssert(element, 'a[href="ba.r"]').textContent!.trim(),
      'bar'
    );
  });

  test('computePatchSetCommentsString', () => {
    // Test string with unresolved comments.
    const comments: PathToCommentsInfoMap = {
      foo: [
        {
          id: '27dcee4d_f7b77cfa' as UrlEncodedCommentId,
          message: 'test',
          patch_set: 1 as RevisionPatchSetNum,
          unresolved: true,
          updated: '2017-10-11 20:48:40.000000000' as Timestamp,
        },
      ],
      bar: [
        {
          id: '27dcee4d_f7b77cfa' as UrlEncodedCommentId,
          message: 'test',
          patch_set: 1 as RevisionPatchSetNum,
          updated: '2017-10-12 20:48:40.000000000' as Timestamp,
        },
        {
          id: '27dcee4d_f7b77cfa' as UrlEncodedCommentId,
          message: 'test',
          patch_set: 1 as RevisionPatchSetNum,
          updated: '2017-10-13 20:48:40.000000000' as Timestamp,
        },
      ],
      abc: [],
      // Patchset level comment does not contribute to the count
      [SpecialFilePath.PATCHSET_LEVEL_COMMENTS]: [
        {
          id: '27dcee4d_f7b77cfa' as UrlEncodedCommentId,
          message: 'test',
          patch_set: 1 as RevisionPatchSetNum,
          unresolved: true,
          updated: '2017-10-11 20:48:40.000000000' as Timestamp,
        },
      ],
    };
    element.changeComments = new ChangeComments(comments);

    assert.equal(
      element.computePatchSetCommentsString(1 as PatchSetNum),
      ' (3 comments, 1 unresolved)'
    );

    // Test string with no unresolved comments.
    delete comments['foo'];
    element.changeComments = new ChangeComments(comments);
    assert.equal(
      element.computePatchSetCommentsString(1 as PatchSetNum),
      ' (2 comments)'
    );

    // Test string with no comments.
    delete comments['bar'];
    element.changeComments = new ChangeComments(comments);
    assert.equal(element.computePatchSetCommentsString(1 as PatchSetNum), '');
  });

  test('patch-range-change fires', async () => {
    const handler = sinon.stub();
    element.basePatchNum = 1 as BasePatchSetNum;
    element.patchNum = 3 as PatchSetNumber;
    element.availablePatches = [
      {num: EDIT, sha: '1'} as PatchSet,
      {num: 3, sha: '2'} as PatchSet,
      {num: 2, sha: '3'} as PatchSet,
      {num: 1, sha: '4'} as PatchSet,
    ];
    element.sortedRevisions = [
      createRevision(2),
      createRevision(3),
      createRevision(1),
      createRevision(4),
    ];
    element.revisionInfo = getInfo(element.sortedRevisions);
    await element.updateComplete;

    element.addEventListener('patch-range-change', handler);
    const basePatchDropdown = queryAndAssert<GrDropdownList>(
      element,
      '#basePatchDropdown'
    );
    basePatchDropdown.value = '2';
    await basePatchDropdown.updateComplete;
    assert.equal(handler.callCount, 1);
    assert.deepEqual(handler.lastCall.args[0].detail, {
      basePatchNum: 2,
      patchNum: 3,
    });

    // BasePatchNum should not have changed, due to one-way data binding.
    const patchNumDropdown = queryAndAssert<GrDropdownList>(
      element,
      '#patchNumDropdown'
    );
    patchNumDropdown.value = EDIT;
    await patchNumDropdown.updateComplete;
    assert.deepEqual(handler.lastCall.args[0].detail, {
      basePatchNum: 1,
      patchNum: EDIT,
    });
  });

  test('handlePatchChange', async () => {
    element.availablePatches = [
      {num: EDIT, sha: '1'} as PatchSet,
      {num: 3, sha: '2'} as PatchSet,
      {num: 2, sha: '3'} as PatchSet,
      {num: 1, sha: '4'} as PatchSet,
    ];
    element.sortedRevisions = [
      createRevision(2),
      createRevision(3),
      createRevision(1),
      createRevision(4),
    ];
    element.revisionInfo = getInfo(element.sortedRevisions);
    element.patchNum = 1 as PatchSetNumber;
    element.basePatchNum = PARENT;
    await element.updateComplete;

    const stub = stubReporting('reportInteraction');
    fire(element.patchNumDropdown!, 'value-change', {value: '1'});
    assert.isFalse(stub.called);

    fire(element.patchNumDropdown!, 'value-change', {value: '2'});
    assert.isTrue(stub.called);
  });
});
