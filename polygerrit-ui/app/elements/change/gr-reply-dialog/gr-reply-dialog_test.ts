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
import './gr-reply-dialog';
import {
  mockPromise,
  queryAll,
  queryAndAssert,
  stubStorage,
} from '../../../test/test-utils';
import {
  ChangeStatus,
  ReviewerState,
  SpecialFilePath,
} from '../../../constants/constants';
import {appContext} from '../../../services/app-context';
import {addListenerForTest} from '../../../test/test-utils';
import {stubRestApi} from '../../../test/test-utils';
import {JSON_PREFIX} from '../../shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {StandardLabels} from '../../../utils/label-util';
import {
  createAccountWithId,
  createChange,
  createCommentThread,
  createDraft,
  createRevision,
} from '../../../test/test-data-generators';
import {
  pressAndReleaseKeyOn,
  tap,
} from '@polymer/iron-test-helpers/mock-interactions';
import {GrReplyDialog} from './gr-reply-dialog';
import {
  AccountId,
  AccountInfo,
  CommitId,
  DetailedLabelInfo,
  GroupId,
  GroupName,
  NumericChangeId,
  PatchSetNum,
  ReviewerInput,
  ReviewInput,
  ReviewResult,
  Suggestion,
  UrlEncodedCommentId,
} from '../../../types/common';
import {CommentThread} from '../../../utils/comment-util';
import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import {
  AccountInfoInput,
  GrAccountList,
} from '../../shared/gr-account-list/gr-account-list';
import {GrLabelScoreRow} from '../gr-label-score-row/gr-label-score-row';
import {GrLabelScores} from '../gr-label-scores/gr-label-scores';
import {GrThreadList} from '../gr-thread-list/gr-thread-list';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';

const basicFixture = fixtureFromElement('gr-reply-dialog');

function cloneableResponse(status: number, text: string) {
  return {
    ...new Response(),
    ok: false,
    status,
    text() {
      return Promise.resolve(text);
    },
    clone() {
      return {
        ok: false,
        status,
        text() {
          return Promise.resolve(text);
        },
      };
    },
  };
}

