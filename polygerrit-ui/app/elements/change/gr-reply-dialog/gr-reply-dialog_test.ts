/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
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
  stubReporting,
  stubRestApi,
  waitEventLoop,
  waitUntilVisible,
} from '../../../test/test-utils';
import {
  ChangeStatus,
  DraftsAction,
  ReviewerState,
} from '../../../constants/constants';
import {StandardLabels} from '../../../utils/label-util';
import {
  createAccountWithEmail,
  createAccountWithId,
  createChange,
  createComment,
  createCommentThread,
  createDraft,
  createLabelInfo,
  createRevision,
  createServiceUserWithId,
} from '../../../test/test-data-generators';
import {GrReplyDialog} from './gr-reply-dialog';
import {
  AccountId,
  AccountInfo,
  ChangeInfo,
  CommentThread,
  CommitId,
  DetailedLabelInfo,
  EmailAddress,
  GroupId,
  GroupName,
  NumericChangeId,
  PatchSetNumber,
  ReviewerInput,
  ReviewInput,
  ReviewResult,
  RevisionPatchSetNum,
  Suggestion,
  Timestamp,
  UrlEncodedCommentId,
  UserId,
} from '../../../types/common';
import {GrAccountList} from '../../shared/gr-account-list/gr-account-list';
import {GrLabelScoreRow} from '../gr-label-score-row/gr-label-score-row';
import {GrLabelScores} from '../gr-label-scores/gr-label-scores';
import {assert, fixture, html, waitUntil} from '@open-wc/testing';
import {accountKey} from '../../../utils/account-util';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrAccountLabel} from '../../shared/gr-account-label/gr-account-label';
import {Key, Modifier} from '../../../utils/dom-util';
import {GrComment} from '../../shared/gr-comment/gr-comment';
import {testResolver} from '../../../test/common-test-setup';
import {
  CommentsModel,
  commentsModelToken,
} from '../../../models/comments/comments-model';
import {isOwner} from '../../../utils/change-util';
import {createNewPatchsetLevel} from '../../../utils/comment-util';
import {Timing} from '../../../constants/reporting';
import {ParsedChangeInfo} from '../../../types/types';
import {changeModelToken} from '../../../models/change/change-model';

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
  let latestPatchNum: PatchSetNumber;
  let commentsModel: CommentsModel;
  let change: ParsedChangeInfo;
  let changeNoRevisions: ChangeInfo;

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
    latestPatchNum = 1 as PatchSetNumber;
    const owner: AccountInfo = {
      _account_id: 999 as AccountId,
      display_name: 'Kermit',
      email: 'abcd' as EmailAddress,
    };

    changeNoRevisions = {
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

      current_revision_number: 1 as PatchSetNumber,
    };
    change = {
      ...changeNoRevisions,
      revisions: {'commit-id': {...createRevision(), uploader: owner}},
      current_revision: 'commit-id' as CommitId,
    };

    stubRestApi('getChange').returns(Promise.resolve(change as ChangeInfo));
    stubRestApi('getChangeSuggestedReviewers').returns(Promise.resolve([]));

    element = await fixture<GrReplyDialog>(html`
      <gr-reply-dialog></gr-reply-dialog>
    `);

    element.change = change;
    element.latestPatchNum = latestPatchNum;
    element.permittedLabels = {
      'Code-Review': ['-1', ' 0', '+1'],
      Verified: ['-1', ' 0', '+1'],
    };
    element.draftCommentThreads = [];
    commentsModel = testResolver(commentsModelToken);
    commentsModel.addNewDraft(
      createNewPatchsetLevel(latestPatchNum, '', false)
    );

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
            resolve(result);
          } catch (err) {
            reject(err);
          }
        })
    );
  }

  function interceptSaveReview(): Promise<ReviewInput> {
    let resolver: (review: ReviewInput) => void;
    const promise = new Promise<ReviewInput>(resolve => {
      resolver = resolve;
    });
    stubSaveReview((review: ReviewInput) => {
      resolver(review);
      return {
        change_info: changeNoRevisions,
      };
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
                  </div>
                  <div class="rightActions">
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
                      <div>
                        <gr-icon icon="edit" filled small></gr-icon>
                        <span>Modify</span>
                      </div>
                    </gr-button>
                    <a
                      href="/Documentation/user-attention-set.html"
                      target="_blank"
                      rel="noopener noreferrer"
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

  test('save review fires sendReply metric', async () => {
    const timeEndStub = stubReporting('timeEnd');

    // Async tick is needed because iron-selector content is distributed and
    // distributed content requires an observer to be set up.
    await element.updateComplete;
    element.patchsetLevelDraftMessage = 'I wholeheartedly disapprove';
    element.draftCommentThreads = [createCommentThread([createComment()])];

    element.includeComments = true;

    // This is needed on non-Blink engines most likely due to the ways in
    // which the dom-repeat elements are stamped.
    await element.updateComplete;
    queryAndAssert<GrButton>(element, '.send').click();

    await interceptSaveReview();
    await element.updateComplete;

    await waitUntil(() => timeEndStub.calledWith(Timing.SEND_REPLY));
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
      drafts: DraftsAction.PUBLISH_ALL_REVISIONS,
      labels: {
        'Code-Review': 0,
        Verified: 0,
      },
      reviewers: [],
      add_to_attention_set: [
        {
          reason: '<GERRIT_ACCOUNT_1> replied on the change',
          user: 999 as UserId,
        },
      ],
      remove_from_attention_set: [],
      ignore_automatic_attention_set_rules: true,
    });
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
      drafts: DraftsAction.PUBLISH_ALL_REVISIONS,
      labels: {
        'Code-Review': 0,
        Verified: 0,
      },
      add_to_attention_set: [
        {
          reason: '<GERRIT_ACCOUNT_123> replied on the change',
          user: 314 as UserId,
        },
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
      drafts: DraftsAction.PUBLISH_ALL_REVISIONS,
      labels: {
        'Code-Review': 0,
        Verified: 0,
      },
      add_to_attention_set: [
        // Name coming from createUserConfig in test-data-generator
        {
          reason: 'Name of user not set replied on the change',
          user: 314 as UserId,
        },
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
    element.isOwner = isOwner(change, element.account);

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
    element.isOwner = isOwner(element.change, element.account);
    element.computeNewAttention();
    await element.updateComplete;
    assert.sameMembers(
      [...element.newAttentionSet],
      [2 as AccountId, 3 as AccountId]
    );

    // ... but not when someone else replies.
    element.account = {_account_id: 4 as AccountId};
    element.isOwner = isOwner(element.change, element.account);
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

  test('computeCommentAccountsForAttention owner comments', () => {
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
    element.isOwner = true;
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
    const actualAccounts = [
      ...element.computeCommentAccountsForAttention(threads, false),
    ];
    // Account 3 is not included, because the comment is resolved *and* they
    // have given the highest possible vote on the Code-Review label.
    assert.sameMembers(actualAccounts, [1, 2, 4]);
  });

  test('computeCommentAccountsForAttention reviewer comments', () => {
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
            author: element.change.owner,
            unresolved: false,
          },
          {
            ...createComment(),
            id: '5' as UrlEncodedCommentId,
            in_reply_to: '4' as UrlEncodedCommentId,
            author: {_account_id: 4 as AccountId},
            unresolved: false,
          },
        ]),
      },
    ];
    const actualAccounts = [
      ...element.computeCommentAccountsForAttention(threads, false),
    ];
    // Accounts 1 and 2 are not included, because the thread is still unresolved
    // and the new comment is from another reviewer.
    // Account 3 is not included, because the comment is resolved *and* they
    // have given the highest possible vote on the Code-Review label.
    // element.change.owner is similarly not included, because they don't need
    // to vote. (In the overall logic owner is added as part of
    // computeNewAttention)
    assert.sameMembers(actualAccounts, [4]);
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
      drafts: DraftsAction.PUBLISH_ALL_REVISIONS,
      labels: {
        'Code-Review': -1,
        Verified: -1,
      },
      reviewers: [],
      add_to_attention_set: [
        {
          user: 999 as UserId,
          reason: '<GERRIT_ACCOUNT_1> replied on the change',
        },
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
      drafts: DraftsAction.KEEP,
      labels: {
        'Code-Review': 0,
        Verified: 0,
      },
      reviewers: [],
      add_to_attention_set: [
        {
          reason: '<GERRIT_ACCOUNT_1> replied on the change',
          user: 999 as UserId,
        },
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

    const group = {
      id: 'id' as GroupId,
      name: 'name' as GroupName,
    };
    if (cc) {
      element.ccPendingConfirmation = {
        group,
        confirm: false,
        count: 10,
      };
    } else {
      element.reviewerPendingConfirmation = {
        group,
        confirm: false,
        count: 10,
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

    assert.isTrue(
      isVisible(queryAndAssert(element, '#reviewerConfirmationModal'))
    );
    const expected = 'Group name has 10 members';
    assert.notEqual(
      queryAndAssert<HTMLElement>(
        element,
        '#reviewerConfirmationModal'
      ).innerText.indexOf(expected),
      -1
    );
    noButton.click(); // close the dialog
    await waitUntil(
      () => !isVisible(queryAndAssert(element, '#reviewerConfirmationModal'))
    );

    // TODO(dhruvsri): figure out why focus is not on the input element
    // We should be focused on account entry input.
    // const reviewersEntry = queryAndAssert<GrAccountList>(element, '#reviewers');
    // assert.isTrue(
    //   isFocusInsideElement(
    //     queryAndAssert<GrAutocomplete>(reviewersEntry.entry, '#input').input!
    //   )
    // );

    // No reviewer/CC should have been added.
    assert.equal(element.ccsList?.additions().length, 0);
    assert.equal(element.reviewersList?.additions().length, 0);

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

    assert.isTrue(
      isVisible(queryAndAssert(element, '#reviewerConfirmationModal'))
    );

    yesButton.click(); // Confirm the group.
    await waitUntil(
      () => !isVisible(queryAndAssert(element, '#reviewerConfirmationModal'))
    );
    const additions = cc
      ? element.ccsList?.additions()
      : element.reviewersList?.additions();
    assert.deepEqual(additions, [
      {
        confirmed: true,
        id: 'id' as GroupId,
        name: 'name' as GroupName,
      },
    ]);

    // We should be focused on account entry input.
    // TODO(dhruvsri): figure out why focus is not on the input element
    // if (cc) {
    //   const ccsEntry = queryAndAssert<GrAccountList>(element, '#ccs');
    //   assert.isTrue(
    //     isFocusInsideElement(
    //       queryAndAssert<GrAutocomplete>(ccsEntry.entry, '#input').input!
    //     )
    //   );
    // } else {
    //   const reviewersEntry = queryAndAssert<GrAccountList>(
    //     element,
    //     '#reviewers'
    //   );
    //   assert.isTrue(
    //     isFocusInsideElement(
    //       queryAndAssert<GrAutocomplete>(reviewersEntry.entry, '#input').input!
    //     )
    //   );
    // }
  }

  test('cc confirmation', async () => {
    testConfirmationDialog(true);
  });

  test('reviewer confirmation', async () => {
    testConfirmationDialog(false);
  });

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
      const restApiPromise = interceptSaveReview();
      element.send(false, false);
      await restApiPromise;

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
      const restApiPromise = interceptSaveReview();
      element.send(false, true);
      await restApiPromise;

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
    element.isOwner = false;
    assert.equal(element.chooseFocusTarget(), element.FocusTarget.BODY);

    element.isOwner = true;
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
    const promise = mockPromise();
    stubSaveReview((review: ReviewInput) => {
      assert.deepEqual(review?.labels, {
        'Code-Review': 0,
        Verified: -1,
      });
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

    assert.isTrue(element.attentionExpanded);

    let accountLabels = Array.from(
      queryAll(element, '.attention-detail gr-account-label')
    );
    assert.equal(accountLabels.length, 5);

    element.reviewers = [...element.reviewers, makeAccount()];
    element._ccs = [...element.ccs, makeAccount()];
    await element.updateComplete;

    assert.isTrue(element.attentionExpanded);
    accountLabels = Array.from(
      queryAll(element, '.attention-detail gr-account-label')
    );
    assert.equal(accountLabels.length, 7);

    // Verify that toggling the attention-set-button collapses.
    queryAndAssert<GrButton>(element, '.edit-attention-button').click();
    await element.updateComplete;
    assert.isFalse(element.attentionExpanded);

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
      new CustomEvent('remove-account', {
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
      new CustomEvent('remove-account', {
        detail: {account: cc1},
        composed: true,
        bubbles: true,
      })
    );
    ccs.dispatchEvent(
      new CustomEvent('remove-account', {
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
      state?: ReviewerState
    ) {
      const result: ReviewerInput = {
        reviewer: reviewer._account_id as AccountId,
      };
      if (state) {
        result.state = state;
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

  test('send and start review on ctrl+enter for owner', async () => {
    element.canBeStarted = true;
    element.isOwner = true;
    await element.updateComplete;

    const savePromise = interceptSaveReview();
    pressKey(element, Key.ENTER, Modifier.CTRL_KEY);
    const reviewInput = await savePromise;
    assert.isTrue(reviewInput.ready);
  });

  test('save on ctrl+enter for reviewer', async () => {
    element.canBeStarted = true;
    element.isOwner = false;
    await element.updateComplete;

    const savePromise = interceptSaveReview();
    pressKey(element, Key.ENTER, Modifier.CTRL_KEY);
    const reviewInput = await savePromise;
    assert.isUndefined(reviewInput.ready);
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

  test('sendButton text', async () => {
    element.canBeStarted = false;
    await element.updateComplete;
    assert.equal(element.sendButton?.innerText, 'Send');

    element.canBeStarted = true;
    await element.updateComplete;
    assert.equal(element.sendButton?.innerText, 'Send and Start Review');
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

  test('isSendDisabled_canBeStarted', () => {
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
    assert.isFalse(element.isSendDisabled());
  });

  test('isSendDisabled_allFalse', () => {
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
    assert.isTrue(element.isSendDisabled());
  });

  test('isSendDisabled_draftCommentsSend', () => {
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
    assert.isFalse(element.isSendDisabled());
  });

  test('isSendDisabled_draftCommentsDoNotSend', () => {
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

    assert.isTrue(element.isSendDisabled());
  });

  test('isSendDisabled_changeMessage', () => {
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

    assert.isFalse(element.isSendDisabled());
  });

  test('isSendDisabledreviewersChanged', () => {
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

    assert.isFalse(element.isSendDisabled());
  });

  test('isSendDisabled_labelsChanged', () => {
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

    assert.isFalse(element.isSendDisabled());
  });

  test('isSendDisabled_dialogDisabled', () => {
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

    assert.isTrue(element.isSendDisabled());
  });

  test('isSendDisabled_existingVote', async () => {
    const account = createAccountWithId();
    (
      element.change!.labels![StandardLabels.CODE_REVIEW] as DetailedLabelInfo
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
    assert.isFalse(element.isSendDisabled());
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
      assert.isTrue(element.isSendDisabled());

      queryAndAssert<GrComment>(element, '#patchsetLevelComment').messageText =
        'hello';
      await waitUntil(() => element.patchsetLevelDraftMessage === 'hello');

      assert.isFalse(element.isSendDisabled());

      queryAndAssert<GrComment>(element, '#patchsetLevelComment').messageText =
        '';
      await waitUntil(() => element.patchsetLevelDraftMessage === '');

      assert.isTrue(element.isSendDisabled());
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
      await element.updateComplete;

      const saveReviewPromise = interceptSaveReview();

      assert.deepEqual(autoSaveStub.callCount, 0);

      queryAndAssert<GrButton>(element, '.send').click();

      const review = await saveReviewPromise;

      assert.deepEqual(autoSaveStub.callCount, 0);

      assert.deepEqual(review, {
        drafts: DraftsAction.PUBLISH_ALL_REVISIONS,
        labels: {
          'Code-Review': 0,
          Verified: 0,
        },
        reviewers: [],
        add_to_attention_set: [
          {
            reason: '<GERRIT_ACCOUNT_1> replied on the change',
            user: 999 as UserId,
          },
        ],
        comments: {
          '/PATCHSET_LEVEL': [
            {
              message: 'hello world',
              path: '/PATCHSET_LEVEL',
              unresolved: false,
            },
          ],
        },
        remove_from_attention_set: [],
        ignore_automatic_attention_set_rules: true,
      });
    });

    test('sending waits for inflight autosave', async () => {
      const patchsetLevelComment = queryAndAssert<GrComment>(
        element,
        '#patchsetLevelComment'
      );

      const waitForPendingDiffDrafts = stubRestApi(
        'awaitPendingDiffDrafts'
      ).returns(Promise.resolve());

      patchsetLevelComment.messageText = 'hello world';
      await waitUntil(
        () => element.patchsetLevelDraftMessage === 'hello world'
      );
      await element.updateComplete;

      const saveReviewPromise = interceptSaveReview();

      queryAndAssert<GrButton>(element, '.send').click();

      const review = await saveReviewPromise;
      assert.deepEqual(waitForPendingDiffDrafts.callCount, 1);

      assert.deepEqual(review, {
        drafts: DraftsAction.PUBLISH_ALL_REVISIONS,
        labels: {
          'Code-Review': 0,
          Verified: 0,
        },
        reviewers: [],
        add_to_attention_set: [
          {
            reason: '<GERRIT_ACCOUNT_1> replied on the change',
            user: 999 as UserId,
          },
        ],
        comments: {
          '/PATCHSET_LEVEL': [
            {
              message: 'hello world',
              path: '/PATCHSET_LEVEL',
              unresolved: false,
            },
          ],
        },
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

    test('comment is auto saved when ESC is pressed from patchset level comment', async () => {
      const patchsetLevelComment = queryAndAssert<GrComment>(
        element,
        '#patchsetLevelComment'
      );
      const autoSaveStub = sinon
        .stub(patchsetLevelComment, 'save')
        .returns(Promise.resolve());
      const cancelSpy = sinon.spy(element, 'cancel');

      patchsetLevelComment.messageText = 'hello world';

      await waitUntil(
        () => element.patchsetLevelDraftMessage === 'hello world'
      );
      assert.deepEqual(autoSaveStub.callCount, 0);

      patchsetLevelComment.messageText = '';
      pressKey(element, Key.ESC);

      await waitUntil(() => autoSaveStub.callCount === 1);
      assert.isTrue(cancelSpy.called);
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

  test('manually added users are not lost when view updates.', async () => {
    assert.sameMembers([...element.newAttentionSet], []);

    element.reviewers = [
      createAccountWithId(1),
      createAccountWithId(2),
      createAccountWithId(3),
    ];
    element.patchsetLevelDraftMessage = 'abc';

    await element.updateComplete;
    assert.sameMembers(
      [...element.newAttentionSet],
      [2 as AccountId, 3 as AccountId, 999 as AccountId]
    );

    const modifyButton = queryAndAssert<GrButton>(
      element,
      '.edit-attention-button'
    );

    modifyButton.click();
    assert.isTrue(element.attentionExpanded);
    await element.updateComplete;

    const accountsChips = Array.from(
      queryAll<GrAccountLabel>(element, '.attention-detail gr-account-label')
    );
    assert.equal(accountsChips.length, 4);
    for (let i = 0; i < 4; ++i) {
      if (accountsChips[i].account?._account_id === 1) {
        accountsChips[i].click();
        break;
      }
    }

    await element.updateComplete;

    assert.sameMembers(
      [...element.newAttentionSet],
      [1 as AccountId, 2 as AccountId, 3 as AccountId, 999 as AccountId]
    );

    // Doesn't get reset when message changes.
    element.patchsetLevelDraftMessage = 'def';
    await element.updateComplete;

    assert.sameMembers(
      [...element.newAttentionSet],
      [1 as AccountId, 2 as AccountId, 3 as AccountId, 999 as AccountId]
    );
  });

  test('reload change if patchset updated', async () => {
    // Async tick is needed because iron-selector content is distributed and
    // distributed content requires an observer to be set up.
    await element.updateComplete;
    const changeModel = testResolver(changeModelToken);
    const changeStateUpdateSpy = sinon.spy(changeModel, 'updateStateChange');
    const responseChange = {
      ...change,
      labels: {Verified: createLabelInfo(-1)},
      revisions: undefined,
      current_revision: undefined,
      current_revision_number: (change.current_revision_number +
        1) as PatchSetNumber,
    };
    stubSaveReview(() => {
      return {
        change_info: responseChange as ChangeInfo,
      };
    });
    const reloadPromise = mockPromise();
    let reloadTriggered = false;
    document.addEventListener('reload', () => {
      reloadTriggered = true;
      reloadPromise.resolve();
    });

    // Set a different label value
    const el = queryAndAssert<GrLabelScoreRow>(
      queryAndAssert(element, 'gr-label-scores'),
      'gr-label-score-row[name="Verified"]'
    );
    el.setSelectedValue('-1');
    await element.updateComplete;
    await element.updateComplete;

    queryAndAssert<GrButton>(element, '.send').click();
    await element.updateComplete;

    await reloadPromise;
    assert.isTrue(reloadTriggered);

    // All revision information is old, but all other information is new.
    const expectedChange = {...change, labels: {Verified: createLabelInfo(-1)}};
    assert.deepEqual(changeStateUpdateSpy.firstCall.args[0], expectedChange);
  });

  test('no reload if patchset is the same', async () => {
    // Async tick is needed because iron-selector content is distributed and
    // distributed content requires an observer to be set up.
    await element.updateComplete;
    const changeModel = testResolver(changeModelToken);
    const changeStateUpdateSpy = sinon.spy(changeModel, 'updateStateChange');
    const responseChange = {
      ...change,
      labels: {Verified: createLabelInfo(-1)},
      revisions: undefined,
      current_revision: undefined,
      current_revision_number: change.current_revision_number,
    };
    stubSaveReview(() => {
      return {
        change_info: responseChange as ChangeInfo,
      };
    });
    let reloadTriggered = false;
    document.addEventListener('reload', () => {
      reloadTriggered = true;
    });

    // Set a different label value
    const el = queryAndAssert<GrLabelScoreRow>(
      queryAndAssert(element, 'gr-label-scores'),
      'gr-label-score-row[name="Verified"]'
    );
    el.setSelectedValue('-1');
    await element.updateComplete;
    await element.updateComplete;

    queryAndAssert<GrButton>(element, '.send').click();
    await element.updateComplete;

    await waitEventLoop();
    assert.isFalse(reloadTriggered);
    // All revision information is old, but all other information is new.
    const expectedChange = {...change, labels: {Verified: createLabelInfo(-1)}};
    assert.deepEqual(changeStateUpdateSpy.firstCall.args[0], expectedChange);
  });

  suite('mention users', () => {
    setup(async () => {
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

      // Random update
      element.patchsetLevelDraftMessage = 'abc';
      await element.updateComplete;

      assert.sameMembers([...element.newAttentionSet], [999 as AccountId]);
      element.patchsetLevelDraftMessage = 'abc';
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

  test('show warning when change is merged', async () => {
    element.isChangeMerged = false;
    await element.updateComplete;
    let warning = query(element, '#changeIsMergedLabel');
    assert.isNotOk(warning);

    element.isChangeMerged = true;
    await element.updateComplete;
    warning = queryAndAssert(element, '#changeIsMergedLabel');
    assert.isOk(warning);
    assert.include(warning.textContent, 'Change has already been merged');
  });
});
