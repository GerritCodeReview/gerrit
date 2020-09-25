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
import './gr-change-list-item.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';

const basicFixture = fixtureFromElement('gr-change-list-item');

suite('gr-change-list-item tests', () => {
  let element;

  setup(() => {
    stub('gr-rest-api-interface', {
      getConfig() { return Promise.resolve({}); },
      getLoggedIn() { return Promise.resolve(false); },
    });
    element = basicFixture.instantiate();
  });

  test('computed fields', () => {
    assert.equal(element._computeLabelClass({labels: {}}),
        'cell label u-gray-background');
    assert.equal(element._computeLabelClass(
        {labels: {}}, 'Verified'), 'cell label u-gray-background');
    assert.equal(element._computeLabelClass(
        {labels: {Verified: {approved: true, value: 1}}}, 'Verified'),
    'cell label u-green u-monospace');
    assert.equal(element._computeLabelClass(
        {labels: {Verified: {rejected: true, value: -1}}}, 'Verified'),
    'cell label u-monospace u-red');
    assert.equal(element._computeLabelClass(
        {labels: {'Code-Review': {value: 1}}}, 'Code-Review'),
    'cell label u-green u-monospace');
    assert.equal(element._computeLabelClass(
        {labels: {'Code-Review': {value: -1}}}, 'Code-Review'),
    'cell label u-monospace u-red');
    assert.equal(element._computeLabelClass(
        {labels: {'Code-Review': {value: -1}}}, 'Verified'),
    'cell label u-gray-background');

    assert.equal(element._computeLabelTitle({labels: {}}, 'Verified'),
        'Label not applicable');
    assert.equal(element._computeLabelTitle(
        {labels: {Verified: {approved: {name: 'Diffy'}}}}, 'Verified'),
    'Verified\nby Diffy');
    assert.equal(element._computeLabelTitle(
        {labels: {Verified: {approved: {name: 'Diffy'}}}}, 'Code-Review'),
    'Label not applicable');
    assert.equal(element._computeLabelTitle(
        {labels: {Verified: {rejected: {name: 'Diffy'}}}}, 'Verified'),
    'Verified\nby Diffy');
    assert.equal(element._computeLabelTitle(
        {labels: {'Code-Review': {disliked: {name: 'Diffy'}, value: -1}}},
        'Code-Review'), 'Code-Review\nby Diffy');
    assert.equal(element._computeLabelTitle(
        {labels: {'Code-Review': {recommended: {name: 'Diffy'}, value: 1}}},
        'Code-Review'), 'Code-Review\nby Diffy');
    assert.equal(element._computeLabelTitle(
        {labels: {'Code-Review': {recommended: {name: 'Diffy'},
          rejected: {name: 'Admin'}}}}, 'Code-Review'),
    'Code-Review\nby Admin');
    assert.equal(element._computeLabelTitle(
        {labels: {'Code-Review': {approved: {name: 'Diffy'},
          rejected: {name: 'Admin'}}}}, 'Code-Review'),
    'Code-Review\nby Admin');
    assert.equal(element._computeLabelTitle(
        {labels: {'Code-Review': {recommended: {name: 'Diffy'},
          disliked: {name: 'Admin'}, value: -1}}}, 'Code-Review'),
    'Code-Review\nby Admin');
    assert.equal(element._computeLabelTitle(
        {labels: {'Code-Review': {approved: {name: 'Diffy'},
          disliked: {name: 'Admin'}, value: -1}}}, 'Code-Review'),
    'Code-Review\nby Diffy');

    assert.equal(element._computeLabelValue({labels: {}}), '');
    assert.equal(element._computeLabelValue({labels: {}}, 'Verified'), '');
    assert.equal(element._computeLabelValue(
        {labels: {Verified: {approved: true, value: 1}}}, 'Verified'), '✓');
    assert.equal(element._computeLabelValue(
        {labels: {Verified: {value: 1}}}, 'Verified'), '+1');
    assert.equal(element._computeLabelValue(
        {labels: {Verified: {value: -1}}}, 'Verified'), '-1');
    assert.equal(element._computeLabelValue(
        {labels: {Verified: {approved: true}}}, 'Verified'), '✓');
    assert.equal(element._computeLabelValue(
        {labels: {Verified: {rejected: true}}}, 'Verified'), '✕');
  });

  test('no hidden columns', () => {
    element.visibleChangeTableColumns = [
      'Subject',
      'Status',
      'Owner',
      'Assignee',
      'Reviewers',
      'Comments',
      'Repo',
      'Branch',
      'Updated',
      'Size',
    ];

    flush();

    for (const column of element.columnNames) {
      const elementClass = '.' + column.toLowerCase();
      assert.isOk(element.shadowRoot
          .querySelector(elementClass),
      `Expect ${elementClass} element to be found`);
      assert.isFalse(element.shadowRoot
          .querySelector(elementClass).hidden);
    }
  });

  test('repo column hidden', () => {
    element.visibleChangeTableColumns = [
      'Subject',
      'Status',
      'Owner',
      'Assignee',
      'Reviewers',
      'Comments',
      'Branch',
      'Updated',
      'Size',
    ];

    flush();

    for (const column of element.columnNames) {
      const elementClass = '.' + column.toLowerCase();
      if (column === 'Repo') {
        assert.isTrue(element.shadowRoot
            .querySelector(elementClass).hidden);
      } else {
        assert.isFalse(element.shadowRoot
            .querySelector(elementClass).hidden);
      }
    }
  });

  function checkComputeReviewers(
      userId, reviewerIds, reviewerNames, attSetIds, expected) {
    element.account = userId ? {_account_id: userId} : null;
    element.change = {
      owner: {
        _account_id: 99,
      },
      reviewers: {
        REVIEWER: [],
      },
      attention_set: {},
    };
    for (let i = 0; i < reviewerIds.length; i++) {
      element.change.reviewers.REVIEWER.push({
        _account_id: reviewerIds[i],
        name: reviewerNames[i],
      });
    }
    attSetIds.forEach(id => element.change.attention_set[id] = {});

    const actual = element._computeReviewers(element.change)
        .map(r => r._account_id);
    assert.deepEqual(actual, expected);
  }

  test('compute reviewers', () => {
    checkComputeReviewers(null, [], [], [], []);
    checkComputeReviewers(1, [], [], [], []);
    checkComputeReviewers(1, [2], ['a'], [], [2]);
    checkComputeReviewers(1, [2, 3], [undefined, 'a'], [], [2, 3]);
    checkComputeReviewers(1, [2, 3], ['a', undefined], [], [3, 2]);
    checkComputeReviewers(1, [99], ['owner'], [], []);
    checkComputeReviewers(
        1, [2, 3, 4, 5], ['b', 'a', 'd', 'c'], [3, 4], [3, 4, 2, 5]);
    checkComputeReviewers(
        1, [2, 3, 1, 4, 5], ['b', 'a', 'x', 'd', 'c'], [3, 4], [1, 3, 4, 2, 5]);
  });

  test('random column does not exist', () => {
    element.visibleChangeTableColumns = [
      'Bad',
    ];

    flush();
    const elementClass = '.bad';
    assert.isNotOk(element.shadowRoot
        .querySelector(elementClass));
  });

  test('assignee only displayed if there is one', () => {
    element.change = {};
    flush();
    assert.isNotOk(element.shadowRoot
        .querySelector('.assignee gr-account-link'));
    assert.equal(element.shadowRoot
        .querySelector('.assignee').textContent.trim(), '--');
    element.change = {
      assignee: {
        name: 'test',
        status: 'test',
      },
    };
    flush();
    assert.isOk(element.shadowRoot
        .querySelector('.assignee gr-account-link'));
  });

  test('TShirt sizing tooltip', () => {
    assert.equal(element._computeSizeTooltip({
      insertions: 'foo',
      deletions: 'bar',
    }), 'Size unknown');
    assert.equal(element._computeSizeTooltip({
      insertions: 0,
      deletions: 0,
    }), 'Size unknown');
    assert.equal(element._computeSizeTooltip({
      insertions: 1,
      deletions: 2,
    }), 'added 1, removed 2 lines');
  });

  test('TShirt sizing', () => {
    assert.equal(element._computeChangeSize({
      insertions: 'foo',
      deletions: 'bar',
    }), null);
    assert.equal(element._computeChangeSize({
      insertions: 1,
      deletions: 1,
    }), 'XS');
    assert.equal(element._computeChangeSize({
      insertions: 9,
      deletions: 1,
    }), 'S');
    assert.equal(element._computeChangeSize({
      insertions: 10,
      deletions: 200,
    }), 'M');
    assert.equal(element._computeChangeSize({
      insertions: 99,
      deletions: 900,
    }), 'L');
    assert.equal(element._computeChangeSize({
      insertions: 99,
      deletions: 999,
    }), 'XL');
  });

  test('change params passed to gr-navigation', () => {
    sinon.stub(GerritNav);
    const change = {
      internalHost: 'test-host',
      project: 'test-repo',
      topic: 'test-topic',
      branch: 'test-branch',
    };
    element.change = change;
    flush();

    assert.deepEqual(GerritNav.getUrlForChange.lastCall.args, [change]);
    assert.deepEqual(GerritNav.getUrlForProjectChanges.lastCall.args,
        [change.project, true, change.internalHost]);
    assert.deepEqual(GerritNav.getUrlForBranch.lastCall.args,
        [change.branch, change.project, undefined, change.internalHost]);
    assert.deepEqual(GerritNav.getUrlForTopic.lastCall.args,
        [change.topic, change.internalHost]);
  });

  test('_computeRepoDisplay', () => {
    const change = {
      project: 'a/test/repo',
      internalHost: 'host',
    };
    assert.equal(element._computeRepoDisplay(change), 'host/a/test/repo');
    assert.equal(element._computeRepoDisplay(change, true),
        'host/…/test/repo');
    delete change.internalHost;
    assert.equal(element._computeRepoDisplay(change), 'a/test/repo');
    assert.equal(element._computeRepoDisplay(change, true),
        '…/test/repo');
  });
});

