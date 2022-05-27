/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import {fixture} from '@open-wc/testing-helpers';
import {html} from 'lit';
import './gr-hovercard-account';
import {GrHovercardAccount} from './gr-hovercard-account';
import {
  mockPromise,
  query,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import {
  AccountDetailInfo,
  AccountId,
  EmailAddress,
  ReviewerState,
} from '../../../api/rest-api.js';
import {
  createAccountDetailWithId,
  createChange,
} from '../../../test/test-data-generators.js';
import {GrButton} from '../gr-button/gr-button.js';

suite('gr-hovercard-account tests', () => {
  let element: GrHovercardAccount;

  const ACCOUNT: AccountDetailInfo = {
    ...createAccountDetailWithId(31),
    email: 'kermit@gmail.com' as EmailAddress,
    username: 'kermit',
    name: 'Kermit The Frog',
    status: 'I am a frog',
    _account_id: 31415926535 as AccountId,
  };

  setup(async () => {
    stubRestApi('getAccount').returns(Promise.resolve({...ACCOUNT}));
    const change = {
      ...createChange(),
      attention_set: {},
      reviewers: {},
      owner: {...ACCOUNT},
    };
    element = await fixture<GrHovercardAccount>(
      html`<gr-hovercard-account
        class="hovered"
        .account=${ACCOUNT}
        .change=${change}
      >
      </gr-hovercard-account>`
    );
    await element.show({});
    await element.updateComplete;
  });

  teardown(async () => {
    element.mouseHide(new MouseEvent('click'));
    await element.updateComplete;
  });

  test('renders', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `
      <div id="container" role="tooltip" tabindex="-1">
        <div class="top">
          <div class="avatar">
            <gr-avatar hidden="" imagesize="56"></gr-avatar>
          </div>
          <div class="account">
            <h3 class="heading-3 name">Kermit The Frog</h3>
            <div class="email">kermit@gmail.com</div>
          </div>
        </div>
        <gr-endpoint-decorator name="hovercard-status">
          <gr-endpoint-param name="account"></gr-endpoint-param>
        </gr-endpoint-decorator>
        <div class="status">
          <span class="title">About me:</span>
          <span class="value">I am a frog</span>
        </div>
        <div class="links">
          <iron-icon class="linkIcon" icon="gr-icons:link"></iron-icon>
          <a href="">Changes</a>·<a href="">Dashboard</a>
        </div>
      </div>
    `);
  });

  test('account name is shown', () => {
    const name = queryAndAssert<HTMLHeadingElement>(element, '.name');
    assert.equal(name.innerText, 'Kermit The Frog');
  });

  test('computePronoun', async () => {
    element.account = createAccountDetailWithId(1);
    element._selfAccount = createAccountDetailWithId(1);
    await element.updateComplete;
    assert.equal(element.computePronoun(), 'Your');
    element.account = createAccountDetailWithId(2);
    await element.updateComplete;
    assert.equal(element.computePronoun(), 'Their');
  });

  test('account status is not shown if the property is not set', async () => {
    element.account = {...ACCOUNT, status: undefined};
    await element.updateComplete;
    assert.isUndefined(query(element, '.status'));
  });

  test('account status is displayed', () => {
    const status = queryAndAssert<HTMLSpanElement>(element, '.status .value');
    assert.equal(status.innerText, 'I am a frog');
  });

  test('voteable div is not shown if the property is not set', () => {
    assert.isUndefined(query(element, '.voteable'));
  });

  test('voteable div is displayed', async () => {
    element.voteableText = 'CodeReview: +2';
    await element.updateComplete;
    const voteableEl = queryAndAssert<HTMLSpanElement>(
      element,
      '.voteable .value'
    );
    assert.equal(voteableEl.innerText, element.voteableText);
  });

  test('remove reviewer', async () => {
    element.change = {
      ...createChange(),
      removable_reviewers: [ACCOUNT],
      reviewers: {
        [ReviewerState.REVIEWER]: [ACCOUNT],
      },
    };
    await element.updateComplete;
    stubRestApi('removeChangeReviewer').returns(
      Promise.resolve({...new Response(), ok: true})
    );
    const reloadListener = sinon.spy();
    element._target?.addEventListener('reload', reloadListener);
    const button = queryAndAssert<GrButton>(element, '.removeReviewerOrCC');
    assert.isOk(button);
    assert.equal(button.innerText, 'Remove Reviewer');
    button.click();
    await element.updateComplete;
    assert.isTrue(reloadListener.called);
  });

  test('move reviewer to cc', async () => {
    element.change = {
      ...createChange(),
      removable_reviewers: [ACCOUNT],
      reviewers: {
        [ReviewerState.REVIEWER]: [ACCOUNT],
      },
    };
    await element.updateComplete;
    const saveReviewStub = stubRestApi('saveChangeReview').returns(
      Promise.resolve({...new Response(), ok: true})
    );
    stubRestApi('removeChangeReviewer').returns(
      Promise.resolve({...new Response(), ok: true})
    );
    const reloadListener = sinon.spy();
    element._target?.addEventListener('reload', reloadListener);

    const button = queryAndAssert<GrButton>(element, '.changeReviewerOrCC');

    assert.isOk(button);
    assert.equal(button.innerText, 'Move Reviewer to CC');
    button.click();
    await element.updateComplete;
    assert.isTrue(saveReviewStub.called);
    assert.isTrue(reloadListener.called);
  });

  test('move reviewer to cc', async () => {
    element.change = {
      ...createChange(),
      removable_reviewers: [ACCOUNT],
      reviewers: {
        [ReviewerState.REVIEWER]: [],
      },
    };
    await element.updateComplete;
    const saveReviewStub = stubRestApi('saveChangeReview').returns(
      Promise.resolve({...new Response(), ok: true})
    );
    stubRestApi('removeChangeReviewer').returns(
      Promise.resolve({...new Response(), ok: true})
    );
    const reloadListener = sinon.spy();
    element._target?.addEventListener('reload', reloadListener);

    const button = queryAndAssert<GrButton>(element, '.changeReviewerOrCC');
    assert.isOk(button);
    assert.equal(button.innerText, 'Move CC to Reviewer');

    button.click();
    await element.updateComplete;
    assert.isTrue(saveReviewStub.called);
    assert.isTrue(reloadListener.called);
  });

  test('remove cc', async () => {
    element.change = {
      ...createChange(),
      removable_reviewers: [ACCOUNT],
      reviewers: {
        [ReviewerState.REVIEWER]: [],
      },
    };
    await element.updateComplete;
    stubRestApi('removeChangeReviewer').returns(
      Promise.resolve({...new Response(), ok: true})
    );
    const reloadListener = sinon.spy();
    element._target?.addEventListener('reload', reloadListener);

    const button = queryAndAssert<GrButton>(element, '.removeReviewerOrCC');

    assert.equal(button.innerText, 'Remove CC');
    assert.isOk(button);
    button.click();
    await element.updateComplete;
    assert.isTrue(reloadListener.called);
  });

  test('add to attention set', async () => {
    const apiPromise = mockPromise<Response>();
    const apiSpy = stubRestApi('addToAttentionSet').returns(apiPromise);
    element.highlightAttention = true;
    element._target = document.createElement('div');
    await element.updateComplete;
    const showAlertListener = sinon.spy();
    const hideAlertListener = sinon.spy();
    const updatedListener = sinon.spy();
    element._target.addEventListener('show-alert', showAlertListener);
    element._target.addEventListener('hide-alert', hideAlertListener);
    element._target.addEventListener('attention-set-updated', updatedListener);

    const button = queryAndAssert<GrButton>(element, '.addToAttentionSet');
    assert.isOk(button);
    assert.isTrue(element._isShowing, 'hovercard is showing');
    button.click();

    assert.equal(Object.keys(element.change?.attention_set ?? {}).length, 1);
    const attention_set_info = Object.values(
      element.change?.attention_set ?? {}
    )[0];
    assert.equal(
      attention_set_info.reason,
      `Added by <GERRIT_ACCOUNT_${ACCOUNT._account_id}>` +
        ' using the hovercard menu'
    );
    assert.equal(
      attention_set_info.reason_account?._account_id,
      ACCOUNT._account_id
    );
    assert.isTrue(showAlertListener.called, 'showAlertListener was called');
    assert.isTrue(updatedListener.called, 'updatedListener was called');
    assert.isFalse(element._isShowing, 'hovercard is hidden');

    apiPromise.resolve({...new Response(), ok: true});
    await element.updateComplete;
    assert.isTrue(apiSpy.calledOnce);
    assert.equal(
      apiSpy.lastCall.args[2],
      `Added by <GERRIT_ACCOUNT_${ACCOUNT._account_id}>` +
        ' using the hovercard menu'
    );
    assert.isTrue(hideAlertListener.called, 'hideAlertListener was called');
  });

  test('remove from attention set', async () => {
    const apiPromise = mockPromise<Response>();
    const apiSpy = stubRestApi('removeFromAttentionSet').returns(apiPromise);
    element.highlightAttention = true;
    element.change = {
      ...createChange(),
      attention_set: {
        '31415926535': {account: ACCOUNT, reason: 'a good reason'},
      },
      reviewers: {},
      owner: {...ACCOUNT},
    };
    element._target = document.createElement('div');
    await element.updateComplete;
    const showAlertListener = sinon.spy();
    const hideAlertListener = sinon.spy();
    const updatedListener = sinon.spy();
    element._target.addEventListener('show-alert', showAlertListener);
    element._target.addEventListener('hide-alert', hideAlertListener);
    element._target.addEventListener('attention-set-updated', updatedListener);

    const button = queryAndAssert<GrButton>(element, '.removeFromAttentionSet');
    assert.isOk(button);
    assert.isTrue(element._isShowing, 'hovercard is showing');
    button.click();

    assert.isDefined(element.change?.attention_set);
    assert.equal(Object.keys(element.change?.attention_set ?? {}).length, 0);
    assert.isTrue(showAlertListener.called, 'showAlertListener was called');
    assert.isTrue(updatedListener.called, 'updatedListener was called');
    assert.isFalse(element._isShowing, 'hovercard is hidden');

    apiPromise.resolve({...new Response(), ok: true});
    await element.updateComplete;

    assert.isTrue(apiSpy.calledOnce);
    assert.equal(
      apiSpy.lastCall.args[2],
      `Removed by <GERRIT_ACCOUNT_${ACCOUNT._account_id}>` +
        ' using the hovercard menu'
    );
    assert.isTrue(hideAlertListener.called, 'hideAlertListener was called');
  });
});
