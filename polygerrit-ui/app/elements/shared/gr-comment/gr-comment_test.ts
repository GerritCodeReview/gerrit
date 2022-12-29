/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-comment';
import {AUTO_SAVE_DEBOUNCE_DELAY_MS, GrComment} from './gr-comment';
import {
  queryAndAssert,
  stubRestApi,
  query,
  pressKey,
  listenOnce,
  mockPromise,
  waitUntilCalled,
  dispatch,
  MockPromise,
  stubFlags,
} from '../../../test/test-utils';
import {
  AccountId,
  EmailAddress,
  NumericChangeId,
  PatchSetNum,
  Timestamp,
  UrlEncodedCommentId,
} from '../../../types/common';
import {
  createComment,
  createDraft,
  createFixSuggestionInfo,
  createRobotComment,
  createUnsaved,
} from '../../../test/test-data-generators';
import {
  ReplyToCommentEvent,
  OpenFixPreviewEventDetail,
} from '../../../types/events';
import {GrConfirmDeleteCommentDialog} from '../gr-confirm-delete-comment-dialog/gr-confirm-delete-comment-dialog';
import {
  DraftInfo,
  USER_SUGGESTION_START_PATTERN,
} from '../../../utils/comment-util';
import {assertIsDefined} from '../../../utils/common-util';
import {Modifier} from '../../../utils/dom-util';
import {SinonStub} from 'sinon';
import {fixture, html, assert} from '@open-wc/testing';
import {GrButton} from '../gr-button/gr-button';
import {testResolver} from '../../../test/common-test-setup';
import {
  CommentsModel,
  commentsModelToken,
} from '../../../models/comments/comments-model';

