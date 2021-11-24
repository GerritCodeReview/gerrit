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
import './gr-comment';
import {AUTO_SAVE_DEBOUNCE_DELAY_MS, GrComment} from './gr-comment';
import {
  queryAndAssert,
  stubRestApi,
  stubStorage,
  query,
  pressKey,
  listenOnce,
  stubComments,
  mockPromise,
  waitUntilCalled,
  dispatch,
  MockPromise,
} from '../../../test/test-utils';
import {
  AccountId,
  EmailAddress,
  NumericChangeId,
  PatchSetNum,
  Timestamp,
  UrlEncodedCommentId,
} from '../../../types/common';
import {tap} from '@polymer/iron-test-helpers/mock-interactions';
import {
  createComment,
  createDraft,
  createFixSuggestionInfo,
  createRobotComment,
} from '../../../test/test-data-generators';
import {
  CreateFixCommentEvent,
  OpenFixPreviewEventDetail,
} from '../../../types/events';
import {GrConfirmDeleteCommentDialog} from '../gr-confirm-delete-comment-dialog/gr-confirm-delete-comment-dialog';
import {DraftInfo} from '../../../utils/comment-util';
import {assertIsDefined} from '../../../utils/common-util';
import {Modifier} from '../../../utils/dom-util';
import {SinonStub} from 'sinon';

