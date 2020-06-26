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

import '../../../test/common-test-setup-karma.js';
import './gr-reviewer-list.js';
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';

const basicFixture = fixtureFromElement('gr-reviewer-list');

suite('gr-reviewer-list tests', () => {
  let element;
  let sandbox;

  setup(() => {
    element = basicFixture.instantiate();
    element.serverConfig = {};
    sandbox = sinon.sandbox.create();
    stub('gr-rest-api-interface', {
      getConfig() { return Promise.resolve({}); },
      removeChangeReviewer() {
        return Promise.resolve({ok: true});
      },
    });
  });

  teardown(() => {
    sandbox.restore();
  });

  test('controls hidden on immutable element', () => {
    element.mutable = false;
    assert.isTrue(element.shadowRoot
        .querySelector('.controlsContainer').hasAttribute('hidden'));
    element.mutable = true;
    assert.isFalse(element.shadowRoot
        .querySelector('.controlsContainer').hasAttribute('hidden'));
  });

  test('add reviewer button opens reply dialog', done => {
    element.addEventListener('show-reply-dialog', () => {
      done();
    });
    MockInteractions.tap(element.shadowRoot
        .querySelector('.addReviewer'));
  });

  test('only show remove for removable reviewers', () => {
    element.mutable = true;
    element.change = {
      owner: {
        _account_id: 1,
      },
      reviewers: {
        REVIEWER: [
          {
            _account_id: 2,
            name: 'Bojack Horseman',
            email: 'SecretariatRulez96@hotmail.com',
          },
          {
            _account_id: 3,
            name: 'Pinky Penguin',
          },
        ],
        CC: [
          {
            _account_id: 4,
            name: 'Diane Nguyen',
            email: 'macarthurfellow2B@juno.com',
          },
          {
            email: 'test@e.mail',
          },
        ],
      },
      removable_reviewers: [
        {
          _account_id: 3,
          name: 'Pinky Penguin',
        },
        {
          _account_id: 4,
          name: 'Diane Nguyen',
          email: 'macarthurfellow2B@juno.com',
        },
        {
          email: 'test@e.mail',
        },
      ],
    };
    flushAsynchronousOperations();
    const chips =
        dom(element.root).querySelectorAll('gr-account-chip');
    assert.equal(chips.length, 4);

    for (const el of Array.from(chips)) {
      const accountID = el.account._account_id || el.account.email;
      assert.ok(accountID);

      const buttonEl = el.shadowRoot
          .querySelector('gr-button');
      assert.isNotNull(buttonEl);
      if (accountID == 2) {
        assert.isTrue(buttonEl.hasAttribute('hidden'));
      } else {
        assert.isFalse(buttonEl.hasAttribute('hidden'));
      }
    }
  });

  test('tracking reviewers and ccs', () => {
    let counter = 0;
    function makeAccount() {
      return {_account_id: counter++};
    }

    const owner = makeAccount();
    const reviewer = makeAccount();
    const cc = makeAccount();
    const reviewers = {
      REMOVED: [makeAccount()],
      REVIEWER: [owner, reviewer],
      CC: [owner, cc],
    };

    element.ccsOnly = false;
    element.reviewersOnly = false;
    element.change = {
      owner,
      reviewers,
    };
    assert.deepEqual(element._reviewers, [reviewer, cc]);

    element.reviewersOnly = true;
    element.change = {
      owner,
      reviewers,
    };
    assert.deepEqual(element._reviewers, [reviewer]);

    element.ccsOnly = true;
    element.reviewersOnly = false;
    element.change = {
      owner,
      reviewers,
    };
    assert.deepEqual(element._reviewers, [cc]);
  });

  test('_handleAddTap passes mode with event', () => {
    const fireStub = sandbox.stub(element, 'dispatchEvent');
    const e = {preventDefault() {}};

    element.ccsOnly = false;
    element.reviewersOnly = false;
    element._handleAddTap(e);
    assert.equal(fireStub.lastCall.args[0].type, 'show-reply-dialog');
    assert.deepEqual(fireStub.lastCall.args[0].detail, {value: {}});

    element.reviewersOnly = true;
    element._handleAddTap(e);
    assert.equal(fireStub.lastCall.args[0].type, 'show-reply-dialog');
    assert.deepEqual(
        fireStub.lastCall.args[0].detail,
        {value: {reviewersOnly: true}});

    element.ccsOnly = true;
    element.reviewersOnly = false;
    element._handleAddTap(e);
    assert.equal(fireStub.lastCall.args[0].type, 'show-reply-dialog');
    assert.deepEqual(fireStub.lastCall.args[0].detail,
        {value: {ccsOnly: true}});
  });

  test('dont show all reviewers button with 4 reviewers', () => {
    const reviewers = [];
    element.maxReviewersDisplayed = 3;
    for (let i = 0; i < 4; i++) {
      reviewers.push(
          {email: i+'reviewer@google.com', name: 'reviewer-' + i});
    }
    element.ccsOnly = true;

    element.change = {
      owner: {
        _account_id: 1,
      },
      reviewers: {
        CC: reviewers,
      },
    };
    assert.equal(element._hiddenReviewerCount, 0);
    assert.equal(element._displayedReviewers.length, 4);
    assert.equal(element._reviewers.length, 4);
    assert.isTrue(element.shadowRoot
        .querySelector('.hiddenReviewers').hidden);
  });

  test('show all reviewers button with 6 reviewers', () => {
    const reviewers = [];
    for (let i = 0; i < 6; i++) {
      reviewers.push(
          {email: i+'reviewer@google.com', name: 'reviewer-' + i});
    }
    element.ccsOnly = true;

    element.change = {
      owner: {
        _account_id: 1,
      },
      reviewers: {
        CC: reviewers,
      },
    };
    assert.equal(element._hiddenReviewerCount, 3);
    assert.equal(element._displayedReviewers.length, 3);
    assert.equal(element._reviewers.length, 6);
    assert.isFalse(element.shadowRoot
        .querySelector('.hiddenReviewers').hidden);
  });

  test('show all reviewers button', () => {
    const reviewers = [];
    for (let i = 0; i < 100; i++) {
      reviewers.push(
          {email: i+'reviewer@google.com', name: 'reviewer-' + i});
    }
    element.ccsOnly = true;

    element.change = {
      owner: {
        _account_id: 1,
      },
      reviewers: {
        CC: reviewers,
      },
    };
    assert.equal(element._hiddenReviewerCount, 97);
    assert.equal(element._displayedReviewers.length, 3);
    assert.equal(element._reviewers.length, 100);
    assert.isFalse(element.shadowRoot
        .querySelector('.hiddenReviewers').hidden);

    MockInteractions.tap(element.shadowRoot
        .querySelector('.hiddenReviewers'));

    assert.equal(element._hiddenReviewerCount, 0);
    assert.equal(element._displayedReviewers.length, 100);
    assert.equal(element._reviewers.length, 100);
    assert.isTrue(element.shadowRoot
        .querySelector('.hiddenReviewers').hidden);
  });

  test('votable labels', () => {
    const change = {
      labels: {
        Foo: {
          all: [{_account_id: 7, permitted_voting_range: {max: 2}}],
        },
        Bar: {
          all: [{_account_id: 1, permitted_voting_range: {max: 1}},
            {_account_id: 7, permitted_voting_range: {max: 1}}],
        },
        FooBar: {
          all: [{_account_id: 7, value: 0}],
        },
      },
      permitted_labels: {
        Foo: ['-1', ' 0', '+1', '+2'],
        FooBar: ['-1', ' 0'],
      },
    };
    assert.strictEqual(
        element._computeVoteableText({_account_id: 1}, change),
        'Bar');
    assert.strictEqual(
        element._computeVoteableText({_account_id: 7}, change),
        'Foo: +2, Bar, FooBar');
    assert.strictEqual(
        element._computeVoteableText({_account_id: 2}, change),
        '');
  });

  test('fails gracefully when all is not included', () => {
    const change = {
      labels: {Foo: {}},
      permitted_labels: {
        Foo: ['-1', ' 0', '+1', '+2'],
      },
    };
    assert.strictEqual(
        element._computeVoteableText({_account_id: 1}, change), '');
  });
});

