/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-comment';
import {AUTO_SAVE_DEBOUNCE_DELAY_MS, GrComment} from './gr-comment';
import {
  dispatch,
  MockPromise,
  mockPromise,
  pressKey,
  query,
  queryAndAssert,
  stubFlags,
  stubRestApi,
  waitUntil,
  waitUntilCalled,
} from '../../../test/test-utils';
import {
  AccountId,
  DraftInfo,
  EmailAddress,
  FixId,
  NumericChangeId,
  PatchSetNum,
  SavingState,
  Timestamp,
  UrlEncodedCommentId,
} from '../../../types/common';
import {
  createComment,
  createDraft,
  createNewDraft,
} from '../../../test/test-data-generators';
import {GrConfirmDeleteCommentDialog} from '../gr-confirm-delete-comment-dialog/gr-confirm-delete-comment-dialog';
import {assertIsDefined} from '../../../utils/common-util';
import {Key, Modifier} from '../../../utils/dom-util';
import {SinonStub, SinonStubbedMember} from 'sinon';
import {assert, fixture, html} from '@open-wc/testing';
import {GrButton} from '../gr-button/gr-button';
import {testResolver} from '../../../test/common-test-setup';
import {
  CommentsModel,
  commentsModelToken,
} from '../../../models/comments/comments-model';
import {KnownExperimentId} from '../../../services/flags/flags';
import {GrSuggestionDiffPreview} from '../gr-suggestion-diff-preview/gr-suggestion-diff-preview';
import {ResponseCode} from '../../../api/suggestions';
import {suggestionsServiceToken} from '../../../services/suggestions/suggestions-service';

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
            <gr-endpoint-param name="comment"> </gr-endpoint-param>
            <gr-endpoint-param name="editing"> </gr-endpoint-param>
            <gr-endpoint-param name="message"> </gr-endpoint-param>
            <gr-endpoint-param name="isDraft"> </gr-endpoint-param>
            <div class="container" id="container">
              <div class="header" id="header">
                <div class="headerLeft">
                  <gr-account-label deselected=""> </gr-account-label>
                </div>
                <div class="headerMiddle">
                  <span class="collapsedContent">
                    This is the test comment message.
                  </span>
                </div>
                <span class="patchset-text"> Patchset 1 </span>
                <div class="show-hide" tabindex="0">
                  <label aria-label="Expand" class="show-hide">
                    <md-checkbox checked="" class="show-hide"> </md-checkbox>
                    <gr-icon icon="expand_more" id="icon"> </gr-icon>
                  </label>
                </div>
              </div>
              <div class="body">
                <gr-endpoint-slot name="above-actions"> </gr-endpoint-slot>
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
            <gr-endpoint-param name="comment"> </gr-endpoint-param>
            <gr-endpoint-param name="editing"> </gr-endpoint-param>
            <gr-endpoint-param name="message"> </gr-endpoint-param>
            <gr-endpoint-param name="isDraft"> </gr-endpoint-param>
            <div class="container" id="container">
              <div class="header" id="header">
                <div class="headerLeft">
                  <gr-account-label deselected=""> </gr-account-label>
                </div>
                <div class="headerMiddle"></div>
                <span class="patchset-text"> Patchset 1 </span>
                <span class="separator"> </span>
                <span class="date" tabindex="0">
                  <gr-date-formatter withtooltip=""> </gr-date-formatter>
                </span>
                <div class="show-hide" tabindex="0">
                  <label aria-label="Collapse" class="show-hide">
                    <md-checkbox class="show-hide"> </md-checkbox>
                    <gr-icon icon="expand_less" id="icon"> </gr-icon>
                  </label>
                </div>
              </div>
              <div class="body">
                <gr-formatted-text class="message"> </gr-formatted-text>
                <gr-endpoint-slot name="above-actions"> </gr-endpoint-slot>
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
      (element.comment as DraftInfo).savingState = SavingState.OK;
      await element.updateComplete;
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="comment">
            <gr-endpoint-param name="comment"> </gr-endpoint-param>
            <gr-endpoint-param name="editing"> </gr-endpoint-param>
            <gr-endpoint-param name="message"> </gr-endpoint-param>
            <gr-endpoint-param name="isDraft"> </gr-endpoint-param>
            <div class="container draft" id="container">
              <div class="header" id="header">
                <div class="headerLeft">
                  <gr-tooltip-content
                    class="draftTooltip"
                    has-tooltip=""
                    max-width="20em"
                    title="This draft is only visible to you. To publish drafts, click the 'Reply' or 'Start review' button at the top of the change or press the 'a' key."
                  >
                    <gr-icon filled="" icon="rate_review"> </gr-icon>
                    <span class="draftLabel"> Draft </span>
                  </gr-tooltip-content>
                </div>
                <div class="headerMiddle"></div>
                <span class="patchset-text"> Patchset 1 </span>
                <span class="separator"> </span>
                <span class="date" tabindex="0">
                  <gr-date-formatter withtooltip=""> </gr-date-formatter>
                </span>
                <div class="show-hide" tabindex="0">
                  <label aria-label="Collapse" class="show-hide">
                    <md-checkbox class="show-hide"> </md-checkbox>
                    <gr-icon icon="expand_less" id="icon"> </gr-icon>
                  </label>
                </div>
              </div>
              <div class="body">
                <gr-formatted-text class="message"> </gr-formatted-text>
                <gr-endpoint-slot name="above-actions"> </gr-endpoint-slot>
                <div class="actions">
                  <div class="leftActions">
                    <div class="action resolve">
                      <label>
                        <md-checkbox checked="" id="resolvedCheckbox">
                        </md-checkbox>
                        Resolved
                      </label>
                    </div>
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
                    <gr-endpoint-slot name="draft-actions-end">
                    </gr-endpoint-slot>
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
      (element.comment as DraftInfo).savingState = SavingState.OK;
      element.editing = true;
      await element.updateComplete;
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="comment">
            <gr-endpoint-param name="comment"> </gr-endpoint-param>
            <gr-endpoint-param name="editing"> </gr-endpoint-param>
            <gr-endpoint-param name="message"> </gr-endpoint-param>
            <gr-endpoint-param name="isDraft"> </gr-endpoint-param>
            <div class="container draft" id="container">
              <div class="header" id="header">
                <div class="headerLeft">
                  <gr-tooltip-content
                    class="draftTooltip"
                    has-tooltip=""
                    max-width="20em"
                    title="This draft is only visible to you. To publish drafts, click the 'Reply' or 'Start review' button at the top of the change or press the 'a' key."
                  >
                    <gr-icon filled="" icon="rate_review"> </gr-icon>
                    <span class="draftLabel"> Draft </span>
                  </gr-tooltip-content>
                </div>
                <div class="headerMiddle"></div>
                <gr-button
                  aria-disabled="false"
                  class="action suggestEdit"
                  link=""
                  role="button"
                  tabindex="0"
                  title="This button copies the text to make a suggestion"
                >
                  <gr-icon filled="" icon="edit" id="icon"> </gr-icon>
                  Suggest Edit
                </gr-button>
                <span class="patchset-text"> Patchset 1 </span>
                <span class="separator"> </span>
                <span class="date" tabindex="0">
                  <gr-date-formatter withtooltip=""> </gr-date-formatter>
                </span>
                <div class="show-hide" tabindex="0">
                  <label aria-label="Collapse" class="show-hide">
                    <md-checkbox class="show-hide"> </md-checkbox>
                    <gr-icon icon="expand_less" id="icon"> </gr-icon>
                  </label>
                </div>
              </div>
              <div class="body">
                <gr-suggestion-textarea
                  autocomplete="on"
                  autocompletehint=""
                  class="code editMessage"
                  code=""
                  id="editTextarea"
                  rows="4"
                >
                </gr-suggestion-textarea>
                <gr-endpoint-slot name="above-actions"> </gr-endpoint-slot>
                <div class="actions">
                  <div class="leftActions">
                    <div class="action resolve">
                      <label>
                        <md-checkbox checked="" id="resolvedCheckbox">
                        </md-checkbox>
                        Resolved
                      </label>
                    </div>
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
                    <gr-endpoint-slot name="draft-actions-end">
                    </gr-endpoint-slot>
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
      savingState: SavingState.OK,
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
      savingState: SavingState.OK,
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
        savingState: SavingState.OK,
        path: '/path/to/file',
        line: 5,
      };
    });

    test('isSaveDisabled', async () => {
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

      element.comment = {...element.comment, savingState: SavingState.SAVING};
      await element.updateComplete;
      assert.isTrue(element.isSaveDisabled());
    });

    test('ctrl+s saves comment', async () => {
      const spy = sinon.stub(element, 'save');
      element.messageText = 'is that the horse from horsing around??';
      element.editing = true;
      await element.updateComplete;
      pressKey(element.textarea!, 's', Modifier.CTRL_KEY);
      assert.isTrue(spy.called);
    });

    suite('ctrl+ENTER  ', () => {
      test('saves comment', async () => {
        const spy = sinon.stub(element, 'save');
        element.messageText = 'is that the horse from horsing around??';
        element.editing = true;
        await element.updateComplete;
        pressKey(element.textarea!, Key.ENTER, Modifier.CTRL_KEY);
        assert.isTrue(spy.called);
      });
      test('propagates on patchset comment', async () => {
        const event = new KeyboardEvent('keydown', {
          key: Key.ENTER,
          ctrlKey: true,
        });
        const stopPropagationStub = sinon.stub(event, 'stopPropagation');
        element.permanentEditingMode = true;
        element.messageText = 'is that the horse from horsing around??';
        element.editing = true;
        await element.updateComplete;
        element.dispatchEvent(event);
        assert.isFalse(stopPropagationStub.called);
      });
      test('does not propagate on normal comment', async () => {
        const event = new KeyboardEvent('keydown', {
          key: Key.ENTER,
          ctrlKey: true,
        });
        const stopPropagationStub = sinon.stub(event, 'stopPropagation');
        element.messageText = 'is that the horse from horsing around??';
        element.editing = true;
        await element.updateComplete;
        element.dispatchEvent(event);
        assert.isTrue(stopPropagationStub.called);
      });
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
      assert.isFalse(element.editing);

      savePromise.resolve();
      await element.updateComplete;

      assert.isFalse(element.editing);
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
      sinon.stub(commentsModel, 'saveDraft').returns(
        Promise.resolve({
          ...createNewDraft(),
          message: 'something, not important',
          unresolved: true,
          savingState: SavingState.ERROR,
        })
      );

      element.comment = createNewDraft({
        message: '',
        unresolved: true,
      });
      element.unresolved = true;
      element.editing = true;
      await element.updateComplete;
      element.messageText = 'something, not important';
      await element.updateComplete;

      element.save();
      assert.isFalse(element.editing);

      await waitUntil(() => element.hasAttribute('error'));
      assert.isTrue(element.editing);
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
      assert.isFalse(element.editing);

      discardPromise.resolve();
      await element.updateComplete;

      assert.isFalse(element.editing);
    });

    test('resolved comment state indicated by checkbox', async () => {
      const saveStub = sinon.stub(commentsModel, 'saveDraft');
      element.comment = {
        ...createComment(),
        savingState: SavingState.OK,
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

    test('clicking resolved checkbox fills message text', async () => {
      element.comment = {
        ...createComment(),
        savingState: SavingState.OK,
        unresolved: false,
      };
      element.editing = true;
      await element.updateComplete;

      element.messageText = '';
      await element.updateComplete;

      let checkbox = queryAndAssert<HTMLInputElement>(
        element,
        '#resolvedCheckbox'
      );
      assert.isTrue(checkbox.checked);
      assert.equal(element.messageText, '');

      checkbox.click();
      await element.updateComplete;

      checkbox = queryAndAssert<HTMLInputElement>(element, '#resolvedCheckbox');
      assert.isFalse(checkbox.checked);
      assert.equal(element.messageText, 'Marked as unresolved.');
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

    test('converting to input for empty text calls discard()', async () => {
      const saveStub = sinon.stub(commentsModel, 'saveDraft');
      const discardStub = sinon.stub(commentsModel, 'discardDraft');
      element.comment = createDraft();
      element.editing = true;
      await element.updateComplete;

      element.messageText = '';
      await element.updateComplete;

      await element.convertToCommentInputAndOrDiscard();
      assert.isTrue(discardStub.called);
      assert.isFalse(saveStub.called);
    });
  });

  suite('auto saving', () => {
    let clock: sinon.SinonFakeTimers;
    let savePromise: MockPromise<DraftInfo>;
    let saveStub: SinonStubbedMember<CommentsModel['saveDraft']>;

    setup(async () => {
      clock = sinon.useFakeTimers();
      savePromise = mockPromise<DraftInfo>();
      saveStub = sinon.stub(commentsModel, 'saveDraft').returns(savePromise);

      element.comment = createNewDraft();
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
      saveStub.reset();
      const autoSavePromise = mockPromise<DraftInfo>();
      saveStub.onCall(0).returns(autoSavePromise);
      saveStub.onCall(1).returns(savePromise);

      const textarea = queryAndAssert<HTMLElement>(element, '#editTextarea');
      dispatch(textarea, 'text-changed', {value: 'auto save text'});

      clock.tick(2 * AUTO_SAVE_DEBOUNCE_DELAY_MS);
      assert.equal(saveStub.callCount, 1);
      assert.equal(saveStub.firstCall.firstArg.message, 'auto save text');

      element.messageText = 'actual save text';
      const save = element.save();
      await element.updateComplete;
      // First wait for the auto saving to finish.
      assert.equal(saveStub.callCount, 1);

      autoSavePromise.resolve({
        ...element.comment,
        savingState: SavingState.OK,
        message: 'auto save text',
        id: 'exp123' as UrlEncodedCommentId,
        updated: '2018-02-13 22:48:48.018000000' as Timestamp,
      });
      savePromise.resolve({
        ...element.comment,
        savingState: SavingState.OK,
        message: 'actual save text',
        id: 'exp123' as UrlEncodedCommentId,
        updated: '2018-02-13 22:48:49.018000000' as Timestamp,
      });
      await save;
      // Only then save.
      assert.equal(saveStub.callCount, 2);
      assert.equal(saveStub.lastCall.firstArg.message, 'actual save text');
      assert.equal(saveStub.lastCall.firstArg.id, 'exp123');
    });
  });

  suite('handleTextChangedForAutocomplete', () => {
    test('foo -> foo with asdf', async () => {
      const ctx = {draftContent: 'foo', commentCompletion: 'asdf'};
      element.autocompleteHint = ctx;
      element.autocompleteCache.set(ctx);
      element.messageText = 'foo';
      element.handleTextChangedForAutocomplete();
      assert.equal(element.autocompleteHint.commentCompletion, 'asdf');
    });

    test('foo -> bar with asdf', async () => {
      const ctx = {draftContent: 'foo', commentCompletion: 'asdf'};
      element.autocompleteHint = ctx;
      element.autocompleteCache.set(ctx);
      element.messageText = 'bar';
      element.handleTextChangedForAutocomplete();
      assert.isUndefined(element.autocompleteHint);
    });

    test('foo -> foofoo with asdf', async () => {
      const ctx = {draftContent: 'foo', commentCompletion: 'asdf'};
      element.autocompleteHint = ctx;
      element.autocompleteCache.set(ctx);
      element.messageText = 'foofoo';
      element.handleTextChangedForAutocomplete();
      assert.isUndefined(element.autocompleteHint);
    });

    test('foo -> foofoo with foomore', async () => {
      const ctx = {draftContent: 'foo', commentCompletion: 'foomore'};
      element.autocompleteHint = ctx;
      element.autocompleteCache.set(ctx);
      element.messageText = 'foofoo';
      element.handleTextChangedForAutocomplete();
      assert.equal(element.autocompleteHint.commentCompletion, 'more');
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
        savingState: SavingState.OK,
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
      element.editing = true;
    });
    test('renders suggest edit button', () => {
      assert.dom.equal(
        queryAndAssert(element, 'gr-button.suggestEdit'),
        /* HTML */ `<gr-button
          class="action suggestEdit"
          link=""
          role="button"
          tabindex="0"
          title="This button copies the text to make a suggestion"
        >
          <gr-icon icon="edit" id="icon" filled></gr-icon> Suggest Edit
        </gr-button> `
      );
    });
  });

  suite('suggested fix', () => {
    let element: GrComment;
    const generatedFixSuggestion = {
      description: 'prompt_to_edit',
      fix_id: 'ml' as FixId,
      replacements: [
        {
          path: 'google3/ts',
          range: {
            start_line: 83,
            start_character: 0,
            end_line: 83,
            end_character: 0,
          },
          replacement: "import {useUtil} from '../../../utils/use_util';\n",
        },
        {
          path: 'google3/ts',
          range: {
            start_line: 985,
            start_character: 0,
            end_line: 988,
            end_character: 0,
          },
          replacement:
            '        this.suggestionsProvider.supportedFileExtensions.includes(useUtil.getExtension(this.comment.path))) &&\n',
        },
      ],
    };
    setup(async () => {
      stubFlags('isEnabled')
        .withArgs(KnownExperimentId.ML_SUGGESTED_EDIT_V2)
        .returns(true);
    });

    test('shows suggestion count when unchecked', async () => {
      const comment: DraftInfo = {
        ...createDraft(),
        author: {
          name: 'Mr. Peanutbutter',
          email: 'tenn1sballchaser@aol.com' as EmailAddress,
        },
        line: 5,
        path: 'test',
        savingState: SavingState.OK,
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
      element.editing = true;
      sinon.stub(element, 'showGeneratedSuggestion').returns(true);
      element.generateSuggestion = false;
      element.generatedFixSuggestion = generatedFixSuggestion;
      await element.updateComplete;

      const label = queryAndAssert<HTMLLabelElement>(
        element,
        'label.suggestEdit'
      );
      assert.include(label.textContent, '(1)');
    });

    test('renders suggestions in comment', async () => {
      const comment = {
        ...createComment(),
        author: {
          name: 'MitoMr. Peanutbutter',
          email: 'tenn1sballchaser@aol.com' as EmailAddress,
        },
        line: 5,
        path: 'test',
        savingState: SavingState.OK,
        message: 'hello world',
        fix_suggestions: [generatedFixSuggestion],
      };
      element = await fixture(
        html`<gr-comment
          .account=${account}
          .showPatchset=${true}
          .comment=${comment}
          .initiallyCollapsed=${false}
        ></gr-comment>`
      );
      element.editing = false;
      await element.updateComplete;
      assert.dom.equal(
        queryAndAssert(element, 'gr-fix-suggestions'),
        /* HTML */ '<gr-fix-suggestions> </gr-fix-suggestions>'
      );
    });

    test('renders suggestions in draft', async () => {
      const comment: DraftInfo = {
        ...createDraft(),
        author: {
          name: 'Mr. Peanutbutter',
          email: 'tenn1sballchaser@aol.com' as EmailAddress,
        },
        line: 5,
        path: 'test',
        savingState: SavingState.OK,
        message: 'hello world',
        fix_suggestions: [generatedFixSuggestion],
      };
      element = await fixture(
        html`<gr-comment
          .account=${account}
          .showPatchset=${true}
          .comment=${comment}
          .initiallyCollapsed=${false}
        ></gr-comment>`
      );
      element.editing = false;
      await element.updateComplete;
      assert.dom.equal(
        queryAndAssert(element, 'gr-fix-suggestions'),
        /* HTML */ '<gr-fix-suggestions> </gr-fix-suggestions>'
      );
    });

    test('doesn`t render fix_suggestion when not in draft', async () => {
      const comment: DraftInfo = {
        ...createDraft(),
        author: {
          name: 'Mr. Peanutbutter',
          email: 'tenn1sballchaser@aol.com' as EmailAddress,
        },
        line: 5,
        path: 'test',
        savingState: SavingState.OK,
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
      element.editing = true;
      await element.updateComplete;
      assert.isUndefined(query(element, 'gr-suggestion-diff-preview'));
    });

    test('render suggestions in draft is in editing & generatedFixSuggestions is not empty', async () => {
      const comment: DraftInfo = {
        ...createDraft(),
        author: {
          name: 'Mr. Peanutbutter',
          email: 'tenn1sballchaser@aol.com' as EmailAddress,
        },
        line: 5,
        path: 'test',
        savingState: SavingState.OK,
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
      element.editing = true;
      sinon.stub(element, 'showGeneratedSuggestion').returns(true);
      element.generateSuggestion = true;
      element.generatedFixSuggestion = generatedFixSuggestion;
      await element.updateComplete;
      assert.dom.equal(
        queryAndAssert(element, 'gr-suggestion-diff-preview'),
        /* HTML */ '<gr-suggestion-diff-preview id="suggestionDiffPreview"> </gr-suggestion-diff-preview>'
      );
    });

    suite('save', () => {
      const savePromise = mockPromise<DraftInfo>();
      let saveDraftStub: SinonStub;
      const textToSave = 'something, not important';
      setup(async () => {
        const comment = createDraft();
        element = await fixture(
          html`<gr-comment
            .account=${account}
            .showPatchset=${true}
            .comment=${comment}
            .initiallyCollapsed=${false}
          ></gr-comment>`
        );
        saveDraftStub = sinon
          .stub(commentsModel, 'saveDraft')
          .returns(savePromise);
        sinon.stub(element, 'showGeneratedSuggestion').returns(true);
        element.editing = true;
        await element.updateComplete;
        element.messageText = textToSave;
        element.unresolved = true;
        element.generateSuggestion = true;
        element.generatedFixSuggestion = generatedFixSuggestion;
        await element.updateComplete;
      });

      test('save fix suggestion when previewed', async () => {
        const suggestionDiffPreview = queryAndAssert<GrSuggestionDiffPreview>(
          element,
          '#suggestionDiffPreview'
        );
        suggestionDiffPreview.previewed = true;
        suggestionDiffPreview.previewLoadedFor = generatedFixSuggestion;
        await element.updateComplete;
        // trigger event preview-loaded on suggestionDiffPreview with detail
        suggestionDiffPreview.dispatchEvent(
          new CustomEvent('preview-loaded', {
            bubbles: true,
            detail: {previewLoadedFor: generatedFixSuggestion},
          })
        );
        // await element.waitPreviewForGeneratedSuggestion();
        await element.updateComplete;
        element.save();
        await element.updateComplete;
        waitUntilCalled(saveDraftStub, 'saveDraft()');
        assert.equal(
          saveDraftStub.lastCall.firstArg.fix_suggestions[0]?.fix_id,
          generatedFixSuggestion.fix_id
        );
        assert.isFalse(element.editing);

        savePromise.resolve();
      });

      test("don't save fix suggestion when not previewed", async () => {
        const suggestionDiffPreview = queryAndAssert<GrSuggestionDiffPreview>(
          element,
          '#suggestionDiffPreview'
        );
        suggestionDiffPreview.previewed = false;
        await element.updateComplete;
        element.save();
        await element.updateComplete;
        waitUntilCalled(saveDraftStub, 'saveDraft()');
        assert.equal(saveDraftStub.lastCall.firstArg.message, textToSave);
        assert.equal(
          saveDraftStub.lastCall.firstArg.fix_suggestions,
          undefined
        );
        assert.isFalse(element.editing);

        savePromise.resolve();
      });
    });
  });

  suite('autocompleteComment', () => {
    setup(async () => {
      element.autocompleteEnabled = true;
      element.comment = createDraft();
      element.messageText = 'test message';
    });

    test('sets cache when response code is OK', async () => {
      const setStub = sinon
        .stub(element.autocompleteCache, 'set')
        .callThrough();
      const suggestionsService = testResolver(suggestionsServiceToken);
      const ctx = {
        draftContent: 'test message',
        commentCompletion: 'suggested completion',
        responseCode: ResponseCode.OK,
      };
      sinon.stub(suggestionsService, 'autocompleteComment').resolves(ctx);
      element.messageText = 'test message';
      await element.updateComplete;
      await element.autocompleteComment();

      assert.isTrue(setStub.calledWith(ctx));
      assert.deepInclude(element.autocompleteHint, ctx);
    });

    test('does not set cache when response code is not OK', async () => {
      const setStub = sinon
        .stub(element.autocompleteCache, 'set')
        .callThrough();
      const suggestionsService = testResolver(suggestionsServiceToken);
      const ctx = {
        draftContent: 'test message',
        commentCompletion: 'suggested completion',
        responseCode: ResponseCode.OK_LOW_CONFIDENCE,
      };
      sinon.stub(suggestionsService, 'autocompleteComment').resolves(ctx);

      await element.autocompleteComment();

      assert.isFalse(setStub.called);
      assert.isUndefined(element.autocompleteHint);
    });
  });
});
