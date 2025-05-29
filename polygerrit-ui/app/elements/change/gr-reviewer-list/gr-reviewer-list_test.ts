/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-reviewer-list';
import {mockPromise, queryAndAssert} from '../../../test/test-utils';
import {GrReviewerList} from './gr-reviewer-list';
import {
  createAccountDetailWithId,
  createParsedChange,
} from '../../../test/test-data-generators';
import {GrButton} from '../../shared/gr-button/gr-button';
import {AccountId, EmailAddress} from '../../../types/common';
import './gr-reviewer-list';
import {assert, fixture, html} from '@open-wc/testing';
import {IdToAttentionSetMap} from '../../../api/rest-api';
import {StandardLabels} from '../../../utils/label-util';

suite('gr-reviewer-list tests', () => {
  let element: GrReviewerList;

  setup(async () => {
    element = await fixture(html`<gr-reviewer-list></gr-reviewer-list>`);
  });

  test('render', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="container">
          <div class="reviewersAndControls">
            <div class="controlsContainer" hidden="">
              <gr-button
                aria-disabled="false"
                class="addReviewer"
                id="addReviewer"
                link=""
                role="button"
                tabindex="0"
                title="Add Reviewer"
              >
                <div>
                  <gr-icon icon="edit" filled small></gr-icon>
                </div>
              </gr-button>
            </div>
          </div>
          <gr-button
            aria-disabled="false"
            class="hiddenReviewers"
            hidden=""
            link=""
            role="button"
            tabindex="0"
          >
            and 0 more
          </gr-button>
        </div>
      `
    );
  });

  test('controls hidden on immutable element', async () => {
    element.mutable = false;
    await element.updateComplete;

    assert.isTrue(
      queryAndAssert(element, '.controlsContainer').hasAttribute('hidden')
    );

    element.mutable = true;
    await element.updateComplete;

    assert.isFalse(
      queryAndAssert(element, '.controlsContainer').hasAttribute('hidden')
    );
  });

  test('add reviewer button opens reply dialog', async () => {
    const dialogShown = mockPromise();
    element.addEventListener('show-reply-dialog', () => {
      dialogShown.resolve();
    });
    queryAndAssert<GrButton>(element, '.addReviewer').click();
    await dialogShown;
  });

  test('tracking reviewers and ccs', async () => {
    let counter = 0;
    function makeAccount() {
      return {_account_id: counter++ as AccountId};
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
      ...createParsedChange(),
      owner,
      reviewers,
    };
    await element.updateComplete;
    assert.deepEqual(element.reviewers, [reviewer, cc]);

    element.reviewersOnly = true;
    element.change = {
      ...createParsedChange(),
      owner,
      reviewers,
    };
    await element.updateComplete;

    assert.deepEqual(element.reviewers, [reviewer]);

    element.ccsOnly = true;
    element.reviewersOnly = false;
    element.change = {
      ...createParsedChange(),
      owner,
      reviewers,
    };
    await element.updateComplete;

    assert.deepEqual(element.reviewers, [cc]);
  });

  test('handleAddTap passes mode with event', () => {
    const fireStub = sinon.stub(element, 'dispatchEvent');
    const e = {...new Event(''), preventDefault() {}};

    element.ccsOnly = false;
    element.reviewersOnly = false;
    element.handleAddTap(e);
    assert.equal(fireStub.lastCall.args[0].type, 'show-reply-dialog');
    assert.deepEqual((fireStub.lastCall.args[0] as CustomEvent).detail, {
      value: {
        reviewersOnly: false,
        ccsOnly: false,
      },
    });

    element.reviewersOnly = true;
    element.handleAddTap(e);
    assert.equal(fireStub.lastCall.args[0].type, 'show-reply-dialog');
    assert.deepEqual((fireStub.lastCall.args[0] as CustomEvent).detail, {
      value: {reviewersOnly: true, ccsOnly: false},
    });

    element.ccsOnly = true;
    element.reviewersOnly = false;
    element.handleAddTap(e);
    assert.equal(fireStub.lastCall.args[0].type, 'show-reply-dialog');
    assert.deepEqual((fireStub.lastCall.args[0] as CustomEvent).detail, {
      value: {ccsOnly: true, reviewersOnly: false},
    });
  });

  test('dont show all reviewers button with 4 reviewers', async () => {
    const reviewers = [];
    for (let i = 0; i < 4; i++) {
      reviewers.push({
        ...createAccountDetailWithId(i),
        email: `${i}reviewer@google.com` as EmailAddress,
        name: `reviewer${i}`,
      });
    }
    element.ccsOnly = true;

    element.change = {
      ...createParsedChange(),
      owner: {
        ...createAccountDetailWithId(111),
      },
      reviewers: {
        CC: reviewers,
      },
    };
    await element.updateComplete;

    const displayedReviewers = element.computeDisplayedReviewers() ?? [];
    assert.equal(element.computeHiddenReviewerCount(displayedReviewers), 0);
    assert.equal(displayedReviewers.length, 4);
    assert.equal(element.reviewers.length, 4);
    assert.isTrue(queryAndAssert<GrButton>(element, '.hiddenReviewers').hidden);
  });

  test('account owner comes first in list of reviewers', async () => {
    const reviewers = [];
    for (let i = 0; i < 4; i++) {
      reviewers.push({
        ...createAccountDetailWithId(i),
        email: `${i}reviewer@google.com` as EmailAddress,
        name: `reviewer${i}`,
      });
    }
    element.reviewersOnly = true;
    element.account = {
      ...createAccountDetailWithId(1),
    };
    element.change = {
      ...createParsedChange(),
      owner: {
        ...createAccountDetailWithId(11),
      },
      reviewers: {
        REVIEWER: reviewers,
      },
    };
    await element.updateComplete;

    const displayedReviewers = element.computeDisplayedReviewers() ?? [];
    assert.equal(displayedReviewers[0]._account_id, 1 as AccountId);
  });

  test('show all reviewers button with 9 reviewers', async () => {
    const reviewers = [];
    for (let i = 0; i < 9; i++) {
      reviewers.push({
        ...createAccountDetailWithId(i),
        email: `${i}reviewer@google.com` as EmailAddress,
        name: `reviewer${i}`,
      });
    }
    element.ccsOnly = true;

    element.change = {
      ...createParsedChange(),
      owner: {
        ...createAccountDetailWithId(111),
      },
      reviewers: {
        CC: reviewers,
      },
    };
    await element.updateComplete;

    const displayedReviewers = element.computeDisplayedReviewers() ?? [];
    assert.equal(element.computeHiddenReviewerCount(displayedReviewers), 3);
    assert.equal(displayedReviewers.length, 6);
    assert.equal(element.reviewers.length, 9);
    assert.isFalse(
      queryAndAssert<GrButton>(element, '.hiddenReviewers').hidden
    );
  });

  test('if more than 6 reviewers have attention or vote, show all of them', async () => {
    const reviewers = [];
    for (let i = 1; i < 11; i++) {
      reviewers.push({
        ...createAccountDetailWithId(i),
        email: `${i}reviewer@google.com` as EmailAddress,
        name: `reviewer${i}`,
      });
    }
    element.ccsOnly = true;

    element.change = {
      ...createParsedChange(),
      owner: {
        ...createAccountDetailWithId(111),
      },
      reviewers: {
        CC: reviewers,
      },
    };

    // add attention set to 7 reviewers (reviewers 1-7)
    const attentionSet: IdToAttentionSetMap = {};
    for (let i = 1; i < 8; i++) {
      attentionSet[i] = {
        account: reviewers[i],
      };
    }
    element.change.attention_set = attentionSet;

    // add vote to 1 reviewer (reviewer 8)
    element.change.labels = {
      [StandardLabels.CODE_REVIEW]: {
        all: [
          {
            ...reviewers[8],
            value: 2,
          },
        ],
      },
    };
    await element.updateComplete;

    const displayedReviewers = element.computeDisplayedReviewers() ?? [];
    assert.equal(element.computeHiddenReviewerCount(displayedReviewers), 2);
    // reviewers 1-8 are displayed, reviewers 9-10 are hidden
    assert.equal(displayedReviewers.length, 8);
    assert.equal(element.reviewers.length, 10);
    assert.isFalse(
      queryAndAssert<GrButton>(element, '.hiddenReviewers').hidden
    );
  });

  test('show all reviewers button', async () => {
    const reviewers = [];
    for (let i = 0; i < 100; i++) {
      reviewers.push({
        ...createAccountDetailWithId(i),
        email: `${i}reviewer@google.com` as EmailAddress,
        name: `reviewer${i}`,
      });
    }
    element.ccsOnly = true;

    element.change = {
      ...createParsedChange(),
      owner: {
        ...createAccountDetailWithId(111),
      },
      reviewers: {
        CC: reviewers,
      },
    };

    await element.updateComplete;

    let displayedReviewers = element.computeDisplayedReviewers() ?? [];
    assert.equal(element.computeHiddenReviewerCount(displayedReviewers), 94);
    assert.equal(displayedReviewers.length, 6);
    assert.equal(element.reviewers.length, 100);
    assert.isFalse(
      queryAndAssert<GrButton>(element, '.hiddenReviewers').hidden
    );

    queryAndAssert<GrButton>(element, '.hiddenReviewers').click();

    await element.updateComplete;

    displayedReviewers = element.computeDisplayedReviewers() ?? [];
    assert.equal(element.computeHiddenReviewerCount(displayedReviewers), 0);
    assert.equal(displayedReviewers.length, 100);
    assert.equal(element.reviewers.length, 100);
    assert.isTrue(queryAndAssert<GrButton>(element, '.hiddenReviewers').hidden);
  });
});
