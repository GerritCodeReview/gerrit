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
  AccountId,
  CommentInfo,
  CommentThread,
  DraftInfo,
  NumericChangeId,
  RepoName,
  RevisionPatchSetNum,
  SavingState,
  Timestamp,
  UrlEncodedCommentId,
} from '../../../types/common';
import {
  MockPromise,
  mockPromise,
  query,
  queryAndAssert,
  stubRestApi,
  waitUntil,
  waitUntilCalled,
} from '../../../test/test-utils';
import {
  createAccountDetailWithId,
  createComment,
  createFixSuggestionInfo,
  createNewDraft,
  createThread,
} from '../../../test/test-data-generators';
import {SinonStubbedMember} from 'sinon';
import {assert, fixture, html} from '@open-wc/testing';
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
import {GrComment} from '../gr-comment/gr-comment';
import {GrSuggestionTextarea} from '../gr-suggestion-textarea/gr-suggestion-textarea';
import {suggestionsServiceToken} from '../../../services/suggestions/suggestions-service';

const c1: CommentInfo = {
  author: {name: 'Kermit', _account_id: 1 as AccountId},
  id: 'the-root' as UrlEncodedCommentId,
  message: 'start the conversation',
  updated: '2021-11-01 10:11:12.000000000' as Timestamp,
};

const c2: CommentInfo = {
  author: {name: 'Ms Piggy', _account_id: 2 as AccountId},
  id: 'the-reply' as UrlEncodedCommentId,
  message: 'keep it going',
  updated: '2021-11-02 10:11:12.000000000' as Timestamp,
  in_reply_to: 'the-root' as UrlEncodedCommentId,
};

const c3: DraftInfo = {
  author: {name: 'Kermit', _account_id: 1 as AccountId},
  id: 'the-draft' as UrlEncodedCommentId,
  message: 'stop it',
  updated: '2021-11-03 10:11:12.000000000' as Timestamp,
  in_reply_to: 'the-reply' as UrlEncodedCommentId,
  savingState: SavingState.OK,
};

