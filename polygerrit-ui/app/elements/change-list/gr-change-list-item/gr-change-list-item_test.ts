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
import {
  createAccountWithId,
  createChange,
} from '../../../test/test-data-generators';
import {query, queryAndAssert, stubRestApi} from '../../../test/test-utils';
import {
  AccountId,
  BranchName,
  ChangeInfo,
  RepoName,
  TopicName,
} from '../../../types/common';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {columnNames} from '../gr-change-list/gr-change-list';
import './gr-change-list-item';
import {GrChangeListItem, LabelCategory} from './gr-change-list-item';

const basicFixture = fixtureFromElement('gr-change-list-item');

suite('gr-change-list-item tests', () => {
  const account = createAccountWithId();
  const change: ChangeInfo = {
    ...createChange(),
    internalHost: 'host',
    project: 'a/test/repo' as RepoName,
    topic: 'test-topic' as TopicName,
    branch: 'test-branch' as BranchName,
  };

  let element: GrChangeListItem;

  setup(() => {
    stubRestApi('getLoggedIn').returns(Promise.resolve(false));
    element = basicFixture.instantiate();
  });

  test('_computeLabelCategory', () => {
    assert.equal(
      element._computeLabelCategory({...change, labels: {}}, 'Verified'),
      LabelCategory.NOT_APPLICABLE
    );
    assert.equal(
      element._computeLabelCategory(
        {...change, labels: {Verified: {approved: account, value: 1}}},
        'Verified'
      ),
      LabelCategory.APPROVED
    );
    assert.equal(
      element._computeLabelCategory(
        {...change, labels: {Verified: {rejected: account, value: -1}}},
        'Verified'
      ),
      LabelCategory.REJECTED
    );
    assert.equal(
      element._computeLabelCategory(
        {
          ...change,
          labels: {'Code-Review': {approved: account, value: 1}},
          unresolved_comment_count: 1,
        },
        'Code-Review'
      ),
      LabelCategory.UNRESOLVED_COMMENTS
    );
    assert.equal(
      element._computeLabelCategory(
        {...change, labels: {'Code-Review': {value: 1}}},
        'Code-Review'
      ),
      LabelCategory.POSITIVE
    );
    assert.equal(
      element._computeLabelCategory(
        {...change, labels: {'Code-Review': {value: -1}}},
        'Code-Review'
      ),
      LabelCategory.NEGATIVE
    );
    assert.equal(
      element._computeLabelCategory(
        {...change, labels: {'Code-Review': {value: -1}}},
        'Verified'
      ),
      LabelCategory.NOT_APPLICABLE
    );
  });

  test('_computeLabelClass', () => {
    assert.equal(
      element._computeLabelClass({...change, labels: {}}, 'Verified'),
      'cell label u-gray-background'
    );
    assert.equal(
      element._computeLabelClass(
        {...change, labels: {Verified: {approved: account, value: 1}}},
        'Verified'
      ),
      'cell label u-green'
    );
    assert.equal(
      element._computeLabelClass(
        {...change, labels: {Verified: {rejected: account, value: -1}}},
        'Verified'
      ),
      'cell label u-red'
    );
    assert.equal(
      element._computeLabelClass(
        {...change, labels: {'Code-Review': {value: 1}}},
        'Code-Review'
      ),
      'cell label u-green u-monospace'
    );
    assert.equal(
      element._computeLabelClass(
        {...change, labels: {'Code-Review': {value: -1}}},
        'Code-Review'
      ),
      'cell label u-monospace u-red'
    );
    assert.equal(
      element._computeLabelClass(
        {...change, labels: {'Code-Review': {value: -1}}},
        'Verified'
      ),
      'cell label u-gray-background'
    );
  });

  test('_computeLabelTitle', () => {
    assert.equal(
      element._computeLabelTitle({...change, labels: {}}, 'Verified'),
      'Label not applicable'
    );
    assert.equal(
      element._computeLabelTitle(
        {...change, labels: {Verified: {approved: {name: 'Diffy'}}}},
        'Verified'
      ),
      'Verified by Diffy'
    );
    assert.equal(
      element._computeLabelTitle(
        {...change, labels: {Verified: {approved: {name: 'Diffy'}}}},
        'Code-Review'
      ),
      'Label not applicable'
    );
    assert.equal(
      element._computeLabelTitle(
        {...change, labels: {Verified: {rejected: {name: 'Diffy'}}}},
        'Verified'
      ),
      'Verified by Diffy'
    );
    assert.equal(
      element._computeLabelTitle(
        {
          ...change,
          labels: {'Code-Review': {disliked: {name: 'Diffy'}, value: -1}},
        },
        'Code-Review'
      ),
      'Code-Review by Diffy'
    );
    assert.equal(
      element._computeLabelTitle(
        {
          ...change,
          labels: {'Code-Review': {recommended: {name: 'Diffy'}, value: 1}},
        },
        'Code-Review'
      ),
      'Code-Review by Diffy'
    );
    assert.equal(
      element._computeLabelTitle(
        {
          ...change,
          labels: {
            'Code-Review': {
              recommended: {name: 'Diffy'},
              rejected: {name: 'Admin'},
            },
          },
        },
        'Code-Review'
      ),
      'Code-Review by Admin'
    );
    assert.equal(
      element._computeLabelTitle(
        {
          ...change,
          labels: {
            'Code-Review': {
              approved: {name: 'Diffy'},
              rejected: {name: 'Admin'},
            },
          },
        },
        'Code-Review'
      ),
      'Code-Review by Admin'
    );
    assert.equal(
      element._computeLabelTitle(
        {
          ...change,
          labels: {
            'Code-Review': {
              recommended: {name: 'Diffy'},
              disliked: {name: 'Admin'},
              value: -1,
            },
          },
        },
        'Code-Review'
      ),
      'Code-Review by Admin'
    );
    assert.equal(
      element._computeLabelTitle(
        {
          ...change,
          labels: {
            'Code-Review': {
              approved: {name: 'Diffy'},
              disliked: {name: 'Admin'},
              value: -1,
            },
          },
        },
        'Code-Review'
      ),
      'Code-Review by Diffy'
    );
    assert.equal(
      element._computeLabelTitle(
        {
          ...change,
          labels: {'Code-Review': {approved: account, value: 1}},
          unresolved_comment_count: 1,
        },
        'Code-Review'
      ),
      '1 unresolved comment'
    );
    assert.equal(
      element._computeLabelTitle(
        {
          ...change,
          labels: {'Code-Review': {approved: {name: 'Diffy'}, value: 1}},
          unresolved_comment_count: 1,
        },
        'Code-Review'
      ),
      '1 unresolved comment,\nCode-Review by Diffy'
    );
    assert.equal(
      element._computeLabelTitle(
        {
          ...change,
          labels: {'Code-Review': {approved: account, value: 1}},
          unresolved_comment_count: 2,
        },
        'Code-Review'
      ),
      '2 unresolved comments'
    );
  });

  test('_computeLabelIcon', () => {
    assert.equal(
      element._computeLabelIcon({...change, labels: {}}, 'missingLabel'),
      ''
    );
    assert.equal(
      element._computeLabelIcon(
        {...change, labels: {Verified: {approved: account, value: 1}}},
        'Verified'
      ),
      'gr-icons:check'
    );
    assert.equal(
      element._computeLabelIcon(
        {
          ...change,
          labels: {'Code-Review': {approved: account, value: 1}},
          unresolved_comment_count: 1,
        },
        'Code-Review'
      ),
      'gr-icons:comment'
    );
  });

  test('_computeLabelValue', () => {
    assert.equal(
      element._computeLabelValue({...change, labels: {}}, 'Verified'),
      ''
    );
    assert.equal(
      element._computeLabelValue(
        {...change, labels: {Verified: {approved: account, value: 1}}},
        'Verified'
      ),
      '✓'
    );
    assert.equal(
      element._computeLabelValue(
        {...change, labels: {Verified: {value: 1}}},
        'Verified'
      ),
      '+1'
    );
    assert.equal(
      element._computeLabelValue(
        {...change, labels: {Verified: {value: -1}}},
        'Verified'
      ),
      '-1'
    );
    assert.equal(
      element._computeLabelValue(
        {...change, labels: {Verified: {approved: account}}},
        'Verified'
      ),
      '✓'
    );
    assert.equal(
      element._computeLabelValue(
        {...change, labels: {Verified: {rejected: account}}},
        'Verified'
      ),
      '✕'
    );
  });

  test('no hidden columns', async () => {
    element.visibleChangeTableColumns = [
      'Subject',
      'Status',
      'Owner',
      'Reviewers',
      'Comments',
      'Repo',
      'Branch',
      'Updated',
      'Size',
      'Requirements',
    ];

    await flush();

    for (const column of columnNames) {
      const elementClass = '.' + column.toLowerCase();
      assert.isFalse(
        queryAndAssert(element, elementClass).hasAttribute('hidden')
      );
    }
  });

  test('repo column hidden', async () => {
    element.visibleChangeTableColumns = [
      'Subject',
      'Status',
      'Owner',
      'Reviewers',
      'Comments',
      'Branch',
      'Updated',
      'Size',
      'Requirements',
    ];

    await flush();

    for (const column of columnNames) {
      const elementClass = '.' + column.toLowerCase();
      if (column === 'Repo') {
        assert.isTrue(
          queryAndAssert(element, elementClass).hasAttribute('hidden')
        );
      } else {
        assert.isFalse(
          queryAndAssert(element, elementClass).hasAttribute('hidden')
        );
      }
    }
  });

  function checkComputeReviewers(
    userId: number | undefined,
    reviewerIds: number[],
    reviewerNames: (string | undefined)[],
    attSetIds: number[],
    expected: number[]
  ) {
    element.account = userId ? {_account_id: userId as AccountId} : null;
    element.change = {
      ...change,
      owner: {
        _account_id: 99 as AccountId,
      },
      reviewers: {
        REVIEWER: [],
      },
      attention_set: {},
    };
    for (let i = 0; i < reviewerIds.length; i++) {
      element.change!.reviewers.REVIEWER!.push({
        _account_id: reviewerIds[i] as AccountId,
        name: reviewerNames[i],
      });
    }
    attSetIds.forEach(id => (element.change!.attention_set![id] = {account}));

    const actual = element
      ._computeReviewers(element.change)
      .map(r => r._account_id);
    assert.deepEqual(actual, expected as AccountId[]);
  }

  test('compute reviewers', () => {
    checkComputeReviewers(undefined, [], [], [], []);
    checkComputeReviewers(1, [], [], [], []);
    checkComputeReviewers(1, [2], ['a'], [], [2]);
    checkComputeReviewers(1, [2, 3], [undefined, 'a'], [], [2, 3]);
    checkComputeReviewers(1, [2, 3], ['a', undefined], [], [3, 2]);
    checkComputeReviewers(1, [99], ['owner'], [], []);
    checkComputeReviewers(
      1,
      [2, 3, 4, 5],
      ['b', 'a', 'd', 'c'],
      [3, 4],
      [3, 4, 2, 5]
    );
    checkComputeReviewers(
      1,
      [2, 3, 1, 4, 5],
      ['b', 'a', 'x', 'd', 'c'],
      [3, 4],
      [1, 3, 4, 2, 5]
    );
  });

  test('random column does not exist', async () => {
    element.visibleChangeTableColumns = ['Bad'];

    await flush();
    const elementClass = '.bad';
    assert.isNotOk(query(element, elementClass));
  });

  test('TShirt sizing tooltip', () => {
    assert.equal(
      element._computeSizeTooltip({
        ...change,
        insertions: NaN,
        deletions: NaN,
      }),
      'Size unknown'
    );
    assert.equal(
      element._computeSizeTooltip({...change, insertions: 0, deletions: 0}),
      'Size unknown'
    );
    assert.equal(
      element._computeSizeTooltip({...change, insertions: 1, deletions: 2}),
      'added 1, removed 2 lines'
    );
  });

  test('TShirt sizing', () => {
    assert.equal(
      element._computeChangeSize({
        ...change,
        insertions: NaN,
        deletions: NaN,
      }),
      null
    );
    assert.equal(
      element._computeChangeSize({...change, insertions: 1, deletions: 1}),
      'XS'
    );
    assert.equal(
      element._computeChangeSize({...change, insertions: 9, deletions: 1}),
      'S'
    );
    assert.equal(
      element._computeChangeSize({...change, insertions: 10, deletions: 200}),
      'M'
    );
    assert.equal(
      element._computeChangeSize({...change, insertions: 99, deletions: 900}),
      'L'
    );
    assert.equal(
      element._computeChangeSize({...change, insertions: 99, deletions: 999}),
      'XL'
    );
  });

  test('change params passed to gr-navigation', async () => {
    const navStub = sinon.stub(GerritNav);
    element.change = change;
    await flush();

    assert.deepEqual(navStub.getUrlForChange.lastCall.args, [change]);
    assert.deepEqual(navStub.getUrlForProjectChanges.lastCall.args, [
      change.project,
      true,
      change.internalHost,
    ]);
    assert.deepEqual(navStub.getUrlForBranch.lastCall.args, [
      change.branch,
      change.project,
      undefined,
      change.internalHost,
    ]);
    assert.deepEqual(navStub.getUrlForTopic.lastCall.args, [
      change.topic,
      change.internalHost,
    ]);
  });

  test('_computeRepoDisplay', () => {
    assert.equal(element._computeRepoDisplay(change), 'host/a/test/repo');
    assert.equal(
      element._computeTruncatedRepoDisplay(change),
      'host/…/test/repo'
    );
    delete change.internalHost;
    assert.equal(element._computeRepoDisplay(change), 'a/test/repo');
    assert.equal(element._computeTruncatedRepoDisplay(change), '…/test/repo');
  });
});
