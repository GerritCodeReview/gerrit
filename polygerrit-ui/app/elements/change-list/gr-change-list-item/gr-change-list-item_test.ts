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

import {fixture} from '@open-wc/testing-helpers';
import {html} from 'lit';
import {
  SubmitRequirementResultInfo,
  NumericChangeId,
} from '../../../api/rest-api';
import {getAppContext} from '../../../services/app-context';
import '../../../test/common-test-setup-karma';
import {
  createAccountWithId,
  createChange,
  createSubmitRequirementExpressionInfo,
  createSubmitRequirementResultInfo,
  createNonApplicableSubmitRequirementResultInfo,
  createServerInfo,
} from '../../../test/test-data-generators';
import {
  query,
  queryAndAssert,
  stubRestApi,
  stubFlags,
  waitUntilObserved,
} from '../../../test/test-utils';
import {
  AccountId,
  BranchName,
  ChangeInfo,
  RepoName,
  TopicName,
} from '../../../types/common';
import {StandardLabels} from '../../../utils/label-util';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import './gr-change-list-item';
import {GrChangeListItem} from './gr-change-list-item';
import {
  DIProviderElement,
  wrapInProvider,
} from '../../../models/di-provider-element';
import {
  bulkActionsModelToken,
  BulkActionsModel,
} from '../../../models/bulk-actions/bulk-actions-model';
import {createTestAppContext} from '../../../test/test-app-context-init';
import {tap} from '@polymer/iron-test-helpers/mock-interactions';
import {ColumnNames} from '../../../constants/constants';

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
  let bulkActionsModel: BulkActionsModel;

  setup(async () => {
    stubRestApi('getLoggedIn').returns(Promise.resolve(false));

    bulkActionsModel = new BulkActionsModel(
      createTestAppContext().restApiService
    );
    element = (
      await fixture<DIProviderElement>(
        wrapInProvider(
          html`<gr-change-list-item></gr-change-list-item>`,
          bulkActionsModelToken,
          bulkActionsModel
        )
      )
    ).element as GrChangeListItem;
    await element.updateComplete;
  });

  test('no hidden columns', async () => {
    element.visibleChangeTableColumns = [
      ColumnNames.SUBJECT,
      ColumnNames.STATUS,
      ColumnNames.OWNER,
      ColumnNames.REVIEWERS,
      ColumnNames.COMMENTS,
      ColumnNames.REPO,
      ColumnNames.BRANCH,
      ColumnNames.UPDATED,
      ColumnNames.SIZE,
      ColumnNames.STATUS2,
    ];

    await element.updateComplete;

    for (const column of Object.values(ColumnNames)) {
      const elementClass = '.' + column.trim().toLowerCase();
      assert.isFalse(
        queryAndAssert(element, elementClass).hasAttribute('hidden')
      );
    }
  });

  suite('checkbox', () => {
    test('selection checkbox is only shown if experiment is enabled', async () => {
      assert.isNotOk(query(element, '.selection'));
      stubFlags('isEnabled').returns(true);
      element.requestUpdate();
      await element.updateComplete;
      assert.isOk(query(element, '.selection'));
    });

    test('bulk actions checkboxes', async () => {
      stubFlags('isEnabled').returns(true);
      element.change = {...createChange(), _number: 1 as NumericChangeId};
      bulkActionsModel.sync([element.change]);
      await element.updateComplete;

      const checkbox = queryAndAssert<HTMLInputElement>(
        element,
        '.selection > input'
      );
      tap(checkbox);
      let selectedChangeNums = await waitUntilObserved(
        bulkActionsModel.selectedChangeNums$,
        s => s.length === 1
      );

      assert.deepEqual(selectedChangeNums, [1]);

      tap(checkbox);
      selectedChangeNums = await waitUntilObserved(
        bulkActionsModel.selectedChangeNums$,
        s => s.length === 0
      );

      assert.deepEqual(selectedChangeNums, []);
    });

    test('checkbox state updates with model updates', async () => {
      stubFlags('isEnabled').returns(true);
      element.requestUpdate();
      await element.updateComplete;

      element.change = {...createChange(), _number: 1 as NumericChangeId};
      bulkActionsModel.sync([element.change]);
      bulkActionsModel.addSelectedChangeNum(element.change._number);
      await waitUntilObserved(
        bulkActionsModel.selectedChangeNums$,
        s => s.length === 1
      );
      await element.updateComplete;

      const checkbox = queryAndAssert<HTMLInputElement>(
        element,
        '.selection > input'
      );
      assert.isTrue(checkbox.checked);

      bulkActionsModel.removeSelectedChangeNum(element.change._number);
      await waitUntilObserved(
        bulkActionsModel.selectedChangeNums$,
        s => s.length === 0
      );
      await element.updateComplete;

      assert.isFalse(checkbox.checked);
    });
  });

  test('repo column hidden', async () => {
    element.visibleChangeTableColumns = [
      ColumnNames.SUBJECT,
      ColumnNames.STATUS,
      ColumnNames.OWNER,
      ColumnNames.REVIEWERS,
      ColumnNames.COMMENTS,
      ColumnNames.BRANCH,
      ColumnNames.UPDATED,
      ColumnNames.SIZE,
      ColumnNames.STATUS2,
    ];

    await element.updateComplete;

    for (const column of Object.values(ColumnNames)) {
      const elementClass = '.' + column.trim().toLowerCase();
      if (column === 'Repo') {
        assert.isNotOk(query(element, elementClass));
      } else {
        assert.isOk(query(element, elementClass));
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
      element.change.reviewers.REVIEWER!.push({
        _account_id: reviewerIds[i] as AccountId,
        name: reviewerNames[i],
      });
    }
    attSetIds.forEach(id => (element.change!.attention_set![id] = {account}));

    const actual = element.computeReviewers().map(r => r._account_id);
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

    await element.updateComplete;
    const elementClass = '.bad';
    assert.isNotOk(query(element, elementClass));
  });

  test('TShirt sizing tooltip', () => {
    element.change = {
      ...change,
      insertions: NaN,
      deletions: NaN,
    };
    assert.equal(element.computeSizeTooltip(), 'Size unknown');
    element.change = {
      ...change,
      insertions: 0,
      deletions: 0,
    };
    assert.equal(element.computeSizeTooltip(), 'Size unknown');
    element.change = {
      ...change,
      insertions: 1,
      deletions: 2,
    };
    assert.equal(element.computeSizeTooltip(), 'added 1, removed 2 lines');
  });

  test('TShirt sizing', () => {
    element.change = {
      ...change,
      insertions: NaN,
      deletions: NaN,
    };
    assert.equal(element.computeChangeSize(), null);

    element.change = {
      ...change,
      insertions: 1,
      deletions: 1,
    };
    assert.equal(element.computeChangeSize(), 'XS');

    element.change = {
      ...change,
      insertions: 9,
      deletions: 1,
    };
    assert.equal(element.computeChangeSize(), 'S');

    element.change = {
      ...change,
      insertions: 10,
      deletions: 200,
    };
    assert.equal(element.computeChangeSize(), 'M');

    element.change = {
      ...change,
      insertions: 99,
      deletions: 900,
    };
    assert.equal(element.computeChangeSize(), 'L');

    element.change = {
      ...change,
      insertions: 99,
      deletions: 999,
    };
    assert.equal(element.computeChangeSize(), 'XL');
  });

  test('change params passed to gr-navigation', async () => {
    const navStub = sinon.stub(GerritNav);
    element.change = change;
    await element.updateComplete;

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

  test('computeRepoDisplay', () => {
    element.change = {...change};
    assert.equal(element.computeRepoDisplay(), 'host/a/test/repo');
    assert.equal(element.computeTruncatedRepoDisplay(), 'host/…/test/repo');
    delete change.internalHost;
    element.change = {...change};
    assert.equal(element.computeRepoDisplay(), 'a/test/repo');
    assert.equal(element.computeTruncatedRepoDisplay(), '…/test/repo');
  });

  test('renders', async () => {
    element.showStar = true;
    element.showNumber = true;
    element.account = createAccountWithId(1);
    element.config = createServerInfo();
    element.change = createChange();
    await element.updateComplete;
    expect(element).shadowDom.to.equal(`
      <gr-change-star></gr-change-star>
      <a href="">42</a>
      <a href="" title="Test subject">
        <div class="container">
          <div class="content"> Test subject </div>
          <div class="spacer"> Test subject </div>
          <span></span>
        </div>
      </a>
      <span class="placeholder"> -- </span>
      <gr-account-label
        deselected=""
        clickable=""
        highlightattention=""
      ></gr-account-label>
      <div></div>
      <span></span>
      <a class="fullRepo" href=""> test-project </a>
      <a class="truncatedRepo" href="" title="test-project"> test-project </a>
      <a href=""> test-branch </a>
      <gr-date-formatter withtooltip=""></gr-date-formatter>
      <gr-date-formatter withtooltip=""></gr-date-formatter>
      <gr-date-formatter forcerelative="" relativeoptionnoago="" withtooltip="">
      </gr-date-formatter>
      <gr-tooltip-content has-tooltip="" title="Size unknown">
        <span class="placeholder"> -- </span>
      </gr-tooltip-content>
      <gr-change-list-column-requirements-summary>
      </gr-change-list-column-requirements-summary>
    `);
  });

  test('renders requirement with new submit requirements', async () => {
    sinon.stub(getAppContext().flagsService, 'isEnabled').returns(true);
    const submitRequirement: SubmitRequirementResultInfo = {
      ...createSubmitRequirementResultInfo(),
      name: StandardLabels.CODE_REVIEW,
      submittability_expression_result: createSubmitRequirementExpressionInfo(),
    };
    const change: ChangeInfo = {
      ...createChange(),
      submit_requirements: [
        submitRequirement,
        createNonApplicableSubmitRequirementResultInfo(),
      ],
      unresolved_comment_count: 1,
    };
    const element = (
      await fixture<DIProviderElement>(
        wrapInProvider(
          html`<gr-change-list-item
            .change=${change}
            .labelNames=${[StandardLabels.CODE_REVIEW]}
          ></gr-change-list-item>`,
          bulkActionsModelToken,
          bulkActionsModel
        )
      )
    ).element as GrChangeListItem;

    const requirement = queryAndAssert(element, '.requirement');
    expect(requirement).dom.to
      .equal(/* HTML */ ` <gr-change-list-column-requirement>
    </gr-change-list-column-requirement>`);
  });
});