suite('gr-comment tests', () => {
  suite('basic tests', () => {
    let element: GrComment;

    setup(() => {
      element = fixtureFromElement('gr-comment').instantiate();
      element.account = {
        email: 'dhruvsri@google.com' as EmailAddress,
        name: 'Dhruv Srivastava',
        _account_id: 1083225 as AccountId,
        avatars: [{url: 'abc', height: 32, width: 32}],
        registered_on: '123' as Timestamp,
      };
      element.showPatchset = true;
      element.getRandomInt = () => 1;
      element.comment = {
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
    });

    test('renders collapsed', async () => {
      element.initiallyCollapsed = true;
      await element.updateComplete;
      expect(element).shadowDom.to.equal(`
        <div class="container" id="container">
          <div class="header" id="header">
            <div class="headerLeft">
              <gr-account-label deselected="" hidestatus=""></gr-account-label>
            </div>
            <div class="headerMiddle">
              <span class="collapsedContent">
                This is the test comment message.
              </span>
            </div>
            <span class="patchset-text">Patchset 1</span>
            <div class="show-hide" tabindex="0">
              <label aria-label="Expand" class="show-hide">
                <input checked="" class="show-hide" type="checkbox">
                <iron-icon id="icon" icon="gr-icons:expand-more"></iron-icon>
              </label>
            </div>
          </div>
          <div class="body"></div>
        </div>
      `);
    });

    test('renders expanded', async () => {
      element.initiallyCollapsed = false;
      await element.updateComplete;
      expect(element).shadowDom.to.equal(`
        <div class="container" id="container">
          <div class="header" id="header">
            <div class="headerLeft">
              <gr-account-label deselected="" hidestatus=""></gr-account-label>
            </div>
            <div class="headerMiddle"></div>
            <span class="patchset-text">Patchset 1</span>
            <span class="separator"></span>
            <span class="date" tabindex="0">
              <gr-date-formatter withtooltip=""></gr-date-formatter>
            </span>
            <div class="show-hide" tabindex="0">
              <label aria-label="Collapse" class="show-hide">
                <input class="show-hide" type="checkbox">
                <iron-icon id="icon" icon="gr-icons:expand-less"></iron-icon>
              </label>
            </div>
          </div>
          <div class="body">
            <gr-formatted-text class="message" notrailingmargin=""></gr-formatted-text>
          </div>
        </div>
      `);
    });

    test('renders expanded robot', async () => {
      element.initiallyCollapsed = false;
      element.comment = createRobotComment();
      await element.updateComplete;
      expect(element).shadowDom.to.equal(`
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
                <input class="show-hide" type="checkbox">
                <iron-icon id="icon" icon="gr-icons:expand-less"></iron-icon>
              </label>
            </div>
          </div>
          <div class="body">
            <div class="robotId"></div>
            <gr-formatted-text class="message" notrailingmargin=""></gr-formatted-text>
            <div class="robotActions">
              <iron-icon class="copy link-icon" icon="gr-icons:link" role="button" tabindex="0"
                         title="Copy link to this comment">
              </iron-icon>
              <gr-endpoint-decorator name="robot-comment-controls">
                <gr-endpoint-param name="comment"></gr-endpoint-param>
              </gr-endpoint-decorator>
              <gr-button aria-disabled="false" class="action show-fix" link="" role="button" secondary="" tabindex="0">
                Show Fix
              </gr-button>
              <gr-button aria-disabled="false" class="action fix" link="" role="button" tabindex="0">
                Please Fix
              </gr-button>
            </div>
          </div>
        </div>
      `);
    });

    test('renders expanded admin', async () => {
      element.initiallyCollapsed = false;
      element.isAdmin = true;
      await element.updateComplete;
      expect(queryAndAssert(element, 'gr-button.delete')).dom.to.equal(`
        <gr-button
          aria-disabled="false"
          class="action delete"
          id="deleteBtn"
          link=""
          role="button"
          tabindex="0"
          title="Delete Comment"
        >
          <iron-icon icon="gr-icons:delete" id="icon"></iron-icon>
        </gr-button>
      `);
    });

    test('renders draft', async () => {
      element.initiallyCollapsed = false;
      (element.comment as DraftInfo).__draft = true;
      await element.updateComplete;
      expect(element).shadowDom.to.equal(`
        <div class="container draft" id="container">
          <div class="header" id="header">
            <div class="headerLeft">
              <gr-account-label class="draft" deselected="" hidestatus=""></gr-account-label>
              <gr-tooltip-content
                class="draftTooltip" has-tooltip="" max-width="20em" show-icon=""
                title="This draft is only visible to you. To publish drafts, click the 'Reply' or 'Start review' button at the top of the change or press the 'a' key."
              >
                <span class="draftLabel">DRAFT</span>
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
                <input class="show-hide" type="checkbox">
                <iron-icon id="icon" icon="gr-icons:expand-less"></iron-icon>
              </label>
            </div>
          </div>
          <div class="body">
            <gr-formatted-text class="message"></gr-formatted-text>
            <div class="actions">
              <div class="action resolve">
                <label>
                  <input checked="" id="resolvedCheckbox" type="checkbox">
                  Resolved
                </label>
              </div>
              <div class="rightActions">
                <gr-button aria-disabled="false" class="action discard" link="" role="button" tabindex="0">
                  Discard
                </gr-button>
                <gr-button aria-disabled="false" class="action edit" link="" role="button" tabindex="0">
                  Edit
                </gr-button>
              </div>
            </div>
          </div>
        </div>
      `);
    });

    test('renders draft in editing mode', async () => {
      element.initiallyCollapsed = false;
      (element.comment as DraftInfo).__draft = true;
      element.editing = true;
      await element.updateComplete;
      expect(element).shadowDom.to.equal(`
        <div class="container draft" id="container">
          <div class="header" id="header">
            <div class="headerLeft">
              <gr-account-label class="draft" deselected="" hidestatus=""></gr-account-label>
              <gr-tooltip-content
                class="draftTooltip" has-tooltip="" max-width="20em" show-icon=""
                title="This draft is only visible to you. To publish drafts, click the 'Reply' or 'Start review' button at the top of the change or press the 'a' key."
              >
                <span class="draftLabel">DRAFT</span>
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
                <input class="show-hide" type="checkbox">
                <iron-icon id="icon" icon="gr-icons:expand-less"></iron-icon>
              </label>
            </div>
          </div>
          <div class="body">
            <gr-textarea
              autocomplete="on" class="code editMessage" code="" id="editTextarea" rows="4"
              text="This is the test comment message."
            >
            </gr-textarea>
            <div class="actions">
              <div class="action resolve">
                <label>
                  <input checked="" id="resolvedCheckbox" type="checkbox">
                  Resolved
                </label>
              </div>
              <div class="rightActions">
                <gr-button aria-disabled="false" class="action cancel" link="" role="button" tabindex="0">
                  Cancel
                </gr-button>
                <gr-button aria-disabled="false" class="action save" link="" role="button" tabindex="0">
                  Save
                </gr-button>
              </div>
            </div>
          </div>
        </div>
      `);
    });

    test('clicking on date link fires event', async () => {
      const stub = sinon.stub();
      element.addEventListener('comment-anchor-tap', stub);
      await element.updateComplete;

      const dateEl = queryAndAssert(element, '.date');
      tap(dateEl);

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

      element.comment!.message = 'new message';
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

      element.comment!.message = 'new message';
      await element.updateComplete;
      // messageText was already set so do not overwrite it
      assert.equal(element.messageText, 'hello world');
    });

    test('delete comment', async () => {
      element.changeNum = 42 as NumericChangeId;
      element.isAdmin = true;
      await element.updateComplete;

      const deleteButton = queryAndAssert(element, '.action.delete');
      tap(deleteButton);
      await element.updateComplete;

      assertIsDefined(element.confirmDeleteOverlay, 'confirmDeleteOverlay');
      const dialog = queryAndAssert<GrConfirmDeleteCommentDialog>(
        element.confirmDeleteOverlay,
        '#confirmDeleteComment'
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
        element.resolved = false;
        element.comment = {...createComment(), unresolved: true};
        element.messageText = 'asdf';
        await element.updateComplete;
        assert.isFalse(element.isSaveDisabled());

        element.messageText = '';
        await element.updateComplete;
        assert.isTrue(element.isSaveDisabled());

        element.resolved = true;
        await element.updateComplete;
        assert.isFalse(element.isSaveDisabled());

        element.saving = true;
        await element.updateComplete;
        assert.isTrue(element.isSaveDisabled());
      });

      test('ctrl+s saves comment', async () => {
        const spy = sinon.stub(element, 'save');
        element.messageText = 'is that the horse from horsing around??';
        element.editing = true;
        await element.updateComplete;
        pressKey(element.textarea!.$.textarea.textarea, 's', Modifier.CTRL_KEY);
        assert.isTrue(spy.called);
      });

      test('save', async () => {
        const savePromise = mockPromise<void>();
        const stub = stubComments('saveDraft').returns(savePromise);

        element.comment = createDraft();
        element.editing = true;
        await element.updateComplete;
        const textToSave = 'something, not important';
        element.messageText = textToSave;
        element.resolved = false;
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

      test('discard', async () => {
        const discardPromise = mockPromise<void>();
        const stub = stubComments('discardDraft').returns(discardPromise);

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
        const saveStub = sinon.stub(element, 'save');
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

        tap(checkbox);
        await element.updateComplete;

        checkbox = queryAndAssert<HTMLInputElement>(
          element,
          '#resolvedCheckbox'
        );
        assert.isFalse(checkbox.checked);

        assert.isTrue(saveStub.called);
      });

      test('saving empty text calls discard()', async () => {
        const saveStub = stubComments('saveDraft');
        const discardStub = stubComments('discardDraft');
        element.comment = createDraft();
        element.editing = true;
        await element.updateComplete;

        element.messageText = '';
        await element.updateComplete;

        await element.save();
        assert.isTrue(discardStub.called);
        assert.isFalse(saveStub.called);
      });

      test('handleFix fires create-fix event', async () => {
        const listener = listenOnce<CreateFixCommentEvent>(
          element,
          'create-fix-comment'
        );
        element.comment = createRobotComment();
        element.comments = [element.comment!];
        await element.updateComplete;

        tap(queryAndAssert(element, '.fix'));

        const e = await listener;
        assert.deepEqual(e.detail, element.getEventPayload());
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

        tap(queryAndAssert(element, '.show-fix'));

        const e = await listener;
        assert.deepEqual(e.detail, element.getEventPayload());
      });
    });

    suite('auto saving', () => {
      let clock: sinon.SinonFakeTimers;
      let savePromise: MockPromise<void>;
      let saveStub: SinonStub;

      setup(async () => {
        clock = sinon.useFakeTimers();
        savePromise = mockPromise<void>();
        saveStub = stubComments('saveDraft').returns(savePromise);

        element.comment = createDraft();
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
          'some new text  '.trim()
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
        element.save();
        await element.updateComplete;
        // First wait for the auto saving to finish.
        assert.isFalse(saveStub.called);

        savePromise.resolve();
        await element.updateComplete;
        // Only then save.
        assert.isTrue(saveStub.called);
        assert.equal(saveStub.firstCall.firstArg.message, 'actual save text');
      });
    });

    suite('respectful tips', () => {
      let clock: sinon.SinonFakeTimers;
      setup(async () => {
        clock = sinon.useFakeTimers();
      });

      teardown(() => {
        clock.restore();
        sinon.restore();
      });

      test('show tip when no cached record', async () => {
        const respectfulGetStub = stubStorage('getRespectfulTipVisibility');
        const respectfulSetStub = stubStorage('setRespectfulTipVisibility');
        respectfulGetStub.returns(null);
        element.editing = true;
        element.getRandomInt = () => 0;
        element.comment = createDraft();
        await element.updateComplete;

        assert.isTrue(respectfulGetStub.called);
        assert.isTrue(respectfulSetStub.called);
        queryAndAssert(element, '.respectfulReviewTip');
      });

      test('add 14-day delays once dismissed', async () => {
        const respectfulGetStub = stubStorage('getRespectfulTipVisibility');
        const respectfulSetStub = stubStorage('setRespectfulTipVisibility');
        respectfulGetStub.returns(null);
        element.editing = true;
        element.getRandomInt = () => 0;
        element.comment = createDraft();
        await element.updateComplete;

        assert.isTrue(respectfulGetStub.called);
        assert.isTrue(respectfulSetStub.called);
        assert.isTrue(respectfulSetStub.lastCall.args[0] === undefined);
        const closeLink = queryAndAssert(
          element,
          '.respectfulReviewTip a.close'
        );
        tap(closeLink);
        await element.updateComplete;

        assert.isTrue(respectfulSetStub.lastCall.args[0] === 14);
      });

      test('do not show tip when fall out of probability', async () => {
        const respectfulGetStub = stubStorage('getRespectfulTipVisibility');
        const respectfulSetStub = stubStorage('setRespectfulTipVisibility');
        respectfulGetStub.returns(null);
        element.editing = true;
        element.getRandomInt = () => 2;
        element.comment = createDraft();
        await element.updateComplete;

        assert.isTrue(respectfulGetStub.called);
        assert.isFalse(respectfulSetStub.called);
        assert.isNotOk(query(element, '.respectfulReviewTip'));
      });

      test('show tip when editing changed to true', async () => {
        const respectfulGetStub = stubStorage('getRespectfulTipVisibility');
        const respectfulSetStub = stubStorage('setRespectfulTipVisibility');
        respectfulGetStub.returns(null);
        element.editing = false;
        element.getRandomInt = () => 0;
        element.comment = createComment();
        await element.updateComplete;

        assert.isFalse(respectfulGetStub.called);
        assert.isFalse(respectfulSetStub.called);
        assert.isNotOk(query(element, '.respectfulReviewTip'));

        element.editing = true;
        await element.updateComplete;
        assert.isTrue(respectfulGetStub.called);
        assert.isTrue(respectfulSetStub.called);
        assert.isTrue(!!queryAndAssert(element, '.respectfulReviewTip'));
      });

      test('no tip when cached record', async () => {
        const respectfulGetStub = stubStorage('getRespectfulTipVisibility');
        const respectfulSetStub = stubStorage('setRespectfulTipVisibility');
        respectfulGetStub.returns({updated: 0});
        element.editing = true;
        element.getRandomInt = () => 0;
        element.comment = createDraft();
        await element.updateComplete;

        assert.isTrue(respectfulGetStub.called);
        assert.isFalse(respectfulSetStub.called);
        assert.isNotOk(query(element, '.respectfulReviewTip'));
      });
    });
  });
});
