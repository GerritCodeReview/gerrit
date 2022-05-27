/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../../test/common-test-setup-karma';
import './gr-reply-dialog';
import {
  addListenerForTest,
  isVisible,
  mockPromise,
  queryAll,
  queryAndAssert,
  stubRestApi,
  stubStorage,
} from '../../../test/test-utils';
import {
  ChangeStatus,
  ReviewerState,
  SpecialFilePath,
} from '../../../constants/constants';
import {JSON_PREFIX} from '../../shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {StandardLabels} from '../../../utils/label-util';
import {
  createAccountWithId,
  createChange,
  createComment,
  createCommentThread,
  createDraft,
  createRevision,
} from '../../../test/test-data-generators';
import {
  pressAndReleaseKeyOn,
  tap,
} from '@polymer/iron-test-helpers/mock-interactions';
import {FocusTarget, GrReplyDialog} from './gr-reply-dialog';
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
import {GrAccountList} from '../../shared/gr-account-list/gr-account-list';
import {GrLabelScoreRow} from '../gr-label-score-row/gr-label-score-row';
import {GrLabelScores} from '../gr-label-scores/gr-label-scores';
import {GrThreadList} from '../gr-thread-list/gr-thread-list';
import {GrAutocomplete} from '../../shared/gr-autocomplete/gr-autocomplete';
import {fixture, html} from '@open-wc/testing-helpers';
import {accountKey} from '../../../utils/account-util';
import {GrButton} from '../../shared/gr-button/gr-button';

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

    element = await fixture<GrReplyDialog>(html`
      <gr-reply-dialog></gr-reply-dialog>
    `);

    element.change = {
      ...createChange(),
      _number: changeNum,
      owner: {
        _account_id: 999 as AccountId,
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
            '-2': 'This shall not be submitted',
            '-1': 'I would prefer this is not submitted as is',
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

    await element.updateComplete;
  });

  function stubSaveReview(
    jsonResponseProducer: (input: ReviewInput) => ReviewResult | void
  ) {
    return sinon.stub(element, 'saveReview').callsFake(
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

  test('renders', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `
      <div tabindex="-1">
        <section class="peopleContainer">
          <gr-endpoint-decorator name="reply-reviewers">
            <gr-endpoint-param name="change"> </gr-endpoint-param>
            <gr-endpoint-param name="reviewers"> </gr-endpoint-param>
            <div class="peopleList">
              <div class="peopleListLabel">Reviewers</div>
              <gr-account-list id="reviewers"> </gr-account-list>
              <gr-endpoint-slot name="right"> </gr-endpoint-slot>
            </div>
            <gr-endpoint-slot name="below"> </gr-endpoint-slot>
          </gr-endpoint-decorator>
          <div class="peopleList">
            <div class="peopleListLabel">CC</div>
            <gr-account-list allow-any-input="" id="ccs"> </gr-account-list>
          </div>
          <gr-overlay
            aria-hidden="true"
            id="reviewerConfirmationOverlay"
            style="outline: none; display: none;"
          >
            <div class="reviewerConfirmation">
              Group
              <span class="groupName"> </span>
              has
              <span class="groupSize"> </span>
              members.
              <br />
              Are you sure you want to add them all?
            </div>
            <div class="reviewerConfirmationButtons">
              <gr-button aria-disabled="false" role="button" tabindex="0">
                Yes
              </gr-button>
              <gr-button aria-disabled="false" role="button" tabindex="0">
                No
              </gr-button>
            </div>
          </gr-overlay>
        </section>
        <section class="labelsContainer">
          <gr-endpoint-decorator name="reply-label-scores">
            <gr-label-scores id="labelScores"> </gr-label-scores>
            <gr-endpoint-param name="change"> </gr-endpoint-param>
          </gr-endpoint-decorator>
          <div id="pluginMessage"></div>
        </section>
        <section class="newReplyDialog textareaContainer">
          <div class="patchsetLevelContainer resolved">
            <gr-endpoint-decorator name="reply-text">
              <gr-textarea
                class="message monospace newReplyDialog"
                id="textarea"
                monospace=""
              >
              </gr-textarea>
              <gr-endpoint-param name="change"> </gr-endpoint-param>
            </gr-endpoint-decorator>
            <div class="labelContainer">
              <label>
                <input
                  checked=""
                  id="resolvedPatchsetLevelCommentCheckbox"
                  type="checkbox"
                />
                Resolved
              </label>
              <label class="preview-formatting">
                <input type="checkbox" />
                Preview formatting
              </label>
            </div>
          </div>
        </section>
        <div class="newReplyDialog stickyBottom">
          <gr-endpoint-decorator name="reply-bottom">
            <gr-endpoint-param name="change"> </gr-endpoint-param>
            <section class="attention">
              <div class="attentionSummary">
                <div>
                  <span> No changes to the attention set. </span>
                  <gr-tooltip-content
                    has-tooltip=""
                    title="Edit attention set changes"
                  >
                    <gr-button
                      aria-disabled="false"
                      class="edit-attention-button"
                      data-action-key="edit"
                      data-action-type="change"
                      data-label="Edit"
                      link=""
                      position-below=""
                      role="button"
                      tabindex="0"
                    >
                      <iron-icon icon="gr-icons:edit"> </iron-icon>
                      Modify
                    </gr-button>
                  </gr-tooltip-content>
                </div>
                <div>
                  <a
                    href="https://gerrit-review.googlesource.com/Documentation/user-attention-set.html"
                    target="_blank"
                  >
                    <iron-icon
                      icon="gr-icons:help-outline"
                      title="read documentation"
                    >
                    </iron-icon>
                  </a>
                </div>
              </div>
            </section>
            <gr-endpoint-slot name="above-actions"> </gr-endpoint-slot>
            <section class="actions">
              <div class="left"></div>
              <div class="right">
                <gr-button
                  aria-disabled="false"
                  class="action cancel"
                  id="cancelButton"
                  link=""
                  role="button"
                  tabindex="0"
                >
                  Cancel
                </gr-button>
                <gr-tooltip-content has-tooltip="" title="Send reply">
                  <gr-button
                    aria-disabled="false"
                    class="action send"
                    id="sendButton"
                    primary=""
                    role="button"
                    tabindex="0"
                  >
                    Send
                  </gr-button>
                </gr-tooltip-content>
              </div>
            </section>
          </gr-endpoint-decorator>
        </div>
      </div>
    `);
  });

  test('default to publishing draft comments with reply', async () => {
    // Async tick is needed because iron-selector content is distributed and
    // distributed content requires an observer to be set up.
    await element.updateComplete;
    element.draft = 'I wholeheartedly disapprove';
    element.draftCommentThreads = [createCommentThread([createComment()])];

    element.includeComments = true;
    const saveReviewPromise = interceptSaveReview();

    // This is needed on non-Blink engines most likely due to the ways in
    // which the dom-repeat elements are stamped.
    await element.updateComplete;
    tap(queryAndAssert(element, '.send'));
    await element.updateComplete;

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
      add_to_attention_set: [
        {reason: '<GERRIT_ACCOUNT_1> replied on the change', user: 999},
      ],
      remove_from_attention_set: [],
      ignore_automatic_attention_set_rules: true,
    });
    assert.isFalse(
      queryAndAssert<GrThreadList>(element, '#commentList').hidden
    );
  });

  test('modified attention set', async () => {
    await element.updateComplete;
    element.account = {_account_id: 123 as AccountId};
    element.newAttentionSet = new Set([314 as AccountId]);
    const saveReviewPromise = interceptSaveReview();
    const modifyButton = queryAndAssert(element, '.edit-attention-button');
    tap(modifyButton);
    await element.updateComplete;

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
    await element.updateComplete;
    element.account = {};
    element.newAttentionSet = new Set([314 as AccountId]);
    const saveReviewPromise = interceptSaveReview();
    const modifyButton = queryAndAssert(element, '.edit-attention-button');
    tap(modifyButton);
    await element.updateComplete;

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
    element.newAttentionSet = new Set();
    await element.updateComplete;
  });

  async function checkComputeAttention(
    element: GrReplyDialog,
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
    element.account = {_account_id: userId};
    element.reviewers =
      reviewerIds?.map(id => {
        return {_account_id: id};
      }) ?? [];
    let draftThreads: CommentThread[] = [];
    if (hasDraft) {
      draftThreads = [
        {
          ...createCommentThread([{...createDraft(), unresolved: true}]),
        },
      ];
    }
    replyToIds?.forEach(id =>
      draftThreads[0].comments.push({
        ...createComment(),
        author: {_account_id: id},
      })
    );
    const change = {
      ...createChange(),
      owner: {_account_id: ownerId},
      status,
      reviewers: {
        [ReviewerState.REVIEWER]: element.reviewers,
      },
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
    element.ccs = [];
    element.draftCommentThreads = draftThreads;
    element.includeComments = includeComments;

    await element.updateComplete;

    element.computeNewAttention();
    assert.sameMembers([...element.newAttentionSet], expectedIds!);
  }

  test('computeNewAttention NEW', async () => {
    await checkComputeAttention(
      element,
      ChangeStatus.NEW,
      1 as AccountId,
      [],
      999 as AccountId,
      [],
      [],
      [999 as AccountId]
    );
    await checkComputeAttention(
      element,
      ChangeStatus.NEW,
      1 as AccountId,
      [],
      999 as AccountId,
      [1 as AccountId],
      [],
      [999 as AccountId]
    );
    await checkComputeAttention(
      element,
      ChangeStatus.NEW,
      1 as AccountId,
      [22 as AccountId],
      999 as AccountId,
      [],
      [],
      [999 as AccountId]
    );
    await checkComputeAttention(
      element,
      ChangeStatus.NEW,
      1 as AccountId,
      [22 as AccountId],
      999 as AccountId,
      [22 as AccountId],
      [],
      [22 as AccountId, 999 as AccountId]
    );
    await checkComputeAttention(
      element,
      ChangeStatus.NEW,
      1 as AccountId,
      [22 as AccountId],
      999 as AccountId,
      [],
      [22 as AccountId],
      [22 as AccountId, 999 as AccountId]
    );
    await checkComputeAttention(
      element,
      ChangeStatus.NEW,
      1 as AccountId,
      [22 as AccountId, 33 as AccountId],
      999 as AccountId,
      [33 as AccountId],
      [22 as AccountId],
      [22 as AccountId, 33 as AccountId, 999 as AccountId]
    );
    // If the owner replies, then do not add them.
    await checkComputeAttention(
      element,
      ChangeStatus.NEW,
      1 as AccountId,
      [],
      1 as AccountId,
      [],
      [],
      []
    );
    await checkComputeAttention(
      element,
      ChangeStatus.NEW,
      1 as AccountId,
      [],
      1 as AccountId,
      [1 as AccountId],
      [],
      []
    );
    await checkComputeAttention(
      element,
      ChangeStatus.NEW,
      1 as AccountId,
      [22 as AccountId],
      1 as AccountId,
      [],
      [],
      []
    );

    await checkComputeAttention(
      element,
      ChangeStatus.NEW,
      1 as AccountId,
      [22 as AccountId],
      1 as AccountId,
      [],
      [22 as AccountId],
      [22 as AccountId]
    );
    await checkComputeAttention(
      element,
      ChangeStatus.NEW,
      1 as AccountId,
      [22 as AccountId, 33 as AccountId],
      1 as AccountId,
      [33 as AccountId],
      [22 as AccountId],
      [22 as AccountId, 33 as AccountId]
    );
    await checkComputeAttention(
      element,
      ChangeStatus.NEW,
      1 as AccountId,
      [22 as AccountId, 33 as AccountId],
      1 as AccountId,
      [],
      [22 as AccountId],
      [22 as AccountId]
    );
    await checkComputeAttention(
      element,
      ChangeStatus.NEW,
      1 as AccountId,
      [22 as AccountId, 33 as AccountId],
      1 as AccountId,
      [],
      [22 as AccountId, 33 as AccountId],
      [22 as AccountId, 33 as AccountId]
    );
    await checkComputeAttention(
      element,
      ChangeStatus.NEW,
      1 as AccountId,
      [22 as AccountId, 33 as AccountId],
      1 as AccountId,
      [22 as AccountId, 33 as AccountId],
      [],
      [22 as AccountId, 33 as AccountId]
    );
    // with uploader
    await checkComputeAttention(
      element,
      ChangeStatus.NEW,
      1 as AccountId,
      [],
      1 as AccountId,
      [],
      [2 as AccountId],
      [2 as AccountId],
      2 as AccountId
    );
    await checkComputeAttention(
      element,
      ChangeStatus.NEW,
      1 as AccountId,
      [],
      1 as AccountId,
      [2 as AccountId],
      [],
      [2 as AccountId],
      2 as AccountId
    );
    await checkComputeAttention(
      element,
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

  test('computeNewAttention MERGED', async () => {
    await checkComputeAttention(
      element,
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
    await checkComputeAttention(
      element,
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
    await checkComputeAttention(
      element,
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
    await checkComputeAttention(
      element,
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
    await checkComputeAttention(
      element,
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
    await checkComputeAttention(
      element,
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
    await checkComputeAttention(
      element,
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
    await checkComputeAttention(
      element,
      ChangeStatus.MERGED,
      1 as AccountId,
      [22 as AccountId],
      999 as AccountId,
      [],
      [22 as AccountId],
      []
    );
    await checkComputeAttention(
      element,
      ChangeStatus.MERGED,
      1 as AccountId,
      [22 as AccountId, 33 as AccountId],
      999 as AccountId,
      [33 as AccountId],
      [22 as AccountId],
      [33 as AccountId]
    );
    await checkComputeAttention(
      element,
      ChangeStatus.MERGED,
      1 as AccountId,
      [],
      1 as AccountId,
      [],
      [],
      []
    );
    await checkComputeAttention(
      element,
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
    await checkComputeAttention(
      element,
      ChangeStatus.MERGED,
      1 as AccountId,
      [],
      1 as AccountId,
      [1 as AccountId],
      [],
      []
    );
    await checkComputeAttention(
      element,
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
    await checkComputeAttention(
      element,
      ChangeStatus.MERGED,
      1 as AccountId,
      [22 as AccountId],
      1 as AccountId,
      [],
      [],
      []
    );
    await checkComputeAttention(
      element,
      ChangeStatus.MERGED,
      1 as AccountId,
      [22 as AccountId],
      1 as AccountId,
      [],
      [22 as AccountId],
      []
    );
    await checkComputeAttention(
      element,
      ChangeStatus.MERGED,
      1 as AccountId,
      [22 as AccountId, 33 as AccountId],
      1 as AccountId,
      [33 as AccountId],
      [22 as AccountId],
      [33 as AccountId]
    );
    await checkComputeAttention(
      element,
      ChangeStatus.MERGED,
      1 as AccountId,
      [22 as AccountId, 33 as AccountId],
      1 as AccountId,
      [],
      [22 as AccountId],
      []
    );
    await checkComputeAttention(
      element,
      ChangeStatus.MERGED,
      1 as AccountId,
      [22 as AccountId, 33 as AccountId],
      1 as AccountId,
      [],
      [22 as AccountId, 33 as AccountId],
      []
    );
    await checkComputeAttention(
      element,
      ChangeStatus.MERGED,
      1 as AccountId,
      [22 as AccountId, 33 as AccountId],
      1 as AccountId,
      [22 as AccountId, 33 as AccountId],
      [],
      [22 as AccountId, 33 as AccountId]
    );
  });

  test('computeNewAttention when adding reviewers', async () => {
    element.account = {_account_id: 1 as AccountId};
    element.change = {
      ...createChange(),
      owner: {_account_id: 5 as AccountId},
      status: ChangeStatus.NEW,
      attention_set: {},
    };
    // let rebuildReviewers triggered by change update finish running
    await element.updateComplete;

    element.reviewers = [
      {_account_id: 1 as AccountId, _pendingAdd: true},
      {_account_id: 2 as AccountId, _pendingAdd: true},
    ];
    element.ccs = [];
    element.draftCommentThreads = [];
    element.includeComments = true;
    element.canBeStarted = true;
    await element.updateComplete;
    assert.sameMembers([...element.newAttentionSet], [1, 2]);

    // If the user votes on the change, then they should not be added to the
    // attention set, even if they have just added themselves as reviewer.
    // But voting should also add the owner (5).
    element.labelsChanged = true;
    await element.updateComplete;
    assert.sameMembers([...element.newAttentionSet], [2, 5]);
  });

  test('computeNewAttention when sending wip change for review', async () => {
    element.change = {
      ...createChange(),
      owner: {_account_id: 1 as AccountId},
      status: ChangeStatus.NEW,
      attention_set: {},
    };
    // let rebuildReviewers triggered by change update finish running
    await element.updateComplete;

    element.reviewers = [
      {...createAccountWithId(2)},
      {...createAccountWithId(3)},
    ];

    element.ccs = [];
    element.draftCommentThreads = [];
    element.includeComments = false;
    element.account = {_account_id: 1 as AccountId};

    await element.updateComplete;

    // For an active change there is no reason to add anyone to the set.
    element.computeNewAttention();
    assert.sameMembers([...element.newAttentionSet], []);

    // If the change is "work in progress" and the owner sends a reply, then
    // add all reviewers.
    element.canBeStarted = true;
    element.computeNewAttention();
    await element.updateComplete;
    assert.sameMembers([...element.newAttentionSet], [2, 3]);

    // ... but not when someone else replies.
    element.account = {_account_id: 4 as AccountId};
    element.computeNewAttention();
    assert.sameMembers([...element.newAttentionSet], []);
  });

  test('computeNewAttentionAccounts', () => {
    element.reviewers = [
      {_account_id: 123 as AccountId, display_name: 'Ernie'},
      {_account_id: 321 as AccountId, display_name: 'Bert'},
    ];
    element.ccs = [{_account_id: 7 as AccountId, display_name: 'Elmo'}];
    const compute = (currentAtt: AccountId[], newAtt: AccountId[]) => {
      element.currentAttentionSet = new Set(currentAtt);
      element.newAttentionSet = new Set(newAtt);
      return element.computeNewAttentionAccounts().map(a => a?._account_id);
    };

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

  test('computeCommentAccounts', () => {
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
            '-2': 'This shall not be submitted',
            '-1': 'I would prefer this is not submitted as is',
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
            ...createComment(),
            id: '1' as UrlEncodedCommentId,
            author: {_account_id: 1 as AccountId},
            unresolved: false,
          },
          {
            ...createComment(),
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
            ...createComment(),
            id: '3' as UrlEncodedCommentId,
            author: {_account_id: 3 as AccountId},
            unresolved: false,
          },
          {
            ...createComment(),
            id: '4' as UrlEncodedCommentId,
            in_reply_to: '3' as UrlEncodedCommentId,
            author: {_account_id: 4 as AccountId},
            unresolved: false,
          },
        ]),
      },
    ];
    const actualAccounts = [...element.computeCommentAccounts(threads)];
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
    await element.updateComplete;
    element.draft = 'I wholeheartedly disapprove';
    element.draftCommentThreads = [createCommentThread([createComment()])];

    const saveReviewPromise = interceptSaveReview();

    // This is needed on non-Blink engines most likely due to the ways in
    // which the dom-repeat elements are stamped.
    await element.updateComplete;
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
      add_to_attention_set: [
        {reason: '<GERRIT_ACCOUNT_1> replied on the change', user: 999},
      ],
      remove_from_attention_set: [],
      ignore_automatic_attention_set_rules: true,
    });
  });

  test('label picker', async () => {
    element.draft = 'I wholeheartedly disapprove';
    element.draftCommentThreads = [createCommentThread([createComment()])];

    const saveReviewPromise = interceptSaveReview();

    sinon.stub(element.getLabelScores(), 'getLabelValues').callsFake(() => {
      return {
        'Code-Review': -1,
        Verified: -1,
      };
    });

    // This is needed on non-Blink engines most likely due to the ways in
    // which the dom-repeat elements are stamped.
    await element.updateComplete;
    tap(queryAndAssert(element, '.send'));
    assert.isTrue(element.disabled);

    const review = await saveReviewPromise;
    await element.updateComplete;
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
      add_to_attention_set: [
        {user: 999, reason: '<GERRIT_ACCOUNT_1> replied on the change'},
      ],
      remove_from_attention_set: [],
      ignore_automatic_attention_set_rules: true,
    });
  });

  test('keep draft comments with reply', async () => {
    element.draftCommentThreads = [createCommentThread([createComment()])];
    await element.updateComplete;

    tap(queryAndAssert(element, '#includeComments'));
    assert.equal(element.includeComments, false);

    // Async tick is needed because iron-selector content is distributed and
    // distributed content requires an observer to be set up.
    await element.updateComplete;
    element.draft = 'I wholeheartedly disapprove';

    const saveReviewPromise = interceptSaveReview();

    // This is needed on non-Blink engines most likely due to the ways in
    // which the dom-repeat elements are stamped.
    await element.updateComplete;
    tap(queryAndAssert(element, '.send'));

    const review = await saveReviewPromise;
    await element.updateComplete;
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
      add_to_attention_set: [
        {reason: '<GERRIT_ACCOUNT_1> replied on the change', user: 999},
      ],
      remove_from_attention_set: [],
      ignore_automatic_attention_set_rules: true,
    });
  });

  test('getlabelValue returns value', async () => {
    const el = queryAndAssert<GrLabelScoreRow>(
      queryAndAssert(element, 'gr-label-scores'),
      'gr-label-score-row[name="Verified"]'
    );
    el.setSelectedValue('-1');
    assert.equal('-1', element.getLabelValue('Verified'));
  });

  test('getlabelValue when no score is selected', async () => {
    const el = queryAndAssert<GrLabelScoreRow>(
      queryAndAssert(element, 'gr-label-scores'),
      'gr-label-score-row[name="Code-Review"]'
    );
    el.setSelectedValue('-1');
    assert.strictEqual(element.getLabelValue('Verified'), ' 0');
  });

  test('setlabelValue', async () => {
    element.account = {_account_id: 1 as AccountId};
    await element.updateComplete;
    const label = 'Verified';
    const value = '+1';
    element.setLabelValue(label, value);
    await element.updateComplete;

    const labels = queryAndAssert<GrLabelScores>(
      element,
      '#labelScores'
    ).getLabelValues();
    assert.deepEqual(labels, {
      'Code-Review': 0,
      Verified: 1,
    });
  });

  function getActiveElement() {
    return document.activeElement;
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

    element.ccPendingConfirmation = null;
    element.reviewerPendingConfirmation = null;
    await element.updateComplete;
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
      element.ccPendingConfirmation = {
        group,
        confirm: false,
        count: 1,
      };
    } else {
      element.reviewerPendingConfirmation = {
        group,
        confirm: false,
        count: 1,
      };
    }
    await element.updateComplete;

    if (cc) {
      assert.deepEqual(
        element.ccPendingConfirmation,
        element.pendingConfirmationDetails
      );
    } else {
      assert.deepEqual(
        element.reviewerPendingConfirmation,
        element.pendingConfirmationDetails
      );
    }

    await observer;
    assert.isTrue(
      isVisible(queryAndAssert(element, 'reviewerConfirmationOverlay'))
    );
    observer = overlayObserver('closed');
    const expected = 'Group name has 10 members';
    assert.notEqual(
      queryAndAssert<HTMLElement>(
        element,
        'reviewerConfirmationOverlay'
      ).innerText.indexOf(expected),
      -1
    );
    tap(noButton); // close the overlay

    await observer;
    assert.isFalse(
      isVisible(queryAndAssert(element, 'reviewerConfirmationOverlay'))
    );

    // We should be focused on account entry input.
    const reviewersEntry = queryAndAssert<GrAccountList>(element, '#reviewers');
    assert.isTrue(
      isFocusInsideElement(
        queryAndAssert<GrAutocomplete>(reviewersEntry.entry, '#input').input!
      )
    );

    // No reviewer/CC should have been added.
    assert.equal(
      queryAndAssert<GrAccountList>(element, '#ccs').additions().length,
      0
    );
    assert.equal(
      queryAndAssert<GrAccountList>(element, '#reviewers').additions().length,
      0
    );

    // Reopen confirmation dialog.
    observer = overlayObserver('opened');
    if (cc) {
      element.ccPendingConfirmation = {
        group,
        confirm: false,
        count: 1,
      };
    } else {
      element.reviewerPendingConfirmation = {
        group,
        confirm: false,
        count: 1,
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
      ? queryAndAssert<GrAccountList>(element, '#ccs').additions()
      : queryAndAssert<GrAccountList>(element, '#reviewers').additions();
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
      const ccsEntry = queryAndAssert<GrAccountList>(element, '#ccs');
      assert.isTrue(
        isFocusInsideElement(
          queryAndAssert<GrAutocomplete>(ccsEntry.entry, '#input').input!
        )
      );
    } else {
      const reviewersEntry = queryAndAssert<GrAccountList>(
        element,
        '#reviewers'
      );
      assert.isTrue(
        isFocusInsideElement(
          queryAndAssert<GrAutocomplete>(reviewersEntry.entry, '#input').input!
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

  test('getStorageLocation', () => {
    const actual = element.getStorageLocation();
    assert.equal(actual.changeNum, changeNum);
    assert.equal(actual.patchNum, '@change');
    assert.equal(actual.path, '@change');
  });

  test('reviewersMutated when account-text-change is fired from ccs', () => {
    assert.isFalse(element.reviewersMutated);
    assert.isTrue(queryAndAssert<GrAccountList>(element, '#ccs').allowAnyInput);
    assert.isFalse(
      queryAndAssert<GrAccountList>(element, '#reviewers').allowAnyInput
    );
    queryAndAssert(element, '#ccs').dispatchEvent(
      new CustomEvent('account-text-changed', {bubbles: true, composed: true})
    );
    assert.isTrue(element.reviewersMutated);
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
    element.open(FocusTarget.ANY, quote);
    assert.isFalse(getDraftCommentStub.called);
    assert.equal(element.draft, quote);
  });

  test('updates stored draft on edits', async () => {
    const clock = sinon.useFakeTimers();

    const firstEdit = 'hello';
    const location = element.getStorageLocation();

    element.draft = firstEdit;
    clock.tick(1000);
    await element.updateComplete;
    await element.storeTask?.flush();

    assert.isTrue(setDraftCommentStub.calledWith(location, firstEdit));

    element.draft = '';
    clock.tick(1000);
    await element.updateComplete;
    await element.storeTask?.flush();

    assert.isTrue(eraseDraftCommentStub.calledWith(location));
  });

  test('400 converts to human-readable server-error', async () => {
    stubRestApi('saveChangeReview').callsFake(
      (_changeNum, _patchNum, _review, errFn) => {
        // eslint-disable-next-line @typescript-eslint/no-unnecessary-type-assertion
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

    await element.updateComplete;
    element.send(false, false);
    await promise;
  });

  test('non-json 400 is treated as a normal server-error', async () => {
    stubRestApi('saveChangeReview').callsFake(
      (_changeNum, _patchNum, _review, errFn) => {
        // eslint-disable-next-line @typescript-eslint/no-unnecessary-type-assertion
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
    await element.updateComplete;
    element.send(false, false);
    await promise;
  });

  test('filterReviewerSuggestion', () => {
    const owner = makeAccount();
    const reviewer1 = makeAccount();
    const reviewer2 = makeGroup();
    const cc1 = makeAccount();
    const cc2 = makeGroup();
    let filter = element.filterReviewerSuggestionGenerator(false);

    element.owner = owner;
    element.reviewers = [reviewer1, reviewer2];
    element.ccs = [cc1, cc2];

    assert.isTrue(filter({account: makeAccount()} as Suggestion));
    assert.isTrue(filter({group: makeGroup()} as Suggestion));

    // Owner should be excluded.
    assert.isFalse(filter({account: owner} as Suggestion));

    // Existing and pending reviewers should be excluded when isCC = false.
    assert.isFalse(filter({account: reviewer1} as Suggestion));
    assert.isFalse(filter({group: reviewer2} as Suggestion));

    filter = element.filterReviewerSuggestionGenerator(true);

    // Existing and pending CCs should be excluded when isCC = true;.
    assert.isFalse(filter({account: cc1} as Suggestion));
    assert.isFalse(filter({group: cc2} as Suggestion));
  });

  test('focusOn', async () => {
    await element.updateComplete;
    const clock = sinon.useFakeTimers();
    const chooseFocusTargetSpy = sinon.spy(element, 'chooseFocusTarget');
    element.focusOn();
    // element.focus() is called after a setTimeout(). The focusOn() method
    // does not trigger any changes in the element hence element.updateComplete
    // resolves immediately and cannot be used here, hence tick the clock here
    // explicitly instead
    clock.tick(1);
    assert.equal(chooseFocusTargetSpy.callCount, 1);
    assert.equal(element?.shadowRoot?.activeElement?.tagName, 'GR-TEXTAREA');
    assert.equal(element?.shadowRoot?.activeElement?.id, 'textarea');

    element.focusOn(element.FocusTarget.ANY);
    clock.tick(1);
    assert.equal(chooseFocusTargetSpy.callCount, 2);
    assert.equal(element?.shadowRoot?.activeElement?.tagName, 'GR-TEXTAREA');
    assert.equal(element?.shadowRoot?.activeElement?.id, 'textarea');

    element.focusOn(element.FocusTarget.BODY);
    clock.tick(1);
    assert.equal(chooseFocusTargetSpy.callCount, 2);
    assert.equal(element?.shadowRoot?.activeElement?.tagName, 'GR-TEXTAREA');
    assert.equal(element?.shadowRoot?.activeElement?.id, 'textarea');

    element.focusOn(element.FocusTarget.REVIEWERS);
    clock.tick(1);
    assert.equal(chooseFocusTargetSpy.callCount, 2);
    assert.equal(
      element?.shadowRoot?.activeElement?.tagName,
      'GR-ACCOUNT-LIST'
    );
    assert.equal(element?.shadowRoot?.activeElement?.id, 'reviewers');

    element.focusOn(element.FocusTarget.CCS);
    clock.tick(1);
    assert.equal(chooseFocusTargetSpy.callCount, 2);
    assert.equal(
      element?.shadowRoot?.activeElement?.tagName,
      'GR-ACCOUNT-LIST'
    );
    assert.equal(element?.shadowRoot?.activeElement?.id, 'ccs');
    clock.restore();
  });

  test('chooseFocusTarget', () => {
    element.account = undefined;
    assert.strictEqual(element.chooseFocusTarget(), element.FocusTarget.BODY);

    element.account = {_account_id: 1 as AccountId};
    assert.strictEqual(element.chooseFocusTarget(), element.FocusTarget.BODY);

    element.change!.owner = {_account_id: 2 as AccountId};
    assert.strictEqual(element.chooseFocusTarget(), element.FocusTarget.BODY);

    element.change!.owner._account_id = 1 as AccountId;
    assert.strictEqual(
      element.chooseFocusTarget(),
      element.FocusTarget.REVIEWERS
    );

    element.reviewers = [];
    assert.strictEqual(
      element.chooseFocusTarget(),
      element.FocusTarget.REVIEWERS
    );

    element.reviewers.push({});
    assert.strictEqual(element.chooseFocusTarget(), element.FocusTarget.BODY);
  });

  test('only send labels that have changed', async () => {
    await element.updateComplete;
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
    const el = queryAndAssert<GrLabelScoreRow>(
      queryAndAssert(element, 'gr-label-scores'),
      'gr-label-score-row[name="Verified"]'
    );
    el.setSelectedValue('-1');
    tap(queryAndAssert(element, '.send'));
    await promise;
  });

  test('moving from cc to reviewer', async () => {
    const reviewer1 = makeAccount();
    const reviewer2 = makeAccount();
    const reviewer3 = makeAccount();
    const cc1 = makeAccount();
    const cc2 = makeAccount();
    const cc3 = makeAccount();
    const cc4 = makeAccount();
    element.reviewers = [reviewer1, reviewer2, reviewer3];
    element.ccs = [cc1, cc2, cc3, cc4];
    element.reviewers.push(cc1);
    element.reviewersList!.dispatchEvent(
      new CustomEvent('account-added', {
        detail: {account: cc1},
      })
    );
    await element.updateComplete;

    assert.deepEqual(element.reviewers, [reviewer1, reviewer2, reviewer3, cc1]);
    assert.deepEqual(element.ccs, [cc2, cc3, cc4]);

    element.reviewers.push(cc4);
    element.reviewersList!.dispatchEvent(
      new CustomEvent('account-added', {
        detail: {account: cc4},
      })
    );
    await element.updateComplete;

    element.reviewers.push(cc3);
    element.reviewersList!.dispatchEvent(
      new CustomEvent('account-added', {
        detail: {account: cc3},
      })
    );
    await element.updateComplete;

    assert.deepEqual(element.reviewers, [
      reviewer1,
      reviewer2,
      reviewer3,
      cc1,
      cc4,
      cc3,
    ]);
    assert.deepEqual(element.ccs, [cc2]);
  });

  test('update attention section when reviewers and ccs change', async () => {
    element.account = makeAccount();
    element.reviewers = [makeAccount(), makeAccount()];
    element.ccs = [makeAccount(), makeAccount()];
    element.draftCommentThreads = [];

    const modifyButton = queryAndAssert(element, '.edit-attention-button');
    tap(modifyButton);

    await element.updateComplete;

    assert.isFalse(element.attentionExpanded);

    element.draft = 'a test comment';
    await element.updateComplete;

    tap(modifyButton);

    await element.updateComplete;

    assert.isTrue(element.attentionExpanded);

    let accountLabels = Array.from(
      queryAll(element, '.attention-detail gr-account-label')
    );
    assert.equal(accountLabels.length, 5);

    element.reviewers = [...element.reviewers, makeAccount()];
    element.ccs = [...element.ccs, makeAccount()];
    await element.updateComplete;

    // The 'attention modified' section collapses and resets when reviewers or
    // ccs change.
    assert.isFalse(element.attentionExpanded);

    tap(queryAndAssert(element, '.edit-attention-button'));
    await element.updateComplete;

    assert.isTrue(element.attentionExpanded);
    accountLabels = Array.from(
      queryAll(element, '.attention-detail gr-account-label')
    );
    assert.equal(accountLabels.length, 7);

    element.reviewers.pop();
    element.reviewers.pop();
    element.ccs.pop();
    element.ccs.pop();
    element.reviewers = [...element.reviewers];
    element.ccs = [...element.ccs]; // trigger willUpdate observer

    await element.updateComplete;

    tap(queryAndAssert(element, '.edit-attention-button'));

    await element.updateComplete;

    accountLabels = Array.from(
      queryAll(element, '.attention-detail gr-account-label')
    );
    assert.equal(accountLabels.length, 3);
  });

  test('moving from reviewer to cc', async () => {
    const reviewer1 = makeAccount();
    const reviewer2 = makeAccount();
    const reviewer3 = makeAccount();
    const cc1 = makeAccount();
    const cc2 = makeAccount();
    const cc3 = makeAccount();
    const cc4 = makeAccount();
    element.reviewers = [reviewer1, reviewer2, reviewer3];
    element.ccs = [cc1, cc2, cc3, cc4];
    element.ccs.push(reviewer1);
    element.ccsList!.dispatchEvent(
      new CustomEvent('account-added', {
        detail: {account: reviewer1},
      })
    );

    await element.updateComplete;

    assert.deepEqual(element.reviewers, [reviewer2, reviewer3]);
    assert.deepEqual(element.ccs, [cc1, cc2, cc3, cc4, reviewer1]);

    element.ccs.push(reviewer3);
    element.ccsList!.dispatchEvent(
      new CustomEvent('account-added', {
        detail: {account: reviewer3},
      })
    );
    await element.updateComplete;

    element.ccs.push(reviewer2);
    element.ccsList!.dispatchEvent(
      new CustomEvent('account-added', {
        detail: {account: reviewer2},
      })
    );
    await element.updateComplete;

    assert.deepEqual(element.reviewers, []);
    assert.deepEqual(element.ccs, [
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
    const reviewers = queryAndAssert<GrAccountList>(element, '#reviewers');
    const ccs = queryAndAssert<GrAccountList>(element, '#ccs');
    const reviewer1 = makeAccount();
    const reviewer2 = makeAccount();
    const cc1 = makeAccount();
    const cc2 = makeAccount();
    const cc3 = makeAccount();
    element.reviewers = [reviewer1, reviewer2];
    element.ccs = [cc1, cc2, cc3];

    element.change!.reviewers = {
      [ReviewerState.CC]: [],
      [ReviewerState.REVIEWER]: [{_account_id: 33 as AccountId}],
    };
    await element.updateComplete;

    const mutations: ReviewerInput[] = [];

    stubSaveReview((review: ReviewInput) => {
      mutations.push(...review.reviewers!);
    });

    assert.isFalse(element.reviewersMutated);

    // Remove and add to other field.
    reviewers.dispatchEvent(
      new CustomEvent('remove', {
        detail: {account: reviewer1},
        composed: true,
        bubbles: true,
      })
    );

    await element.updateComplete;
    assert.isTrue(element.reviewersMutated);
    ccs.entry!.dispatchEvent(
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
    reviewers.entry!.dispatchEvent(
      new CustomEvent('add', {
        detail: {value: {account: cc1}},
        composed: true,
        bubbles: true,
      })
    );

    assert.deepEqual(
      element.reviewers.map(v => accountKey(v)),
      [reviewer2, cc1].map(v => accountKey(v))
    );
    assert.deepEqual(
      element.ccs.map(v => accountKey(v)),
      [cc2, reviewer1].map(v => accountKey(v))
    );

    // Add to Reviewer/CC which will automatically remove from CC/Reviewer.
    reviewers.entry!.dispatchEvent(
      new CustomEvent('add', {
        detail: {value: {account: cc2}},
        composed: true,
        bubbles: true,
      })
    );

    await element.updateComplete;

    assert.deepEqual(
      element.reviewers.map(v => accountKey(v)),
      [reviewer2, cc1, cc2].map(v => accountKey(v))
    );
    assert.deepEqual(
      element.ccs.map(v => accountKey(v)),
      [reviewer1].map(v => accountKey(v))
    );

    ccs.entry!.dispatchEvent(
      new CustomEvent('add', {
        detail: {value: {account: reviewer2}},
        composed: true,
        bubbles: true,
      })
    );

    await element.updateComplete;

    assert.deepEqual(
      element.reviewers.map(v => accountKey(v)),
      [cc1, cc2].map(v => accountKey(v))
    );
    assert.deepEqual(
      element.ccs.map(v => accountKey(v)),
      [reviewer1, reviewer2].map(v => accountKey(v))
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
    await element.updateComplete;
    assert.equal(mutations.length, 5);

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
    await element.updateComplete;
    const reviewers = queryAndAssert<GrAccountList>(element, '#reviewers');
    const ccs = queryAndAssert<GrAccountList>(element, '#ccs');
    const reviewer1 = makeAccount();
    element.reviewers = [reviewer1];
    element.ccs = [];

    element.change!.reviewers = {
      [ReviewerState.CC]: [],
      [ReviewerState.REVIEWER]: [{_account_id: reviewer1._account_id}],
    };

    const mutations: ReviewerInput[] = [];

    stubSaveReview((review: ReviewInput) => {
      mutations.push(...review.reviewers!);
    });

    // Remove and add to other field.
    reviewers.dispatchEvent(
      new CustomEvent('remove', {
        detail: {account: reviewer1},
        composed: true,
        bubbles: true,
      })
    );
    ccs.entry!.dispatchEvent(
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

  test('emits cancel on esc key', async () => {
    const cancelHandler = sinon.spy();
    element.addEventListener('cancel', cancelHandler);
    pressAndReleaseKeyOn(element, 27, null, 'Escape');
    await element.updateComplete;

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

  test('computeMessagePlaceholder', async () => {
    element.canBeStarted = false;
    await element.updateComplete;
    assert.equal(element.messagePlaceholder, 'Say something nice...');

    element.canBeStarted = true;
    await element.updateComplete;
    assert.equal(
      element.messagePlaceholder,
      'Add a note for your reviewers...'
    );
  });

  test('computeSendButtonLabel', async () => {
    element.canBeStarted = false;
    await element.updateComplete;
    assert.equal(element.sendButtonLabel, 'Send');

    element.canBeStarted = true;
    await element.updateComplete;
    assert.equal(element.sendButtonLabel, 'Send and Start review');
  });

  test('handle400Error reviewers and CCs', async () => {
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
    element.handle400Error(cloneableResponse(400, text) as Response);
    await promise;
  });

  test('fires height change when the drafts comments load', async () => {
    // Flush DOM operations before binding to the autogrow event so we don't
    // catch the events fired from the initial layout.
    await element.updateComplete;
    const autoGrowHandler = sinon.stub();
    element.addEventListener('autogrow', autoGrowHandler);
    element.draftCommentThreads = [];
    await element.updateComplete;
    assert.isTrue(autoGrowHandler.called);
  });

  suite('start review and save buttons', () => {
    let sendStub: sinon.SinonStub;

    setup(async () => {
      sendStub = sinon.stub(element, 'send').callsFake(() => Promise.resolve());
      element.canBeStarted = true;
      // Flush to make both Start/Save buttons appear in DOM.
      await element.updateComplete;
    });

    test('start review sets ready', async () => {
      tap(queryAndAssert(element, '.send'));
      await element.updateComplete;
      assert.isTrue(sendStub.calledWith(true, true));
    });

    test("save review doesn't set ready", async () => {
      tap(queryAndAssert(element, '.save'));
      await element.updateComplete;
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

    test('error occurs in saveReview', () => {
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
        assert.isTrue(element.savingComments);

        promise.resolve();
        await element.updateComplete;

        assert.isTrue(refreshSpy.called);
        assert.isFalse(element.savingComments);
      });

      test('no', () => {
        stubRestApi('hasPendingDiffDrafts').returns(0);
        element.open();
        assert.isFalse(element.savingComments);
      });
    });
  });

  test('computeSendButtonDisabled_canBeStarted', () => {
    // Mock canBeStarted
    element.canBeStarted = true;
    element.draftCommentThreads = [];
    element.draft = '';
    element.reviewersMutated = false;
    element.labelsChanged = false;
    element.includeComments = false;
    element.disabled = false;
    element.commentEditing = false;
    element.account = makeAccount();
    assert.isFalse(element.computeSendButtonDisabled());
  });

  test('computeSendButtonDisabled_allFalse', () => {
    // Mock everything false
    element.canBeStarted = false;
    element.draftCommentThreads = [];
    element.draft = '';
    element.reviewersMutated = false;
    element.labelsChanged = false;
    element.includeComments = false;
    element.disabled = false;
    element.commentEditing = false;
    element.account = makeAccount();
    assert.isTrue(element.computeSendButtonDisabled());
  });

  test('computeSendButtonDisabled_draftCommentsSend', () => {
    // Mock nonempty comment draft array; with sending comments.
    element.canBeStarted = false;
    element.draftCommentThreads = [{...createCommentThread([createComment()])}];
    element.draft = '';
    element.reviewersMutated = false;
    element.labelsChanged = false;
    element.includeComments = true;
    element.disabled = false;
    element.commentEditing = false;
    element.account = makeAccount();
    assert.isFalse(element.computeSendButtonDisabled());
  });

  test('computeSendButtonDisabled_draftCommentsDoNotSend', () => {
    // Mock nonempty comment draft array; without sending comments.
    element.canBeStarted = false;
    element.draftCommentThreads = [{...createCommentThread([createComment()])}];
    element.draft = '';
    element.reviewersMutated = false;
    element.labelsChanged = false;
    element.includeComments = false;
    element.disabled = false;
    element.commentEditing = false;
    element.account = makeAccount();

    assert.isTrue(element.computeSendButtonDisabled());
  });

  test('computeSendButtonDisabled_changeMessage', () => {
    // Mock nonempty change message.
    element.canBeStarted = false;
    element.draftCommentThreads = [{...createCommentThread([createComment()])}];
    element.draft = 'test';
    element.reviewersMutated = false;
    element.labelsChanged = false;
    element.includeComments = false;
    element.disabled = false;
    element.commentEditing = false;
    element.account = makeAccount();

    assert.isFalse(element.computeSendButtonDisabled());
  });

  test('computeSendButtonDisabledreviewersChanged', () => {
    // Mock reviewers mutated.
    element.canBeStarted = false;
    element.draftCommentThreads = [{...createCommentThread([createComment()])}];
    element.draft = '';
    element.reviewersMutated = true;
    element.labelsChanged = false;
    element.includeComments = false;
    element.disabled = false;
    element.commentEditing = false;
    element.account = makeAccount();

    assert.isFalse(element.computeSendButtonDisabled());
  });

  test('computeSendButtonDisabled_labelsChanged', () => {
    // Mock labels changed.
    element.canBeStarted = false;
    element.draftCommentThreads = [{...createCommentThread([createComment()])}];
    element.draft = '';
    element.reviewersMutated = false;
    element.labelsChanged = true;
    element.includeComments = false;
    element.disabled = false;
    element.commentEditing = false;
    element.account = makeAccount();

    assert.isFalse(element.computeSendButtonDisabled());
  });

  test('computeSendButtonDisabled_dialogDisabled', () => {
    // Whole dialog is disabled.
    element.canBeStarted = false;
    element.draftCommentThreads = [{...createCommentThread([createComment()])}];
    element.draft = '';
    element.reviewersMutated = false;
    element.labelsChanged = true;
    element.includeComments = false;
    element.disabled = true;
    element.commentEditing = false;
    element.account = makeAccount();

    assert.isTrue(element.computeSendButtonDisabled());
  });

  test('computeSendButtonDisabled_existingVote', async () => {
    const account = createAccountWithId();
    (
      element.change!.labels![StandardLabels.CODE_REVIEW]! as DetailedLabelInfo
    ).all = [account];
    element.canBeStarted = false;
    element.draftCommentThreads = [{...createCommentThread([createComment()])}];
    element.draft = '';
    element.reviewersMutated = false;
    element.labelsChanged = false;
    element.includeComments = false;
    element.disabled = false;
    element.commentEditing = false;
    element.account = account;

    // User has already voted.
    assert.isFalse(element.computeSendButtonDisabled());
  });

  test('_submit blocked when no mutations exist', async () => {
    const sendStub = sinon.stub(element, 'send').returns(Promise.resolve());
    element.draftCommentThreads = [];
    await element.updateComplete;

    tap(queryAndAssert(element, 'gr-button.send'));
    assert.isFalse(sendStub.called);

    element.draftCommentThreads = [
      {
        ...createCommentThread([
          {
            ...createDraft(),
            path: 'test',
            line: 1,
            patch_set: 1 as PatchSetNum,
          },
        ]),
      },
    ];
    await element.updateComplete;

    tap(queryAndAssert(element, 'gr-button.send'));
    assert.isTrue(sendStub.called);
  });

  test('getFocusStops', async () => {
    // Setting draftCommentThreads to an empty object causes _sendDisabled to be
    // computed to false.
    element.draftCommentThreads = [];
    await element.updateComplete;

    assert.equal(
      element.getFocusStops()!.end,
      queryAndAssert<GrButton>(element, '#cancelButton')
    );
    element.draftCommentThreads = [
      {
        ...createCommentThread([
          {
            ...createDraft(),
            path: 'test',
            line: 1,
            patch_set: 1 as PatchSetNum,
          },
        ]),
      },
    ];
    await element.updateComplete;

    assert.equal(
      element.getFocusStops()!.end,
      queryAndAssert<GrButton>(element, '#sendButton')
    );
  });

  test('setPluginMessage', async () => {
    element.setPluginMessage('foo');
    await element.updateComplete;
    assert.equal(queryAndAssert(element, '#pluginMessage').textContent, 'foo');
  });
});
