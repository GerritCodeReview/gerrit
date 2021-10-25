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

import '../../../test/common-test-setup-karma.js';
import '../gr-comment-api/gr-comment-api.js';
import '../../shared/revision-info/revision-info.js';
import './gr-patch-range-select.js';
import '../../../test/mocks/comment-api.js';
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {RevisionInfo} from '../../shared/revision-info/revision-info.js';
import {createCommentApiMockWithTemplateElement} from '../../../test/mocks/comment-api';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';
import {ChangeComments} from '../gr-comment-api/gr-comment-api.js';
import {stubRestApi} from '../../../test/test-utils.js';
import {EditPatchSetNum} from '../../../types/common.js';
import {SpecialFilePath} from '../../../constants/constants.js';

const commentApiMockElement = createCommentApiMockWithTemplateElement(
    'gr-patch-range-select-comment-api-mock', html`
    <gr-patch-range-select id="patchRange" auto
        change-comments="[[_changeComments]]"></gr-patch-range-select>
    <gr-comment-api id="commentAPI"></gr-comment-api>
`);

const basicFixture = fixtureFromElement(commentApiMockElement.is);

suite('gr-patch-range-select tests', () => {
  let element;

  let commentApiWrapper;

  function getInfo(revisions) {
    const revisionObj = {};
    for (let i = 0; i < revisions.length; i++) {
      revisionObj[i] = revisions[i];
    }
    return new RevisionInfo({revisions: revisionObj});
  }

  setup(() => {
    stubRestApi('getDiffComments').returns(Promise.resolve({}));
    stubRestApi('getDiffRobotComments').returns(Promise.resolve({}));
    stubRestApi('getDiffDrafts').returns(Promise.resolve({}));

    // Element must be wrapped in an element with direct access to the
    // comment API.
    commentApiWrapper = basicFixture.instantiate();
    element = commentApiWrapper.$.patchRange;

    // Stub methods on the changeComments object after changeComments has
    // been initialized.
    element.changeComments = new ChangeComments();
  });

  test('enabled/disabled options', () => {
    const patchRange = {
      basePatchNum: 'PARENT',
      patchNum: 3,
    };
    const sortedRevisions = [
      {_number: 3},
      {_number: EditPatchSetNum, basePatchNum: 2},
      {_number: 2},
      {_number: 1},
    ];
    for (const patchNum of ['1', '2', '3']) {
      assert.isFalse(element._computeRightDisabled(patchRange.basePatchNum,
          patchNum, sortedRevisions));
    }
    for (const basePatchNum of ['1', '2']) {
      assert.isFalse(element._computeLeftDisabled(basePatchNum,
          patchRange.patchNum, sortedRevisions));
    }
    assert.isTrue(element._computeLeftDisabled('3', patchRange.patchNum));

    patchRange.basePatchNum = EditPatchSetNum;
    assert.isTrue(element._computeLeftDisabled('3', patchRange.patchNum,
        sortedRevisions));
    assert.isTrue(element._computeRightDisabled(patchRange.basePatchNum, '1',
        sortedRevisions));
    assert.isTrue(element._computeRightDisabled(patchRange.basePatchNum, '2',
        sortedRevisions));
    assert.isFalse(element._computeRightDisabled(patchRange.basePatchNum, '3',
        sortedRevisions));
    assert.isTrue(element._computeRightDisabled(patchRange.basePatchNum,
        EditPatchSetNum, sortedRevisions));
  });

  test('_computeBaseDropdownContent', () => {
    const availablePatches = [
      {num: 'edit', sha: '1'},
      {num: 3, sha: '2'},
      {num: 2, sha: '3'},
      {num: 1, sha: '4'},
    ];
    const revisions = [
      {
        commit: {parents: []},
        _number: 2,
        description: 'description',
      },
      {commit: {parents: []}},
      {commit: {parents: []}},
      {commit: {parents: []}},
    ];
    element.revisionInfo = getInfo(revisions);
    const patchNum = 1;
    const sortedRevisions = [
      {_number: 3, created: 'Mon, 01 Jan 2001 00:00:00 GMT'},
      {_number: EditPatchSetNum, basePatchNum: 2},
      {_number: 2, description: 'description'},
      {_number: 1},
    ];
    const expectedResult = [
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
        date: 'Mon, 01 Jan 2001 00:00:00 GMT',
      },
      {
        disabled: true,
        triggerText: 'Patchset 2',
        text: 'Patchset 2 | 3',
        mobileText: '2 description',
        bottomText: 'description',
        value: 2,
      },
      {
        disabled: true,
        triggerText: 'Patchset 1',
        text: 'Patchset 1 | 4',
        mobileText: '1',
        bottomText: '',
        value: 1,
      },
      {
        text: 'Base',
        value: 'PARENT',
      },
    ];
    assert.deepEqual(element._computeBaseDropdownContent(availablePatches,
        patchNum, sortedRevisions, element.changeComments,
        element.revisionInfo),
    expectedResult);
  });

  test('_computeBaseDropdownContent called when patchNum updates', () => {
    element.revisions = [
      {commit: {parents: []}},
      {commit: {parents: []}},
      {commit: {parents: []}},
      {commit: {parents: []}},
    ];
    element.revisionInfo = getInfo(element.revisions);
    element.availablePatches = [
      {num: 1, sha: '1'},
      {num: 2, sha: '2'},
      {num: 3, sha: '3'},
      {num: 'edit', sha: '4'},
    ];
    element.patchNum = 2;
    element.basePatchNum = 'PARENT';
    flush();

    sinon.stub(element, '_computeBaseDropdownContent');

    // Should be recomputed for each available patch
    element.set('patchNum', 1);
    assert.equal(element._computeBaseDropdownContent.callCount, 1);
  });

  test('_computeBaseDropdownContent called when changeComments update',
      async () => {
        element.revisions = [
          {commit: {parents: []}},
          {commit: {parents: []}},
          {commit: {parents: []}},
          {commit: {parents: []}},
        ];
        element.revisionInfo = getInfo(element.revisions);
        element.availablePatches = [
          {num: 'edit', sha: '1'},
          {num: 3, sha: '2'},
          {num: 2, sha: '3'},
          {num: 1, sha: '4'},
        ];
        element.patchNum = 2;
        element.basePatchNum = 'PARENT';
        await flush();

        // Should be recomputed for each available patch
        sinon.stub(element, '_computeBaseDropdownContent');
        assert.equal(element._computeBaseDropdownContent.callCount, 0);
        element.changeComments = new ChangeComments();
        await flush();
        assert.equal(element._computeBaseDropdownContent.callCount, 1);
      });

  test('_computePatchDropdownContent called when basePatchNum updates', () => {
    element.revisions = [
      {commit: {parents: []}},
      {commit: {parents: []}},
      {commit: {parents: []}},
      {commit: {parents: []}},
    ];
    element.revisionInfo = getInfo(element.revisions);
    element.availablePatches = [
      {num: 1, sha: '1'},
      {num: 2, sha: '2'},
      {num: 3, sha: '3'},
      {num: 'edit', sha: '4'},
    ];
    element.patchNum = 2;
    element.basePatchNum = 'PARENT';
    flush();

    // Should be recomputed for each available patch
    sinon.stub(element, '_computePatchDropdownContent');
    element.set('basePatchNum', 1);
    assert.equal(element._computePatchDropdownContent.callCount, 1);
  });

  test('_computePatchDropdownContent', () => {
    const availablePatches = [
      {num: 'edit', sha: '1'},
      {num: 3, sha: '2'},
      {num: 2, sha: '3'},
      {num: 1, sha: '4'},
    ];
    const basePatchNum = 1;
    const sortedRevisions = [
      {_number: 3, created: 'Mon, 01 Jan 2001 00:00:00 GMT'},
      {_number: EditPatchSetNum, basePatchNum: 2},
      {_number: 2, description: 'description'},
      {_number: 1},
    ];

    const expectedResult = [
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
        date: 'Mon, 01 Jan 2001 00:00:00 GMT',
      },
      {
        disabled: false,
        triggerText: 'Patchset 2',
        text: 'Patchset 2 | 3',
        mobileText: '2 description',
        bottomText: 'description',
        value: 2,
      },
      {
        disabled: true,
        triggerText: 'Patchset 1',
        text: 'Patchset 1 | 4',
        mobileText: '1',
        bottomText: '',
        value: 1,
      },
    ];

    assert.deepEqual(element._computePatchDropdownContent(availablePatches,
        basePatchNum, sortedRevisions, element.changeComments),
    expectedResult);
  });

  test('filesWeblinks', () => {
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
    flush();
    const domApi = dom(element.root);
    assert.equal(
        domApi.querySelector('a[href="f.oo"]').textContent, 'foo');
    assert.equal(
        domApi.querySelector('a[href="ba.r"]').textContent, 'bar');
  });

  test('_computePatchSetCommentsString', () => {
    // Test string with unresolved comments.
    const comments = {
      foo: [{
        id: '27dcee4d_f7b77cfa',
        message: 'test',
        patch_set: 1,
        unresolved: true,
        updated: '2017-10-11 20:48:40.000000000',
      }],
      bar: [
        {
          id: '27dcee4d_f7b77cfa',
          message: 'test',
          patch_set: 1,
          updated: '2017-10-12 20:48:40.000000000',
        },
        {
          id: '27dcee4d_f7b77cfa',
          message: 'test',
          patch_set: 1,
          updated: '2017-10-13 20:48:40.000000000',
        },
      ],
      abc: [],
      // Patchset level comment does not contribute to the count
      [SpecialFilePath.PATCHSET_LEVEL_COMMENTS]: {
        id: '27dcee4d_f7b77cfa',
        message: 'test',
        patch_set: 1,
        unresolved: true,
        updated: '2017-10-11 20:48:40.000000000',
      },
    };
    element.changeComments = new ChangeComments(comments);

    assert.equal(element._computePatchSetCommentsString(
        element.changeComments, 1), ' (3 comments, 1 unresolved)');

    // Test string with no unresolved comments.
    delete element.changeComments._comments['foo'];
    assert.equal(element._computePatchSetCommentsString(
        element.changeComments, 1), ' (2 comments)');

    // Test string with no comments.
    delete element.changeComments._comments['bar'];
    assert.equal(element._computePatchSetCommentsString(
        element.changeComments, 1), '');
  });

  test('patch-range-change fires', () => {
    const handler = sinon.stub();
    element.basePatchNum = 1;
    element.patchNum = 3;
    element.addEventListener('patch-range-change', handler);

    element.$.basePatchDropdown._handleValueChange(2, [{value: 2}]);
    assert.isTrue(handler.calledOnce);
    assert.deepEqual(handler.lastCall.args[0].detail,
        {basePatchNum: 2, patchNum: 3});

    // BasePatchNum should not have changed, due to one-way data binding.
    element.$.patchNumDropdown._handleValueChange('edit', [{value: 'edit'}]);
    assert.deepEqual(handler.lastCall.args[0].detail,
        {basePatchNum: 1, patchNum: 'edit'});
  });
});

