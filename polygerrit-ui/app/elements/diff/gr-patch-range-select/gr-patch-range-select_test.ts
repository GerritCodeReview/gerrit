/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import '../../shared/revision-info/revision-info';
import './gr-patch-range-select';
import {GrPatchRangeSelect} from './gr-patch-range-select';
import {RevisionInfo as RevisionInfoClass} from '../../shared/revision-info/revision-info';
import {ChangeComments} from '../gr-comment-api/gr-comment-api';
import {queryAll, stubReporting} from '../../../test/test-utils';
import {
  AccountId,
  BasePatchSetNum,
  ChangeMessageId,
  CommentInfo,
  EDIT,
  PARENT,
  PatchSetNum,
  PatchSetNumber,
  RevisionInfo,
  RevisionPatchSetNum,
  Timestamp,
  UrlEncodedCommentId,
} from '../../../types/common';
import {EditRevisionInfo, ParsedChangeInfo} from '../../../types/types';
import {SpecialFilePath} from '../../../constants/constants';
import {
  createAccountDetailWithId,
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
import {assert, fixture, html} from '@open-wc/testing';
import {testResolver} from '../../../test/common-test-setup';
import {changeViewModelToken} from '../../../models/views/change';
import {
  changeModelToken,
  RevisionFileUpdateStatus,
} from '../../../models/change/change-model';
import {userModelToken} from '../../../models/user/user-model';

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

  test('valid/invalid options', async () => {
    element.sortedRevisions = [
      createRevision(3),
      createEditRevision(2),
      createRevision(2),
      createRevision(1),
    ];
    await element.updateComplete;

    for (const patchNum of [1, 2, 3]) {
      assert.isTrue(
        element.isValidRightPatchNum(PARENT, patchNum as PatchSetNumber)
      );
    }
    for (const basePatchNum of [1, 2]) {
      const base = basePatchNum as PatchSetNum;
      assert.isTrue(element.isValidLeftPatchNum(base, 3 as PatchSetNum));
    }
    assert.isFalse(
      element.isValidLeftPatchNum(3 as PatchSetNum, 3 as PatchSetNum)
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
        triggerText: 'Patchset 1',
        text: 'Patchset 1 | 4',
        mobileText: '1',
        bottomText: '',
        value: 1,
        date: '2020-02-01 01:02:03.000000000' as Timestamp,
        commentThreads: [],
        deemphasizeReason: undefined,
        label: undefined,
        vote: undefined,
      } as DropdownItem,
      {
        triggerText: 'Base',
        value: PARENT,
        bottomText: '',
        text: 'Base | ',
      } as DropdownItem,
    ];
    element.patchNum = 2 as PatchSetNumber;
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
        triggerText: EDIT,
        text: 'edit | 1',
        mobileText: EDIT,
        bottomText: '',
        value: EDIT,
        commentThreads: [],
        deemphasizeReason: undefined,
        vote: undefined,
        label: undefined,
      },
      {
        triggerText: 'Patchset 3',
        text: 'Patchset 3 | 2',
        mobileText: '3',
        bottomText: '',
        value: 3,
        date: '2020-02-01 01:02:03.000000000' as Timestamp,
        commentThreads: [],
        deemphasizeReason: undefined,
        vote: undefined,
        label: undefined,
      } as DropdownItem,
      {
        triggerText: 'Patchset 2',
        text: 'Patchset 2 | 3',
        mobileText: '2 description',
        bottomText: 'description',
        value: 2,
        date: '2020-02-01 01:02:03.000000000' as Timestamp,
        commentThreads: [],
        deemphasizeReason: undefined,
        vote: undefined,
        label: undefined,
      } as DropdownItem,
    ];

    assert.deepEqual(element.computePatchDropdownContent(), expectedResult);
  });

  test('filesWeblinks', async () => {
    element.filesWeblinks = {
      meta_a: [{name: 'foo', url: 'f.oo'}],
      meta_b: [{name: 'bar', url: 'ba.r'}],
    };
    await element.updateComplete;
    assert.equal(queryAll(element, 'gr-weblink').length, 2);
  });

  test('computePatchSetCommentsString', () => {
    // Test string with unresolved comments.
    const comments: {[path: string]: CommentInfo[]} = {
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
      ' (4 comments, 2 unresolved)'
    );

    // Test string for specific file path.
    element.path = 'foo';
    assert.equal(
      element.computePatchSetCommentsString(1 as PatchSetNum),
      ' (1 comment, 1 unresolved)'
    );
    element.path = undefined;

    // Test string with no unresolved comments.
    delete comments['foo'];
    element.changeComments = new ChangeComments(comments);
    assert.equal(
      element.computePatchSetCommentsString(1 as PatchSetNum),
      ' (3 comments, 1 unresolved)'
    );

    // Test string with no comments.
    delete comments['bar'];
    element.changeComments = new ChangeComments(comments);
    assert.equal(
      element.computePatchSetCommentsString(1 as PatchSetNum),
      ' (1 comment, 1 unresolved)'
    );
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
    fire(element.patchNumDropdown, 'value-change', {value: '1'});
    assert.isFalse(stub.called);

    fire(element.patchNumDropdown, 'value-change', {value: '2'});
    assert.isTrue(stub.called);
  });

  test('createDropdownEntry includes patchset level comments when path is undefined', async () => {
    element.availablePatches = [{num: 1, sha: '4'} as PatchSet];
    element.sortedRevisions = [createRevision(1)];
    element.revisionInfo = getInfo(element.sortedRevisions);

    // Create mock ChangeComments with a spy on computeCommentThreads
    element.changeComments = new ChangeComments();
    const computeCommentThreadsSpy = sinon.spy(
      element.changeComments,
      'computeCommentThreads'
    );

    // First test with path undefined
    element.path = undefined;
    await element.updateComplete;
    computeCommentThreadsSpy.resetHistory();

    element.createDropdownEntry(1 as PatchSetNum, 'Patchset ', '4');

    // Verify computeCommentThreads was called with the correct ignorePatchsetLevelComments value
    assert.isTrue(computeCommentThreadsSpy.called);
    assert.deepEqual(computeCommentThreadsSpy.firstCall.args[0], {
      path: undefined,
      patchNum: 1 as PatchSetNum,
    });
    assert.isFalse(
      computeCommentThreadsSpy.firstCall.args[1],
      'Should not ignore patchset level comments when path is undefined'
    );
    // Reset the spy
    computeCommentThreadsSpy.resetHistory();

    // Now test with path defined
    element.path = 'some/file/path';
    await element.updateComplete;

    element.createDropdownEntry(1 as PatchSetNum, 'Patchset ', '4');

    // Verify computeCommentThreads was called with the correct ignorePatchsetLevelComments value
    assert.isTrue(computeCommentThreadsSpy.called);
    assert.deepEqual(computeCommentThreadsSpy.firstCall.args[0], {
      path: 'some/file/path',
      patchNum: 1 as PatchSetNum,
    });
    assert.isTrue(
      computeCommentThreadsSpy.firstCall.args[1],
      'Should ignore patchset level comments when path is defined'
    );
  });

  test('revisions without modification are deemphasized', async () => {
    element.availablePatches = [
      {num: 4, sha: 'sha4'} as PatchSet,
      {num: 3, sha: 'sha3'} as PatchSet,
      {num: 2, sha: 'sha2'} as PatchSet,
      {num: 1, sha: 'sha1'} as PatchSet,
    ];
    element.sortedRevisions = [
      createRevision(4),
      createRevision(3),
      createRevision(2),
      createRevision(1),
    ];
    element.revisionUpdatedFiles = {
      sha1: {
        foo: RevisionFileUpdateStatus.MODIFIED,
        bar: RevisionFileUpdateStatus.MODIFIED,
      },
      sha2: {
        foo: RevisionFileUpdateStatus.SAME,
        bar: RevisionFileUpdateStatus.MODIFIED,
      },
      sha3: {
        foo: RevisionFileUpdateStatus.UNKNOWN,
        bar: RevisionFileUpdateStatus.SAME,
      },
      sha4: {
        foo: RevisionFileUpdateStatus.SAME,
        bar: RevisionFileUpdateStatus.SAME,
      },
    };
    element.path = 'foo';
    element.revisionInfo = getInfo(element.sortedRevisions);
    element.patchNum = 4 as PatchSetNumber;
    element.basePatchNum = PARENT;
    await element.updateComplete;

    const expectedResult: {triggerText: string; deemphasizeReason?: string}[] =
      [
        {
          triggerText: 'Patchset 3',
          deemphasizeReason: undefined,
        },
        {
          triggerText: 'Patchset 2',
          deemphasizeReason: 'Unmodified',
        },
        {
          triggerText: 'Patchset 1',
          deemphasizeReason: undefined,
        },
        {
          triggerText: 'Base',
          deemphasizeReason: undefined,
        },
      ];
    assert.deepEqual(
      element.computeBaseDropdownContent().map(
        x =>
          ({
            triggerText: x.triggerText,
            deemphasizeReason: x.deemphasizeReason,
          } as {triggerText: string; deemphasizeReason?: string})
      ),
      expectedResult
    );
  });
});

