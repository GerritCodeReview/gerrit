/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-comment-thread';
import {sortComments} from '../../../utils/comment-util';
import {GrCommentThread} from './gr-comment-thread';
import {
  NumericChangeId,
  UrlEncodedCommentId,
  Timestamp,
  CommentInfo,
  RepoName,
  DraftInfo,
  SavingState,
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
  createNewDraft,
} from '../../../test/test-data-generators';
import {SinonStubbedMember} from 'sinon';
import {fixture, html, assert} from '@open-wc/testing';
import {GrButton} from '../gr-button/gr-button';
import {SpecialFilePath} from '../../../constants/constants';
import {GrIcon} from '../gr-icon/gr-icon';
import {
  CommentsModel,
  commentsModelToken,
} from '../../../models/comments/comments-model';
import {testResolver} from '../../../test/common-test-setup';
import {
  ChangeChildView,
  changeViewModelToken,
} from '../../../models/views/change';
import {GerritView} from '../../../services/router/router-model';

const c1: CommentInfo = {
  author: {name: 'Kermit'},
  id: 'the-root' as UrlEncodedCommentId,
  message: 'start the conversation',
  updated: '2021-11-01 10:11:12.000000000' as Timestamp,
};

const c2: CommentInfo = {
  author: {name: 'Ms Piggy'},
  id: 'the-reply' as UrlEncodedCommentId,
  message: 'keep it going',
  updated: '2021-11-02 10:11:12.000000000' as Timestamp,
  in_reply_to: 'the-root' as UrlEncodedCommentId,
};

