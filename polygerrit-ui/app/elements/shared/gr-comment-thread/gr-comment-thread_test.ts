/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-comment-thread';
import {DraftInfo, sortComments} from '../../../utils/comment-util';
import {GrCommentThread} from './gr-comment-thread';
import {
  NumericChangeId,
  UrlEncodedCommentId,
  Timestamp,
  CommentInfo,
  RepoName,
} from '../../../types/common';
import {
  mockPromise,
  queryAndAssert,
  stubRestApi,
  waitUntilCalled,
  MockPromise,
} from '../../../test/test-utils';
import {
  createAccountDetailWithId,
  createThread,
} from '../../../test/test-data-generators';
import {tap} from '@polymer/iron-test-helpers/mock-interactions';
import {SinonStub} from 'sinon';
import {waitUntil} from '@open-wc/testing-helpers';

const basicFixture = fixtureFromElement('gr-comment-thread');

const c1 = {
  author: {name: 'Kermit'},
  id: 'the-root' as UrlEncodedCommentId,
  message: 'start the conversation',
  updated: '2021-11-01 10:11:12.000000000' as Timestamp,
};

const c2 = {
  author: {name: 'Ms Piggy'},
  id: 'the-reply' as UrlEncodedCommentId,
  message: 'keep it going',
  updated: '2021-11-02 10:11:12.000000000' as Timestamp,
  in_reply_to: 'the-root' as UrlEncodedCommentId,
};

const c3 = {
  author: {name: 'Kermit'},
  id: 'the-draft' as UrlEncodedCommentId,
  message: 'stop it',
  updated: '2021-11-03 10:11:12.000000000' as Timestamp,
  in_reply_to: 'the-reply' as UrlEncodedCommentId,
  __draft: true,
};

const commentWithContext = {
  author: {name: 'Kermit'},
  id: 'the-draft' as UrlEncodedCommentId,
  message: 'just for context',
  updated: '2021-11-03 10:11:12.000000000' as Timestamp,
  line: 5,
  context_lines: [
    {line_number: 4, context_line: 'content of line 4'},
    {line_number: 5, context_line: 'content of line 5'},
    {line_number: 6, context_line: 'content of line 6'},
  ],
};

