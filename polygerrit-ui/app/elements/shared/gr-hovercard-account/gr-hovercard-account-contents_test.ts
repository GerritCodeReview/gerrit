/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {fixture, assert} from '@open-wc/testing';
import {html} from 'lit';
import './gr-hovercard-account-contents';
import {GrHovercardAccountContents} from './gr-hovercard-account-contents';
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
} from '../../../api/rest-api';
import {
  createAccountDetailWithId,
  createChange,
  createDetailedLabelInfo,
} from '../../../test/test-data-generators';
import {GrButton} from '../gr-button/gr-button';
import {EventType} from '../../../types/events';
import {testResolver} from '../../../test/common-test-setup';
import {userModelToken} from '../../../models/user/user-model';

suite('gr-hovercard-account-contents tests', () => {
  let element: GrHovercardAccountContents;

  const ACCOUNT: AccountDetailInfo = {
    ...createAccountDetailWithId(31),
    email: 'kermit@gmail.com' as EmailAddress,
    username: 'kermit',
    name: 'Kermit The Frog',
    status: 'I am a frog',
    _account_id: 31415926535 as AccountId,
  };

  setup(async () => {
    const change = {
      ...createChange(),
      attention_set: {},
      reviewers: {},
      owner: {...ACCOUNT},
    };
    element = await fixture(
      html`<gr-hovercard-account-contents .account=${ACCOUNT} .change=${change}>
      </gr-hovercard-account-contents>`
    );
    testResolver(userModelToken).setAccount({...ACCOUNT});
    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="top">
          <div class="avatar">
            <gr-avatar hidden=""></gr-avatar>
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
          <gr-icon icon="link" class="linkIcon"></gr-icon>
          <a href="/q/owner:kermit@gmail.com">Changes</a>
          ·
          <a href="/dashboard/31415926535">Dashboard</a>
        </div>
      `
    );
  });

  test('renders without change data', async () => {
    const elementWithoutChange = await fixture(
      html`<gr-hovercard-account-contents
        .account=${ACCOUNT}
      ></gr-hovercard-account-contents>`
    );
    assert.shadowDom.equal(
      elementWithoutChange,
      /* HTML */ `
        <div class="top">
          <div class="avatar">
            <gr-avatar hidden=""></gr-avatar>
          </div>
          <div class="account">
            <h3 class="heading-3 name">Kermit The Frog</h3>
            <div class="email">kermit@gmail.com</div>
          </div>
        </div>
        <gr-endpoint-decorator name="hovercard-status">
          <gr-endpoint-param name="account"> </gr-endpoint-param>
        </gr-endpoint-decorator>
        <div class="status">
          <span class="title"> About me: </span>
          <span class="value"> I am a frog </span>
        </div>
        <div class="links">
          <gr-icon class="linkIcon" icon="link"> </gr-icon>
          <a href="/q/owner:kermit@gmail.com"> Changes </a>
          ·
          <a href="/dashboard/31415926535"> Dashboard </a>
        </div>
      `
    );
  });

  test('account name is shown', () => {
    const name = queryAndAssert<HTMLHeadingElement>(element, '.name');
    assert.equal(name.innerText, 'Kermit The Frog');
  });

  test('computePronoun', async () => {
    element.account = createAccountDetailWithId(1);
    element.selfAccount = createAccountDetailWithId(1);
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
    element.change = {
      ...createChange(),
      labels: {
        Foo: {
          ...createDetailedLabelInfo(),
          all: [
            {
              _account_id: 7 as AccountId,
              permitted_voting_range: {max: 2, min: 0},
            },
          ],
        },
        Bar: {
          ...createDetailedLabelInfo(),
          all: [
            {
              ...createAccountDetailWithId(1),
              permitted_voting_range: {max: 1, min: 0},
            },
            {
              _account_id: 7 as AccountId,
              permitted_voting_range: {max: 1, min: 0},
            },
          ],
        },
        FooBar: {
          ...createDetailedLabelInfo(),
          all: [{_account_id: 7 as AccountId, value: 0}],
        },
      },
      permitted_labels: {
        Foo: ['-1', ' 0', '+1', '+2'],
        FooBar: ['-1', ' 0'],
      },
    };
    element.account = createAccountDetailWithId(1);

    await element.updateComplete;
    const voteableEl = queryAndAssert<HTMLSpanElement>(
      element,
      '.voteable .value'
    );
    assert.equal(voteableEl.innerText, 'Bar: +1');
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
    element.addEventListener('reload', reloadListener);
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
    element.addEventListener('reload', reloadListener);

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
    element.addEventListener('reload', reloadListener);

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
    element.addEventListener('reload', reloadListener);

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
    await element.updateComplete;
    const showAlertListener = sinon.spy();
    const hideAlertListener = sinon.spy();
    const updatedListener = sinon.spy();
    element.addEventListener(EventType.SHOW_ALERT, showAlertListener);
    element.addEventListener('hide-alert', hideAlertListener);
    element.addEventListener('attention-set-updated', updatedListener);

    const button = queryAndAssert<GrButton>(element, '.addToAttentionSet');
    assert.isOk(button);
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
    await element.updateComplete;
    const showAlertListener = sinon.spy();
    const hideAlertListener = sinon.spy();
    const updatedListener = sinon.spy();
    element.addEventListener(EventType.SHOW_ALERT, showAlertListener);
    element.addEventListener('hide-alert', hideAlertListener);
    element.addEventListener('attention-set-updated', updatedListener);

    const button = queryAndAssert<GrButton>(element, '.removeFromAttentionSet');
    assert.isOk(button);
    button.click();

    assert.isDefined(element.change?.attention_set);
    assert.equal(Object.keys(element.change?.attention_set ?? {}).length, 0);
    assert.isTrue(showAlertListener.called, 'showAlertListener was called');
    assert.isTrue(updatedListener.called, 'updatedListener was called');

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