const c3: DraftInfo = {
  author: {name: 'Kermit'},
  id: 'the-draft' as UrlEncodedCommentId,
  message: 'stop it',
  updated: '2021-11-03 10:11:12.000000000' as Timestamp,
  in_reply_to: 'the-reply' as UrlEncodedCommentId,
  savingState: SavingState.OK,
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
    testResolver(changeViewModelToken).setState({
      view: GerritView.CHANGE,
      childView: ChangeChildView.OVERVIEW,
      changeNum: 1 as NumericChangeId,
      repo: 'test-repo-name' as RepoName,
    });
    stubRestApi('getLoggedIn').returns(Promise.resolve(false));
    element = await fixture(html`<gr-comment-thread></gr-comment-thread>`);
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
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="fileName">
          <a href="/c/test-repo-name/+/1/1/test-path-comment-thread">
            test-path-comment-thread
          </a>
          <gr-copy-clipboard hideinput=""></gr-copy-clipboard>
        </div>
        <div class="pathInfo">
          <a href="/c/test-repo-name/+/1/comment/the-root/"> #314 </a>
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
            <gr-comment
              class="draft"
              robot-button-disabled=""
              show-patchset=""
            ></gr-comment>
          </div>
        </div>
      `
    );
  });

  test('renders unsaved', async () => {
    element.thread = createThread(createNewDraft());
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="fileName">
          <a href="/c/test-repo-name/+/1/1/test-path-comment-thread">
            test-path-comment-thread
          </a>
          <gr-copy-clipboard hideinput=""></gr-copy-clipboard>
        </div>
        <div class="pathInfo">
          <span>#314</span>
        </div>
        <div id="container">
          <h3 class="assistive-tech-only">Draft Comment thread by Yoda</h3>
          <div class="comment-box" tabindex="0">
            <gr-comment
              class="draft"
              robot-button-disabled=""
              show-patchset=""
            ></gr-comment>
          </div>
        </div>
      `
    );
  });

  test('renders with actions resolved', async () => {
    element.thread = createThread(c1, c2);
    await element.updateComplete;
    assert.dom.equal(
      queryAndAssert(element, '#container'),
      /* HTML */ `
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
                <gr-icon
                  icon="link"
                  class="copy link-icon"
                  role="button"
                  tabindex="0"
                  title="Copy link to this comment"
                ></gr-icon>
              </div>
            </div>
          </div>
        </div>
      `
    );
  });

  test('renders with actions unresolved', async () => {
    element.thread = createThread(c1, {...c2, unresolved: true});
    await element.updateComplete;
    assert.dom.equal(
      queryAndAssert(element, '#container'),
      /* HTML */ `
        <div id="container">
          <h3 class="assistive-tech-only">
            Unresolved Comment thread by Kermit
          </h3>
          <div class="comment-box unresolved" tabindex="0">
            <gr-comment show-patchset=""></gr-comment>
            <gr-comment show-patchset=""></gr-comment>
            <div id="actionsContainer">
              <span id="unresolvedLabel"> Unresolved </span>
              <div id="actions">
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
                <gr-icon
                  icon="link"
                  class="copy link-icon"
                  role="button"
                  tabindex="0"
                  title="Copy link to this comment"
                ></gr-icon>
              </div>
            </div>
          </div>
        </div>
      `
    );
  });

  test('renders with diff', async () => {
    element.showCommentContext = true;
    element.thread = createThread(commentWithContext);
    await element.updateComplete;
    assert.dom.equal(
      queryAndAssert(element, '.diff-container'),
      /* HTML */ `
        <div class="diff-container">
          <gr-diff
            class="disable-context-control-buttons hide-line-length-indicator"
            id="diff"
            style="--line-limit-marker:100ch; --content-width:none; --diff-max-width:none; --font-size:12px;"
          >
          </gr-diff>
          <div class="view-diff-container">
            <a href="/c/test-repo-name/+/1/comment/the-draft/">
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
      `,
      {ignoreAttributes: ['style']}
    );
  });

  suite('action button clicks', () => {
    let savePromise: MockPromise<DraftInfo>;
    let stubSave: SinonStubbedMember<CommentsModel['saveDraft']>;
    let stubAdd: SinonStubbedMember<CommentsModel['addNewDraft']>;

    setup(async () => {
      savePromise = mockPromise<DraftInfo>();
      stubSave = sinon
        .stub(testResolver(commentsModelToken), 'saveDraft')
        .returns(savePromise);
      stubAdd = sinon.stub(testResolver(commentsModelToken), 'addNewDraft');

      element.thread = createThread(c1, {...c2, unresolved: true});
      await element.updateComplete;
    });

    test('handle Acknowledge', async () => {
      queryAndAssert<GrButton>(element, '#ackBtn').click();
      waitUntilCalled(stubSave, 'saveDraft()');
      assert.equal(stubSave.lastCall.firstArg.message, 'Acknowledged');
      assert.equal(stubSave.lastCall.firstArg.unresolved, false);
      assert.isTrue(element.saving);

      savePromise.resolve();
      await element.updateComplete;
      assert.isFalse(element.saving);
    });

    test('handle Done', async () => {
      queryAndAssert<GrButton>(element, '#doneBtn').click();
      waitUntilCalled(stubSave, 'saveDraft()');
      assert.equal(stubSave.lastCall.firstArg.message, 'Done');
      assert.equal(stubSave.lastCall.firstArg.unresolved, false);
    });

    test('handle Reply', async () => {
      assert.equal(element.thread?.comments.length, 2);
      queryAndAssert<GrButton>(element, '#replyBtn').click();
      assert.isTrue(stubAdd.called);
      assert.equal(stubAdd.lastCall.firstArg.message, '');
    });

    test('handle Quote', async () => {
      assert.equal(element.thread?.comments.length, 2);
      queryAndAssert<GrButton>(element, '#quoteBtn').click();
      assert.isTrue(stubAdd.called);
      assert.equal(stubAdd.lastCall.firstArg.message.trim(), `> ${c2.message}`);
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

  test('patchset comments link to /comments URL', async () => {
    const clipboardStub = sinon.stub(navigator.clipboard, 'writeText');
    element.thread = {
      ...createThread(c1),
      path: SpecialFilePath.PATCHSET_LEVEL_COMMENTS,
    };
    await element.updateComplete;

    queryAndAssert<GrIcon>(element, 'gr-icon.copy').click();

    assert.equal(1, clipboardStub.callCount);
    assert.equal(
      clipboardStub.firstCall.args[0],
      'http://localhost:9876/c/test-repo-name/+/1/comments/the-root'
    );
  });

  test('file comments link to /comment URL', async () => {
    const clipboardStub = sinon.stub(navigator.clipboard, 'writeText');
    element.thread = createThread(c1);
    await element.updateComplete;

    queryAndAssert<GrIcon>(element, 'gr-icon.copy').click();

    assert.equal(1, clipboardStub.callCount);
    assert.equal(
      clipboardStub.firstCall.args[0],
      'http://localhost:9876/c/test-repo-name/+/1/comment/the-root/'
    );
  });
});