suite('gr-comment-thread tests', () => {
  let element: GrCommentThread;

  setup(async () => {
    stubRestApi('getLoggedIn').returns(Promise.resolve(false));
    element = basicFixture.instantiate();
    element.changeNum = 1 as NumericChangeId;
    element.showFileName = true;
    element.showFilePath = true;
    element.repoName = 'test-repo-name' as RepoName;
    await element.updateComplete;
    element.account = {...createAccountDetailWithId(13), name: 'Yoda'};
  });

  test('renders with draft', async () => {
    element.thread = createThread(c1, c2, c3);
    await element.updateComplete;
  });

  test('renders with draft', async () => {
    element.thread = createThread(c1, c2, c3);
    await element.updateComplete;
    expect(element).shadowDom.to.equal(/* HTML */ `
      <div class="fileName">
        <span>test-path-comment-thread</span>
        <gr-copy-clipboard hideinput=""></gr-copy-clipboard>
      </div>
      <div class="pathInfo">
        <span>#314</span>
      </div>
      <div id="container">
        <h3 class="assistive-tech-only">Draft Comment thread by Kermit</h3>
        <div class="comment-box" tabindex="0">
          <gr-comment
            collapsed=""
            initially-collapsed=""
            robot-button-disabled=""
            show-patchset=""
          ></gr-comment>
          <gr-comment
            collapsed=""
            initially-collapsed=""
            robot-button-disabled=""
            show-patchset=""
          ></gr-comment>
          <gr-comment robot-button-disabled="" show-patchset=""></gr-comment>
        </div>
      </div>
    `);
  });

  test('renders unsaved', async () => {
    element.thread = createThread();
    await element.updateComplete;
    expect(element).shadowDom.to.equal(/* HTML */ `
      <div class="fileName">
        <span>test-path-comment-thread</span>
        <gr-copy-clipboard hideinput=""></gr-copy-clipboard>
      </div>
      <div class="pathInfo">
        <span>#314</span>
      </div>
      <div id="container">
        <h3 class="assistive-tech-only">
          Unresolved Draft Comment thread by Yoda
        </h3>
        <div class="comment-box unresolved" tabindex="0">
          <gr-comment robot-button-disabled="" show-patchset=""></gr-comment>
        </div>
      </div>
    `);
  });

  test('renders with actions resolved', async () => {
    element.thread = createThread(c1, c2);
    await element.updateComplete;
    expect(queryAndAssert(element, '#container')).dom.to.equal(/* HTML */ `
      <div id="container">
        <h3 class="assistive-tech-only">Comment thread by Kermit</h3>
        <div class="comment-box" tabindex="0">
          <gr-comment
            collapsed=""
            initially-collapsed=""
            show-patchset=""
          ></gr-comment>
          <gr-comment
            collapsed=""
            initially-collapsed=""
            show-patchset=""
          ></gr-comment>
          <div id="actionsContainer">
            <span id="unresolvedLabel"> Resolved </span>
            <div id="actions">
              <iron-icon
                class="copy link-icon"
                icon="gr-icons:link"
                role="button"
                tabindex="0"
                title="Copy link to this comment"
              >
              </iron-icon>
              <gr-button
                aria-disabled="false"
                class="action reply"
                id="replyBtn"
                link=""
                role="button"
                tabindex="0"
              >
                Reply
              </gr-button>
              <gr-button
                aria-disabled="false"
                class="action quote"
                id="quoteBtn"
                link=""
                role="button"
                tabindex="0"
              >
                Quote
              </gr-button>
            </div>
          </div>
        </div>
      </div>
    `);
  });

  test('renders with actions unresolved', async () => {
    element.thread = createThread(c1, {...c2, unresolved: true});
    await element.updateComplete;
    expect(queryAndAssert(element, '#container')).dom.to.equal(/* HTML */ `
      <div id="container">
        <h3 class="assistive-tech-only">Unresolved Comment thread by Kermit</h3>
        <div class="comment-box unresolved" tabindex="0">
          <gr-comment show-patchset=""></gr-comment>
          <gr-comment show-patchset=""></gr-comment>
          <div id="actionsContainer">
            <span id="unresolvedLabel"> Unresolved </span>
            <div id="actions">
              <iron-icon
                class="copy link-icon"
                icon="gr-icons:link"
                role="button"
                tabindex="0"
                title="Copy link to this comment"
              >
              </iron-icon>
              <gr-button
                aria-disabled="false"
                class="action reply"
                id="replyBtn"
                link=""
                role="button"
                tabindex="0"
              >
                Reply
              </gr-button>
              <gr-button
                aria-disabled="false"
                class="action quote"
                id="quoteBtn"
                link=""
                role="button"
                tabindex="0"
              >
                Quote
              </gr-button>
              <gr-button
                aria-disabled="false"
                class="action ack"
                id="ackBtn"
                link=""
                role="button"
                tabindex="0"
              >
                Ack
              </gr-button>
              <gr-button
                aria-disabled="false"
                class="action done"
                id="doneBtn"
                link=""
                role="button"
                tabindex="0"
              >
                Done
              </gr-button>
            </div>
          </div>
        </div>
      </div>
    `);
  });

  test('renders with diff', async () => {
    element.showCommentContext = true;
    element.thread = createThread(commentWithContext);
    await element.updateComplete;
    expect(queryAndAssert(element, '.diff-container')).dom.to.equal(/* HTML */ `
      <div class="diff-container">
        <gr-diff
          class="disable-context-control-buttons hide-line-length-indicator no-left"
          id="diff"
          style="--line-limit-marker:100ch; --content-width:none; --diff-max-width:none; --font-size:12px;"
        >
        </gr-diff>
        <div class="view-diff-container">
          <a href="">
            <gr-button
              aria-disabled="false"
              class="view-diff-button"
              link=""
              role="button"
              tabindex="0"
            >
              View Diff
            </gr-button>
          </a>
        </div>
      </div>
    `);
  });

  suite('action button clicks', () => {
    let savePromise: MockPromise<DraftInfo>;
    let stub: SinonStub;

    setup(async () => {
      savePromise = mockPromise<DraftInfo>();
      stub = sinon
        .stub(element.getCommentsModel(), 'saveDraft')
        .returns(savePromise);

      element.thread = createThread(c1, {...c2, unresolved: true});
      await element.updateComplete;
    });

    test('handle Ack', async () => {
      tap(queryAndAssert(element, '#ackBtn'));
      waitUntilCalled(stub, 'saveDraft()');
      assert.equal(stub.lastCall.firstArg.message, 'Ack');
      assert.equal(stub.lastCall.firstArg.unresolved, false);
      assert.isTrue(element.saving);

      savePromise.resolve();
      await element.updateComplete;
      assert.isFalse(element.saving);
    });

    test('handle Done', async () => {
      tap(queryAndAssert(element, '#doneBtn'));
      waitUntilCalled(stub, 'saveDraft()');
      assert.equal(stub.lastCall.firstArg.message, 'Done');
      assert.equal(stub.lastCall.firstArg.unresolved, false);
    });

    test('handle Reply', async () => {
      assert.isUndefined(element.unsavedComment);
      tap(queryAndAssert(element, '#replyBtn'));
      assert.equal(element.unsavedComment?.message, '');
    });

    test('handle Quote', async () => {
      assert.isUndefined(element.unsavedComment);
      tap(queryAndAssert(element, '#quoteBtn'));
      assert.equal(element.unsavedComment?.message?.trim(), `> ${c2.message}`);
    });
  });

  suite('self removal when empty thread changed to editing:false', () => {
    let threadEl: GrCommentThread;

    setup(async () => {
      threadEl = basicFixture.instantiate();
      threadEl.thread = createThread();
    });

    test('new thread el normally has a parent and an unsaved comment', async () => {
      await waitUntil(() => threadEl.editing);
      assert.isOk(threadEl.unsavedComment);
      assert.isOk(threadEl.parentElement);
    });

    test('thread el removed after clicking CANCEL', async () => {
      await waitUntil(() => threadEl.editing);

      const commentEl = queryAndAssert(threadEl, 'gr-comment');
      const buttonEl = queryAndAssert(commentEl, 'gr-button.cancel');
      tap(buttonEl);

      await waitUntil(() => !threadEl.editing);
      assert.isNotOk(threadEl.parentElement);
    });
  });

  test('comments are sorted correctly', () => {
    const comments: CommentInfo[] = [
      {
        id: 'jacks_confession' as UrlEncodedCommentId,
        in_reply_to: 'sallys_confession' as UrlEncodedCommentId,
        message: 'i like you, too',
        updated: '2015-12-25 15:00:20.396000000' as Timestamp,
      },
      {
        id: 'sallys_confession' as UrlEncodedCommentId,
        message: 'i like you, jack',
        updated: '2015-12-24 15:00:20.396000000' as Timestamp,
      },
      {
        id: 'sally_to_dr_finklestein' as UrlEncodedCommentId,
        message: 'i’m running away',
        updated: '2015-10-31 09:00:20.396000000' as Timestamp,
      },
      {
        id: 'sallys_defiance' as UrlEncodedCommentId,
        in_reply_to: 'sally_to_dr_finklestein' as UrlEncodedCommentId,
        message: 'i will poison you so i can get away',
        updated: '2015-10-31 15:00:20.396000000' as Timestamp,
      },
      {
        id: 'dr_finklesteins_response' as UrlEncodedCommentId,
        in_reply_to: 'sally_to_dr_finklestein' as UrlEncodedCommentId,
        message: 'no i will pull a thread and your arm will fall off',
        updated: '2015-10-31 11:00:20.396000000' as Timestamp,
      },
      {
        id: 'sallys_mission' as UrlEncodedCommentId,
        message: 'i have to find santa',
        updated: '2015-12-24 15:00:20.396000000' as Timestamp,
      },
    ];
    const results = sortComments(comments);
    assert.deepEqual(results, [
      {
        id: 'sally_to_dr_finklestein' as UrlEncodedCommentId,
        message: 'i’m running away',
        updated: '2015-10-31 09:00:20.396000000' as Timestamp,
      },
      {
        id: 'dr_finklesteins_response' as UrlEncodedCommentId,
        in_reply_to: 'sally_to_dr_finklestein' as UrlEncodedCommentId,
        message: 'no i will pull a thread and your arm will fall off',
        updated: '2015-10-31 11:00:20.396000000' as Timestamp,
      },
      {
        id: 'sallys_defiance' as UrlEncodedCommentId,
        in_reply_to: 'sally_to_dr_finklestein' as UrlEncodedCommentId,
        message: 'i will poison you so i can get away',
        updated: '2015-10-31 15:00:20.396000000' as Timestamp,
      },
      {
        id: 'sallys_confession' as UrlEncodedCommentId,
        message: 'i like you, jack',
        updated: '2015-12-24 15:00:20.396000000' as Timestamp,
      },
      {
        id: 'sallys_mission' as UrlEncodedCommentId,
        message: 'i have to find santa',
        updated: '2015-12-24 15:00:20.396000000' as Timestamp,
      },
      {
        id: 'jacks_confession' as UrlEncodedCommentId,
        in_reply_to: 'sallys_confession' as UrlEncodedCommentId,
        message: 'i like you, too',
        updated: '2015-12-25 15:00:20.396000000' as Timestamp,
      },
    ]);
  });
});