suite('gr-comment tests', () => {
  let element: GrComment;
  let commentsModel: CommentsModel;
  const account = {
    email: 'dhruvsri@google.com' as EmailAddress,
    name: 'Dhruv Srivastava',
    _account_id: 1083225 as AccountId,
    avatars: [{url: 'abc', height: 32, width: 32}],
    registered_on: '123' as Timestamp,
  };
  const comment = {
    ...createComment(),
    author: {
      name: 'Mr. Peanutbutter',
      email: 'tenn1sballchaser@aol.com' as EmailAddress,
    },
    id: 'baf0414d_60047215' as UrlEncodedCommentId,
    line: 5,
    message: 'This is the test comment message.',
    updated: '2015-12-08 19:48:33.843000000' as Timestamp,
  };

  setup(async () => {
    element = await fixture(
      html`<gr-comment
        .account=${account}
        .showPatchset=${true}
        .comment=${comment}
      ></gr-comment>`
    );
    commentsModel = testResolver(commentsModelToken);
  });

  suite('DOM rendering', () => {
    test('renders collapsed', async () => {
      const initiallyCollapsedElement = await fixture(
        html`<gr-comment
          .account=${account}
          .showPatchset=${true}
          .comment=${comment}
          .initiallyCollapsed=${true}
        ></gr-comment>`
      );
      assert.shadowDom.equal(
        initiallyCollapsedElement,
        /* HTML */ `
          <gr-endpoint-decorator name="comment">
            <gr-endpoint-param name="comment"></gr-endpoint-param>
            <gr-endpoint-param name="editing"></gr-endpoint-param>
            <gr-endpoint-param name="message"></gr-endpoint-param>
            <gr-endpoint-param name="isDraft"></gr-endpoint-param>
            <div class="container" id="container">
              <div class="header" id="header">
                <div class="headerLeft">
                  <gr-account-label deselected=""></gr-account-label>
                </div>
                <div class="headerMiddle">
                  <span class="collapsedContent">
                    This is the test comment message.
                  </span>
                </div>
                <span class="patchset-text">Patchset 1</span>
                <div class="show-hide" tabindex="0">
                  <label aria-label="Expand" class="show-hide">
                    <input checked="" class="show-hide" type="checkbox" />
                    <gr-icon id="icon" icon="expand_more"></gr-icon>
                  </label>
                </div>
              </div>
              <div class="body">
                <gr-endpoint-slot name="above-actions"></gr-endpoint-slot>
              </div>
            </div>
          </gr-endpoint-decorator>
          <dialog id="confirmDeleteModal" tabindex="-1">
            <gr-confirm-delete-comment-dialog id="confirmDeleteCommentDialog">
            </gr-confirm-delete-comment-dialog>
          </dialog>
        `
      );
    });

    test('renders expanded', async () => {
      element.initiallyCollapsed = false;
      await element.updateComplete;
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="comment">
            <gr-endpoint-param name="comment"></gr-endpoint-param>
            <gr-endpoint-param name="editing"></gr-endpoint-param>
            <gr-endpoint-param name="message"></gr-endpoint-param>
            <gr-endpoint-param name="isDraft"></gr-endpoint-param>
            <div class="container" id="container">
              <div class="header" id="header">
                <div class="headerLeft">
                  <gr-account-label deselected=""></gr-account-label>
                </div>
                <div class="headerMiddle"></div>
                <span class="patchset-text">Patchset 1</span>
                <span class="separator"></span>
                <span class="date" tabindex="0">
                  <gr-date-formatter withtooltip=""></gr-date-formatter>
                </span>
                <div class="show-hide" tabindex="0">
                  <label aria-label="Collapse" class="show-hide">
                    <input class="show-hide" type="checkbox" />
                    <gr-icon id="icon" icon="expand_less"></gr-icon>
                  </label>
                </div>
              </div>
              <div class="body">
                <gr-formatted-text class="message"></gr-formatted-text>
                <gr-endpoint-slot name="above-actions"></gr-endpoint-slot>
              </div>
            </div>
          </gr-endpoint-decorator>
          <dialog id="confirmDeleteModal" tabindex="-1">
            <gr-confirm-delete-comment-dialog id="confirmDeleteCommentDialog">
            </gr-confirm-delete-comment-dialog>
          </dialog>
        `
      );
    });

    test('renders expanded robot', async () => {
      element.initiallyCollapsed = false;
      element.comment = createRobotComment();
      await element.updateComplete;
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="comment">
            <gr-endpoint-param name="comment"></gr-endpoint-param>
            <gr-endpoint-param name="editing"></gr-endpoint-param>
            <gr-endpoint-param name="message"></gr-endpoint-param>
            <gr-endpoint-param name="isDraft"></gr-endpoint-param>
            <div class="container" id="container">
              <div class="header" id="header">
                <div class="headerLeft">
                  <span class="robotName">robot-id-123</span>
                </div>
                <div class="headerMiddle"></div>
                <span class="patchset-text">Patchset 1</span>
                <span class="separator"></span>
                <span class="date" tabindex="0">
                  <gr-date-formatter withtooltip=""></gr-date-formatter>
                </span>
                <div class="show-hide" tabindex="0">
                  <label aria-label="Collapse" class="show-hide">
                    <input class="show-hide" type="checkbox" />
                    <gr-icon id="icon" icon="expand_less"></gr-icon>
                  </label>
                </div>
              </div>
              <div class="body">
                <div class="robotId"></div>
                <gr-formatted-text class="message"></gr-formatted-text>
                <gr-endpoint-slot name="above-actions"></gr-endpoint-slot>
                <div class="robotActions">
                  <gr-icon
                    icon="link"
                    class="copy link-icon"
                    role="button"
                    tabindex="0"
                    title="Copy link to this comment"
                  ></gr-icon>
                  <gr-endpoint-decorator name="robot-comment-controls">
                    <gr-endpoint-param name="comment"></gr-endpoint-param>
                  </gr-endpoint-decorator>
                  <gr-button
                    aria-disabled="false"
                    class="action show-fix"
                    link=""
                    role="button"
                    secondary=""
                    tabindex="0"
                  >
                    Show Fix
                  </gr-button>
                  <gr-button
                    aria-disabled="false"
                    class="action fix"
                    link=""
                    role="button"
                    tabindex="0"
                  >
                    Please Fix
                  </gr-button>
                </div>
              </div>
            </div>
          </gr-endpoint-decorator>
          <dialog id="confirmDeleteModal" tabindex="-1">
            <gr-confirm-delete-comment-dialog id="confirmDeleteCommentDialog">
            </gr-confirm-delete-comment-dialog>
          </dialog>
        `
      );
    });

    test('renders expanded admin', async () => {
      element.initiallyCollapsed = false;
      element.isAdmin = true;
      await element.updateComplete;
      assert.dom.equal(
        queryAndAssert(element, 'gr-button.delete'),
        /* HTML */ `
          <gr-button
            aria-disabled="false"
            class="action delete"
            id="deleteBtn"
            link=""
            role="button"
            tabindex="0"
            title="Delete Comment"
          >
            <gr-icon id="icon" icon="delete" filled></gr-icon>
          </gr-button>
        `
      );
    });

    test('renders draft', async () => {
      element.initiallyCollapsed = false;
      (element.comment as DraftInfo).__draft = true;
      await element.updateComplete;
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="comment">
            <gr-endpoint-param name="comment"></gr-endpoint-param>
            <gr-endpoint-param name="editing"></gr-endpoint-param>
            <gr-endpoint-param name="message"></gr-endpoint-param>
            <gr-endpoint-param name="isDraft"></gr-endpoint-param>
            <div class="container draft" id="container">
              <div class="header" id="header">
                <div class="headerLeft">
                  <gr-tooltip-content
                    class="draftTooltip"
                    has-tooltip=""
                    max-width="20em"
                    title="This draft is only visible to you. To publish drafts, click the 'Reply' or 'Start review' button at the top of the change or press the 'a' key."
                  >
                    <gr-icon filled icon="rate_review"></gr-icon>
                    <span class="draftLabel">Draft</span>
                  </gr-tooltip-content>
                </div>
                <div class="headerMiddle"></div>
                <span class="patchset-text">Patchset 1</span>
                <span class="separator"></span>
                <span class="date" tabindex="0">
                  <gr-date-formatter withtooltip=""></gr-date-formatter>
                </span>
                <div class="show-hide" tabindex="0">
                  <label aria-label="Collapse" class="show-hide">
                    <input class="show-hide" type="checkbox" />
                    <gr-icon id="icon" icon="expand_less"></gr-icon>
                  </label>
                </div>
              </div>
              <div class="body">
                <gr-formatted-text class="message"></gr-formatted-text>
                <gr-endpoint-slot name="above-actions"></gr-endpoint-slot>
                <div class="actions">
                  <div class="action resolve">
                    <label>
                      <input checked="" id="resolvedCheckbox" type="checkbox" />
                      Resolved
                    </label>
                  </div>
                  <div class="rightActions">
                    <gr-button
                      aria-disabled="false"
                      class="action discard"
                      link=""
                      role="button"
                      tabindex="0"
                    >
                      Discard
                    </gr-button>
                    <gr-button
                      aria-disabled="false"
                      class="action edit"
                      link=""
                      role="button"
                      tabindex="0"
                    >
                      Edit
                    </gr-button>
                  </div>
                </div>
              </div>
            </div>
          </gr-endpoint-decorator>
          <dialog id="confirmDeleteModal" tabindex="-1">
            <gr-confirm-delete-comment-dialog id="confirmDeleteCommentDialog">
            </gr-confirm-delete-comment-dialog>
          </dialog>
        `
      );
    });

    test('renders draft in editing mode', async () => {
      element.initiallyCollapsed = false;
      (element.comment as DraftInfo).__draft = true;
      element.editing = true;
      await element.updateComplete;
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="comment">
            <gr-endpoint-param name="comment"></gr-endpoint-param>
            <gr-endpoint-param name="editing"></gr-endpoint-param>
            <gr-endpoint-param name="message"></gr-endpoint-param>
            <gr-endpoint-param name="isDraft"></gr-endpoint-param>
            <div class="container draft" id="container">
              <div class="header" id="header">
                <div class="headerLeft">
                  <gr-tooltip-content
                    class="draftTooltip"
                    has-tooltip=""
                    max-width="20em"
                    title="This draft is only visible to you. To publish drafts, click the 'Reply' or 'Start review' button at the top of the change or press the 'a' key."
                  >
                    <gr-icon filled icon="rate_review"></gr-icon>
                    <span class="draftLabel">Draft</span>
                  </gr-tooltip-content>
                </div>
                <div class="headerMiddle"></div>
                <span class="patchset-text">Patchset 1</span>
                <span class="separator"></span>
                <span class="date" tabindex="0">
                  <gr-date-formatter withtooltip=""></gr-date-formatter>
                </span>
                <div class="show-hide" tabindex="0">
                  <label aria-label="Collapse" class="show-hide">
                    <input class="show-hide" type="checkbox" />
                    <gr-icon id="icon" icon="expand_less"></gr-icon>
                  </label>
                </div>
              </div>
              <div class="body">
                <gr-textarea
                  autocomplete="on"
                  class="code editMessage"
                  code=""
                  id="editTextarea"
                  rows="4"
                  text="This is the test comment message."
                >
                </gr-textarea>
                <gr-endpoint-slot name="above-actions"></gr-endpoint-slot>
                <div class="actions">
                  <div class="action resolve">
                    <label>
                      <input checked="" id="resolvedCheckbox" type="checkbox" />
                      Resolved
                    </label>
                  </div>
                  <div class="rightActions">
                    <gr-button
                      aria-disabled="false"
                      class="action cancel"
                      link=""
                      role="button"
                      tabindex="0"
                    >
                      Cancel
                    </gr-button>
                    <gr-button
                      aria-disabled="false"
                      class="action save"
                      link=""
                      role="button"
                      tabindex="0"
                    >
                      Save
                    </gr-button>
                  </div>
                </div>
              </div>
            </div>
          </gr-endpoint-decorator>
          <dialog id="confirmDeleteModal" tabindex="-1">
            <gr-confirm-delete-comment-dialog id="confirmDeleteCommentDialog">
            </gr-confirm-delete-comment-dialog>
          </dialog>
        `
      );
    });
  });

  test('clicking on date link fires event', async () => {
    const stub = sinon.stub();
    element.addEventListener('comment-anchor-tap', stub);
    await element.updateComplete;

    const dateEl = queryAndAssert<HTMLSpanElement>(element, '.date');
    dateEl.click();

    assert.isTrue(stub.called);
    assert.deepEqual(stub.lastCall.args[0].detail, {
      side: 'REVISION',
      number: element.comment!.line,
    });
  });

  test('comment message sets messageText only when empty', async () => {
    element.changeNum = 1 as NumericChangeId;
    element.messageText = '';
    element.comment = {
      ...createComment(),
      author: {
        name: 'Mr. Peanutbutter',
        email: 'tenn1sballchaser@aol.com' as EmailAddress,
      },
      line: 5,
      path: 'test',
      __draft: true,
      message: 'hello world',
    };
    element.editing = true;
    await element.updateComplete;
    // messageText was empty so overwrite the message now
    assert.equal(element.messageText, 'hello world');

    element.comment.message = 'new message';
    await element.updateComplete;
    // messageText was already set so do not overwrite it
    assert.equal(element.messageText, 'hello world');
  });

  test('comment message sets messageText when not edited', async () => {
    element.changeNum = 1 as NumericChangeId;
    element.messageText = 'Some text';
    element.comment = {
      ...createComment(),
      author: {
        name: 'Mr. Peanutbutter',
        email: 'tenn1sballchaser@aol.com' as EmailAddress,
      },
      line: 5,
      path: 'test',
      __draft: true,
      message: 'hello world',
    };
    element.editing = true;
    await element.updateComplete;
    // messageText was empty so overwrite the message now
    assert.equal(element.messageText, 'hello world');

    element.comment.message = 'new message';
    await element.updateComplete;
    // messageText was already set so do not overwrite it
    assert.equal(element.messageText, 'hello world');
  });

  test('delete comment', async () => {
    element.changeNum = 42 as NumericChangeId;
    element.isAdmin = true;
    await element.updateComplete;

    const deleteButton = queryAndAssert<GrButton>(element, '.action.delete');
    deleteButton.click();
    await element.updateComplete;

    assertIsDefined(element.confirmDeleteModal, 'confirmDeleteModal');
    const dialog = queryAndAssert<GrConfirmDeleteCommentDialog>(
      element.confirmDeleteModal,
      '#confirmDeleteCommentDialog'
    );
    dialog.message = 'removal reason';
    await element.updateComplete;

    const stub = stubRestApi('deleteComment').returns(
      Promise.resolve(createComment())
    );
    element.handleConfirmDeleteComment();
    assert.isTrue(
      stub.calledWith(
        42 as NumericChangeId,
        1 as PatchSetNum,
        'baf0414d_60047215' as UrlEncodedCommentId,
        'removal reason'
      )
    );
  });

  suite('gr-comment draft tests', () => {
    setup(async () => {
      element.changeNum = 42 as NumericChangeId;
      element.comment = {
        ...createComment(),
        __draft: true,
        path: '/path/to/file',
        line: 5,
      };
    });

    test('isSaveDisabled', async () => {
      element.saving = false;
      element.unresolved = true;
      element.comment = {...createComment(), unresolved: true};
      element.messageText = 'asdf';
      await element.updateComplete;
      assert.isFalse(element.isSaveDisabled());

      element.messageText = '';
      await element.updateComplete;
      assert.isTrue(element.isSaveDisabled());

      // After changing the 'resolved' state of the comment the 'Save' button
      // should stay disabled, if the message is empty.
      element.unresolved = false;
      await element.updateComplete;
      assert.isTrue(element.isSaveDisabled());

      element.saving = true;
      await element.updateComplete;
      assert.isTrue(element.isSaveDisabled());
    });

    test('ctrl+s saves comment', async () => {
      const spy = sinon.stub(element, 'save');
      element.messageText = 'is that the horse from horsing around??';
      element.editing = true;
      await element.updateComplete;
      pressKey(element.textarea!.textarea!.textarea, 's', Modifier.CTRL_KEY);
      assert.isTrue(spy.called);
    });

    test('save', async () => {
      const savePromise = mockPromise<DraftInfo>();
      const stub = sinon.stub(commentsModel, 'saveDraft').returns(savePromise);

      element.comment = createDraft();
      element.editing = true;
      await element.updateComplete;
      const textToSave = 'something, not important';
      element.messageText = textToSave;
      element.unresolved = true;
      await element.updateComplete;

      element.save();

      await element.updateComplete;
      waitUntilCalled(stub, 'saveDraft()');
      assert.equal(stub.lastCall.firstArg.message, textToSave);
      assert.equal(stub.lastCall.firstArg.unresolved, true);
      assert.isTrue(element.editing);
      assert.isTrue(element.saving);

      savePromise.resolve();
      await element.updateComplete;

      assert.isFalse(element.editing);
      assert.isFalse(element.saving);
    });

    test('previewing formatting triggers save', async () => {
      element.permanentEditingMode = true;

      const saveStub = sinon.stub(element, 'save').returns(Promise.resolve());

      element.comment = createDraft();
      element.editing = true;
      element.messageText = 'something, not important';
      await element.updateComplete;

      assert.isFalse(saveStub.called);

      queryAndAssert<GrButton>(element, '.save').click();

      assert.isTrue(saveStub.called);
    });

    test('save failed', async () => {
      sinon
        .stub(commentsModel, 'saveDraft')
        .returns(Promise.reject(new Error('saving failed')));

      element.comment = createDraft();
      element.editing = true;
      await element.updateComplete;
      element.messageText = 'something, not important';
      await element.updateComplete;

      element.save();
      await element.updateComplete;

      assert.isTrue(element.unableToSave);
      assert.isTrue(element.editing);
      assert.isFalse(element.saving);
    });

    test('discard', async () => {
      const discardPromise = mockPromise<void>();
      const stub = sinon
        .stub(commentsModel, 'discardDraft')
        .returns(discardPromise);

      element.comment = createDraft();
      element.editing = true;
      await element.updateComplete;

      element.discard();

      await element.updateComplete;
      waitUntilCalled(stub, 'discardDraft()');
      assert.equal(stub.lastCall.firstArg, element.comment.id);
      assert.isTrue(element.editing);
      assert.isTrue(element.saving);

      discardPromise.resolve();
      await element.updateComplete;

      assert.isFalse(element.editing);
      assert.isFalse(element.saving);
    });

    test('resolved comment state indicated by checkbox', async () => {
      const saveStub = sinon.stub(commentsModel, 'saveDraft');
      element.comment = {
        ...createComment(),
        __draft: true,
        unresolved: false,
      };
      await element.updateComplete;

      let checkbox = queryAndAssert<HTMLInputElement>(
        element,
        '#resolvedCheckbox'
      );
      assert.isTrue(checkbox.checked);

      checkbox.click();
      await element.updateComplete;

      checkbox = queryAndAssert<HTMLInputElement>(element, '#resolvedCheckbox');
      assert.isFalse(checkbox.checked);

      assert.isTrue(saveStub.called);
    });

    test('saving empty text calls discard()', async () => {
      const saveStub = sinon.stub(commentsModel, 'saveDraft');
      const discardStub = sinon.stub(commentsModel, 'discardDraft');
      element.comment = createDraft();
      element.editing = true;
      await element.updateComplete;

      element.messageText = '';
      await element.updateComplete;

      await element.save();
      assert.isTrue(discardStub.called);
      assert.isFalse(saveStub.called);
    });

    test('handlePleaseFix fires reply-to-comment event', async () => {
      const listener = listenOnce<ReplyToCommentEvent>(
        element,
        'reply-to-comment'
      );
      element.comment = createRobotComment();
      element.comments = [element.comment];
      await element.updateComplete;

      queryAndAssert<GrButton>(element, '.fix').click();

      const e = await listener;
      assert.equal(e.detail.unresolved, true);
      assert.equal(e.detail.userWantsToEdit, false);
      assert.isTrue(e.detail.content.includes('Please fix.'));
    });

    test('do not show Please Fix button if human reply exists', async () => {
      element.initiallyCollapsed = false;
      const robotComment = createRobotComment();
      element.comment = robotComment;
      await element.updateComplete;

      let actions = query(element, '.robotActions gr-button.fix');
      assert.isOk(actions);

      element.comments = [
        robotComment,
        {...createComment(), in_reply_to: robotComment.id},
      ];
      await element.updateComplete;
      actions = query(element, '.robotActions gr-button.fix');
      assert.isNotOk(actions);
    });

    test('handleShowFix fires open-fix-preview event', async () => {
      const listener = listenOnce<CustomEvent<OpenFixPreviewEventDetail>>(
        element,
        'open-fix-preview'
      );
      element.comment = {
        ...createRobotComment(),
        fix_suggestions: [{...createFixSuggestionInfo()}],
      };
      await element.updateComplete;

      queryAndAssert<GrButton>(element, '.show-fix').click();

      const e = await listener;
      assert.deepEqual(e.detail, await element.createFixPreview());
    });
  });

  suite('auto saving', () => {
    let clock: sinon.SinonFakeTimers;
    let savePromise: MockPromise<DraftInfo>;
    let saveStub: SinonStub;

    setup(async () => {
      clock = sinon.useFakeTimers();
      savePromise = mockPromise<DraftInfo>();
      saveStub = sinon.stub(commentsModel, 'saveDraft').returns(savePromise);

      element.comment = createUnsaved();
      element.editing = true;
      await element.updateComplete;
    });

    teardown(() => {
      clock.restore();
      sinon.restore();
    });

    test('basic auto saving', async () => {
      const textarea = queryAndAssert<HTMLElement>(element, '#editTextarea');
      dispatch(textarea, 'text-changed', {value: 'some new text  '});

      clock.tick(AUTO_SAVE_DEBOUNCE_DELAY_MS / 2);
      assert.isFalse(saveStub.called);

      clock.tick(AUTO_SAVE_DEBOUNCE_DELAY_MS);
      assert.isTrue(saveStub.called);
      assert.equal(
        saveStub.firstCall.firstArg.message,
        'some new text  '.trimEnd()
      );
    });

    test('saving while auto saving', async () => {
      const textarea = queryAndAssert<HTMLElement>(element, '#editTextarea');
      dispatch(textarea, 'text-changed', {value: 'auto save text'});

      clock.tick(2 * AUTO_SAVE_DEBOUNCE_DELAY_MS);
      assert.isTrue(saveStub.called);
      assert.equal(saveStub.firstCall.firstArg.message, 'auto save text');
      saveStub.reset();

      element.messageText = 'actual save text';
      const save = element.save();
      await element.updateComplete;
      // First wait for the auto saving to finish.
      assert.isFalse(saveStub.called);

      // Resolve auto-saving promise.
      savePromise.resolve({
        ...element.comment,
        __draft: true,
        id: 'exp123' as UrlEncodedCommentId,
        updated: '2018-02-13 22:48:48.018000000' as Timestamp,
      });
      await save;
      // Only then save.
      assert.isTrue(saveStub.called);
      assert.equal(saveStub.firstCall.firstArg.message, 'actual save text');
      assert.equal(saveStub.firstCall.firstArg.id, 'exp123');
    });
  });

  suite('suggest edit', () => {
    let element: GrComment;
    setup(async () => {
      stubFlags('isEnabled').returns(true);
      const comment = {
        ...createComment(),
        author: {
          name: 'Mr. Peanutbutter',
          email: 'tenn1sballchaser@aol.com' as EmailAddress,
        },
        line: 5,
        path: 'test',
        __draft: true,
        message: 'hello world',
      };
      element = await fixture(
        html`<gr-comment
          .account=${account}
          .showPatchset=${true}
          .comment=${comment}
          .initiallyCollapsed=${false}
        ></gr-comment>`
      );
    });
    test('renders suggest fix button', () => {
      assert.dom.equal(
        queryAndAssert(element, 'gr-button.suggestEdit'),
        /* HTML */ `<gr-button
          aria-disabled="false"
          class="action suggestEdit"
          link=""
          role="button"
          tabindex="0"
        >
          Suggest Fix
        </gr-button> `
      );
    });

    test('renders preview suggest fix', async () => {
      element.comment = {
        ...createComment(),
        author: {
          name: 'Mr. Peanutbutter',
          email: 'tenn1sballchaser@aol.com' as EmailAddress,
        },
        line: 5,
        path: 'test',
        message: `${USER_SUGGESTION_START_PATTERN}afterSuggestion${'\n```'}`,
      };
      await element.updateComplete;

      assert.dom.equal(
        queryAndAssert(element, 'gr-button.show-fix'),
        /* HTML */ `<gr-button
          aria-disabled="false"
          class="action show-fix"
          link=""
          role="button"
          secondary
          tabindex="0"
        >
          Preview Fix
        </gr-button> `
      );
    });
  });
});
