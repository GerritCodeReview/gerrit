/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-reply-dialog';
import {
  addListenerForTest,
  isVisible,
  mockPromise,
  pressKey,
  query,
  queryAll,
  queryAndAssert,
  stubFlags,
  stubRestApi,
  waitUntilVisible,
} from '../../../test/test-utils';
import {ChangeStatus, ReviewerState} from '../../../constants/constants';
import {JSON_PREFIX} from '../../shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {StandardLabels} from '../../../utils/label-util';
import {
  createAccountWithEmail,
  createAccountWithId,
  createChange,
  createComment,
  createCommentThread,
  createDraft,
  createRevision,
  createServiceUserWithId,
} from '../../../test/test-data-generators';
import {GrReplyDialog} from './gr-reply-dialog';
import {
  AccountId,
  AccountInfo,
  CommitId,
  DetailedLabelInfo,
  EmailAddress,
  GroupId,
  GroupName,
  NumericChangeId,
  PatchSetNum,
  ReviewerInput,
  ReviewInput,
  ReviewResult,
  RevisionPatchSetNum,
  Suggestion,
  Timestamp,
  UrlEncodedCommentId,
  UserId,
} from '../../../types/common';
import {CommentThread} from '../../../utils/comment-util';
import {GrAccountList} from '../../shared/gr-account-list/gr-account-list';
import {GrLabelScoreRow} from '../gr-label-score-row/gr-label-score-row';
import {GrLabelScores} from '../gr-label-scores/gr-label-scores';
import {GrThreadList} from '../gr-thread-list/gr-thread-list';
import {GrAutocomplete} from '../../shared/gr-autocomplete/gr-autocomplete';
import {fixture, html, waitUntil, assert} from '@open-wc/testing';
import {accountKey} from '../../../utils/account-util';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrAccountLabel} from '../../shared/gr-account-label/gr-account-label';
import {KnownExperimentId} from '../../../services/flags/flags';
import {Key, Modifier} from '../../../utils/dom-util';
import {GrComment} from '../../shared/gr-comment/gr-comment';
import {testResolver} from '../../../test/common-test-setup';
import {
  CommentsModel,
  commentsModelToken,
} from '../../../models/comments/comments-model';

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
  let commentsModel: CommentsModel;

  let lastId = 1;
  const makeAccount = function () {
    return {
      _account_id: lastId++ as AccountId,
      email: `${lastId}.com` as EmailAddress,
    };
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
        email: 'abcd' as EmailAddress,
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
    element.draftCommentThreads = [];

    await element.updateComplete;
    commentsModel = testResolver(commentsModelToken);
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
    assert.shadowDom.equal(
      element,
      /* HTML */ `
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
            <dialog tabindex="-1" id="reviewerConfirmationModal">
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
            </dialog>
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
                <gr-comment
                  hide-header=""
                  id="patchsetLevelComment"
                  permanent-editing-mode=""
                >
                </gr-comment>
                <gr-endpoint-param name="change"> </gr-endpoint-param>
              </gr-endpoint-decorator>
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
                        aria-disabled="true"
                        disabled=""
                        class="edit-attention-button"
                        data-action-key="edit"
                        data-action-type="change"
                        data-label="Edit"
                        link=""
                        position-below=""
                        role="button"
                        tabindex="-1"
                      >
                        <div>
                          <gr-icon icon="edit" filled small></gr-icon>
                          <span>Modify</span>
                        </div>
                      </gr-button>
                    </gr-tooltip-content>
                  </div>
                  <div>
                    <a
                      href="https://gerrit-review.googlesource.com/Documentation/user-attention-set.html"
                      target="_blank"
                    >
                      <gr-icon icon="help" title="read documentation"></gr-icon>
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
                      aria-disabled="true"
                      disabled=""
                      class="action send"
                      id="sendButton"
                      primary=""
                      role="button"
                      tabindex="-1"
                    >
                      Send
                    </gr-button>
                  </gr-tooltip-content>
                </div>
              </section>
            </gr-endpoint-decorator>
          </div>
        </div>
      `
    );
  });

  test('renders private change info when reviewer is added', async () => {
    element.change!.is_private = true;
    element.requestUpdate();
    await element.updateComplete;
    const peopleContainer = queryAndAssert<HTMLDivElement>(
      element,
      '.peopleContainer'
    );

    // Info is rendered only if reviewer is added
    assert.dom.equal(
      peopleContainer,
      `
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
        <dialog
          tabindex="-1"
          id="reviewerConfirmationModal"
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
        </dialog>
      </section>
    `
    );

    const account = createAccountWithId(22);
    element.reviewersList!.accounts = [];
    element.reviewersList!.addAccountItem({account, count: 1});
    element.reviewersList!.dispatchEvent(
      new CustomEvent('account-added', {
        detail: {account},
      })
    );
    element.requestUpdate();
    await element.updateComplete;

    assert.dom.equal(
      peopleContainer,
      `
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
        <dialog
          tabindex="-1"
          id="reviewerConfirmationModal"
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
        </dialog>
        <div class="privateVisiblityInfo">
          <gr-icon icon="info">
          </gr-icon>
          <div>
            Adding a reviewer/CC will make this private change visible to them
          </div>
        </div>
      </section>
    `
    );
  });

  test('default to publishing draft comments with reply', async () => {
    // Async tick is needed because iron-selector content is distributed and
    // distributed content requires an observer to be set up.
    await element.updateComplete;
    element.patchsetLevelDraftMessage = 'I wholeheartedly disapprove';
    element.draftCommentThreads = [createCommentThread([createComment()])];

    element.includeComments = true;
    const saveReviewPromise = interceptSaveReview();

    // This is needed on non-Blink engines most likely due to the ways in
    // which the dom-repeat elements are stamped.
    await element.updateComplete;
    queryAndAssert<GrButton>(element, '.send').click();
    await element.updateComplete;

    const review = await saveReviewPromise;
    assert.deepEqual(review, {
      drafts: 'PUBLISH_ALL_REVISIONS',
      labels: {
        'Code-Review': 0,
        Verified: 0,
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

    // required so that "Send" button is enabled
    element.canBeStarted = true;
    await element.updateComplete;

    element.account = {_account_id: 123 as AccountId};
    element.newAttentionSet = new Set([314 as AccountId]);
    element.uploader = createAccountWithId(314);
    const saveReviewPromise = interceptSaveReview();

    queryAndAssert<GrButton>(element, '.send').click();
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
      ready: true,
      remove_from_attention_set: [],
      ignore_automatic_attention_set_rules: true,
    });
  });

  test('modified attention set by anonymous', async () => {
    await element.updateComplete;

    // required so that "Send" button is enabled
    element.canBeStarted = true;
    await element.updateComplete;

    element.account = {};
    element.uploader = createAccountWithId(314);
    element.newAttentionSet = new Set([314 as AccountId]);
    const saveReviewPromise = interceptSaveReview();

    queryAndAssert<GrButton>(element, '.send').click();
    const review = await saveReviewPromise;

    assert.deepEqual(review, {
      drafts: 'PUBLISH_ALL_REVISIONS',
      labels: {
        'Code-Review': 0,
        Verified: 0,
      },
      add_to_attention_set: [
        // Name coming from createUserConfig in test-data-generator
        {reason: 'Name of user not set replied on the change', user: 314},
      ],
      reviewers: [],
      ready: true,
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
    element._ccs = [];
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
      {_account_id: 1 as AccountId},
      {_account_id: 2 as AccountId},
    ];
    element._ccs = [];
    element.draftCommentThreads = [];
    element.includeComments = true;
    element.canBeStarted = true;
    await element.updateComplete;
    assert.sameMembers(
      [...element.newAttentionSet],
      [1 as AccountId, 2 as AccountId]
    );

    // If the user votes on the change, then they should not be added to the
    // attention set, even if they have just added themselves as reviewer.
    // But voting should also add the owner (5).
    element.labelsChanged = true;
    await element.updateComplete;
    assert.sameMembers(
      [...element.newAttentionSet],
      [2 as AccountId, 5 as AccountId]
    );
  });

  test('computeNewAttention when sending wip change for review', async () => {
    element.change = {
      ...createChange(),
      owner: {_account_id: 1 as AccountId},
      status: ChangeStatus.NEW,
      attention_set: {},
      reviewers: {
        [ReviewerState.REVIEWER]: [
          {...createAccountWithId(2)},
          {...createAccountWithId(3)},
        ],
      },
    };
    // let rebuildReviewers triggered by change update finish running
    await element.updateComplete;

    element.reviewers = [
      {...createAccountWithId(2)},
      {...createAccountWithId(3)},
    ];

    element._ccs = [];
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
    assert.sameMembers(
      [...element.newAttentionSet],
      [2 as AccountId, 3 as AccountId]
    );

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
    element._ccs = [{_account_id: 7 as AccountId, display_name: 'Elmo'}];
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

  test('label picker', async () => {
    element.patchsetLevelDraftMessage = 'I wholeheartedly disapprove';
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
    queryAndAssert<GrButton>(element, '.send').click();
    assert.isTrue(element.disabled);

    const review = await saveReviewPromise;
    await element.updateComplete;
    await waitUntil(() => element.disabled === false);
    assert.equal(element.patchsetLevelDraftMessage.length, 0);
    assert.deepEqual(review, {
      drafts: 'PUBLISH_ALL_REVISIONS',
      labels: {
        'Code-Review': -1,
        Verified: -1,
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

    queryAndAssert<HTMLInputElement>(element, '#includeComments').click();
    assert.equal(element.includeComments, false);

    // Async tick is needed because iron-selector content is distributed and
    // distributed content requires an observer to be set up.
    await element.updateComplete;
    element.patchsetLevelDraftMessage = 'I wholeheartedly disapprove';

    const saveReviewPromise = interceptSaveReview();

    // This is needed on non-Blink engines most likely due to the ways in
    // which the dom-repeat elements are stamped.
    await element.updateComplete;
    queryAndAssert<GrButton>(element, '.send').click();

    const review = await saveReviewPromise;
    await element.updateComplete;
    assert.deepEqual(review, {
      drafts: 'KEEP',
      labels: {
        'Code-Review': 0,
        Verified: 0,
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
    const yesButton = queryAndAssert<GrButton>(
      element,
      '.reviewerConfirmationButtons gr-button:first-child'
    );
    const noButton = queryAndAssert<GrButton>(
      element,
      '.reviewerConfirmationButtons gr-button:last-child'
    );

    element.ccPendingConfirmation = null;
    element.reviewerPendingConfirmation = null;
    await element.updateComplete;
    assert.isFalse(
      isVisible(queryAndAssert(element, '#reviewerConfirmationModal'))
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
      isVisible(queryAndAssert(element, '#reviewerConfirmationModal'))
    );
    observer = overlayObserver('closed');
    const expected = 'Group name has 10 members';
    assert.notEqual(
      queryAndAssert<HTMLElement>(
        element,
        'reviewerConfirmationModal'
      ).innerText.indexOf(expected),
      -1
    );
    noButton.click(); // close the overlay

    await observer;
    assert.isFalse(
      isVisible(queryAndAssert(element, '#reviewerConfirmationModal'))
    );

    // We should be focused on account entry input.
    const reviewersEntry = queryAndAssert<GrAccountList>(element, '#reviewers');
    assert.isTrue(
      isFocusInsideElement(
        queryAndAssert<GrAutocomplete>(reviewersEntry.entry, '#input').input!
      )
    );

    // No reviewer/CC should have been added.
    assert.equal(element.ccsList?.additions().length, 0);
    assert.equal(element.reviewersList?.additions().length, 0);

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
      isVisible(queryAndAssert(element, '#reviewerConfirmationModal'))
    );
    observer = overlayObserver('closed');
    yesButton.click(); // Confirm the group.

    await observer;
    assert.isFalse(
      isVisible(queryAndAssert(element, '#reviewerConfirmationModal'))
    );
    const additions = cc
      ? element.ccsList?.additions()
      : element.reviewersList?.additions();
    assert.deepEqual(additions, [
      {
        name: 'name' as GroupName,
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

  suite('reviewer toast for WIP changes', () => {
    let fireStub: sinon.SinonStub;
    setup(() => {
      fireStub = sinon.stub(element, 'dispatchEvent');
    });

    test('toast not fired if change is already active', async () => {
      element.change = {
        ...createChange(),
        status: ChangeStatus.NEW,
      };
      element.send(false, false);

      await waitUntil(() => fireStub.called);

      const events = fireStub.args.map(arg => arg[0].type || '');
      assert.isFalse(events.includes('show-alert'));
    });

    test('toast is not fired if change is WIP and becomes active', async () => {
      const account = createAccountWithId(22);
      element.reviewersList!.accounts = [];
      element.reviewersList!.addAccountItem({account, count: 1});
      element.reviewersList!.dispatchEvent(
        new CustomEvent('account-added', {
          detail: {account},
        })
      );
      element.change = {
        ...createChange(),
        status: ChangeStatus.NEW,
        work_in_progress: true,
      };
      element.send(false, true);

      await waitUntil(() => fireStub.called);

      const events = fireStub.args.map(arg => arg[0].type || '');
      assert.isFalse(events.includes('show-alert'));
    });

    test('toast is fired if change is WIP and becomes active and reviewer added', async () => {
      const account = createAccountWithId(22);
      element.reviewersList!.accounts = [];
      element.reviewersList!.addAccountItem({account, count: 1});
      element.reviewersList!.dispatchEvent(
        new CustomEvent('account-added', {
          detail: {account},
        })
      );
      element.change = {
        ...createChange(),
        status: ChangeStatus.NEW,
        work_in_progress: true,
      };
      element.send(false, false);

      await waitUntil(() => fireStub.called);

      const events = fireStub.args.map(arg => arg[0].type || '');
      assert.isTrue(events.includes('show-alert'));
    });
  });

  test('cc confirmation', async () => {
    testConfirmationDialog(true);
  });

  test('reviewer confirmation', async () => {
    testConfirmationDialog(false);
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

    element.change = createChange();
    element.change.owner = owner;
    element.reviewers = [reviewer1, reviewer2];
    element._ccs = [cc1, cc2];

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
    const chooseFocusTargetSpy = sinon.spy(element, 'chooseFocusTarget');
    element.focusOn();
    await waitUntilVisible(element); // let whenVisible resolve

    assert.equal(chooseFocusTargetSpy.callCount, 1);
    assert.equal(element?.shadowRoot?.activeElement?.tagName, 'GR-COMMENT');
    assert.equal(
      element?.shadowRoot?.activeElement?.id,
      'patchsetLevelComment'
    );

    element.focusOn(element.FocusTarget.ANY);
    await waitUntilVisible(element); // let whenVisible resolve

    assert.equal(chooseFocusTargetSpy.callCount, 2);
    assert.equal(element?.shadowRoot?.activeElement?.tagName, 'GR-COMMENT');
    assert.equal(
      element?.shadowRoot?.activeElement?.id,
      'patchsetLevelComment'
    );

    element.focusOn(element.FocusTarget.BODY);
    await waitUntilVisible(element); // let whenVisible resolve

    assert.equal(chooseFocusTargetSpy.callCount, 2);
    assert.equal(element?.shadowRoot?.activeElement?.tagName, 'GR-COMMENT');
    assert.equal(
      element?.shadowRoot?.activeElement?.id,
      'patchsetLevelComment'
    );

    element.focusOn(element.FocusTarget.REVIEWERS);
    await waitUntilVisible(element); // let whenVisible resolve

    assert.equal(chooseFocusTargetSpy.callCount, 2);
    await waitUntil(
      () => element?.shadowRoot?.activeElement?.tagName === 'GR-ACCOUNT-LIST'
    );
    assert.equal(element?.shadowRoot?.activeElement?.id, 'reviewers');

    element.focusOn(element.FocusTarget.CCS);
    assert.equal(chooseFocusTargetSpy.callCount, 2);
    assert.equal(
      element?.shadowRoot?.activeElement?.tagName,
      'GR-ACCOUNT-LIST'
    );
    await waitUntil(() => element?.shadowRoot?.activeElement?.id === 'ccs');
  });

  test('chooseFocusTarget', () => {
    element.account = undefined;
    assert.equal(element.chooseFocusTarget(), element.FocusTarget.BODY);

    element.account = element.change!.owner;
    assert.equal(element.chooseFocusTarget(), element.FocusTarget.REVIEWERS);

    element.change!.reviewers.REVIEWER = [createAccountWithId(314)];
    assert.equal(element.chooseFocusTarget(), element.FocusTarget.BODY);

    element.change!.reviewers.REVIEWER = [createServiceUserWithId(314)];
    assert.equal(element.chooseFocusTarget(), element.FocusTarget.REVIEWERS);

    element.change!.reviewers.REVIEWER = [
      createAccountWithId(314),
      createServiceUserWithId(314),
    ];
    assert.equal(element.chooseFocusTarget(), element.FocusTarget.BODY);
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
    // Without wrapping this test in await element.updateComplete, the below two
    // calls to tap() cause a race in some situations in shadow DOM. The send
    // button can be tapped before the others, causing the test to fail.
    const el = queryAndAssert<GrLabelScoreRow>(
      queryAndAssert(element, 'gr-label-scores'),
      'gr-label-score-row[name="Verified"]'
    );
    el.setSelectedValue('-1');

    // required so that "Send" button is enabled
    element.canBeStarted = true;
    await element.updateComplete;

    queryAndAssert<GrButton>(element, '.send').click();
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
    element.reviewersList!.accounts = [reviewer1, reviewer2, reviewer3];
    element.ccsList!.accounts = [cc1, cc2, cc3, cc4];
    await element.updateComplete;
    element.reviewersList!.accounts.push(cc1);

    element.reviewers = element.reviewersList!.accounts;
    element.ccs = element.ccsList!.accounts;

    element.reviewersList!.dispatchEvent(
      new CustomEvent('account-added', {
        detail: {account: cc1},
      })
    );
    await element.updateComplete;

    assert.deepEqual(element.reviewers, [reviewer1, reviewer2, reviewer3, cc1]);
    assert.deepEqual(element.ccs, [cc2, cc3, cc4]);

    element.reviewersList!.addAccountItem({account: cc4, count: 1});
    element.reviewersList!.dispatchEvent(
      new CustomEvent('account-added', {
        detail: {account: cc4},
      })
    );
    await element.updateComplete;

    element.reviewersList!.addAccountItem({account: cc3, count: 1});
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
    element._ccs = [makeAccount(), makeAccount()];
    element.draftCommentThreads = [];

    const modifyButton = queryAndAssert<GrButton>(
      element,
      '.edit-attention-button'
    );
    modifyButton.click();

    await element.updateComplete;

    assert.isFalse(element.attentionExpanded);

    element.patchsetLevelDraftMessage = 'a test comment';
    await element.updateComplete;

    modifyButton.click();

    await element.updateComplete;

    assert.isTrue(element.attentionExpanded);

    let accountLabels = Array.from(
      queryAll(element, '.attention-detail gr-account-label')
    );
    assert.equal(accountLabels.length, 5);

    element.reviewers = [...element.reviewers, makeAccount()];
    element._ccs = [...element.ccs, makeAccount()];
    await element.updateComplete;

    // The 'attention modified' section collapses and resets when reviewers or
    // ccs change.
    assert.isFalse(element.attentionExpanded);

    queryAndAssert<GrButton>(element, '.edit-attention-button').click();
    await element.updateComplete;

    assert.isTrue(element.attentionExpanded);
    accountLabels = Array.from(
      queryAll(element, '.attention-detail gr-account-label')
    );
    assert.equal(accountLabels.length, 7);

    element.reviewers.pop();
    element.reviewers.pop();
    element._ccs.pop();
    element._ccs.pop();
    element.reviewers = [...element.reviewers];
    element._ccs = [...element.ccs]; // trigger willUpdate observer

    await element.updateComplete;

    queryAndAssert<GrButton>(element, '.edit-attention-button').click();

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
    element.reviewersList!.accounts = [reviewer1, reviewer2, reviewer3];
    element.ccsList!.accounts = [cc1, cc2, cc3, cc4];
    element.reviewers = element.reviewersList!.accounts;
    element._ccs = element.ccsList!.accounts;
    element._ccs.push(reviewer1);
    element.ccsList!.dispatchEvent(
      new CustomEvent('account-added', {
        detail: {account: reviewer1},
      })
    );

    await element.updateComplete;
    await element.updateComplete;
    await element.updateComplete;
    await element.updateComplete;

    assert.deepEqual(element.reviewers, [reviewer2, reviewer3]);
    assert.deepEqual(element.ccs, [cc1, cc2, cc3, cc4, reviewer1]);

    element.ccsList!.addAccountItem({account: reviewer3, count: 1});
    element.ccsList!.dispatchEvent(
      new CustomEvent('account-added', {
        detail: {account: reviewer3},
      })
    );
    await element.updateComplete;

    element.ccsList!.addAccountItem({account: reviewer2, count: 1});
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
    element._ccs = [cc1, cc2, cc3];

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

    assert.deepEqual(mutations[0], mapReviewer(cc1, ReviewerState.REVIEWER));
    assert.deepEqual(mutations[1], mapReviewer(cc2, ReviewerState.REVIEWER));
    assert.deepEqual(mutations[2], mapReviewer(reviewer1, ReviewerState.CC));
    assert.deepEqual(mutations[3], mapReviewer(reviewer2, ReviewerState.CC));

    // Only 1 account was initially part of the change
    assert.deepEqual(mutations[4], {
      reviewer: 33 as UserId,
      state: ReviewerState.REMOVED,
    });
  });

  test('Ignore removal requests if being added as reviewer/CC', async () => {
    await element.updateComplete;
    const reviewers = queryAndAssert<GrAccountList>(element, '#reviewers');
    const ccs = queryAndAssert<GrAccountList>(element, '#ccs');
    const reviewer1 = makeAccount();
    element.reviewers = [reviewer1];
    element._ccs = [];

    element.change!.reviewers = {
      [ReviewerState.CC]: [],
      [ReviewerState.REVIEWER]: [{_account_id: reviewer1._account_id}],
    };

    await element.updateComplete;

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
    assert.lengthOf(mutations, 1);
    // Only 1 account was initially part of the change
    assert.deepEqual(mutations[0], {
      reviewer: reviewer1._account_id,
      state: ReviewerState.CC,
    });
  });

  test('Ignore removal requests from reviewer if owner', async () => {
    await element.updateComplete;
    const reviewer1 = makeAccount();
    element.reviewers = [reviewer1];
    element._ccs = [];
    element.change!.owner = reviewer1;

    element.change!.reviewers = {
      [ReviewerState.CC]: [],
      [ReviewerState.REVIEWER]: [{_account_id: reviewer1._account_id}],
    };

    await element.updateComplete;

    const mutations: ReviewerInput[] = [];

    stubSaveReview((review: ReviewInput) => {
      mutations.push(...review.reviewers!);
    });

    await element.send(false, false);
    assert.lengthOf(mutations, 0);
  });

  test('emits cancel on esc key', async () => {
    const cancelHandler = sinon.spy();
    element.addEventListener('cancel', cancelHandler);
    pressKey(element, Key.ESC);
    await element.updateComplete;

    assert.isTrue(cancelHandler.called);
  });

  test('should not send on enter key', () => {
    stubSaveReview(() => undefined);
    element.addEventListener('send', () => assert.fail('wrongly called'));
    pressKey(element, Key.ENTER);
  });

  test('emit send on ctrl+enter key', async () => {
    // required so that "Send" button is enabled
    element.canBeStarted = true;
    await element.updateComplete;

    stubSaveReview(() => undefined);
    const promise = mockPromise();
    element.addEventListener('send', () => promise.resolve());
    pressKey(element, Key.ENTER, Modifier.CTRL_KEY);
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

  suite('start review and save buttons', () => {
    let sendStub: sinon.SinonStub;

    setup(async () => {
      sendStub = sinon.stub(element, 'send').callsFake(() => Promise.resolve());
      element.canBeStarted = true;
      // Flush to make both Start/Save buttons appear in DOM.
      await element.updateComplete;
    });

    test('start review sets ready', async () => {
      queryAndAssert<GrButton>(element, '.send').click();
      await element.updateComplete;
      assert.isTrue(sendStub.calledWith(true, true));
    });

    test("save review doesn't set ready", async () => {
      queryAndAssert<GrButton>(element, '.save').click();
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
      element.patchsetLevelDraftMessage = expectedDraft;
    });

    function assertDialogOpenAndEnabled() {
      assert.strictEqual(expectedDraft, element.patchsetLevelDraftMessage);
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
    element.patchsetLevelDraftMessage = '';
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
    element.patchsetLevelDraftMessage = '';
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
    element.patchsetLevelDraftMessage = '';
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
    element.patchsetLevelDraftMessage = '';
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
    element.patchsetLevelDraftMessage = 'test';
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
    element.patchsetLevelDraftMessage = '';
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
    element.patchsetLevelDraftMessage = '';
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
    element.patchsetLevelDraftMessage = '';
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
    element.patchsetLevelDraftMessage = '';
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

    queryAndAssert<GrButton>(element, 'gr-button.send').click();
    assert.isFalse(sendStub.called);

    element.draftCommentThreads = [
      {
        ...createCommentThread([
          {
            ...createDraft(),
            path: 'test',
            line: 1,
            patch_set: 1 as RevisionPatchSetNum,
          },
        ]),
      },
    ];
    await element.updateComplete;

    queryAndAssert<GrButton>(element, 'gr-button.send').click();
    assert.isTrue(sendStub.called);
  });

  suite('patchset level comment using GrComment', () => {
    setup(async () => {
      element.account = createAccountWithId(1);
      element.requestUpdate();
      await element.updateComplete;
    });

    test('renders GrComment', () => {
      assert.dom.equal(
        query(element, '.patchsetLevelContainer'),
        /* HTML */ `
          <div class="patchsetLevelContainer resolved">
            <gr-endpoint-decorator name="reply-text">
              <gr-comment
                hide-header=""
                id="patchsetLevelComment"
                permanent-editing-mode=""
              >
              </gr-comment>
              <gr-endpoint-param name="change"> </gr-endpoint-param>
            </gr-endpoint-decorator>
          </div>
        `
      );
    });

    test('send button updates state as text is typed in patchset comment', async () => {
      assert.isTrue(element.computeSendButtonDisabled());

      queryAndAssert<GrComment>(element, '#patchsetLevelComment').messageText =
        'hello';
      await waitUntil(() => element.patchsetLevelDraftMessage === 'hello');

      assert.isFalse(element.computeSendButtonDisabled());

      queryAndAssert<GrComment>(element, '#patchsetLevelComment').messageText =
        '';
      await waitUntil(() => element.patchsetLevelDraftMessage === '');

      assert.isTrue(element.computeSendButtonDisabled());
    });

    test('sending patchset level comment', async () => {
      const patchsetLevelComment = queryAndAssert<GrComment>(
        element,
        '#patchsetLevelComment'
      );
      const autoSaveStub = sinon
        .stub(patchsetLevelComment, 'save')
        .returns(Promise.resolve());

      patchsetLevelComment.messageText = 'hello world';
      await waitUntil(
        () => element.patchsetLevelDraftMessage === 'hello world'
      );

      const saveReviewPromise = interceptSaveReview();

      assert.deepEqual(autoSaveStub.callCount, 0);

      queryAndAssert<GrButton>(element, '.send').click();

      const review = await saveReviewPromise;

      assert.deepEqual(autoSaveStub.callCount, 1);

      assert.deepEqual(review, {
        drafts: 'PUBLISH_ALL_REVISIONS',
        labels: {
          'Code-Review': 0,
          Verified: 0,
        },
        reviewers: [],
        add_to_attention_set: [
          {reason: '<GERRIT_ACCOUNT_1> replied on the change', user: 999},
        ],
        remove_from_attention_set: [],
        ignore_automatic_attention_set_rules: true,
      });
    });

    test('comment is auto saved when dialog is canceled', async () => {
      const patchsetLevelComment = queryAndAssert<GrComment>(
        element,
        '#patchsetLevelComment'
      );
      const autoSaveStub = sinon
        .stub(patchsetLevelComment, 'save')
        .returns(Promise.resolve());

      patchsetLevelComment.messageText = 'hello world';

      await waitUntil(
        () => element.patchsetLevelDraftMessage === 'hello world'
      );
      assert.deepEqual(autoSaveStub.callCount, 0);

      patchsetLevelComment.messageText = '';
      queryAndAssert<GrButton>(element, '#cancelButton').click();

      await waitUntil(() => autoSaveStub.callCount === 1);

      assert.deepEqual(patchsetLevelComment.messageText, '');
    });

    test('replies to patchset level comments are not filtered out', async () => {
      const draft = {...createDraft(), in_reply_to: '1' as UrlEncodedCommentId};
      commentsModel.setState({
        drafts: {
          'abc.txt': [draft],
        },
        discardedDrafts: [],
      });
      await waitUntil(() => element.draftCommentThreads.length === 1);

      // patchset level draft as a reply is not loaded in patchsetLevel comment
      assert.equal(element.patchsetLevelDraftMessage, '');

      assert.deepEqual(element.draftCommentThreads[0].comments[0], draft);
    });
  });

  suite('mention users', () => {
    setup(async () => {
      stubFlags('isEnabled')
        .withArgs(KnownExperimentId.MENTION_USERS)
        .returns(true);
      element.account = createAccountWithId(1);
      element.requestUpdate();
      await element.updateComplete;
    });

    test('mentioned user in resolved draft is added to CC', async () => {
      const account = {
        ...createAccountWithEmail('abcd@def.com' as EmailAddress),
        _account_id: 1234 as AccountId,
        registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
      };
      stubRestApi('getAccountDetails').returns(Promise.resolve(account));
      const draft = {
        ...createDraft(),
        message: 'hey @abcd@def take a look at this',
      };
      commentsModel.setState({
        comments: {},
        robotComments: {},
        drafts: {
          a: [draft],
        },
        portedComments: {},
        portedDrafts: {},
        discardedDrafts: [],
      });

      element.draftCommentThreads = [createCommentThread([draft])];
      await waitUntil(() => element.mentionedUsers.length > 0);

      await element.updateComplete;

      assert.deepEqual(element.mentionedUsers, [account]);
      assert.deepEqual(element.ccs, [account]);

      // owner(999) is added since (accountId = 1) replied to the change
      assert.sameMembers([...element.newAttentionSet], [999 as AccountId]);
    });

    test('mentioned user in unresolved draft is added to CC and AttentionSet', async () => {
      const account = {
        ...createAccountWithEmail('abcd@def.com' as EmailAddress),
        _account_id: 1234 as AccountId,
        registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
      };
      stubRestApi('getAccountDetails').returns(Promise.resolve(account));
      const draft = {
        ...createDraft(),
        message: 'hey @abcd@def.com take a look at this',
        unresolved: true,
      };
      commentsModel.setState({
        comments: {},
        robotComments: {},
        drafts: {
          a: [draft],
        },
        portedComments: {},
        portedDrafts: {},
        discardedDrafts: [],
      });
      element.draftCommentThreads = [createCommentThread([draft])];
      await waitUntil(
        () => element.mentionedUsersInUnresolvedDrafts.length > 0
      );

      await element.updateComplete;

      assert.deepEqual(element.mentionedUsers, [account]);
      assert.deepEqual(element.ccs, [account]);

      // owner(999) is added since (accountId = 1) replied to the change
      assert.sameMembers(
        [...element.newAttentionSet],
        [999 as AccountId, 1234 as AccountId]
      );
    });

    test('mention user can be manually removed from attention set', async () => {
      stubRestApi('getAccountDetails').returns(
        Promise.resolve({
          ...createAccountWithEmail('abcd@def.com' as EmailAddress),
          _account_id: 1234 as AccountId,
          registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
        })
      );
      const draft = {
        ...createDraft(),
        message: 'hey @abcd@def.com take a look at this',
        unresolved: true,
      };
      commentsModel.setState({
        comments: {},
        robotComments: {},
        drafts: {
          a: [draft],
        },
        portedComments: {},
        portedDrafts: {},
        discardedDrafts: [],
      });
      element.draftCommentThreads = [createCommentThread([draft])];
      await waitUntil(
        () => element.mentionedUsersInUnresolvedDrafts.length > 0
      );

      await element.updateComplete;

      // owner(999) is added since (accountId = 1) replied to the change
      assert.sameMembers(
        [...element.newAttentionSet],
        [999 as AccountId, 1234 as AccountId]
      );

      const modifyButton = queryAndAssert<GrButton>(
        element,
        '.edit-attention-button'
      );
      modifyButton.click();
      await element.updateComplete;

      const accountsChips = Array.from(
        queryAll<GrAccountLabel>(element, '.attention-detail gr-account-label')
      );
      assert.deepEqual(accountsChips[1].account, {
        email: 'abcd@def.com' as EmailAddress,
        registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
        _account_id: 1234 as AccountId,
      } as AccountInfo);
      accountsChips[1].click();

      await element.updateComplete;

      assert.sameMembers([...element.newAttentionSet], [999 as AccountId]);
    });

    test('mention user who is already CCed', async () => {
      const account = {
        ...createAccountWithEmail('abcd@def.com' as EmailAddress),
        _account_id: 1234 as AccountId,
        registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
      };
      stubRestApi('getAccountDetails').returns(Promise.resolve(account));

      commentsModel.setState({
        comments: {},
        robotComments: {},
        drafts: {
          a: [
            {
              ...createDraft(),
              message: 'hey @abcd@def.com take a look at this',
              unresolved: true,
            },
          ],
        },
        portedComments: {},
        portedDrafts: {},
        discardedDrafts: [],
      });

      await element.updateComplete;
      await waitUntil(() => element.mentionedUsers.length > 0);

      assert.deepEqual(element.ccs, [account]);
      assert.deepEqual(element.mentionedUsers, [account]);
      element._ccs = [account];

      await element.updateComplete;

      assert.deepEqual(element.mentionedUsers, [account]);
      assert.deepEqual(element.ccs, [account]);
    });

    test('mention user who is already a reviewer', async () => {
      const account = {
        ...createAccountWithEmail('abcd@def.com' as EmailAddress),
        _account_id: 1234 as AccountId,
        registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
      };
      stubRestApi('getAccountDetails').returns(Promise.resolve(account));
      commentsModel.setState({
        comments: {},
        robotComments: {},
        drafts: {
          a: [
            {
              ...createDraft(),
              message: 'hey @abcd@def.com take a look at this',
              unresolved: true,
            },
          ],
        },
        portedComments: {},
        portedDrafts: {},
        discardedDrafts: [],
      });

      await element.updateComplete;
      await waitUntil(() => element.mentionedUsers.length > 0);

      assert.deepEqual(element.mentionedUsers, [account]);

      // ensure updates to reviewers is reflected to mentionedUsers property
      element.reviewers = [account];

      await element.updateComplete;

      // overall ccs is empty since we filter out existing reviewers
      assert.deepEqual(element.ccs, []);
      assert.deepEqual(element.mentionedUsers, [account]);
      assert.deepEqual(element.reviewers, [account]);
    });
  });

  test('setPluginMessage', async () => {
    element.setPluginMessage('foo');
    await element.updateComplete;
    assert.equal(queryAndAssert(element, '#pluginMessage').textContent, 'foo');
  });
});