suite('gr-reply-dialog tests', () => {
  let element: GrReplyDialog;
  let changeNum: NumericChangeId;
  let patchNum: PatchSetNum;

  let getDraftCommentStub: sinon.SinonStub;
  let setDraftCommentStub: sinon.SinonStub;
  let eraseDraftCommentStub: sinon.SinonStub;

  const emptyAccountInfoInputChanges =
    [] as unknown as PolymerDeepPropertyChange<
      AccountInfoInput[],
      AccountInfoInput[]
    >;

  let lastId = 1;
  const makeAccount = function () {
    return {_account_id: lastId++ as AccountId};
  };
  const makeGroup = function () {
    return {id: `${lastId++}` as GroupId};
  };

  setup(async () => {
    changeNum = 42 as NumericChangeId;
    patchNum = 1 as PatchSetNum;

    stubRestApi('getChange').returns(Promise.resolve({...createChange()}));
    stubRestApi('getChangeSuggestedReviewers').returns(Promise.resolve([]));

    sinon.stub(appContext.flagsService, 'isEnabled').returns(true);

    element = basicFixture.instantiate();
    element.change = {
      ...createChange(),
      _number: changeNum,
      owner: {
        _account_id: 999 as AccountId as AccountId,
        display_name: 'Kermit',
      },
      labels: {
        Verified: {
          values: {
            '-1': 'Fails',
            ' 0': 'No score',
            '+1': 'Verified',
          },
          default_value: 0,
        },
        'Code-Review': {
          values: {
            '-2': 'Do not submit',
            '-1': "I would prefer that you didn't submit this",
            ' 0': 'No score',
            '+1': 'Looks good to me, but someone else must approve',
            '+2': 'Looks good to me, approved',
          },
          default_value: 0,
        },
      },
    };
    element.patchNum = patchNum;
    element.permittedLabels = {
      'Code-Review': ['-1', ' 0', '+1'],
      Verified: ['-1', ' 0', '+1'],
    };

    getDraftCommentStub = stubStorage('getDraftComment');
    setDraftCommentStub = stubStorage('setDraftComment');
    eraseDraftCommentStub = stubStorage('eraseDraftComment');

    // sinon.stub(patchSetUtilMockProxy, 'fetchChangeUpdates')
    //     .returns(Promise.resolve({isLatest: true}));

    // Allow the elements created by dom-repeat to be stamped.
    await flush();
  });

  function stubSaveReview(
    jsonResponseProducer: (input: ReviewInput) => ReviewResult | void
  ) {
    return sinon.stub(element, '_saveReview').callsFake(
      review =>
        new Promise((resolve, reject) => {
          try {
            const result = jsonResponseProducer(review) || {};
            const resultStr = JSON_PREFIX + JSON.stringify(result);
            resolve({
              ...new Response(),
              ok: true,
              text() {
                return Promise.resolve(resultStr);
              },
            });
          } catch (err) {
            reject(err);
          }
        })
    );
  }

  function interceptSaveReview() {
    let resolver: (review: ReviewInput) => void;
    const promise = new Promise(resolve => {
      resolver = resolve;
    });
    stubSaveReview((review: ReviewInput) => {
      resolver(review);
    });
    return promise;
  }

  test('default to publishing draft comments with reply', async () => {
    // Async tick is needed because iron-selector content is distributed and
    // distributed content requires an observer to be set up.
    await flush();
    element.draft = 'I wholeheartedly disapprove';
    const saveReviewPromise = interceptSaveReview();

    // This is needed on non-Blink engines most likely due to the ways in
    // which the dom-repeat elements are stamped.
    await flush();
    tap(queryAndAssert(element, '.send'));
    await flush();

    const review = await saveReviewPromise;
    assert.deepEqual(review, {
      drafts: 'PUBLISH_ALL_REVISIONS',
      labels: {
        'Code-Review': 0,
        Verified: 0,
      },
      comments: {
        [SpecialFilePath.PATCHSET_LEVEL_COMMENTS]: [
          {
            message: 'I wholeheartedly disapprove',
            unresolved: false,
          },
        ],
      },
      reviewers: [],
      add_to_attention_set: [],
      remove_from_attention_set: [],
      ignore_automatic_attention_set_rules: true,
    });
    assert.isFalse(
      (queryAndAssert(element, '#commentList') as GrThreadList).hidden
    );
  });

  test('modified attention set', async () => {
    await flush();
    element._account = {_account_id: 123 as AccountId};
    element._newAttentionSet = new Set([314 as AccountId]);
    const saveReviewPromise = interceptSaveReview();
    const modifyButton = queryAndAssert(element, '.edit-attention-button');
    tap(modifyButton);
    await flush();

    tap(queryAndAssert(element, '.send'));
    const review = await saveReviewPromise;

    assert.deepEqual(review, {
      drafts: 'PUBLISH_ALL_REVISIONS',
      labels: {
        'Code-Review': 0,
        Verified: 0,
      },
      add_to_attention_set: [
        {reason: '<GERRIT_ACCOUNT_123> replied on the change', user: 314},
      ],
      reviewers: [],
      remove_from_attention_set: [],
      ignore_automatic_attention_set_rules: true,
    });
  });

  test('modified attention set by anonymous', async () => {
    await flush();
    element._account = {};
    element._newAttentionSet = new Set([314 as AccountId]);
    const saveReviewPromise = interceptSaveReview();
    const modifyButton = queryAndAssert(element, '.edit-attention-button');
    tap(modifyButton);
    await flush();

    tap(queryAndAssert(element, '.send'));
    const review = await saveReviewPromise;

    assert.deepEqual(review, {
      drafts: 'PUBLISH_ALL_REVISIONS',
      labels: {
        'Code-Review': 0,
        Verified: 0,
      },
      add_to_attention_set: [
        {reason: 'Anonymous replied on the change', user: 314},
      ],
      reviewers: [],
      remove_from_attention_set: [],
      ignore_automatic_attention_set_rules: true,
    });
    element._newAttentionSet = new Set();
    await flush();
  });

  function checkComputeAttention(
    status: ChangeStatus,
    userId?: AccountId,
    reviewerIds?: AccountId[],
    ownerId?: AccountId,
    attSetIds?: AccountId[],
    replyToIds?: AccountId[],
    expectedIds?: AccountId[],
    uploaderId?: AccountId,
    hasDraft = true,
    includeComments = true
  ) {
    const user = {_account_id: userId};
    const reviewers = {
      base: reviewerIds?.map(id => {
        return {_account_id: id};
      }),
    } as PolymerDeepPropertyChange<AccountInfoInput[], AccountInfoInput[]>;
    let draftThreads: CommentThread[] = [];
    if (hasDraft) {
      draftThreads = [
        {
          ...createCommentThread([
            {
              ...createDraft(),
              __draft: true,
              unresolved: true,
            },
          ]),
        },
      ];
    }
    replyToIds?.forEach(id =>
      draftThreads[0].comments.push({
        author: {_account_id: id},
      })
    );
    const change = {
      ...createChange(),
      owner: {_account_id: ownerId},
      status,
    };
    attSetIds?.forEach(id => {
      if (!change.attention_set) change.attention_set = {};
      change.attention_set[id.toString()] = {
        account: createAccountWithId(id),
      };
    });
    if (uploaderId) {
      change.current_revision = 'b' as CommitId;
      change.revisions = {
        a: createRevision(1),
        b: {...createRevision(2), uploader: {_account_id: uploaderId}},
      };
    }
    element.change = change;
    element._reviewers = reviewers.base!;

    flush();
    const hasDrafts = draftThreads.length > 0;
    element._computeNewAttention(
      user,
      reviewers!,
      emptyAccountInfoInputChanges,
      change,
      draftThreads,
      includeComments,
      undefined,
      hasDrafts
    );
    assert.sameMembers([...element._newAttentionSet], expectedIds!);
  }

  test('computeNewAttention NEW', () => {
    checkComputeAttention(
      ChangeStatus.NEW,
      1 as AccountId,
      [],
      999 as AccountId,
      [],
      [],
      [999 as AccountId]
    );
    checkComputeAttention(
      ChangeStatus.NEW,
      1 as AccountId,
      [],
      999 as AccountId,
      [1 as AccountId],
      [],
      [999 as AccountId]
    );
    checkComputeAttention(
      ChangeStatus.NEW,
      1 as AccountId,
      [22 as AccountId],
      999 as AccountId,
      [],
      [],
      [999 as AccountId]
    );
    checkComputeAttention(
      ChangeStatus.NEW,
      1 as AccountId,
      [22 as AccountId],
      999 as AccountId,
      [22 as AccountId],
      [],
      [22 as AccountId, 999 as AccountId]
    );
    checkComputeAttention(
      ChangeStatus.NEW,
      1 as AccountId,
      [22 as AccountId],
      999 as AccountId,
      [],
      [22 as AccountId],
      [22 as AccountId, 999 as AccountId]
    );
    checkComputeAttention(
      ChangeStatus.NEW,
      1 as AccountId,
      [22 as AccountId, 33 as AccountId],
      999 as AccountId,
      [33 as AccountId],
      [22 as AccountId],
      [22 as AccountId, 33 as AccountId, 999 as AccountId]
    );
    // If the owner replies, then do not add them.
    checkComputeAttention(
      ChangeStatus.NEW,
      1 as AccountId,
      [],
      1 as AccountId,
      [],
      [],
      []
    );
    checkComputeAttention(
      ChangeStatus.NEW,
      1 as AccountId,
      [],
      1 as AccountId,
      [1 as AccountId],
      [],
      []
    );
    checkComputeAttention(
      ChangeStatus.NEW,
      1 as AccountId,
      [22 as AccountId],
      1 as AccountId,
      [],
      [],
      []
    );

    checkComputeAttention(
      ChangeStatus.NEW,
      1 as AccountId,
      [22 as AccountId],
      1 as AccountId,
      [],
      [22 as AccountId],
      [22 as AccountId]
    );
    checkComputeAttention(
      ChangeStatus.NEW,
      1 as AccountId,
      [22 as AccountId, 33 as AccountId],
      1 as AccountId,
      [33 as AccountId],
      [22 as AccountId],
      [22 as AccountId, 33 as AccountId]
    );
    checkComputeAttention(
      ChangeStatus.NEW,
      1 as AccountId,
      [22 as AccountId, 33 as AccountId],
      1 as AccountId,
      [],
      [22 as AccountId],
      [22 as AccountId]
    );
    checkComputeAttention(
      ChangeStatus.NEW,
      1 as AccountId,
      [22 as AccountId, 33 as AccountId],
      1 as AccountId,
      [],
      [22 as AccountId, 33 as AccountId],
      [22 as AccountId, 33 as AccountId]
    );
    checkComputeAttention(
      ChangeStatus.NEW,
      1 as AccountId,
      [22 as AccountId, 33 as AccountId],
      1 as AccountId,
      [22 as AccountId, 33 as AccountId],
      [],
      [22 as AccountId, 33 as AccountId]
    );
    // with uploader
    checkComputeAttention(
      ChangeStatus.NEW,
      1 as AccountId,
      [],
      1 as AccountId,
      [],
      [2 as AccountId],
      [2 as AccountId],
      2 as AccountId
    );
    checkComputeAttention(
      ChangeStatus.NEW,
      1 as AccountId,
      [],
      1 as AccountId,
      [2 as AccountId],
      [],
      [2 as AccountId],
      2 as AccountId
    );
    checkComputeAttention(
      ChangeStatus.NEW,
      1 as AccountId,
      [],
      3 as AccountId,
      [],
      [],
      [2 as AccountId, 3 as AccountId],
      2 as AccountId
    );
  });

  test('computeNewAttention MERGED', () => {
    checkComputeAttention(
      ChangeStatus.MERGED,
      undefined,
      [],
      999 as AccountId,
      [],
      [],
      [],
      undefined,
      false
    );
    checkComputeAttention(
      ChangeStatus.MERGED,
      1 as AccountId,
      [],
      999 as AccountId,
      [],
      [],
      [],
      undefined,
      false
    );
    checkComputeAttention(
      ChangeStatus.MERGED,
      1 as AccountId,
      [],
      999 as AccountId,
      [],
      [],
      [999 as AccountId],
      undefined,
      true
    );
    checkComputeAttention(
      ChangeStatus.MERGED,
      1 as AccountId,
      [],
      999 as AccountId,
      [],
      [],
      [],
      undefined,
      true,
      false
    );
    checkComputeAttention(
      ChangeStatus.MERGED,
      1 as AccountId,
      [],
      999 as AccountId,
      [1 as AccountId],
      [],
      [],
      undefined,
      false
    );
    checkComputeAttention(
      ChangeStatus.MERGED,
      1 as AccountId,
      [22 as AccountId],
      999 as AccountId,
      [],
      [],
      [],
      undefined,
      false
    );
    checkComputeAttention(
      ChangeStatus.MERGED,
      1 as AccountId,
      [22 as AccountId],
      999 as AccountId,
      [22 as AccountId],
      [],
      [22 as AccountId],
      undefined,
      false
    );
    checkComputeAttention(
      ChangeStatus.MERGED,
      1 as AccountId,
      [22 as AccountId],
      999 as AccountId,
      [],
      [22 as AccountId],
      []
    );
    checkComputeAttention(
      ChangeStatus.MERGED,
      1 as AccountId,
      [22 as AccountId, 33 as AccountId],
      999 as AccountId,
      [33 as AccountId],
      [22 as AccountId],
      [33 as AccountId]
    );
    checkComputeAttention(
      ChangeStatus.MERGED,
      1 as AccountId,
      [],
      1 as AccountId,
      [],
      [],
      []
    );
    checkComputeAttention(
      ChangeStatus.MERGED,
      1 as AccountId,
      [],
      1 as AccountId,
      [],
      [],
      [],
      undefined,
      true
    );
    checkComputeAttention(
      ChangeStatus.MERGED,
      1 as AccountId,
      [],
      1 as AccountId,
      [1 as AccountId],
      [],
      []
    );
    checkComputeAttention(
      ChangeStatus.MERGED,
      1 as AccountId,
      [],
      1 as AccountId,
      [1 as AccountId],
      [],
      [],
      undefined,
      true
    );
    checkComputeAttention(
      ChangeStatus.MERGED,
      1 as AccountId,
      [22 as AccountId],
      1 as AccountId,
      [],
      [],
      []
    );
    checkComputeAttention(
      ChangeStatus.MERGED,
      1 as AccountId,
      [22 as AccountId],
      1 as AccountId,
      [],
      [22 as AccountId],
      []
    );
    checkComputeAttention(
      ChangeStatus.MERGED,
      1 as AccountId,
      [22 as AccountId, 33 as AccountId],
      1 as AccountId,
      [33 as AccountId],
      [22 as AccountId],
      [33 as AccountId]
    );
    checkComputeAttention(
      ChangeStatus.MERGED,
      1 as AccountId,
      [22 as AccountId, 33 as AccountId],
      1 as AccountId,
      [],
      [22 as AccountId],
      []
    );
    checkComputeAttention(
      ChangeStatus.MERGED,
      1 as AccountId,
      [22 as AccountId, 33 as AccountId],
      1 as AccountId,
      [],
      [22 as AccountId, 33 as AccountId],
      []
    );
    checkComputeAttention(
      ChangeStatus.MERGED,
      1 as AccountId,
      [22 as AccountId, 33 as AccountId],
      1 as AccountId,
      [22 as AccountId, 33 as AccountId],
      [],
      [22 as AccountId, 33 as AccountId]
    );
  });

  test('computeNewAttention when adding reviewers', () => {
    const user = {_account_id: 1 as AccountId};
    const reviewers = {
      base: [
        {_account_id: 1 as AccountId, _pendingAdd: true},
        {_account_id: 2 as AccountId, _pendingAdd: true},
      ],
    } as PolymerDeepPropertyChange<AccountInfoInput[], AccountInfoInput[]>;
    const change = {
      ...createChange(),
      owner: {_account_id: 5 as AccountId},
      status: ChangeStatus.NEW,
      attention_set: {},
    };
    element.change = change;
    element._reviewers = reviewers.base;
    flush();

    element._computeNewAttention(
      user,
      reviewers,
      emptyAccountInfoInputChanges,
      change,
      [],
      true
    );
    assert.sameMembers([...element._newAttentionSet], [1, 2]);

    // If the user votes on the change, then they should not be added to the
    // attention set, even if they have just added themselves as reviewer.
    // But voting should also add the owner (5).
    const labelsChanged = true;
    element._computeNewAttention(
      user,
      reviewers,
      emptyAccountInfoInputChanges,
      change,
      [],
      true,
      labelsChanged
    );
    assert.sameMembers([...element._newAttentionSet], [2, 5]);
  });

  test('computeNewAttention when sending wip change for review', () => {
    const reviewers = {
      base: [{...createAccountWithId(2)}, {...createAccountWithId(3)}],
    } as PolymerDeepPropertyChange<AccountInfoInput[], AccountInfoInput[]>;
    const change = {
      ...createChange(),
      owner: {_account_id: 1 as AccountId},
      status: ChangeStatus.NEW,
      attention_set: {},
    };
    element.change = change;
    element._reviewers = reviewers.base;
    flush();

    // For an active change there is no reason to add anyone to the set.
    let user = {_account_id: 1 as AccountId};
    element._computeNewAttention(
      user,
      reviewers,
      emptyAccountInfoInputChanges,
      change,
      [],
      false
    );
    assert.sameMembers([...element._newAttentionSet], []);

    // If the change is "work in progress" and the owner sends a reply, then
    // add all reviewers.
    element.canBeStarted = true;
    flush();
    user = {_account_id: 1 as AccountId};
    element._computeNewAttention(
      user,
      reviewers,
      emptyAccountInfoInputChanges,
      change,
      [],
      false
    );
    assert.sameMembers([...element._newAttentionSet], [2, 3]);

    // ... but not when someone else replies.
    user = {_account_id: 4 as AccountId};
    element._computeNewAttention(
      user,
      reviewers,
      emptyAccountInfoInputChanges,
      change,
      [],
      false
    );
    assert.sameMembers([...element._newAttentionSet], []);
  });

  test('computeNewAttentionAccounts', () => {
    element._reviewers = [
      {_account_id: 123 as AccountId, display_name: 'Ernie'},
      {_account_id: 321 as AccountId, display_name: 'Bert'},
    ];
    element._ccs = [{_account_id: 7 as AccountId, display_name: 'Elmo'}];
    const compute = (currentAtt: AccountId[], newAtt: AccountId[]) =>
      element
        ._computeNewAttentionAccounts(
          undefined,
          new Set(currentAtt),
          new Set(newAtt)
        )
        .map(a => a!._account_id);

    assert.sameMembers(compute([], []), []);
    assert.sameMembers(compute([], [999 as AccountId]), [999 as AccountId]);
    assert.sameMembers(compute([999 as AccountId], []), []);
    assert.sameMembers(compute([999 as AccountId], [999 as AccountId]), []);
    assert.sameMembers(
      compute([123 as AccountId, 321 as AccountId], [999 as AccountId]),
      [999 as AccountId]
    );
    assert.sameMembers(
      compute(
        [999 as AccountId],
        [7 as AccountId, 123 as AccountId, 999 as AccountId]
      ),
      [7 as AccountId, 123 as AccountId]
    );
  });

  test('_computeCommentAccounts', () => {
    element.change = {
      ...createChange(),
      labels: {
        'Code-Review': {
          all: [
            {_account_id: 1 as AccountId, value: 0},
            {_account_id: 2 as AccountId, value: 1},
            {_account_id: 3 as AccountId, value: 2},
          ],
          values: {
            '-2': 'Do not submit',
            '-1': 'I would prefer that you didnt submit this',
            ' 0': 'No score',
            '+1': 'Looks good to me, but someone else must approve',
            '+2': 'Looks good to me, approved',
          },
        },
      },
    };
    const threads = [
      {
        ...createCommentThread([
          {
            id: '1' as UrlEncodedCommentId,
            author: {_account_id: 1 as AccountId},
            unresolved: false,
          },
          {
            id: '2' as UrlEncodedCommentId,
            in_reply_to: '1' as UrlEncodedCommentId,
            author: {_account_id: 2 as AccountId},
            unresolved: true,
          },
        ]),
      },
      {
        ...createCommentThread([
          {
            id: '3' as UrlEncodedCommentId,
            author: {_account_id: 3 as AccountId},
            unresolved: false,
          },
          {
            id: '4' as UrlEncodedCommentId,
            in_reply_to: '3' as UrlEncodedCommentId,
            author: {_account_id: 4 as AccountId},
            unresolved: false,
          },
        ]),
      },
    ];
    const actualAccounts = [...element._computeCommentAccounts(threads)];
    // Account 3 is not included, because the comment is resolved *and* they
    // have given the highest possible vote on the Code-Review label.
    assert.sameMembers(actualAccounts, [1, 2, 4]);
  });

  test('toggle resolved checkbox', async () => {
    const checkboxEl = queryAndAssert(
      element,
      '#resolvedPatchsetLevelCommentCheckbox'
    );
    tap(checkboxEl);

    // Async tick is needed because iron-selector content is distributed and
    // distributed content requires an observer to be set up.
    await flush();
    element.draft = 'I wholeheartedly disapprove';
    const saveReviewPromise = interceptSaveReview();

    // This is needed on non-Blink engines most likely due to the ways in
    // which the dom-repeat elements are stamped.
    await flush();
    tap(queryAndAssert(element, '.send'));

    const review = await saveReviewPromise;
    assert.deepEqual(review, {
      drafts: 'PUBLISH_ALL_REVISIONS',
      labels: {
        'Code-Review': 0,
        Verified: 0,
      },
      comments: {
        [SpecialFilePath.PATCHSET_LEVEL_COMMENTS]: [
          {
            message: 'I wholeheartedly disapprove',
            unresolved: true,
          },
        ],
      },
      reviewers: [],
      add_to_attention_set: [],
      remove_from_attention_set: [],
      ignore_automatic_attention_set_rules: true,
    });
  });

  test('label picker', async () => {
    element.draft = 'I wholeheartedly disapprove';
    const saveReviewPromise = interceptSaveReview();

    sinon.stub(element.getLabelScores(), 'getLabelValues').callsFake(() => {
      return {
        'Code-Review': -1,
        Verified: -1,
      };
    });

    // This is needed on non-Blink engines most likely due to the ways in
    // which the dom-repeat elements are stamped.
    await flush();
    tap(queryAndAssert(element, '.send'));
    assert.isTrue(element.disabled);

    const review = await saveReviewPromise;
    await flush();
    assert.isFalse(
      element.disabled,
      'Element should be enabled when done sending reply.'
    );
    assert.equal(element.draft.length, 0);
    assert.deepEqual(review, {
      drafts: 'PUBLISH_ALL_REVISIONS',
      labels: {
        'Code-Review': -1,
        Verified: -1,
      },
      comments: {
        [SpecialFilePath.PATCHSET_LEVEL_COMMENTS]: [
          {
            message: 'I wholeheartedly disapprove',
            unresolved: false,
          },
        ],
      },
      reviewers: [],
      add_to_attention_set: [],
      remove_from_attention_set: [],
      ignore_automatic_attention_set_rules: true,
    });
  });

  test('keep draft comments with reply', async () => {
    tap(queryAndAssert(element, '#includeComments'));
    assert.equal(element._includeComments, false);

    // Async tick is needed because iron-selector content is distributed and
    // distributed content requires an observer to be set up.
    await flush();
    element.draft = 'I wholeheartedly disapprove';
    const saveReviewPromise = interceptSaveReview();

    // This is needed on non-Blink engines most likely due to the ways in
    // which the dom-repeat elements are stamped.
    await flush();
    tap(queryAndAssert(element, '.send'));

    const review = await saveReviewPromise;
    await flush();
    assert.deepEqual(review, {
      drafts: 'KEEP',
      labels: {
        'Code-Review': 0,
        Verified: 0,
      },
      comments: {
        [SpecialFilePath.PATCHSET_LEVEL_COMMENTS]: [
          {
            message: 'I wholeheartedly disapprove',
            unresolved: false,
          },
        ],
      },
      reviewers: [],
      add_to_attention_set: [],
      remove_from_attention_set: [],
      ignore_automatic_attention_set_rules: true,
    });
  });

  test('getlabelValue returns value', async () => {
    await flush();
    const el = queryAndAssert(
      queryAndAssert(element, 'gr-label-scores'),
      'gr-label-score-row[name="Verified"]'
    ) as GrLabelScoreRow;
    el.setSelectedValue('-1');
    assert.equal('-1', element.getLabelValue('Verified'));
  });

  test('getlabelValue when no score is selected', async () => {
    await flush();
    const el = queryAndAssert(
      queryAndAssert(element, 'gr-label-scores'),
      'gr-label-score-row[name="Code-Review"]'
    ) as GrLabelScoreRow;
    el.setSelectedValue('-1');
    assert.strictEqual(element.getLabelValue('Verified'), ' 0');
  });

  test('setlabelValue', async () => {
    element._account = {_account_id: 1 as AccountId};
    await flush();
    const label = 'Verified';
    const value = '+1';
    element.setLabelValue(label, value);
    await flush();

    const labels = (
      queryAndAssert(element, '#labelScores') as GrLabelScores
    ).getLabelValues();
    assert.deepEqual(labels, {
      'Code-Review': 0,
      Verified: 1,
    });
  });

  function getActiveElement() {
    return document.activeElement;
  }

  function isVisible(el: Element) {
    assert.ok(el);
    return getComputedStyle(el).getPropertyValue('display') !== 'none';
  }

  function overlayObserver(mode: string) {
    return new Promise(resolve => {
      function listener() {
        element.removeEventListener('iron-overlay-' + mode, listener);
        resolve(mode);
      }
      element.addEventListener('iron-overlay-' + mode, listener);
    });
  }

  function isFocusInsideElement(element: Element) {
    // In Polymer 2 focused element either <paper-input> or nested
    // native input <input> element depending on the current focus
    // in browser window.
    // For example, the focus is changed if the developer console
    // get a focus.
    let activeElement = getActiveElement();
    while (activeElement) {
      if (activeElement === element) {
        return true;
      }
      if (activeElement.parentElement) {
        activeElement = activeElement.parentElement;
      } else {
        activeElement = (activeElement.getRootNode() as ShadowRoot).host;
      }
    }
    return false;
  }

  async function testConfirmationDialog(cc?: boolean) {
    const yesButton = queryAndAssert(
      element,
      '.reviewerConfirmationButtons gr-button:first-child'
    );
    const noButton = queryAndAssert(
      element,
      '.reviewerConfirmationButtons gr-button:last-child'
    );

    element._ccPendingConfirmation = null;
    element._reviewerPendingConfirmation = null;
    flush();
    assert.isFalse(
      isVisible(queryAndAssert(element, 'reviewerConfirmationOverlay'))
    );

    // Cause the confirmation dialog to display.
    let observer = overlayObserver('opened');
    const group = {
      id: 'id' as GroupId,
      name: 'name' as GroupName,
    };
    if (cc) {
      element._ccPendingConfirmation = {
        group,
        confirm: false,
      };
    } else {
      element._reviewerPendingConfirmation = {
        group,
        confirm: false,
      };
    }
    flush();

    if (cc) {
      assert.deepEqual(
        element._ccPendingConfirmation,
        element._pendingConfirmationDetails
      );
    } else {
      assert.deepEqual(
        element._reviewerPendingConfirmation,
        element._pendingConfirmationDetails
      );
    }

    await observer;
    assert.isTrue(
      isVisible(queryAndAssert(element, 'reviewerConfirmationOverlay'))
    );
    observer = overlayObserver('closed');
    const expected = 'Group name has 10 members';
    assert.notEqual(
      (
        queryAndAssert(element, 'reviewerConfirmationOverlay') as GrOverlay
      ).innerText.indexOf(expected),
      -1
    );
    tap(noButton); // close the overlay

    await observer;
    assert.isFalse(
      isVisible(queryAndAssert(element, 'reviewerConfirmationOverlay'))
    );

    // We should be focused on account entry input.
    assert.isTrue(
      isFocusInsideElement(
        (queryAndAssert(element, '#reviewers') as GrAccountList).$.entry.$.input
          .$.input
      )
    );

    // No reviewer/CC should have been added.
    assert.equal(
      (queryAndAssert(element, '#ccs') as GrAccountList).additions().length,
      0
    );
    assert.equal(
      (queryAndAssert(element, '#reviewers') as GrAccountList).additions()
        .length,
      0
    );

    // Reopen confirmation dialog.
    observer = overlayObserver('opened');
    if (cc) {
      element._ccPendingConfirmation = {
        group,
        confirm: false,
      };
    } else {
      element._reviewerPendingConfirmation = {
        group,
        confirm: false,
      };
    }

    await observer;
    assert.isTrue(
      isVisible(queryAndAssert(element, 'reviewerConfirmationOverlay'))
    );
    observer = overlayObserver('closed');
    tap(yesButton); // Confirm the group.

    await observer;
    assert.isFalse(
      isVisible(queryAndAssert(element, 'reviewerConfirmationOverlay'))
    );
    const additions = cc
      ? (queryAndAssert(element, '#ccs') as GrAccountList).additions()
      : (queryAndAssert(element, '#reviewers') as GrAccountList).additions();
    assert.deepEqual(additions, [
      {
        group: {
          id: 'id' as GroupId,
          name: 'name' as GroupName,
          confirmed: true,
          _group: true,
          _pendingAdd: true,
        },
      },
    ]);

    // We should be focused on account entry input.
    if (cc) {
      assert.isTrue(
        isFocusInsideElement(
          (queryAndAssert(element, '#ccs') as GrAccountList).$.entry.$.input.$
            .input
        )
      );
    } else {
      assert.isTrue(
        isFocusInsideElement(
          (queryAndAssert(element, '#reviewers') as GrAccountList).$.entry.$
            .input.$.input
        )
      );
    }
  }

  test('cc confirmation', async () => {
    testConfirmationDialog(true);
  });

  test('reviewer confirmation', async () => {
    testConfirmationDialog(false);
  });

  test('_getStorageLocation', () => {
    const actual = element._getStorageLocation();
    assert.equal(actual.changeNum, changeNum);
    assert.equal(actual.patchNum, '@change');
    assert.equal(actual.path, '@change');
  });

  test('_reviewersMutated when account-text-change is fired from ccs', () => {
    flush();
    assert.isFalse(element._reviewersMutated);
    assert.isTrue(
      (queryAndAssert(element, '#ccs') as GrAccountList).allowAnyInput
    );
    assert.isFalse(
      (queryAndAssert(element, '#reviewers') as GrAccountList).allowAnyInput
    );
    queryAndAssert(element, '#ccs').dispatchEvent(
      new CustomEvent('account-text-changed', {bubbles: true, composed: true})
    );
    assert.isTrue(element._reviewersMutated);
  });

  test('gets draft from storage on open', () => {
    const storedDraft = 'hello world';
    getDraftCommentStub.returns({message: storedDraft});
    element.open();
    assert.isTrue(getDraftCommentStub.called);
    assert.equal(element.draft, storedDraft);
  });

  test('gets draft from storage even when text is already present', () => {
    const storedDraft = 'hello world';
    getDraftCommentStub.returns({message: storedDraft});
    element.draft = 'foo bar';
    element.open();
    assert.isTrue(getDraftCommentStub.called);
    assert.equal(element.draft, storedDraft);
  });

  test('blank if no stored draft', () => {
    getDraftCommentStub.returns(null);
    element.draft = 'foo bar';
    element.open();
    assert.isTrue(getDraftCommentStub.called);
    assert.equal(element.draft, '');
  });

  test('does not check stored draft when quote is present', () => {
    const storedDraft = 'hello world';
    const quote = '> foo bar';
    getDraftCommentStub.returns({message: storedDraft});
    element.quote = quote;
    element.open();
    assert.isFalse(getDraftCommentStub.called);
    assert.equal(element.draft, quote);
    assert.isNotOk(element.quote);
  });

  test('updates stored draft on edits', async () => {
    const clock = sinon.useFakeTimers();

    const firstEdit = 'hello';
    const location = element._getStorageLocation();

    element.draft = firstEdit;
    clock.tick(1000);
    await flush();

    assert.isTrue(setDraftCommentStub.calledWith(location, firstEdit));

    element.draft = '';
    clock.tick(1000);
    await flush();

    assert.isTrue(eraseDraftCommentStub.calledWith(location));
  });

  test('400 converts to human-readable server-error', async () => {
    stubRestApi('saveChangeReview').callsFake(
      (_changeNum, _patchNum, _review, errFn) => {
        errFn!(
          cloneableResponse(
            400,
            '....{"reviewers":{"id1":{"error":"human readable"}}}'
          ) as Response
        );
        return Promise.resolve(new Response());
      }
    );

    const promise = mockPromise();
    const listener = (event: Event) => {
      if (event.target !== document) return;
      (event as CustomEvent).detail.response.text().then((body: string) => {
        if (body === 'human readable') {
          promise.resolve();
        }
      });
    };
    addListenerForTest(document, 'server-error', listener);

    await flush();
    element.send(false, false);
    await promise;
  });

  test('non-json 400 is treated as a normal server-error', async () => {
    stubRestApi('saveChangeReview').callsFake(
      (_changeNum, _patchNum, _review, errFn) => {
        errFn!(cloneableResponse(400, 'Comment validation error!') as Response);
        return Promise.resolve(new Response());
      }
    );
    const promise = mockPromise();
    const listener = (event: Event) => {
      if (event.target !== document) return;
      (event as CustomEvent).detail.response.text().then((body: string) => {
        if (body === 'Comment validation error!') {
          promise.resolve();
        }
      });
    };
    addListenerForTest(document, 'server-error', listener);

    // Async tick is needed because iron-selector content is distributed and
    // distributed content requires an observer to be set up.
    await flush();
    element.send(false, false);
    await promise;
  });

  test('filterReviewerSuggestion', () => {
    const owner = makeAccount();
    const reviewer1 = makeAccount();
    const reviewer2 = makeGroup();
    const cc1 = makeAccount();
    const cc2 = makeGroup();
    let filter = element._filterReviewerSuggestionGenerator(false);

    element._owner = owner;
    element._reviewers = [reviewer1, reviewer2];
    element._ccs = [cc1, cc2];

    assert.isTrue(filter({account: makeAccount()} as Suggestion));
    assert.isTrue(filter({group: makeGroup()} as Suggestion));

    // Owner should be excluded.
    assert.isFalse(filter({account: owner} as Suggestion));

    // Existing and pending reviewers should be excluded when isCC = false.
    assert.isFalse(filter({account: reviewer1} as Suggestion));
    assert.isFalse(filter({group: reviewer2} as Suggestion));

    filter = element._filterReviewerSuggestionGenerator(true);

    // Existing and pending CCs should be excluded when isCC = true;.
    assert.isFalse(filter({account: cc1} as Suggestion));
    assert.isFalse(filter({group: cc2} as Suggestion));
  });

  test('_focusOn', async () => {
    const chooseFocusTargetSpy = sinon.spy(element, '_chooseFocusTarget');
    element._focusOn();
    await flush();
    assert.equal(chooseFocusTargetSpy.callCount, 1);
    assert.equal(element?.shadowRoot?.activeElement?.tagName, 'GR-TEXTAREA');
    assert.equal(element?.shadowRoot?.activeElement?.id, 'textarea');

    element._focusOn(element.FocusTarget.ANY);
    await flush();
    assert.equal(chooseFocusTargetSpy.callCount, 2);
    assert.equal(element?.shadowRoot?.activeElement?.tagName, 'GR-TEXTAREA');
    assert.equal(element?.shadowRoot?.activeElement?.id, 'textarea');

    element._focusOn(element.FocusTarget.BODY);
    await flush();
    assert.equal(chooseFocusTargetSpy.callCount, 2);
    assert.equal(element?.shadowRoot?.activeElement?.tagName, 'GR-TEXTAREA');
    assert.equal(element?.shadowRoot?.activeElement?.id, 'textarea');

    element._focusOn(element.FocusTarget.REVIEWERS);
    await flush();
    assert.equal(chooseFocusTargetSpy.callCount, 2);
    assert.equal(
      element?.shadowRoot?.activeElement?.tagName,
      'GR-ACCOUNT-LIST'
    );
    assert.equal(element?.shadowRoot?.activeElement?.id, 'reviewers');

    element._focusOn(element.FocusTarget.CCS);
    await flush();
    assert.equal(chooseFocusTargetSpy.callCount, 2);
    assert.equal(
      element?.shadowRoot?.activeElement?.tagName,
      'GR-ACCOUNT-LIST'
    );
    assert.equal(element?.shadowRoot?.activeElement?.id, 'ccs');
  });

  test('_chooseFocusTarget', () => {
    element._account = undefined;
    assert.strictEqual(element._chooseFocusTarget(), element.FocusTarget.BODY);

    element._account = {_account_id: 1 as AccountId};
    assert.strictEqual(element._chooseFocusTarget(), element.FocusTarget.BODY);

    element.change!.owner = {_account_id: 2 as AccountId};
    assert.strictEqual(element._chooseFocusTarget(), element.FocusTarget.BODY);

    element.change!.owner._account_id = 1 as AccountId;
    assert.strictEqual(
      element._chooseFocusTarget(),
      element.FocusTarget.REVIEWERS
    );

    element._reviewers = [];
    assert.strictEqual(
      element._chooseFocusTarget(),
      element.FocusTarget.REVIEWERS
    );

    element._reviewers.push({});
    assert.strictEqual(element._chooseFocusTarget(), element.FocusTarget.BODY);
  });

  test('only send labels that have changed', async () => {
    await flush();
    stubSaveReview((review: ReviewInput) => {
      assert.deepEqual(review?.labels, {
        'Code-Review': 0,
        Verified: -1,
      });
    });

    const promise = mockPromise();
    element.addEventListener('send', () => {
      promise.resolve();
    });
    // Without wrapping this test in flush(), the below two calls to
    // tap() cause a race in some situations in shadow DOM.
    // The send button can be tapped before the others, causing the test to
    // fail.
    const el = queryAndAssert(
      queryAndAssert(element, 'gr-label-scores'),
      'gr-label-score-row[name="Verified"]'
    ) as GrLabelScoreRow;
    el.setSelectedValue('-1');
    tap(queryAndAssert(element, '.send'));
    await promise;
  });

  test('moving from cc to reviewer', () => {
    flush();

    const reviewer1 = makeAccount();
    const reviewer2 = makeAccount();
    const reviewer3 = makeAccount();
    const cc1 = makeAccount();
    const cc2 = makeAccount();
    const cc3 = makeAccount();
    const cc4 = makeAccount();
    element._reviewers = [reviewer1, reviewer2, reviewer3];
    element._ccs = [cc1, cc2, cc3, cc4];
    element.push('_reviewers', cc1);
    flush();

    assert.deepEqual(element._reviewers, [
      reviewer1,
      reviewer2,
      reviewer3,
      cc1,
    ]);
    assert.deepEqual(element._ccs, [cc2, cc3, cc4]);

    element.push('_reviewers', cc4, cc3);
    flush();

    assert.deepEqual(element._reviewers, [
      reviewer1,
      reviewer2,
      reviewer3,
      cc1,
      cc4,
      cc3,
    ]);
    assert.deepEqual(element._ccs, [cc2]);
  });

  test('update attention section when reviewers and ccs change', () => {
    element._account = makeAccount();
    element._reviewers = [makeAccount(), makeAccount()];
    element._ccs = [makeAccount(), makeAccount()];
    element.draftCommentThreads = [];
    const modifyButton = queryAndAssert(element, '.edit-attention-button');
    tap(modifyButton);
    flush();

    // "Modify" button disabled, because "Send" button is disabled.
    assert.isFalse(element._attentionExpanded);
    element.draft = 'a test comment';
    tap(modifyButton);
    flush();
    assert.isTrue(element._attentionExpanded);

    let accountLabels = Array.from(
      queryAll(element, '.attention-detail gr-account-label')
    );
    assert.equal(accountLabels.length, 5);

    element.push('_reviewers', makeAccount());
    element.push('_ccs', makeAccount());
    flush();

    // The 'attention modified' section collapses and resets when reviewers or
    // ccs change.
    assert.isFalse(element._attentionExpanded);

    tap(queryAndAssert(element, '.edit-attention-button'));
    flush();

    assert.isTrue(element._attentionExpanded);
    accountLabels = Array.from(
      queryAll(element, '.attention-detail gr-account-label')
    );
    assert.equal(accountLabels.length, 7);

    element.pop('_reviewers');
    element.pop('_reviewers');
    element.pop('_ccs');
    element.pop('_ccs');

    tap(queryAndAssert(element, '.edit-attention-button'));
    flush();

    accountLabels = Array.from(
      queryAll(element, '.attention-detail gr-account-label')
    );
    assert.equal(accountLabels.length, 3);
  });

  test('moving from reviewer to cc', () => {
    flush();

    const reviewer1 = makeAccount();
    const reviewer2 = makeAccount();
    const reviewer3 = makeAccount();
    const cc1 = makeAccount();
    const cc2 = makeAccount();
    const cc3 = makeAccount();
    const cc4 = makeAccount();
    element._reviewers = [reviewer1, reviewer2, reviewer3];
    element._ccs = [cc1, cc2, cc3, cc4];
    element.push('_ccs', reviewer1);
    flush();

    assert.deepEqual(element._reviewers, [reviewer2, reviewer3]);
    assert.deepEqual(element._ccs, [cc1, cc2, cc3, cc4, reviewer1]);

    element.push('_ccs', reviewer3, reviewer2);
    flush();

    assert.deepEqual(element._reviewers, []);
    assert.deepEqual(element._ccs, [
      cc1,
      cc2,
      cc3,
      cc4,
      reviewer1,
      reviewer3,
      reviewer2,
    ]);
  });

  test('migrate reviewers between states', async () => {
    flush();
    const reviewers = queryAndAssert(element, '#reviewers') as GrAccountList;
    const ccs = queryAndAssert(element, '#ccs') as GrAccountList;
    const reviewer1 = makeAccount();
    const reviewer2 = makeAccount();
    const cc1 = makeAccount();
    const cc2 = makeAccount();
    const cc3 = makeAccount();
    element._reviewers = [reviewer1, reviewer2];
    element._ccs = [cc1, cc2, cc3];

    element.change!.reviewers = {
      [ReviewerState.CC]: [],
      [ReviewerState.REVIEWER]: [{_account_id: 33 as AccountId}],
    };

    const mutations: ReviewerInput[] = [];

    stubSaveReview((review: ReviewInput) => {
      mutations.push(...review!.reviewers!);
    });

    // Remove and add to other field.
    reviewers.dispatchEvent(
      new CustomEvent('remove', {
        detail: {account: reviewer1},
        composed: true,
        bubbles: true,
      })
    );
    ccs.$.entry.dispatchEvent(
      new CustomEvent('add', {
        detail: {value: {account: reviewer1}},
        composed: true,
        bubbles: true,
      })
    );
    ccs.dispatchEvent(
      new CustomEvent('remove', {
        detail: {account: cc1},
        composed: true,
        bubbles: true,
      })
    );
    ccs.dispatchEvent(
      new CustomEvent('remove', {
        detail: {account: cc3},
        composed: true,
        bubbles: true,
      })
    );
    reviewers.$.entry.dispatchEvent(
      new CustomEvent('add', {
        detail: {value: {account: cc1}},
        composed: true,
        bubbles: true,
      })
    );

    // Add to other field without removing from former field.
    // (Currently not possible in UI, but this is a good consistency check).
    reviewers.$.entry.dispatchEvent(
      new CustomEvent('add', {
        detail: {value: {account: cc2}},
        composed: true,
        bubbles: true,
      })
    );
    ccs.$.entry.dispatchEvent(
      new CustomEvent('add', {
        detail: {value: {account: reviewer2}},
        composed: true,
        bubbles: true,
      })
    );

    const mapReviewer = function (
      reviewer: AccountInfo,
      opt_state?: ReviewerState
    ) {
      const result: ReviewerInput = {
        reviewer: reviewer._account_id as AccountId,
      };
      if (opt_state) {
        result.state = opt_state;
      }
      return result;
    };

    // Send and purge and verify moves, delete cc3.
    await element.send(false, false);
    expect(mutations).to.have.lengthOf(5);
    expect(mutations[0]).to.deep.equal(
      mapReviewer(cc1, ReviewerState.REVIEWER)
    );
    expect(mutations[1]).to.deep.equal(
      mapReviewer(cc2, ReviewerState.REVIEWER)
    );
    expect(mutations[2]).to.deep.equal(
      mapReviewer(reviewer1, ReviewerState.CC)
    );
    expect(mutations[3]).to.deep.equal(
      mapReviewer(reviewer2, ReviewerState.CC)
    );

    // Only 1 account was initially part of the change
    expect(mutations[4]).to.deep.equal({
      reviewer: 33,
      state: ReviewerState.REMOVED,
    });
  });

  test('Ignore removal requests if being added as reviewer/CC', async () => {
    flush();
    const reviewers = queryAndAssert(element, '#reviewers') as GrAccountList;
    const ccs = queryAndAssert(element, '#ccs') as GrAccountList;
    const reviewer1 = makeAccount();
    element._reviewers = [reviewer1];
    element._ccs = [];

    element.change!.reviewers = {
      [ReviewerState.CC]: [],
      [ReviewerState.REVIEWER]: [{_account_id: reviewer1._account_id}],
    };

    const mutations: ReviewerInput[] = [];

    stubSaveReview((review: ReviewInput) => {
      mutations.push(...review!.reviewers!);
    });

    // Remove and add to other field.
    reviewers.dispatchEvent(
      new CustomEvent('remove', {
        detail: {account: reviewer1},
        composed: true,
        bubbles: true,
      })
    );
    ccs.$.entry.dispatchEvent(
      new CustomEvent('add', {
        detail: {value: {account: reviewer1}},
        composed: true,
        bubbles: true,
      })
    );

    await element.send(false, false);
    expect(mutations).to.have.lengthOf(1);
    // Only 1 account was initially part of the change
    expect(mutations[0]).to.deep.equal({
      reviewer: reviewer1._account_id,
      state: ReviewerState.CC,
    });
  });

  test('emits cancel on esc key', () => {
    const cancelHandler = sinon.spy();
    element.addEventListener('cancel', cancelHandler);
    pressAndReleaseKeyOn(element, 27, null, 'Escape');
    flush();

    assert.isTrue(cancelHandler.called);
  });

  test('should not send on enter key', () => {
    stubSaveReview(() => undefined);
    element.addEventListener('send', () => assert.fail('wrongly called'));
    pressAndReleaseKeyOn(element, 13, null, 'Enter');
  });

  test('emit send on ctrl+enter key', async () => {
    stubSaveReview(() => undefined);
    const promise = mockPromise();
    element.addEventListener('send', () => promise.resolve());
    pressAndReleaseKeyOn(element, 13, 'ctrl', 'Enter');
    await promise;
  });

  test('_computeMessagePlaceholder', () => {
    assert.equal(
      element._computeMessagePlaceholder(false),
      'Say something nice...'
    );
    assert.equal(
      element._computeMessagePlaceholder(true),
      'Add a note for your reviewers...'
    );
  });

  test('_computeSendButtonLabel', () => {
    assert.equal(element._computeSendButtonLabel(false), 'Send');
    assert.equal(
      element._computeSendButtonLabel(true),
      'Send and Start review'
    );
  });

  test('_handle400Error reviewers and CCs', async () => {
    const error1 = 'error 1';
    const error2 = 'error 2';
    const error3 = 'error 3';
    const text =
      ")]}'" +
      JSON.stringify({
        reviewers: {
          username1: {
            input: 'username1',
            error: error1,
          },
          username2: {
            input: 'username2',
            error: error2,
          },
          username3: {
            input: 'username3',
            error: error3,
          },
        },
      });
    const promise = mockPromise();
    const listener = (e: Event) => {
      (e as CustomEvent).detail.response.text().then((text: string) => {
        assert.equal(text, [error1, error2, error3].join(', '));
        promise.resolve();
      });
    };
    addListenerForTest(document, 'server-error', listener);
    element._handle400Error(cloneableResponse(400, text) as Response);
    await promise;
  });

  test('fires height change when the drafts comments load', async () => {
    // Flush DOM operations before binding to the autogrow event so we don't
    // catch the events fired from the initial layout.
    await flush();
    const autoGrowHandler = sinon.stub();
    element.addEventListener('autogrow', autoGrowHandler);
    element.draftCommentThreads = [];
    await flush();
    assert.isTrue(autoGrowHandler.called);
  });

  suite('start review and save buttons', () => {
    let sendStub: sinon.SinonStub;

    setup(async () => {
      sendStub = sinon.stub(element, 'send').callsFake(() => Promise.resolve());
      element.canBeStarted = true;
      // Flush to make both Start/Save buttons appear in DOM.
      await flush();
    });

    test('start review sets ready', async () => {
      tap(queryAndAssert(element, '.send'));
      await flush();
      assert.isTrue(sendStub.calledWith(true, true));
    });

    test("save review doesn't set ready", async () => {
      tap(queryAndAssert(element, '.save'));
      await flush();
      assert.isTrue(sendStub.calledWith(true, false));
    });
  });

  test('buttons disabled until all API calls are resolved', () => {
    stubSaveReview(() => {
      return {ready: true};
    });
    return element.send(true, true).then(() => {
      assert.isFalse(element.disabled);
    });
  });

  suite('error handling', () => {
    const expectedDraft = 'draft';
    const expectedError = new Error('test');

    setup(() => {
      element.draft = expectedDraft;
    });

    function assertDialogOpenAndEnabled() {
      assert.strictEqual(expectedDraft, element.draft);
      assert.isFalse(element.disabled);
    }

    test('error occurs in _saveReview', () => {
      stubSaveReview(() => {
        throw expectedError;
      });
      return element.send(true, true).catch(err => {
        assert.strictEqual(expectedError, err);
        assertDialogOpenAndEnabled();
      });
    });

    suite('pending diff drafts?', () => {
      test('yes', async () => {
        const promise = mockPromise();
        const refreshSpy = sinon.spy();
        element.addEventListener('comment-refresh', refreshSpy);
        stubRestApi('hasPendingDiffDrafts').returns(1);
        stubRestApi('awaitPendingDiffDrafts').returns(promise as Promise<void>);

        element.open();

        assert.isFalse(refreshSpy.called);
        assert.isTrue(element._savingComments);

        promise.resolve();
        await flush();

        assert.isTrue(refreshSpy.called);
        assert.isFalse(element._savingComments);
      });

      test('no', () => {
        stubRestApi('hasPendingDiffDrafts').returns(0);
        element.open();
        assert.isFalse(element._savingComments);
      });
    });
  });

  test('_computeSendButtonDisabled_canBeStarted', () => {
    // Mock canBeStarted
    assert.isFalse(
      element._computeSendButtonDisabled(
        /* canBeStarted= */ true,
        /* draftCommentThreads= */ [],
        /* text= */ '',
        /* reviewersMutated= */ false,
        /* labelsChanged= */ false,
        /* includeComments= */ false,
        /* disabled= */ false,
        /* commentEditing= */ false,
        /* change= */ element.change,
        /* account= */ makeAccount()
      )
    );
  });

  test('_computeSendButtonDisabled_allFalse', () => {
    // Mock everything false
    assert.isTrue(
      element._computeSendButtonDisabled(
        /* canBeStarted= */ false,
        /* draftCommentThreads= */ [],
        /* text= */ '',
        /* reviewersMutated= */ false,
        /* labelsChanged= */ false,
        /* includeComments= */ false,
        /* disabled= */ false,
        /* commentEditing= */ false,
        /* change= */ element.change,
        /* account= */ makeAccount()
      )
    );
  });

  test('_computeSendButtonDisabled_draftCommentsSend', () => {
    // Mock nonempty comment draft array, with sending comments.
    assert.isFalse(
      element._computeSendButtonDisabled(
        /* canBeStarted= */ false,
        /* draftCommentThreads= */ [
          {...createCommentThread([{__draft: true}])},
        ],
        /* text= */ '',
        /* reviewersMutated= */ false,
        /* labelsChanged= */ false,
        /* includeComments= */ true,
        /* disabled= */ false,
        /* commentEditing= */ false,
        /* change= */ element.change,
        /* account= */ makeAccount()
      )
    );
  });

  test('_computeSendButtonDisabled_draftCommentsDoNotSend', () => {
    // Mock nonempty comment draft array, without sending comments.
    assert.isTrue(
      element._computeSendButtonDisabled(
        /* canBeStarted= */ false,
        /* draftCommentThreads= */ [
          {...createCommentThread([{__draft: true}])},
        ],
        /* text= */ '',
        /* reviewersMutated= */ false,
        /* labelsChanged= */ false,
        /* includeComments= */ false,
        /* disabled= */ false,
        /* commentEditing= */ false,
        /* change= */ element.change,
        /* account= */ makeAccount()
      )
    );
  });

  test('_computeSendButtonDisabled_changeMessage', () => {
    // Mock nonempty change message.
    assert.isFalse(
      element._computeSendButtonDisabled(
        /* canBeStarted= */ false,
        /* draftCommentThreads= */ [{...createCommentThread([{}])}],
        /* text= */ 'test',
        /* reviewersMutated= */ false,
        /* labelsChanged= */ false,
        /* includeComments= */ false,
        /* disabled= */ false,
        /* commentEditing= */ false,
        /* change= */ element.change,
        /* account= */ makeAccount()
      )
    );
  });

  test('_computeSendButtonDisabled_reviewersChanged', () => {
    // Mock reviewers mutated.
    assert.isFalse(
      element._computeSendButtonDisabled(
        /* canBeStarted= */ false,
        /* draftCommentThreads= */ [{...createCommentThread([{}])}],
        /* text= */ '',
        /* reviewersMutated= */ true,
        /* labelsChanged= */ false,
        /* includeComments= */ false,
        /* disabled= */ false,
        /* commentEditing= */ false,
        /* change= */ element.change,
        /* account= */ makeAccount()
      )
    );
  });

  test('_computeSendButtonDisabled_labelsChanged', () => {
    // Mock labels changed.
    assert.isFalse(
      element._computeSendButtonDisabled(
        /* canBeStarted= */ false,
        /* draftCommentThreads= */ [{...createCommentThread([{}])}],
        /* text= */ '',
        /* reviewersMutated= */ false,
        /* labelsChanged= */ true,
        /* includeComments= */ false,
        /* disabled= */ false,
        /* commentEditing= */ false,
        /* change= */ element.change,
        /* account= */ makeAccount()
      )
    );
  });

  test('_computeSendButtonDisabled_dialogDisabled', () => {
    // Whole dialog is disabled.
    assert.isTrue(
      element._computeSendButtonDisabled(
        /* canBeStarted= */ false,
        /* draftCommentThreads= */ [{...createCommentThread([{}])}],
        /* text= */ '',
        /* reviewersMutated= */ false,
        /* labelsChanged= */ true,
        /* includeComments= */ false,
        /* disabled= */ true,
        /* commentEditing= */ false,
        /* change= */ element.change,
        /* account= */ makeAccount()
      )
    );
  });

  test('_computeSendButtonDisabled_existingVote', async () => {
    const account = createAccountWithId();
    (
      element.change!.labels![StandardLabels.CODE_REVIEW]! as DetailedLabelInfo
    ).all = [account];
    await flush();

    // User has already voted.
    assert.isFalse(
      element._computeSendButtonDisabled(
        /* canBeStarted= */ false,
        /* draftCommentThreads= */ [{...createCommentThread([{}])}],
        /* text= */ '',
        /* reviewersMutated= */ false,
        /* labelsChanged= */ false,
        /* includeComments= */ false,
        /* disabled= */ false,
        /* commentEditing= */ false,
        /* change= */ element.change,
        /* account= */ account
      )
    );
  });

  test('_submit blocked when no mutations exist', async () => {
    const sendStub = sinon.stub(element, 'send').returns(Promise.resolve());
    element.draftCommentThreads = [];
    await flush();

    tap(queryAndAssert(element, 'gr-button.send'));
    assert.isFalse(sendStub.called);

    element.draftCommentThreads = [
      {
        ...createCommentThread([
          {__draft: true, path: 'test', line: 1, patch_set: 1 as PatchSetNum},
        ]),
      },
    ];
    await flush();

    tap(queryAndAssert(element, 'gr-button.send'));
    assert.isTrue(sendStub.called);
  });

  test('getFocusStops', async () => {
    // Setting draftCommentThreads to an empty object causes _sendDisabled to be
    // computed to false.
    element.draftCommentThreads = [];
    await flush();

    assert.equal(
      element.getFocusStops().end,
      queryAndAssert(element, '#cancelButton')
    );
    element.draftCommentThreads = [
      {
        ...createCommentThread([
          {__draft: true, path: 'test', line: 1, patch_set: 1 as PatchSetNum},
        ]),
      },
    ];
    await flush();

    assert.equal(
      element.getFocusStops().end,
      queryAndAssert(element, '#sendButton')
    );
  });

  test('setPluginMessage', () => {
    element.setPluginMessage('foo');
    assert.equal(queryAndAssert(element, '#pluginMessage').textContent, 'foo');
  });
});
