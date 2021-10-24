/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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
import '../gr-comment-api/gr-comment-api';
import '../../shared/revision-info/revision-info';
import './gr-patch-range-select';
import {GrPatchRangeSelect} from './gr-patch-range-select';
import '../../../test/mocks/comment-api';
import {RevisionInfo as RevisionInfoClass} from '../../shared/revision-info/revision-info';
import {ChangeComments} from '../gr-comment-api/gr-comment-api';
import {stubRestApi} from '../../../test/test-utils';
import {
  BasePatchSetNum,
  EditPatchSetNum,
  PatchSetNum,
  RevisionInfo,
  Timestamp,
  UrlEncodedCommentId,
  PathToCommentsInfoMap,
} from '../../../types/common';
import {EditRevisionInfo, ParsedChangeInfo} from '../../../types/types';
// import {SpecialFilePath} from '../../../constants/constants';
import {
  createEditRevision,
  createRevision,
} from '../../../test/test-data-generators';
import {PatchSet} from '../../../utils/patch-set-util';
import {
  DropdownItem,
  GrDropdownList,
} from '../../shared/gr-dropdown-list/gr-dropdown-list';
import {queryAndAssert} from '../../../test/test-utils';

const basicFixture = fixtureFromElement('gr-patch-range-select');

type RevIdToRevisionInfo = {
  [revisionId: string]: RevisionInfo | EditRevisionInfo;
};