suite('gr-patch-range-select with votes', () => {
  let element: GrPatchRangeSelect;

  setup(async () => {
    const changeModel = testResolver(changeModelToken);
    const userModel = testResolver(userModelToken);

    const viewModel = testResolver(changeViewModelToken);
    viewModel.setState({
      ...createChangeViewState(),
      patchNum: 2 as RevisionPatchSetNum,
      basePatchNum: PARENT,
    });

    const account = createAccountDetailWithId(1);
    userModel.setAccount(account);

    const change: ParsedChangeInfo = {
      ...createParsedChange(),
      messages: [
        {
          id: '1' as ChangeMessageId,
          author: {_account_id: 1 as AccountId},
          date: '2020-01-01 10:00:00' as Timestamp,
          message: 'Patch Set 1: Code-Review+1',
          _revision_number: 1 as PatchSetNumber,
        },
        {
          id: '2' as ChangeMessageId,
          author: {_account_id: 1 as AccountId},
          date: '2020-01-01 11:00:00' as Timestamp,
          message: 'Patch Set 2: Code-Review-1',
          _revision_number: 2 as PatchSetNumber,
        },
      ],
      revisions: {
        sha1: createRevision(1),
        sha2: createRevision(2),
      },
      labels: {
        'Code-Review': {
          values: {
            '-1': 'No',
            ' 0': 'No score',
            '+1': 'Yes',
          },
        },
      },
    };

    changeModel.updateStateChange(change);

    element = await fixture(
      html`<gr-patch-range-select></gr-patch-range-select>`
    );
    await element.updateComplete;

    // Unclear why but it's required twice
    changeModel.updateStateChange(change);
    await element.updateComplete;
  });

  test('shows votes in dropdown', async () => {
    const dropdown = queryAndAssert<GrDropdownList>(
      element,
      '#patchNumDropdown'
    );
    await dropdown.updateComplete;

    dropdown.open();
    await dropdown.updateComplete;

    const menu = dropdown.shadowRoot?.querySelector('md-menu');
    assert.isDefined(menu);

    const voteChip = menu!.querySelector('gr-vote-chip');
    assert.isDefined(voteChip);
    assert.equal(voteChip!.vote?.value, -1);
  });
});