const commentWithContext = {
  author: {name: 'Kermit', _account_id: 1 as AccountId},
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
              show-patchset=""
            ></gr-comment>
            <gr-comment
              collapsed=""
              initially-collapsed=""
              show-patchset=""
            ></gr-comment>
            <gr-comment class="draft" show-patchset=""></gr-comment>
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
            <gr-comment class="draft" show-patchset=""></gr-comment>
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
      sinon.stub(testResolver(commentsModelToken), 'discardDraft');

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
      // Use @ts-ignore to bypass TypeScript's private method restriction
      // @ts-ignore
      const addQuoteStub = sinon.stub(element, 'addQuote');
      await element.updateComplete;
      assert.equal(element.thread?.comments.length, 2);
      queryAndAssert<GrButton>(element, '#quoteBtn').click();
      assert.isTrue(stubAdd.called);
      assert.equal(stubAdd.lastCall.firstArg.message, '');
      // @ts-ignore
      assert.isTrue(addQuoteStub.calledWith('> keep it going\n\n'));
    });

    test('cancel after reply discards the comment', async () => {
      element.thread = createThread(c1, {...c2, unresolved: true});
      await element.updateComplete;

      // Restore the original stub before creating a new one
      stubAdd.restore();

      // Create a stub that actually adds a draft to the thread
      stubAdd = sinon
        .stub(testResolver(commentsModelToken), 'addNewDraft')
        .callsFake(draft => {
          const newDraft = {
            ...draft,
            id: 'new-draft' as UrlEncodedCommentId,
            __draft: true,
          };
          if (element.thread) {
            element.thread = {
              ...element.thread,
              comments: [...element.thread.comments, newDraft],
            };
          }
          return Promise.resolve(newDraft);
        });

      // Click reply button to create a new draft
      queryAndAssert<GrButton>(element, '#replyBtn').click();
      assert.isTrue(stubAdd.called);
      await element.updateComplete;
      const draftElement = queryAndAssert<GrComment>(
        element,
        'gr-comment.draft'
      );

      // Simulate user adding additional text to the quoted text
      draftElement.editing = true;
      draftElement.messageText = 'My comment text';
      await draftElement.updateComplete;

      // Simulate cancel action
      const cancelSpy = sinon.spy(draftElement, 'cancel');
      draftElement.cancel();
      assert.isTrue(cancelSpy.called);
      // The draft should be discarded completely
      assert.equal(draftElement.messageText, '');
    });

    test('cancel after quote only discards user input but keeps quoted text', async () => {
      element.thread = createThread(c1, {...c2, unresolved: true});
      await element.updateComplete;

      // Restore the original stub before creating a new one
      stubAdd.restore();

      // Create a stub that actually adds a draft to the thread with the quoted text
      stubAdd = sinon
        .stub(testResolver(commentsModelToken), 'addNewDraft')
        .callsFake(draft => {
          const newDraft = {
            ...draft,
            id: 'new-draft' as UrlEncodedCommentId,
            __draft: true,
          };
          if (element.thread) {
            element.thread = {
              ...element.thread,
              comments: [...element.thread.comments, newDraft],
            };
          }
          return Promise.resolve(newDraft);
        });

      // Click quote button to create a new draft with quoted text
      queryAndAssert<GrButton>(element, '#quoteBtn').click();
      assert.isTrue(stubAdd.called);
      await element.updateComplete;

      const draftElement = queryAndAssert<GrComment>(
        element,
        'gr-comment.draft'
      );
      await draftElement.updateComplete;
      await waitUntil(
        () => !!draftElement.textarea,
        'textarea element not found'
      );
      const textarea = queryAndAssert<GrSuggestionTextarea>(
        draftElement,
        '#editTextarea'
      );
      await textarea.updateComplete;

      // Verify the draft contains the quoted text
      assert.equal(draftElement.messageText, '> keep it going\n\n');

      // Simulate cancel action
      const cancelSpy = sinon.spy(draftElement, 'cancel');
      draftElement.cancel();
      await draftElement.updateComplete;
      assert.isTrue(cancelSpy.called);

      // The draft should be discarded completely
      assert.equal(draftElement.messageText, '');
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

  suite('Get AI fix button', () => {
    setup(async () => {
      const suggestionsService = testResolver(suggestionsServiceToken);
      sinon
        .stub(suggestionsService, 'isGeneratedSuggestedFixEnabled')
        .returns(true);

      element.isOwner = true;
      element.account = createAccountDetailWithId(13);
      element.thread = createThread({...c1, unresolved: true});
      await element.updateComplete;
    });
    test('renders with actions unresolved and AI fix button', async () => {
      assert.isOk(query(element, '#aiFixBtn'));
      assert.dom.equal(
        queryAndAssert(element, '#container'),
        /* HTML */ `
          <div id="container">
            <h3 class="assistive-tech-only">
              Unresolved Comment thread by Kermit
            </h3>
            <div class="comment-box unresolved" tabindex="0">
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
                    class="action ai-fix"
                    id="aiFixBtn"
                    link=""
                    role="button"
                    tabindex="0"
                  >
                    Get AI Fix
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

    test('not show get ai fix if comment has fix_suggestion', async () => {
      element.thread = createThread({
        ...c1,
        fix_suggestions: [createFixSuggestionInfo()],
        unresolved: true,
      });
      await element.updateComplete;
      assert.isNotOk(query(element, '#aiFixBtn'));
    });

    test('handleAppliedFix creates a "Fix applied" reply', async () => {
      const thread: CommentThread = {
        ...createThread(c1),
        comments: [
          {
            ...createComment(),
            id: '123' as any,
            message: 'Test comment',
            author: {name: 'Test User', _account_id: 12345 as AccountId},
            patch_set: 1 as RevisionPatchSetNum,
            line: 10,
            path: 'test.txt',
          },
        ],
      };
      element.thread = thread;
      element.changeNum = 123 as NumericChangeId;
      const createReplyCommentSpy = sinon.spy(
        element as any,
        'createReplyComment'
      );

      element.dispatchEvent(new CustomEvent('apply-user-suggestion'));

      assert.isTrue(createReplyCommentSpy.calledOnce);
      assert.deepEqual(createReplyCommentSpy.firstCall.args, [
        'Fix applied.',
        false,
        false,
        '',
        undefined,
      ]);
    });
  });
});