suite('gr-patch-range-select tests', () => {
  let element: GrPatchRangeSelect;

  function getInfo(revisions: RevisionInfo[]) {
    const revisionObj: Partial<RevIdToRevisionInfo> = {};
    for (let i = 0; i < revisions.length; i++) {
      revisionObj[i] = revisions[i];
    }
    return new RevisionInfoClass({revisions: revisionObj} as ParsedChangeInfo);
  }

  setup(async () => {
    stubRestApi('getDiffComments').returns(Promise.resolve({}));
    stubRestApi('getDiffRobotComments').returns(Promise.resolve({}));
    stubRestApi('getDiffDrafts').returns(Promise.resolve({}));

    // Element must be wrapped in an element with direct access to the
    // comment API.
    element = basicFixture.instantiate();

    // Stub methods on the changeComments object after changeComments has
    // been initialized.
    element.changeComments = new ChangeComments();
    await flush();
  });

  test('enabled/disabled options', () => {
    const patchRange = {
      basePatchNum: 'PARENT' as PatchSetNum,
      patchNum: 3 as PatchSetNum,
    };
    const sortedRevisions = [
      createRevision(3) as RevisionInfo,
      createEditRevision(2) as EditRevisionInfo,
      createRevision(2) as RevisionInfo,
      createRevision(1) as RevisionInfo,
    ];
    for (const patchNum of [1, 2, 3]) {
      assert.isFalse(
        element._computeRightDisabled(
          patchRange.basePatchNum,
          patchNum as PatchSetNum,
          sortedRevisions
        )
      );
    }
    for (const basePatchNum of [1, 2]) {
      assert.isFalse(
        element._computeLeftDisabled(
          basePatchNum as PatchSetNum,
          patchRange.patchNum,
          sortedRevisions
        )
      );
    }
    assert.isTrue(
      element._computeLeftDisabled(3 as PatchSetNum, patchRange.patchNum, [])
    );

    patchRange.basePatchNum = EditPatchSetNum;
    assert.isTrue(
      element._computeLeftDisabled(
        3 as PatchSetNum,
        patchRange.patchNum,
        sortedRevisions
      )
    );
    assert.isTrue(
      element._computeRightDisabled(
        patchRange.basePatchNum,
        1 as PatchSetNum,
        sortedRevisions
      )
    );
    assert.isTrue(
      element._computeRightDisabled(
        patchRange.basePatchNum,
        2 as PatchSetNum,
        sortedRevisions
      )
    );
    assert.isFalse(
      element._computeRightDisabled(
        patchRange.basePatchNum,
        3 as PatchSetNum,
        sortedRevisions
      )
    );
    assert.isTrue(
      element._computeRightDisabled(
        patchRange.basePatchNum,
        EditPatchSetNum,
        sortedRevisions
      )
    );
  });

  test('_computeBaseDropdownContent', () => {
    const availablePatches = [
      {num: 'edit', sha: '1'} as PatchSet,
      {num: 3, sha: '2'} as PatchSet,
      {num: 2, sha: '3'} as PatchSet,
      {num: 1, sha: '4'} as PatchSet,
    ];
    const revisions: RevisionInfo[] = [
      createRevision(2),
      createRevision(3),
      createRevision(1),
      createRevision(4),
    ];
    element.revisionInfo = getInfo(revisions);
    const sortedRevisions = [
      createRevision(3) as RevisionInfo,
      createEditRevision(2) as EditRevisionInfo,
      createRevision(2) as RevisionInfo,
      createRevision(1) as RevisionInfo,
    ];
    const expectedResult: DropdownItem[] = [
      {
        disabled: true,
        triggerText: 'Patchset edit',
        text: 'Patchset edit | 1',
        mobileText: 'edit',
        bottomText: '',
        value: 'edit',
      },
      {
        disabled: true,
        triggerText: 'Patchset 3',
        text: 'Patchset 3 | 2',
        mobileText: '3',
        bottomText: '',
        value: 3,
        date: '2021-03-30 01:02:03.000000000' as Timestamp,
      } as DropdownItem,
      {
        disabled: true,
        triggerText: 'Patchset 2',
        text: 'Patchset 2 | 3',
        mobileText: '2',
        bottomText: '',
        value: 2,
        date: '2021-03-30 01:02:03.000000000' as Timestamp,
      } as DropdownItem,
      {
        disabled: true,
        triggerText: 'Patchset 1',
        text: 'Patchset 1 | 4',
        mobileText: '1',
        bottomText: '',
        value: 1,
        date: '2021-03-30 01:02:03.000000000' as Timestamp,
      } as DropdownItem,
      {
        text: 'Base',
        value: 'PARENT',
      } as DropdownItem,
    ];
    assert.deepEqual(
      element._computeBaseDropdownContent(
        availablePatches,
        1 as PatchSetNum,
        sortedRevisions,
        element.changeComments,
        element.revisionInfo
      ),
      expectedResult
    );
  });

  test('_computeBaseDropdownContent called when patchNum updates', async () => {
    element.revisions = [
      createRevision(2),
      createRevision(3),
      createRevision(1),
      createRevision(4),
    ];
    element.revisionInfo = getInfo(element.revisions);
    element.availablePatches = [
      {num: 1, sha: '1'} as PatchSet,
      {num: 2, sha: '2'} as PatchSet,
      {num: 3, sha: '3'} as PatchSet,
      {num: 'edit', sha: '4'} as PatchSet,
    ];
    element.patchNum = 2 as PatchSetNum;
    element.basePatchNum = 'PARENT' as BasePatchSetNum;
    await flush();

    const baseDropDownStub = sinon.stub(element, '_computeBaseDropdownContent');

    // Should be recomputed for each available patch
    element.patchNum = 1 as PatchSetNum;
    await flush();
    assert.equal(baseDropDownStub.callCount, 1);
  });

  test('_computeBaseDropdownContent called when changeComments update', async () => {
    element.revisions = [
      createRevision(2),
      createRevision(3),
      createRevision(1),
      createRevision(4),
    ];
    element.revisionInfo = getInfo(element.revisions);
    element.availablePatches = [
      {num: 3, sha: '2'} as PatchSet,
      {num: 2, sha: '3'} as PatchSet,
      {num: 1, sha: '4'} as PatchSet,
    ];
    element.patchNum = 2 as PatchSetNum;
    element.basePatchNum = 'PARENT' as BasePatchSetNum;
    await flush();

    // Should be recomputed for each available patch
    const baseDropDownStub = sinon.stub(element, '_computeBaseDropdownContent');
    assert.equal(baseDropDownStub.callCount, 0);
    element.changeComments = new ChangeComments();
    await flush();
    assert.equal(baseDropDownStub.callCount, 1);
  });

  test('_computePatchDropdownContent called when basePatchNum updates', async () => {
    element.revisions = [
      createRevision(2),
      createRevision(3),
      createRevision(1),
      createRevision(4),
    ];
    element.revisionInfo = getInfo(element.revisions);
    element.availablePatches = [
      {num: 1, sha: '1'} as PatchSet,
      {num: 2, sha: '2'} as PatchSet,
      {num: 3, sha: '3'} as PatchSet,
      {num: 'edit', sha: '4'} as PatchSet,
    ];
    element.patchNum = 2 as PatchSetNum;
    element.basePatchNum = 'PARENT' as BasePatchSetNum;
    await flush();

    // Should be recomputed for each available patch
    const baseDropDownStub = sinon.stub(
      element,
      '_computePatchDropdownContent'
    );
    element.basePatchNum = 1 as BasePatchSetNum;
    await flush();
    assert.equal(baseDropDownStub.callCount, 1);
  });

  test('_computePatchDropdownContent', () => {
    const availablePatches: PatchSet[] = [
      {num: 'edit', sha: '1'} as PatchSet,
      {num: 3, sha: '2'} as PatchSet,
      {num: 2, sha: '3'} as PatchSet,
      {num: 1, sha: '4'} as PatchSet,
    ];
    const basePatchNum = 1;
    const sortedRevisions = [
      createRevision(3) as RevisionInfo,
      createEditRevision(2) as EditRevisionInfo,
      createRevision(2, 'description') as RevisionInfo,
      createRevision(1) as RevisionInfo,
    ];

    const expectedResult: DropdownItem[] = [
      {
        disabled: false,
        triggerText: 'edit',
        text: 'edit | 1',
        mobileText: 'edit',
        bottomText: '',
        value: 'edit',
      },
      {
        disabled: false,
        triggerText: 'Patchset 3',
        text: 'Patchset 3 | 2',
        mobileText: '3',
        bottomText: '',
        value: 3,
        date: '2021-03-30 01:02:03.000000000' as Timestamp,
      } as DropdownItem,
      {
        disabled: false,
        triggerText: 'Patchset 2',
        text: 'Patchset 2 | 3',
        mobileText: '2 description',
        bottomText: 'description',
        value: 2,
        date: '2021-03-30 01:02:03.000000000' as Timestamp,
      } as DropdownItem,
      {
        disabled: true,
        triggerText: 'Patchset 1',
        text: 'Patchset 1 | 4',
        mobileText: '1',
        bottomText: '',
        value: 1,
        date: '2021-03-30 01:02:03.000000000' as Timestamp,
      } as DropdownItem,
    ];

    assert.deepEqual(
      element._computePatchDropdownContent(
        availablePatches,
        basePatchNum as BasePatchSetNum,
        sortedRevisions,
        element.changeComments
      ),
      expectedResult
    );
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
    await flush();
    assert.equal(queryAndAssert(element, 'a[href="f.oo"]').textContent!.trim(), 'foo');
    assert.equal(queryAndAssert(element, 'a[href="ba.r"]').textContent!.trim(), 'bar');
  });

  test('_computePatchSetCommentsString', () => {
    // Test string with unresolved comments.
    const comments: PathToCommentsInfoMap = {
      foo: [
        {
          id: '27dcee4d_f7b77cfa' as UrlEncodedCommentId,
          message: 'test',
          patch_set: 1 as PatchSetNum,
          unresolved: true,
          updated: '2017-10-11 20:48:40.000000000' as Timestamp,
        },
      ],
      bar: [
        {
          id: '27dcee4d_f7b77cfa' as UrlEncodedCommentId,
          message: 'test',
          patch_set: 1 as PatchSetNum,
          updated: '2017-10-12 20:48:40.000000000' as Timestamp,
        },
        {
          id: '27dcee4d_f7b77cfa' as UrlEncodedCommentId,
          message: 'test',
          patch_set: 1 as PatchSetNum,
          updated: '2017-10-13 20:48:40.000000000' as Timestamp,
        },
      ],
      abc: [],
      // Patchset level comment does not contribute to the count
      /* [SpecialFilePath.PATCHSET_LEVEL_COMMENTS]: {
          id: '27dcee4d_f7b77cfa' as UrlEncodedCommentId,
          message: 'test',
          patch_set: 1 as PatchSetNum,
          unresolved: true,
          updated: '2017-10-11 20:48:40.000000000' as Timestamp,
        } */
    };
    element.changeComments = new ChangeComments(comments);

    assert.equal(
      element._computePatchSetCommentsString(
        element.changeComments,
        1 as PatchSetNum
      ),
      ' (3 comments, 1 unresolved)'
    );

    // Test string with no unresolved comments.
    delete comments['foo'];
    element.changeComments = new ChangeComments(comments);
    assert.equal(
      element._computePatchSetCommentsString(
        element.changeComments,
        1 as PatchSetNum
      ),
      ' (2 comments)'
    );

    // Test string with no comments.
    delete comments['bar'];
    element.changeComments = new ChangeComments(comments);
    assert.equal(
      element._computePatchSetCommentsString(
        element.changeComments,
        1 as PatchSetNum
      ),
      ''
    );
  });

  test('patch-range-change fires', () => {
    const handler = sinon.stub();
    element.basePatchNum = 1 as BasePatchSetNum;
    element.patchNum = 3 as PatchSetNum;
    element.addEventListener('patch-range-change', handler);

    queryAndAssert<GrDropdownList>(
      element,
      '#basePatchDropdown'
    )._handleValueChange('2', [{text: '', value: '2'}]);
    assert.isTrue(handler.calledOnce);
    assert.deepEqual(handler.lastCall.args[0].detail, {
      basePatchNum: 2,
      patchNum: 3,
    });

    // BasePatchNum should not have changed, due to one-way data binding.
    queryAndAssert<GrDropdownList>(
      element,
      '#patchNumDropdown'
    )._handleValueChange('edit', [{text: '', value: 'edit'}]);
    assert.deepEqual(handler.lastCall.args[0].detail, {
      basePatchNum: 1,
      patchNum: 'edit',
    });
  });
});
