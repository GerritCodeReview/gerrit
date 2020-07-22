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
import {IronOverlayManager} from '@polymer/iron-overlay-behavior/iron-overlay-manager.js';
import './gr-reply-dialog.js';
import {mockPromise} from '../../../test/test-utils.js';
import {SpecialFilePath} from '../../../constants/constants.js';
import {appContext} from '../../../services/app-context.js';
import {querySelectorAll} from '../../../utils/dom-util.js';

const basicFixture = fixtureFromElement('gr-reply-dialog');

function cloneableResponse(status, text) {
  return {
    ok: false,
    status,
    text() {
      return Promise.resolve(text);
    },
    clone() {
      return {
        ok: false,
        status,
        text() {
          return Promise.resolve(text);
        },
      };
    },
  };
}

suite('gr-reply-dialog tests', () => {
  let element;
  let changeNum;
  let patchNum;

  let getDraftCommentStub;
  let setDraftCommentStub;
  let eraseDraftCommentStub;

  let lastId = 0;
  const makeAccount = function() { return {_account_id: lastId++}; };
  const makeGroup = function() { return {id: lastId++}; };

  setup(() => {
    changeNum = 42;
    patchNum = 1;

    stub('gr-rest-api-interface', {
      getConfig() { return Promise.resolve({}); },
      getAccount() { return Promise.resolve({}); },
      getChange() { return Promise.resolve({}); },
      getChangeSuggestedReviewers() { return Promise.resolve([]); },
    });

    sinon.stub(appContext.flagsService, 'isEnabled').returns(true);

    element = basicFixture.instantiate();
    element.change = {
      _number: changeNum,
      owner: {
        _account_id: 999,
      },
      labels: {
        'Verified': {
          values: {
            '-1': 'Fails',
            ' 0': 'No score',
            '+1': 'Verified',
          },
          default_value: 0,
        },
        'Code-Review': {
          values: {
            '-2': 'Do not submit',
            '-1': 'I would prefer that you didn\'t submit this',
            ' 0': 'No score',
            '+1': 'Looks good to me, but someone else must approve',
            '+2': 'Looks good to me, approved',
          },
          default_value: 0,
        },
      },
    };
    element.patchNum = patchNum;
    element.permittedLabels = {
      'Code-Review': [
        '-1',
        ' 0',
        '+1',
      ],
      'Verified': [
        '-1',
        ' 0',
        '+1',
      ],
    };

    getDraftCommentStub = sinon.stub(element.$.storage, 'getDraftComment');
    setDraftCommentStub = sinon.stub(element.$.storage, 'setDraftComment');
    eraseDraftCommentStub = sinon.stub(element.$.storage,
        'eraseDraftComment');

    // sinon.stub(patchSetUtilMockProxy, 'fetchChangeUpdates')
    //     .returns(Promise.resolve({isLatest: true}));

    // Allow the elements created by dom-repeat to be stamped.
    flushAsynchronousOperations();
  });

  function stubSaveReview(jsonResponseProducer) {
    return sinon.stub(
        element,
        '_saveReview')
        .callsFake(review => new Promise((resolve, reject) => {
          try {
            const result = jsonResponseProducer(review) || {};
            const resultStr =
            element.$.restAPI.JSON_PREFIX + JSON.stringify(result);
            resolve({
              ok: true,
              text() {
                return Promise.resolve(resultStr);
              },
            });
          } catch (err) {
            reject(err);
          }
        }));
  }

  test('default to publishing draft comments with reply', done => {
    // Async tick is needed because iron-selector content is distributed and
    // distributed content requires an observer to be set up.
    // Note: Double flush seems to be needed in Safari. {@see Issue 4963}.
    flush(() => {
      flush(() => {
        element.draft = 'I wholeheartedly disapprove';

        stubSaveReview(review => {
          assert.deepEqual(review, {
            drafts: 'PUBLISH_ALL_REVISIONS',
            labels: {
              'Code-Review': 0,
              'Verified': 0,
            },
            comments: {
              [SpecialFilePath.PATCHSET_LEVEL_COMMENTS]: [{
                message: 'I wholeheartedly disapprove',
                unresolved: false,
              }],
            },
            reviewers: [],
          });
          assert.isFalse(element.$.commentList.hidden);
          done();
        });

        // This is needed on non-Blink engines most likely due to the ways in
        // which the dom-repeat elements are stamped.
        flush(() => {
          MockInteractions.tap(element.shadowRoot
              .querySelector('.send'));
        });
      });
    });
  });

  test('modified attention set', done => {
    element._newAttentionSet = new Set([314]);
    const buttonEl = element.shadowRoot.querySelector('.edit-attention-button');
    MockInteractions.tap(buttonEl);
    flushAsynchronousOperations();

    stubSaveReview(review => {
      assert.isTrue(review.ignore_default_attention_set_rules);
      assert.deepEqual(review.add_to_attention_set, [{
        user: 314,
        reason: 'manually added in reply dialog',
      }]);
      assert.deepEqual(review.remove_from_attention_set, []);
      done();
    });
    MockInteractions.tap(element.shadowRoot.querySelector('.send'));
  });

  function checkComputeAttention(
      userId, reviewerIds, ownerId, attSetIds, expectedIds) {
    const user = {_account_id: userId};
    const reviewers = reviewerIds.map(id => {
      return {_account_id: id};
    });
    const change = {
      owner: {_account_id: ownerId},
      attention_set: {},
    };
    attSetIds.forEach(id => change.attention_set[id] = {});
    element._computeNewAttention(user, reviewers, change);
    assert.deepEqual(element._newAttentionSet, new Set(expectedIds));
  }

  test('computeNewAttention', () => {
    checkComputeAttention(null, [], 999, [], [999]);
    checkComputeAttention(1, [], 999, [], [999]);
    checkComputeAttention(1, [], 999, [1], [999]);
    checkComputeAttention(1, [22], 999, [], [999]);
    checkComputeAttention(1, [22], 999, [22], [22, 999]);
    checkComputeAttention(1, [], 1, [], []);
    checkComputeAttention(1, [], 1, [1], []);
    checkComputeAttention(1, [22], 1, [], [22]);
    checkComputeAttention(1, [22, 33], 1, [], [22, 33]);
    checkComputeAttention(1, [22, 33], 1, [22, 33], [22, 33]);
  });

  test('toggle resolved checkbox', done => {
    // Async tick is needed because iron-selector content is distributed and
    // distributed content requires an observer to be set up.
    // Note: Double flush seems to be needed in Safari. {@see Issue 4963}.
    const checkboxEl = element.shadowRoot.querySelector(
        '#resolvedPatchsetLevelCommentCheckbox');
    MockInteractions.tap(checkboxEl);
    flush(() => {
      flush(() => {
        element.draft = 'I wholeheartedly disapprove';

        stubSaveReview(review => {
          assert.deepEqual(review, {
            drafts: 'PUBLISH_ALL_REVISIONS',
            labels: {
              'Code-Review': 0,
              'Verified': 0,
            },
            comments: {
              [SpecialFilePath.PATCHSET_LEVEL_COMMENTS]: [{
                message: 'I wholeheartedly disapprove',
                unresolved: true,
              }],
            },
            reviewers: [],
          });
          done();
        });

        // This is needed on non-Blink engines most likely due to the ways in
        // which the dom-repeat elements are stamped.
        flush(() => {
          MockInteractions.tap(element.shadowRoot
              .querySelector('.send'));
        });
      });
    });
  });

  test('keep draft comments with reply', done => {
    MockInteractions.tap(element.shadowRoot.querySelector('#includeComments'));
    assert.equal(element._includeComments, false);

    // Async tick is needed because iron-selector content is distributed and
    // distributed content requires an observer to be set up.
    // Note: Double flush seems to be needed in Safari. {@see Issue 4963}.
    flush(() => {
      flush(() => {
        element.draft = 'I wholeheartedly disapprove';

        stubSaveReview(review => {
          assert.deepEqual(review, {
            drafts: 'KEEP',
            labels: {
              'Code-Review': 0,
              'Verified': 0,
            },
            comments: {
              [SpecialFilePath.PATCHSET_LEVEL_COMMENTS]: [{
                message: 'I wholeheartedly disapprove',
                unresolved: false,
              }],
            },
            reviewers: [],
          });
          assert.isTrue(element.$.commentList.hidden);
          done();
        });

        // This is needed on non-Blink engines most likely due to the ways in
        // which the dom-repeat elements are stamped.
        flush(() => {
          MockInteractions.tap(element.shadowRoot
              .querySelector('.send'));
        });
      });
    });
  });

  test('label picker', done => {
    element.draft = 'I wholeheartedly disapprove';
    stubSaveReview(review => {
      assert.deepEqual(review, {
        drafts: 'PUBLISH_ALL_REVISIONS',
        labels: {
          'Code-Review': -1,
          'Verified': -1,
        },
        comments: {
          [SpecialFilePath.PATCHSET_LEVEL_COMMENTS]: [{
            message: 'I wholeheartedly disapprove',
            unresolved: false,
          }],
        },
        reviewers: [],
      });
    });

    sinon.stub(element.$.labelScores, 'getLabelValues').callsFake( () => {
      return {
        'Code-Review': -1,
        'Verified': -1,
      };
    });

    element.addEventListener('send', () => {
      // Flush to ensure properties are updated.
      flush(() => {
        assert.isFalse(element.disabled,
            'Element should be enabled when done sending reply.');
        assert.equal(element.draft.length, 0);
        done();
      });
    });

    // This is needed on non-Blink engines most likely due to the ways in
    // which the dom-repeat elements are stamped.
    flush(() => {
      MockInteractions.tap(element.shadowRoot
          .querySelector('.send'));
      assert.isTrue(element.disabled);
    });
  });

  test('getlabelValue returns value', done => {
    flush(() => {
      element.shadowRoot
          .querySelector('gr-label-scores')
          .shadowRoot
          .querySelector(`gr-label-score-row[name="Verified"]`)
          .setSelectedValue(-1);
      assert.equal('-1', element.getLabelValue('Verified'));
      done();
    });
  });

  test('getlabelValue when no score is selected', done => {
    flush(() => {
      element.shadowRoot
          .querySelector('gr-label-scores')
          .shadowRoot
          .querySelector(`gr-label-score-row[name="Code-Review"]`)
          .setSelectedValue(-1);
      assert.strictEqual(element.getLabelValue('Verified'), ' 0');
      done();
    });
  });

  test('setlabelValue', done => {
    element._account = {_account_id: 1};
    flush(() => {
      const label = 'Verified';
      const value = '+1';
      element.setLabelValue(label, value);

      const labels = element.$.labelScores.getLabelValues();
      assert.deepEqual(labels, {
        'Code-Review': 0,
        'Verified': 1,
      });
      done();
    });
  });

  function getActiveElement() {
    return IronOverlayManager.deepActiveElement;
  }

  function isVisible(el) {
    assert.ok(el);
    return getComputedStyle(el).getPropertyValue('display') != 'none';
  }

  function overlayObserver(mode) {
    return new Promise(resolve => {
      function listener() {
        element.removeEventListener('iron-overlay-' + mode, listener);
        resolve();
      }
      element.addEventListener('iron-overlay-' + mode, listener);
    });
  }

  function isFocusInsideElement(element) {
    // In Polymer 2 focused element either <paper-input> or nested
    // native input <input> element depending on the current focus
    // in browser window.
    // For example, the focus is changed if the developer console
    // get a focus.
    let activeElement = getActiveElement();
    while (activeElement) {
      if (activeElement === element) {
        return true;
      }
      if (activeElement.parentElement) {
        activeElement = activeElement.parentElement;
      } else {
        activeElement = activeElement.getRootNode().host;
      }
    }
    return false;
  }

  function testConfirmationDialog(done, cc) {
    const yesButton = element
        .shadowRoot
        .querySelector('.reviewerConfirmationButtons gr-button:first-child');
    const noButton = element
        .shadowRoot
        .querySelector('.reviewerConfirmationButtons gr-button:last-child');

    element._ccPendingConfirmation = null;
    element._reviewerPendingConfirmation = null;
    flushAsynchronousOperations();
    assert.isFalse(isVisible(element.$.reviewerConfirmationOverlay));

    // Cause the confirmation dialog to display.
    let observer = overlayObserver('opened');
    const group = {
      id: 'id',
      name: 'name',
    };
    if (cc) {
      element._ccPendingConfirmation = {
        group,
        count: 10,
      };
    } else {
      element._reviewerPendingConfirmation = {
        group,
        count: 10,
      };
    }
    flushAsynchronousOperations();

    if (cc) {
      assert.deepEqual(
          element._ccPendingConfirmation,
          element._pendingConfirmationDetails);
    } else {
      assert.deepEqual(
          element._reviewerPendingConfirmation,
          element._pendingConfirmationDetails);
    }

    observer
        .then(() => {
          assert.isTrue(isVisible(element.$.reviewerConfirmationOverlay));
          observer = overlayObserver('closed');
          const expected = 'Group name has 10 members';
          assert.notEqual(
              element.$.reviewerConfirmationOverlay.innerText
                  .indexOf(expected),
              -1);
          MockInteractions.tap(noButton); // close the overlay
          return observer;
        }).then(() => {
          assert.isFalse(isVisible(element.$.reviewerConfirmationOverlay));

          // We should be focused on account entry input.
          assert.isTrue(
              isFocusInsideElement(
                  element.$.reviewers.$.entry.$.input.$.input
              )
          );

          // No reviewer/CC should have been added.
          assert.equal(element.$.ccs.additions().length, 0);
          assert.equal(element.$.reviewers.additions().length, 0);

          // Reopen confirmation dialog.
          observer = overlayObserver('opened');
          if (cc) {
            element._ccPendingConfirmation = {
              group,
              count: 10,
            };
          } else {
            element._reviewerPendingConfirmation = {
              group,
              count: 10,
            };
          }
          return observer;
        })
        .then(() => {
          assert.isTrue(isVisible(element.$.reviewerConfirmationOverlay));
          observer = overlayObserver('closed');
          MockInteractions.tap(yesButton); // Confirm the group.
          return observer;
        })
        .then(() => {
          assert.isFalse(isVisible(element.$.reviewerConfirmationOverlay));
          const additions = cc ?
            element.$.ccs.additions() :
            element.$.reviewers.additions();
          assert.deepEqual(
              additions,
              [
                {
                  group: {
                    id: 'id',
                    name: 'name',
                    confirmed: true,
                    _group: true,
                    _pendingAdd: true,
                  },
                },
              ]);

          // We should be focused on account entry input.
          if (cc) {
            assert.isTrue(
                isFocusInsideElement(
                    element.$.ccs.$.entry.$.input.$.input
                )
            );
          } else {
            assert.isTrue(
                isFocusInsideElement(
                    element.$.reviewers.$.entry.$.input.$.input
                )
            );
          }
        })
        .then(done);
  }

  test('cc confirmation', done => {
    testConfirmationDialog(done, true);
  });

  test('reviewer confirmation', done => {
    testConfirmationDialog(done, false);
  });

  test('_getStorageLocation', () => {
    const actual = element._getStorageLocation();
    assert.equal(actual.changeNum, changeNum);
    assert.equal(actual.patchNum, '@change');
    assert.equal(actual.path, '@change');
  });

  test('_reviewersMutated when account-text-change is fired from ccs', () => {
    flushAsynchronousOperations();
    assert.isFalse(element._reviewersMutated);
    assert.isTrue(element.$.ccs.allowAnyInput);
    assert.isFalse(element.shadowRoot
        .querySelector('#reviewers').allowAnyInput);
    element.$.ccs.dispatchEvent(new CustomEvent('account-text-changed',
        {bubbles: true, composed: true}));
    assert.isTrue(element._reviewersMutated);
  });

  test('gets draft from storage on open', () => {
    const storedDraft = 'hello world';
    getDraftCommentStub.returns({message: storedDraft});
    element.open();
    assert.isTrue(getDraftCommentStub.called);
    assert.equal(element.draft, storedDraft);
  });

  test('gets draft from storage even when text is already present', () => {
    const storedDraft = 'hello world';
    getDraftCommentStub.returns({message: storedDraft});
    element.draft = 'foo bar';
    element.open();
    assert.isTrue(getDraftCommentStub.called);
    assert.equal(element.draft, storedDraft);
  });

  test('blank if no stored draft', () => {
    getDraftCommentStub.returns(null);
    element.draft = 'foo bar';
    element.open();
    assert.isTrue(getDraftCommentStub.called);
    assert.equal(element.draft, '');
  });

  test('does not check stored draft when quote is present', () => {
    const storedDraft = 'hello world';
    const quote = '> foo bar';
    getDraftCommentStub.returns({message: storedDraft});
    element.quote = quote;
    element.open();
    assert.isFalse(getDraftCommentStub.called);
    assert.equal(element.draft, quote);
    assert.isNotOk(element.quote);
  });

  test('updates stored draft on edits', () => {
    const firstEdit = 'hello';
    const location = element._getStorageLocation();

    element.draft = firstEdit;
    element.flushDebouncer('store');

    assert.isTrue(setDraftCommentStub.calledWith(location, firstEdit));

    element.draft = '';
    element.flushDebouncer('store');

    assert.isTrue(eraseDraftCommentStub.calledWith(location));
  });

  test('400 converts to human-readable server-error', done => {
    sinon.stub(window, 'fetch').callsFake(() => {
      const text = '....{"reviewers":{"id1":{"error":"first error"}},' +
        '"ccs":{"id2":{"error":"second error"}}}';
      return Promise.resolve(cloneableResponse(400, text));
    });

    element.addEventListener('server-error', event => {
      if (event.target !== element) {
        return;
      }
      event.detail.response.text().then(body => {
        assert.equal(body, 'first error, second error');
        done();
      });
    });

    // Async tick is needed because iron-selector content is distributed and
    // distributed content requires an observer to be set up.
    flush(() => { element.send(); });
  });

  test('non-json 400 is treated as a normal server-error', done => {
    sinon.stub(window, 'fetch').callsFake(() => {
      const text = 'Comment validation error!';
      return Promise.resolve(cloneableResponse(400, text));
    });

    element.addEventListener('server-error', event => {
      if (event.target !== element) {
        return;
      }
      event.detail.response.text().then(body => {
        assert.equal(body, 'Comment validation error!');
        done();
      });
    });

    // Async tick is needed because iron-selector content is distributed and
    // distributed content requires an observer to be set up.
    flush(() => { element.send(); });
  });

  test('filterReviewerSuggestion', () => {
    const owner = makeAccount();
    const reviewer1 = makeAccount();
    const reviewer2 = makeGroup();
    const cc1 = makeAccount();
    const cc2 = makeGroup();
    let filter = element._filterReviewerSuggestionGenerator(false);

    element._owner = owner;
    element._reviewers = [reviewer1, reviewer2];
    element._ccs = [cc1, cc2];

    assert.isTrue(filter({account: makeAccount()}));
    assert.isTrue(filter({group: makeGroup()}));

    // Owner should be excluded.
    assert.isFalse(filter({account: owner}));

    // Existing and pending reviewers should be excluded when isCC = false.
    assert.isFalse(filter({account: reviewer1}));
    assert.isFalse(filter({group: reviewer2}));

    filter = element._filterReviewerSuggestionGenerator(true);

    // Existing and pending CCs should be excluded when isCC = true;.
    assert.isFalse(filter({account: cc1}));
    assert.isFalse(filter({group: cc2}));
  });

  test('_focusOn', () => {
    sinon.spy(element, '_chooseFocusTarget');
    flushAsynchronousOperations();
    const textareaStub = sinon.stub(element.$.textarea, 'async');
    const reviewerEntryStub = sinon.stub(element.$.reviewers.focusStart,
        'async');
    const ccStub = sinon.stub(element.$.ccs.focusStart, 'async');
    element._focusOn();
    assert.equal(element._chooseFocusTarget.callCount, 1);
    assert.deepEqual(textareaStub.callCount, 1);
    assert.deepEqual(reviewerEntryStub.callCount, 0);
    assert.deepEqual(ccStub.callCount, 0);

    element._focusOn(element.FocusTarget.ANY);
    assert.equal(element._chooseFocusTarget.callCount, 2);
    assert.deepEqual(textareaStub.callCount, 2);
    assert.deepEqual(reviewerEntryStub.callCount, 0);
    assert.deepEqual(ccStub.callCount, 0);

    element._focusOn(element.FocusTarget.BODY);
    assert.equal(element._chooseFocusTarget.callCount, 2);
    assert.deepEqual(textareaStub.callCount, 3);
    assert.deepEqual(reviewerEntryStub.callCount, 0);
    assert.deepEqual(ccStub.callCount, 0);

    element._focusOn(element.FocusTarget.REVIEWERS);
    assert.equal(element._chooseFocusTarget.callCount, 2);
    assert.deepEqual(textareaStub.callCount, 3);
    assert.deepEqual(reviewerEntryStub.callCount, 1);
    assert.deepEqual(ccStub.callCount, 0);

    element._focusOn(element.FocusTarget.CCS);
    assert.equal(element._chooseFocusTarget.callCount, 2);
    assert.deepEqual(textareaStub.callCount, 3);
    assert.deepEqual(reviewerEntryStub.callCount, 1);
    assert.deepEqual(ccStub.callCount, 1);
  });

  test('_chooseFocusTarget', () => {
    element._account = null;
    assert.strictEqual(
        element._chooseFocusTarget(), element.FocusTarget.BODY);

    element._account = {_account_id: 1};
    assert.strictEqual(
        element._chooseFocusTarget(), element.FocusTarget.BODY);

    element.change.owner = {_account_id: 2};
    assert.strictEqual(
        element._chooseFocusTarget(), element.FocusTarget.BODY);

    element.change.owner._account_id = 1;
    element.change._reviewers = null;
    assert.strictEqual(
        element._chooseFocusTarget(), element.FocusTarget.REVIEWERS);

    element._reviewers = [];
    assert.strictEqual(
        element._chooseFocusTarget(), element.FocusTarget.REVIEWERS);

    element._reviewers.push({});
    assert.strictEqual(
        element._chooseFocusTarget(), element.FocusTarget.BODY);
  });

  test('only send labels that have changed', done => {
    flush(() => {
      stubSaveReview(review => {
        assert.deepEqual(review.labels, {
          'Code-Review': 0,
          'Verified': -1,
        });
      });

      element.addEventListener('send', () => {
        done();
      });
      // Without wrapping this test in flush(), the below two calls to
      // MockInteractions.tap() cause a race in some situations in shadow DOM.
      // The send button can be tapped before the others, causing the test to
      // fail.

      element.shadowRoot
          .querySelector('gr-label-scores').shadowRoot
          .querySelector(
              'gr-label-score-row[name="Verified"]')
          .setSelectedValue(-1);
      MockInteractions.tap(element.shadowRoot
          .querySelector('.send'));
    });
  });

  test('_processReviewerChange', () => {
    const mockIndexSplices = function(toRemove) {
      return [{
        removed: [toRemove],
      }];
    };

    element._processReviewerChange(
        mockIndexSplices(makeAccount()), 'REVIEWER');
    assert.equal(element._reviewersPendingRemove.REVIEWER.length, 1);
  });

  test('_purgeReviewersPendingRemove', () => {
    const removeStub = sinon.stub(element, '_removeAccount');
    const mock = function() {
      element._reviewersPendingRemove = {
        test: [makeAccount()],
        test2: [makeAccount(), makeAccount()],
      };
    };
    const checkObjEmpty = function(obj) {
      for (const prop in obj) {
        if (obj.hasOwnProperty(prop) && obj[prop].length) { return false; }
      }
      return true;
    };
    mock();
    element._purgeReviewersPendingRemove(true); // Cancel
    assert.isFalse(removeStub.called);
    assert.isTrue(checkObjEmpty(element._reviewersPendingRemove));

    mock();
    element._purgeReviewersPendingRemove(false); // Submit
    assert.isTrue(removeStub.called);
    assert.isTrue(checkObjEmpty(element._reviewersPendingRemove));
  });

  test('_removeAccount', done => {
    sinon.stub(element.$.restAPI, 'removeChangeReviewer')
        .returns(Promise.resolve({ok: true}));
    const arr = [makeAccount(), makeAccount()];
    element.change.reviewers = {
      REVIEWER: arr.slice(),
    };

    element._removeAccount(arr[1], 'REVIEWER').then(() => {
      assert.equal(element.change.reviewers.REVIEWER.length, 1);
      assert.deepEqual(element.change.reviewers.REVIEWER, arr.slice(0, 1));
      done();
    });
  });

  test('moving from cc to reviewer', () => {
    element._reviewersPendingRemove = {
      CC: [],
      REVIEWER: [],
    };
    flushAsynchronousOperations();

    const reviewer1 = makeAccount();
    const reviewer2 = makeAccount();
    const reviewer3 = makeAccount();
    const cc1 = makeAccount();
    const cc2 = makeAccount();
    const cc3 = makeAccount();
    const cc4 = makeAccount();
    element._reviewers = [reviewer1, reviewer2, reviewer3];
    element._ccs = [cc1, cc2, cc3, cc4];
    element.push('_reviewers', cc1);
    flushAsynchronousOperations();

    assert.deepEqual(element._reviewers,
        [reviewer1, reviewer2, reviewer3, cc1]);
    assert.deepEqual(element._ccs, [cc2, cc3, cc4]);
    assert.deepEqual(element._reviewersPendingRemove.CC, [cc1]);

    element.push('_reviewers', cc4, cc3);
    flushAsynchronousOperations();

    assert.deepEqual(element._reviewers,
        [reviewer1, reviewer2, reviewer3, cc1, cc4, cc3]);
    assert.deepEqual(element._ccs, [cc2]);
    assert.deepEqual(element._reviewersPendingRemove.CC, [cc1, cc4, cc3]);
  });

  test('moving from reviewer to cc', () => {
    element._reviewersPendingRemove = {
      CC: [],
      REVIEWER: [],
    };
    flushAsynchronousOperations();

    const reviewer1 = makeAccount();
    const reviewer2 = makeAccount();
    const reviewer3 = makeAccount();
    const cc1 = makeAccount();
    const cc2 = makeAccount();
    const cc3 = makeAccount();
    const cc4 = makeAccount();
    element._reviewers = [reviewer1, reviewer2, reviewer3];
    element._ccs = [cc1, cc2, cc3, cc4];
    element.push('_ccs', reviewer1);
    flushAsynchronousOperations();

    assert.deepEqual(element._reviewers,
        [reviewer2, reviewer3]);
    assert.deepEqual(element._ccs, [cc1, cc2, cc3, cc4, reviewer1]);
    assert.deepEqual(element._reviewersPendingRemove.REVIEWER, [reviewer1]);

    element.push('_ccs', reviewer3, reviewer2);
    flushAsynchronousOperations();

    assert.deepEqual(element._reviewers, []);
    assert.deepEqual(element._ccs,
        [cc1, cc2, cc3, cc4, reviewer1, reviewer3, reviewer2]);
    assert.deepEqual(element._reviewersPendingRemove.REVIEWER,
        [reviewer1, reviewer3, reviewer2]);
  });

  test('migrate reviewers between states', done => {
    element._reviewersPendingRemove = {
      CC: [],
      REVIEWER: [],
    };
    flushAsynchronousOperations();
    const reviewers = element.$.reviewers;
    const ccs = element.$.ccs;
    const reviewer1 = makeAccount();
    const reviewer2 = makeAccount();
    const cc1 = makeAccount();
    const cc2 = makeAccount();
    const cc3 = makeAccount();
    element._reviewers = [reviewer1, reviewer2];
    element._ccs = [cc1, cc2, cc3];

    const mutations = [];

    stubSaveReview(review => mutations.push(...review.reviewers));

    sinon.stub(element, '_removeAccount').callsFake((account, type) => {
      mutations.push({state: 'REMOVED', account});
      return Promise.resolve();
    });

    // Remove and add to other field.
    reviewers.dispatchEvent(
        new CustomEvent('remove', {
          detail: {account: reviewer1},
          composed: true, bubbles: true,
        }));
    ccs.$.entry.dispatchEvent(
        new CustomEvent('add', {
          detail: {value: {account: reviewer1}},
          composed: true, bubbles: true,
        }));
    ccs.dispatchEvent(
        new CustomEvent('remove', {
          detail: {account: cc1},
          composed: true, bubbles: true,
        }));
    ccs.dispatchEvent(
        new CustomEvent('remove', {
          detail: {account: cc3},
          composed: true, bubbles: true,
        }));
    reviewers.$.entry.dispatchEvent(
        new CustomEvent('add', {
          detail: {value: {account: cc1}},
          composed: true, bubbles: true,
        }));

    // Add to other field without removing from former field.
    // (Currently not possible in UI, but this is a good consistency check).
    reviewers.$.entry.dispatchEvent(
        new CustomEvent('add', {
          detail: {value: {account: cc2}},
          composed: true, bubbles: true,
        }));
    ccs.$.entry.dispatchEvent(
        new CustomEvent('add', {
          detail: {value: {account: reviewer2}},
          composed: true, bubbles: true,
        }));
    const mapReviewer = function(reviewer, opt_state) {
      const result = {reviewer: reviewer._account_id, confirmed: undefined};
      if (opt_state) {
        result.state = opt_state;
      }
      return result;
    };

    // Send and purge and verify moves, delete cc3.
    element.send()
        .then(keepReviewers =>
          element._purgeReviewersPendingRemove(false, keepReviewers))
        .then(() => {
          assert.deepEqual(
              mutations, [
                mapReviewer(cc1),
                mapReviewer(cc2),
                mapReviewer(reviewer1, 'CC'),
                mapReviewer(reviewer2, 'CC'),
                {account: cc3, state: 'REMOVED'},
              ]);
          done();
        });
  });

  test('emits cancel on esc key', () => {
    const cancelHandler = sinon.spy();
    element.addEventListener('cancel', cancelHandler);
    MockInteractions.pressAndReleaseKeyOn(element, 27, null, 'esc');
    flushAsynchronousOperations();

    assert.isTrue(cancelHandler.called);
  });

  test('should not send on enter key', () => {
    stubSaveReview(() => undefined);
    element.addEventListener('send', () => assert.fail('wrongly called'));
    MockInteractions.pressAndReleaseKeyOn(element, 13, null, 'enter');
    flushAsynchronousOperations();
  });

  test('emit send on ctrl+enter key', done => {
    stubSaveReview(() => undefined);
    element.addEventListener('send', () => done());
    MockInteractions.pressAndReleaseKeyOn(element, 13, 'ctrl', 'enter');
    flushAsynchronousOperations();
  });

  test('_computeMessagePlaceholder', () => {
    assert.equal(
        element._computeMessagePlaceholder(false),
        'Say something nice...');
    assert.equal(
        element._computeMessagePlaceholder(true),
        'Add a note for your reviewers...');
  });

  test('_computeSendButtonLabel', () => {
    assert.equal(
        element._computeSendButtonLabel(false),
        'Send');
    assert.equal(
        element._computeSendButtonLabel(true),
        'Send and Start review');
  });

  test('_handle400Error reviewrs and CCs', done => {
    const error1 = 'error 1';
    const error2 = 'error 2';
    const error3 = 'error 3';
    const text = ')]}\'' + JSON.stringify({
      reviewers: {
        username1: {
          input: 'user 1',
          error: error1,
        },
        username2: {
          input: 'user 2',
          error: error2,
        },
      },
      ccs: {
        username3: {
          input: 'user 3',
          error: error3,
        },
      },
    });
    element.addEventListener('server-error', e => {
      e.detail.response.text().then(text => {
        assert.equal(text, [error1, error2, error3].join(', '));
        done();
      });
    });
    element._handle400Error(cloneableResponse(400, text));
  });

  test('_handle400Error CCs only', done => {
    const error1 = 'error 1';
    const text = ')]}\'' + JSON.stringify({
      ccs: {
        username1: {
          input: 'user 1',
          error: error1,
        },
      },
    });
    element.addEventListener('server-error', e => {
      e.detail.response.text().then(text => {
        assert.equal(text, error1);
        done();
      });
    });
    element._handle400Error(cloneableResponse(400, text));
  });

  test('fires height change when the drafts comments load', done => {
    // Flush DOM operations before binding to the autogrow event so we don't
    // catch the events fired from the initial layout.
    flush(() => {
      const autoGrowHandler = sinon.stub();
      element.addEventListener('autogrow', autoGrowHandler);
      element.draftCommentThreads = [];
      flush(() => {
        assert.isTrue(autoGrowHandler.called);
        done();
      });
    });
  });

  suite('post review API', () => {
    let startReviewStub;

    setup(() => {
      startReviewStub = sinon.stub(
          element.$.restAPI,
          'startReview')
          .callsFake(() => Promise.resolve());
    });

    test('ready property in review input on start review', () => {
      stubSaveReview(review => {
        assert.isTrue(review.ready);
        return {ready: true};
      });
      return element.send(true, true).then(() => {
        assert.isFalse(startReviewStub.called);
      });
    });

    test('no ready property in review input on save review', () => {
      stubSaveReview(review => {
        assert.isUndefined(review.ready);
      });
      return element.send(true, false).then(() => {
        assert.isFalse(startReviewStub.called);
      });
    });
  });

  suite('start review and save buttons', () => {
    let sendStub;

    setup(() => {
      sendStub = sinon.stub(element, 'send').callsFake(() => Promise.resolve());
      element.canBeStarted = true;
      // Flush to make both Start/Save buttons appear in DOM.
      flushAsynchronousOperations();
    });

    test('start review sets ready', () => {
      MockInteractions.tap(element.shadowRoot
          .querySelector('.send'));
      flushAsynchronousOperations();
      assert.isTrue(sendStub.calledWith(true, true));
    });

    test('save review doesn\'t set ready', () => {
      MockInteractions.tap(element.shadowRoot
          .querySelector('.save'));
      flushAsynchronousOperations();
      assert.isTrue(sendStub.calledWith(true, false));
    });
  });

  test('buttons disabled until all API calls are resolved', () => {
    stubSaveReview(review => {
      return {ready: true};
    });
    return element.send(true, true).then(() => {
      assert.isFalse(element.disabled);
    });
  });

  suite('error handling', () => {
    const expectedDraft = 'draft';
    const expectedError = new Error('test');

    setup(() => {
      element.draft = expectedDraft;
    });

    function assertDialogOpenAndEnabled() {
      assert.strictEqual(expectedDraft, element.draft);
      assert.isFalse(element.disabled);
    }

    test('error occurs in _saveReview', () => {
      stubSaveReview(review => {
        throw expectedError;
      });
      return element.send(true, true).catch(err => {
        assert.strictEqual(expectedError, err);
        assertDialogOpenAndEnabled();
      });
    });

    suite('pending diff drafts?', () => {
      test('yes', () => {
        const promise = mockPromise();
        const refreshHandler = sinon.stub();

        element.addEventListener('comment-refresh', refreshHandler);
        sinon.stub(element.$.restAPI, 'hasPendingDiffDrafts').returns(true);
        element.$.restAPI._pendingRequests.sendDiffDraft = [promise];
        element.open();

        assert.isFalse(refreshHandler.called);
        assert.isTrue(element._savingComments);

        promise.resolve();

        return element.$.restAPI.awaitPendingDiffDrafts().then(() => {
          assert.isTrue(refreshHandler.called);
          assert.isFalse(element._savingComments);
        });
      });

      test('no', () => {
        sinon.stub(element.$.restAPI, 'hasPendingDiffDrafts').returns(false);
        element.open();
        assert.notOk(element._savingComments);
      });
    });
  });

  test('_computeSendButtonDisabled_canBeStarted', () => {
    const fn = element._computeSendButtonDisabled.bind(element);
    // Mock canBeStarted
    assert.isFalse(fn(
        /* canBeStarted= */ true,
        /* draftCommentThreads= */ [],
        /* text= */ '',
        /* reviewersMutated= */ false,
        /* labelsChanged= */ false,
        /* includeComments= */ false,
        /* disabled= */ false,
        /* commentEditing= */ false,
        /* attentionModified= */ false
    ));
  });

  test('_computeSendButtonDisabled_allFalse', () => {
    const fn = element._computeSendButtonDisabled.bind(element);
    // Mock everything false
    assert.isTrue(fn(
        /* canBeStarted= */ false,
        /* draftCommentThreads= */ [],
        /* text= */ '',
        /* reviewersMutated= */ false,
        /* labelsChanged= */ false,
        /* includeComments= */ false,
        /* disabled= */ false,
        /* commentEditing= */ false,
        /* attentionModified= */ false
    ));
  });

  test('_computeSendButtonDisabled_attentionModified true', () => {
    const fn = element._computeSendButtonDisabled.bind(element);
    // Mock everything false
    assert.isFalse(fn(
        /* canBeStarted= */ false,
        /* draftCommentThreads= */ [],
        /* text= */ '',
        /* reviewersMutated= */ false,
        /* labelsChanged= */ false,
        /* includeComments= */ false,
        /* disabled= */ false,
        /* commentEditing= */ false,
        /* attentionModified= */ true
    ));
  });

  test('_computeSendButtonDisabled_draftCommentsSend', () => {
    const fn = element._computeSendButtonDisabled.bind(element);
    // Mock nonempty comment draft array, with sending comments.
    assert.isFalse(fn(
        /* canBeStarted= */ false,
        /* draftCommentThreads= */ [{comments: [{__draft: true}]}],
        /* text= */ '',
        /* reviewersMutated= */ false,
        /* labelsChanged= */ false,
        /* includeComments= */ true,
        /* disabled= */ false,
        /* commentEditing= */ false,
        /* attentionModified= */ false
    ));
  });

  test('_computeSendButtonDisabled_draftCommentsDoNotSend', () => {
    const fn = element._computeSendButtonDisabled.bind(element);
    // Mock nonempty comment draft array, without sending comments.
    assert.isTrue(fn(
        /* canBeStarted= */ false,
        /* draftCommentThreads= */ [{comments: [{__draft: true}]}],
        /* text= */ '',
        /* reviewersMutated= */ false,
        /* labelsChanged= */ false,
        /* includeComments= */ false,
        /* disabled= */ false,
        /* commentEditing= */ false,
        /* attentionModified= */ false
    ));
  });

  test('_computeSendButtonDisabled_changeMessage', () => {
    const fn = element._computeSendButtonDisabled.bind(element);
    // Mock nonempty change message.
    assert.isFalse(fn(
        /* canBeStarted= */ false,
        /* draftCommentThreads= */ {},
        /* text= */ 'test',
        /* reviewersMutated= */ false,
        /* labelsChanged= */ false,
        /* includeComments= */ false,
        /* disabled= */ false,
        /* commentEditing= */ false,
        /* attentionModified= */ false
    ));
  });

  test('_computeSendButtonDisabled_reviewersChanged', () => {
    const fn = element._computeSendButtonDisabled.bind(element);
    // Mock reviewers mutated.
    assert.isFalse(fn(
        /* canBeStarted= */ false,
        /* draftCommentThreads= */ {},
        /* text= */ '',
        /* reviewersMutated= */ true,
        /* labelsChanged= */ false,
        /* includeComments= */ false,
        /* disabled= */ false,
        /* commentEditing= */ false,
        /* attentionModified= */ false
    ));
  });

  test('_computeSendButtonDisabled_labelsChanged', () => {
    const fn = element._computeSendButtonDisabled.bind(element);
    // Mock labels changed.
    assert.isFalse(fn(
        /* canBeStarted= */ false,
        /* draftCommentThreads= */ {},
        /* text= */ '',
        /* reviewersMutated= */ false,
        /* labelsChanged= */ true,
        /* includeComments= */ false,
        /* disabled= */ false,
        /* commentEditing= */ false,
        /* attentionModified= */ false
    ));
  });

  test('_computeSendButtonDisabled_dialogDisabled', () => {
    const fn = element._computeSendButtonDisabled.bind(element);
    // Whole dialog is disabled.
    assert.isTrue(fn(
        /* canBeStarted= */ false,
        /* draftCommentThreads= */ {},
        /* text= */ '',
        /* reviewersMutated= */ false,
        /* labelsChanged= */ true,
        /* includeComments= */ false,
        /* disabled= */ true,
        /* commentEditing= */ false,
        /* attentionModified= */ false
    ));
    assert.isTrue(fn(
        /* buttonLabel= */ 'Send',
        /* draftCommentThreads= */ {},
        /* text= */ '',
        /* reviewersMutated= */ false,
        /* labelsChanged= */ true,
        /* includeComments= */ false,
        /* disabled= */ false,
        /* commentEditing= */ true,
        /* attentionModified= */ false
    ));
  });

  test('_submit blocked when no mutations exist', () => {
    const sendStub = sinon.stub(element, 'send').returns(Promise.resolve());
    // Stub the below function to avoid side effects from the send promise
    // resolving.
    sinon.stub(element, '_purgeReviewersPendingRemove');
    element.draftCommentThreads = [];
    flushAsynchronousOperations();

    MockInteractions.tap(element.shadowRoot
        .querySelector('gr-button.send'));
    assert.isFalse(sendStub.called);

    element.draftCommentThreads = [{comments: [{__draft: true}]}];
    flushAsynchronousOperations();

    MockInteractions.tap(element.shadowRoot
        .querySelector('gr-button.send'));
    assert.isTrue(sendStub.called);
  });

  test('send button should be disabled when any comment in editing', () => {
    element.draftCommentThreads = [
      {
        comments: [
          {
            __date: '2020-07-22 14:57:02.181',
            message: 'test',
            __draft: true,
          },
        ],
      },
      {
        comments: [
          {
            __date: '2020-07-22 14:55:02.181',
            message: 'aaa',
            __draft: true,
          },
        ],
      },
    ];
    flushAsynchronousOperations();
    const comments = querySelectorAll(element, 'gr-comment');

    flushAsynchronousOperations();
    assert.isFalse(element.shadowRoot
        .querySelector('gr-button.send').disabled);

    // edit the first comment
    MockInteractions.tap(comments[0].shadowRoot
        .querySelector('gr-button.edit'));

    flushAsynchronousOperations();
    assert.isTrue(element.shadowRoot
        .querySelector('gr-button.send').disabled);

    // edit the second one
    MockInteractions.tap(comments[1].shadowRoot
        .querySelector('gr-button.edit'));

    flushAsynchronousOperations();
    assert.isTrue(element.shadowRoot
        .querySelector('gr-button.send').disabled);

    // save the first comment
    MockInteractions.tap(comments[0].shadowRoot
        .querySelector('gr-button.save'));

    flushAsynchronousOperations();
    assert.isTrue(element.shadowRoot
        .querySelector('gr-button.send').disabled);

    // save the second one
    MockInteractions.tap(comments[1].shadowRoot
        .querySelector('gr-button.save'));

    flushAsynchronousOperations();
    assert.isFalse(element.shadowRoot
        .querySelector('gr-button.send').disabled);
  });

  test('getFocusStops', () => {
    // Setting draftCommentThreads to an empty object causes _sendDisabled to be
    // computed to false.
    element.draftCommentThreads = [];
    assert.equal(element.getFocusStops().end, element.$.cancelButton);
    element.draftCommentThreads = [{comments: [{__draft: true}]}];
    assert.equal(element.getFocusStops().end, element.$.sendButton);
  });

  test('setPluginMessage', () => {
    element.setPluginMessage('foo');
    assert.equal(element.$.pluginMessage.textContent, 'foo');
  });
});

