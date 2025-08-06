/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup';
import {
  canReviewerVote,
  computeLabels,
  computeOrderedLabelValues,
  extractAssociatedLabels,
  extractLabelsWithCountFrom,
  getApplicableLabels,
  getApprovalInfo,
  getCodeReviewLabel,
  getDefaultValue,
  getLabelStatus,
  getMaxAccounts,
  getRepresentativeValue,
  getRequirements,
  getTriggerVotes,
  getVotingRange,
  getVotingRangeOrDefault,
  hasApprovedVote,
  hasNeutralStatus,
  hasRejectedVote,
  hasVoted,
  hasVotes,
  isBlockingCondition,
  labelCompare,
  LabelStatus,
  mergeLabelInfoMaps,
  mergeLabelMaps,
  orderSubmitRequirementNames,
  orderSubmitRequirements,
  StandardLabels,
  valueString,
} from './label-util';
import {
  AccountId,
  AccountInfo,
  ApprovalInfo,
  DetailedLabelInfo,
  LabelInfo,
  QuickLabelInfo,
} from '../types/common';
import {
  createAccountWithEmail,
  createAccountWithId,
  createApproval,
  createChange,
  createDetailedLabelInfo,
  createNonApplicableSubmitRequirementResultInfo,
  createQuickLabelInfo,
  createSubmitRequirementExpressionInfo,
  createSubmitRequirementResultInfo,
} from '../test/test-data-generators';
import {
  LabelNameToInfoMap,
  LabelValueToDescriptionMap,
  SubmitRequirementExpressionInfoStatus,
  SubmitRequirementResultInfo,
  SubmitRequirementStatus,
} from '../api/rest-api';
import {assert} from '@open-wc/testing';

const VALUES_0 = {
  '0': 'neutral',
};

const VALUES_1 = {
  '-1': 'bad',
  '0': 'neutral',
  '+1': 'good',
};

const VALUES_2 = {
  '-2': 'blocking',
  '-1': 'bad',
  '0': 'neutral',
  '+1': 'good',
  '+2': 'perfect',
};

function createDetailedLabelInfoWithValues(
  values: LabelValueToDescriptionMap = VALUES_2
) {
  return {
    ...createDetailedLabelInfo(),
    values,
  };
}

function createApprovalWithValue(value: number, account?: AccountInfo) {
  const approval = createApproval(account);
  approval.value = value;
  return approval;
}

suite('label-util', () => {
  test('getVotingRange -1 to +1', () => {
    const label = {values: VALUES_1};
    const expectedRange = {min: -1, max: 1};
    assert.deepEqual(getVotingRange(label), expectedRange);
    assert.deepEqual(getVotingRangeOrDefault(label), expectedRange);
  });

  test('getVotingRange -2 to +2', () => {
    const label = {values: VALUES_2};
    const expectedRange = {min: -2, max: 2};
    assert.deepEqual(getVotingRange(label), expectedRange);
    assert.deepEqual(getVotingRangeOrDefault(label), expectedRange);
  });

  test('getVotingRange empty values', () => {
    const label = {
      values: {},
    };
    const expectedRange = {min: 0, max: 0};
    assert.isUndefined(getVotingRange(label));
    assert.deepEqual(getVotingRangeOrDefault(label), expectedRange);
  });

  test('getVotingRange no values', () => {
    const label = {};
    const expectedRange = {min: 0, max: 0};
    assert.isUndefined(getVotingRange(label));
    assert.deepEqual(getVotingRangeOrDefault(label), expectedRange);
  });

  test('getMaxAccounts', () => {
    const label: DetailedLabelInfo = {
      values: VALUES_2,
      all: [
        {value: 2, _account_id: 314 as AccountId},
        {value: 1, _account_id: 777 as AccountId},
      ],
    };

    const maxAccounts = getMaxAccounts(label);

    assert.equal(maxAccounts.length, 1);
    assert.equal(maxAccounts[0]._account_id, 314 as AccountId);
  });

  test('getMaxAccounts unset parameters', () => {
    assert.isEmpty(getMaxAccounts());
    assert.isEmpty(getMaxAccounts({}));
    assert.isEmpty(getMaxAccounts({values: VALUES_2}));
  });

  test('getApprovalInfo', () => {
    const myAccountInfo: AccountInfo = {_account_id: 314 as AccountId};
    const myApprovalInfo: ApprovalInfo = {
      value: 2,
      _account_id: 314 as AccountId,
    };
    const label: DetailedLabelInfo = {
      values: VALUES_2,
      all: [myApprovalInfo, {value: 1, _account_id: 777 as AccountId}],
    };
    assert.equal(getApprovalInfo(label, myAccountInfo), myApprovalInfo);
  });

  test('getApprovalInfo no approval for user', () => {
    const myAccountInfo: AccountInfo = {_account_id: 123 as AccountId};
    const label: DetailedLabelInfo = {
      values: VALUES_2,
      all: [
        {value: 2, _account_id: 314 as AccountId},
        {value: 1, _account_id: 777 as AccountId},
      ],
    };
    assert.isUndefined(getApprovalInfo(label, myAccountInfo));
  });

  test('labelCompare', () => {
    let sorted = ['c', 'b', 'a'].sort(labelCompare);
    assert.sameOrderedMembers(sorted, ['a', 'b', 'c']);
    sorted = ['b', 'a', 'Code-Review'].sort(labelCompare);
    assert.sameOrderedMembers(sorted, ['Code-Review', 'a', 'b']);
  });

  test('getLabelStatus', () => {
    let labelInfo: DetailedLabelInfo = {all: [], values: VALUES_2};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.NEUTRAL);
    labelInfo = {all: [{value: 0}], values: VALUES_0};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.NEUTRAL);
    labelInfo = {all: [{value: 0}], values: VALUES_1};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.NEUTRAL);
    labelInfo = {all: [{value: 0}], values: VALUES_2};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.NEUTRAL);
    labelInfo = {all: [{value: 1}], values: VALUES_2};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.RECOMMENDED);
    labelInfo = {all: [{value: -1}], values: VALUES_2};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.DISLIKED);
    labelInfo = {all: [{value: 1}], values: VALUES_1};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.APPROVED);
    labelInfo = {all: [{value: -1}], values: VALUES_1};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.REJECTED);
    labelInfo = {all: [{value: 2}, {value: 1}], values: VALUES_2};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.APPROVED);
    labelInfo = {all: [{value: -2}, {value: -1}], values: VALUES_2};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.REJECTED);
    labelInfo = {all: [{value: -2}, {value: 2}], values: VALUES_2};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.REJECTED);
    labelInfo = {all: [{value: -1}, {value: 2}], values: VALUES_2};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.APPROVED);
    labelInfo = {all: [{value: 0}, {value: 2}], values: VALUES_2};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.APPROVED);
    labelInfo = {all: [{value: 0}, {value: -2}], values: VALUES_2};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.REJECTED);
  });

  test('getLabelStatus - quicklabelinfo', () => {
    let labelInfo: QuickLabelInfo = {};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.NEUTRAL);
    labelInfo = {approved: createAccountWithEmail()};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.APPROVED);
    labelInfo = {rejected: createAccountWithEmail()};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.REJECTED);
    labelInfo = {recommended: createAccountWithEmail()};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.RECOMMENDED);
    labelInfo = {disliked: createAccountWithEmail()};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.DISLIKED);
  });

  test('getLabelStatus - detailed and quick info', () => {
    let labelInfo: LabelInfo = {all: [], values: VALUES_2};
    labelInfo = {
      all: [{value: 0}],
      values: VALUES_0,
      rejected: createAccountWithEmail(),
    };
    assert.equal(getLabelStatus(labelInfo), LabelStatus.NEUTRAL);
  });

  test('hasNeutralStatus', () => {
    const labelInfo: DetailedLabelInfo = {all: [], values: VALUES_2};
    assert.isTrue(hasNeutralStatus(labelInfo));
    assert.isTrue(hasNeutralStatus(labelInfo, {}));
    assert.isTrue(hasNeutralStatus(labelInfo, {value: 0}));
    assert.isFalse(hasNeutralStatus(labelInfo, {value: -1}));
    assert.isFalse(hasNeutralStatus(labelInfo, {value: 1}));
  });

  test('getRepresentativeValue', () => {
    let labelInfo: DetailedLabelInfo = {all: []};
    assert.equal(getRepresentativeValue(labelInfo), 0);
    labelInfo = {all: [{value: 0}]};
    assert.equal(getRepresentativeValue(labelInfo), 0);
    labelInfo = {all: [{value: 1}]};
    assert.equal(getRepresentativeValue(labelInfo), 1);
    labelInfo = {all: [{value: 2}, {value: 1}]};
    assert.equal(getRepresentativeValue(labelInfo), 2);
    labelInfo = {all: [{value: -2}, {value: -1}]};
    assert.equal(getRepresentativeValue(labelInfo), -2);
    labelInfo = {all: [{value: -2}, {value: 2}]};
    assert.equal(getRepresentativeValue(labelInfo), -2);
    labelInfo = {all: [{value: -1}, {value: 2}]};
    assert.equal(getRepresentativeValue(labelInfo), 2);
    labelInfo = {all: [{value: 0}, {value: 2}]};
    assert.equal(getRepresentativeValue(labelInfo), 2);
    labelInfo = {all: [{value: 0}, {value: -2}]};
    assert.equal(getRepresentativeValue(labelInfo), -2);
  });

  test('computeOrderedLabelValues', () => {
    const labelValues = computeOrderedLabelValues({
      'Code-Review': ['-2', '-1', ' 0', '+1', '+2'],
      Verified: ['-1', ' 0', '+1'],
    });
    assert.deepEqual(labelValues, [-2, -1, 0, 1, 2]);
  });

  test('computeLabels', async () => {
    const accountId = 123 as AccountId;
    const account = createAccountWithId(accountId);
    const change = {
      ...createChange(),
      labels: {
        'Code-Review': {
          values: {
            '0': 'No score',
            '+1': 'good',
            '+2': 'excellent',
            '-1': 'bad',
            '-2': 'terrible',
          },
          default_value: 0,
        } as DetailedLabelInfo,
        Verified: {
          values: {
            '0': 'No score',
            '+1': 'good',
            '+2': 'excellent',
            '-1': 'bad',
            '-2': 'terrible',
          },
          default_value: 0,
        } as DetailedLabelInfo,
      } as LabelNameToInfoMap,
    };
    let labels = computeLabels(account, change);
    assert.deepEqual(labels, [
      {name: 'Code-Review', value: null},
      {name: 'Verified', value: null},
    ]);
    change.labels = {
      ...change.labels,
      Verified: {
        ...change.labels.Verified,
        all: [
          {
            _account_id: accountId,
            value: 1,
          },
        ],
      } as DetailedLabelInfo,
    } as LabelNameToInfoMap;
    labels = computeLabels(account, change);
    assert.deepEqual(labels, [
      {name: 'Code-Review', value: null},
      {name: 'Verified', value: '+1'},
    ]);
  });

  test('mergeLabelInfoMaps', () => {
    assert.deepEqual(
      mergeLabelInfoMaps(
        {
          A: createDetailedLabelInfo(),
          B: createDetailedLabelInfo(),
        },
        undefined
      ),
      {}
    );
    assert.deepEqual(
      mergeLabelInfoMaps(undefined, {
        A: createDetailedLabelInfo(),
        B: createDetailedLabelInfo(),
      }),
      {}
    );

    assert.deepEqual(
      mergeLabelInfoMaps(
        {
          A: createDetailedLabelInfo(),
          B: createDetailedLabelInfo(),
        },
        {
          A: createDetailedLabelInfo(),
          B: createDetailedLabelInfo(),
        }
      ),
      {
        A: createDetailedLabelInfo(),
        B: createDetailedLabelInfo(),
      }
    );

    assert.deepEqual(
      mergeLabelInfoMaps(
        {
          A: createDetailedLabelInfo(),
          B: createDetailedLabelInfo(),
        },
        {
          B: createDetailedLabelInfo(),
          C: createDetailedLabelInfo(),
        }
      ),
      {
        B: createDetailedLabelInfo(),
      }
    );

    assert.deepEqual(
      mergeLabelInfoMaps(
        {
          A: createDetailedLabelInfo(),
          B: createDetailedLabelInfo(),
        },
        {
          X: createDetailedLabelInfo(),
          Y: createDetailedLabelInfo(),
        }
      ),
      {}
    );
  });

  test('mergeLabelMaps', () => {
    assert.deepEqual(
      mergeLabelMaps(
        {
          A: ['-1', '0', '+1', '+2'],
          B: ['-1', '0'],
          C: ['-1', '0'],
          D: ['0'],
        },
        undefined
      ),
      {}
    );

    assert.deepEqual(
      mergeLabelMaps(undefined, {
        A: ['-1', '0', '+1', '+2'],
        B: ['-1', '0'],
        C: ['-1', '0'],
        D: ['0'],
      }),
      {}
    );

    assert.deepEqual(
      mergeLabelMaps(
        {
          A: ['-1', '0', '+1', '+2'],
          B: ['-1', '0'],
          C: ['-1', '0'],
          D: ['0'],
        },
        {
          A: ['-1', '0', '+1', '+2'],
          B: ['-1', '0'],
          C: ['-1', '0'],
          D: ['0'],
        }
      ),
      {
        A: ['-1', '0', '+1', '+2'],
        B: ['-1', '0'],
        C: ['-1', '0'],
        D: ['0'],
      }
    );

    assert.deepEqual(
      mergeLabelMaps(
        {
          A: ['-1', '0', '+1', '+2'],
          B: ['-1', '0'],
          C: ['-1', '0'],
        },
        {
          A: ['-1', '0', '+1', '+2'],
          B: ['-1', '0'],
          D: ['0'],
        }
      ),
      {
        A: ['-1', '0', '+1', '+2'],
        B: ['-1', '0'],
      }
    );

    assert.deepEqual(
      mergeLabelMaps(
        {
          A: ['-1', '0', '+1', '+2'],
          B: ['-1', '0'],
          C: ['-1', '0'],
          D: ['0'],
        },
        {
          A: [],
          B: ['-1', '0'],
          C: ['0', '+1'],
          D: ['0'],
        }
      ),
      {
        A: [],
        B: ['-1', '0'],
        C: ['0'],
        D: ['0'],
      }
    );

    assert.deepEqual(
      mergeLabelMaps(
        {
          A: ['-1', '0', '+1', '+2'],
          B: ['-1', '0'],
          C: ['-1', '0'],
        },
        {
          X: ['-1', '0', '+1', '+2'],
          Y: ['-1', '0'],
          Z: ['0'],
        }
      ),
      {}
    );
  });

  suite('extractAssociatedLabels()', () => {
    test('1 label', () => {
      const submitRequirement = createSubmitRequirementResultInfo();
      const labels = extractAssociatedLabels(submitRequirement);
      assert.deepEqual(labels, ['Verified']);
    });
    test('label with number', () => {
      const submitRequirement = createSubmitRequirementResultInfo(
        'label2:verified=MAX'
      );
      const labels = extractAssociatedLabels(submitRequirement);
      assert.deepEqual(labels, ['verified']);
    });
    test('2 labels', () => {
      const submitRequirement = createSubmitRequirementResultInfo(
        'label:Verified=MAX -label:Code-Review=MIN'
      );
      const labels = extractAssociatedLabels(submitRequirement);
      assert.deepEqual(labels, ['Verified', 'Code-Review']);
    });
    test('overridden label', () => {
      const submitRequirement = {
        ...createSubmitRequirementResultInfo(),
        override_expression_result: createSubmitRequirementExpressionInfo(
          'label:Build-cop-override'
        ),
      };
      const labels = extractAssociatedLabels(submitRequirement);
      assert.deepEqual(labels, ['Verified', 'Build-cop-override']);
    });
  });

  suite('extractLabelsWithCountFrom', () => {
    test('returns an empty array when the expression does not match the pattern', () => {
      assert.deepEqual(extractLabelsWithCountFrom('foo'), []);
      assert.deepEqual(
        extractLabelsWithCountFrom('label:Verified=MAX -label:Code-Review=MIN'),
        []
      );
    });

    test('returns an empty array when count is not number', () => {
      assert.deepEqual(extractLabelsWithCountFrom('label:name,count>=a'), []);
    });

    test('returns an array with label and count object when the expression matches the pattern', () => {
      assert.deepEqual(extractLabelsWithCountFrom('label1:name=MIN,count>=1'), [
        {label: 'name', count: 1},
      ]);

      assert.deepEqual(
        extractLabelsWithCountFrom('label:Code-Review=MAX,count>=2'),
        [{label: 'Code-Review', count: 2}]
      );
    });
  });

  suite('getRequirements()', () => {
    function createChangeInfoWith(
      submit_requirements: SubmitRequirementResultInfo[]
    ) {
      return {
        ...createChange(),
        submit_requirements,
      };
    }
    test('only legacy', () => {
      const requirement = {
        ...createSubmitRequirementResultInfo(),
        is_legacy: true,
      };
      const change = createChangeInfoWith([requirement]);
      assert.deepEqual(getRequirements(change), [requirement]);
    });
    test('legacy and non-legacy - show all', () => {
      const requirement = {
        ...createSubmitRequirementResultInfo(),
        is_legacy: true,
      };
      const requirement2 = {
        ...createSubmitRequirementResultInfo(),
        is_legacy: false,
      };
      const change = createChangeInfoWith([requirement, requirement2]);
      assert.deepEqual(getRequirements(change), [requirement, requirement2]);
    });
    test('filter not applicable', () => {
      const requirement = createSubmitRequirementResultInfo();
      const requirement2 = createNonApplicableSubmitRequirementResultInfo();
      const change = createChangeInfoWith([requirement, requirement2]);
      assert.deepEqual(getRequirements(change), [requirement]);
    });
  });

  suite('getTriggerVotes()', () => {
    test('no requirements', () => {
      const triggerVote = 'Trigger-Vote';
      const change = {
        ...createChange(),
        labels: {
          [triggerVote]: createDetailedLabelInfo(),
        },
      };
      assert.deepEqual(getTriggerVotes(change), [triggerVote]);
    });
    test('no trigger votes, all labels associated with sub requirement', () => {
      const triggerVote = 'Trigger-Vote';
      const change = {
        ...createChange(),
        submit_requirements: [
          {
            ...createSubmitRequirementResultInfo(),
            submittability_expression_result: {
              ...createSubmitRequirementExpressionInfo(),
              expression: `label:${triggerVote}=MAX`,
            },
            is_legacy: false,
          },
        ],
        labels: {
          [triggerVote]: createDetailedLabelInfo(),
        },
      };
      assert.deepEqual(getTriggerVotes(change), []);
    });

    test('labels in not-applicable requirement are not trigger vote', () => {
      const triggerVote = 'Trigger-Vote';
      const change = {
        ...createChange(),
        submit_requirements: [
          {
            ...createSubmitRequirementResultInfo(),
            status: SubmitRequirementStatus.NOT_APPLICABLE,
            submittability_expression_result: {
              ...createSubmitRequirementExpressionInfo(),
              expression: `label:${triggerVote}=MAX`,
            },
            is_legacy: false,
          },
        ],
        labels: {
          [triggerVote]: createDetailedLabelInfo(),
        },
      };
      assert.deepEqual(getTriggerVotes(change), []);
    });
  });

  suite('getApplicableLabels()', () => {
    test('1 not applicable', () => {
      const notApplicableLabel = 'Not-Applicable-Label';
      const change = {
        ...createChange(),
        submit_requirements: [
          {
            ...createSubmitRequirementResultInfo(),
            status: SubmitRequirementStatus.NOT_APPLICABLE,
            submittability_expression_result: {
              ...createSubmitRequirementExpressionInfo(),
              expression: `label:${notApplicableLabel}=MAX`,
            },
            is_legacy: false,
          },
        ],
        labels: {
          [notApplicableLabel]: createDetailedLabelInfo(),
        },
      };
      assert.deepEqual(getApplicableLabels(change), []);
    });
    test('1 applicable, 1 not applicable', () => {
      const applicableLabel = 'Applicable-Label';
      const notApplicableLabel = 'Not-Applicable-Label';
      const change = {
        ...createChange(),
        submit_requirements: [
          {
            ...createSubmitRequirementResultInfo(),
            status: SubmitRequirementStatus.NOT_APPLICABLE,
            submittability_expression_result: {
              ...createSubmitRequirementExpressionInfo(),
              expression: `label:${notApplicableLabel}=MAX`,
            },
            is_legacy: false,
          },
          {
            ...createSubmitRequirementResultInfo(),
            status: SubmitRequirementStatus.UNSATISFIED,
            submittability_expression_result: {
              ...createSubmitRequirementExpressionInfo(),
              expression: `label:${applicableLabel}=MAX`,
            },
            is_legacy: false,
          },
        ],
        labels: {
          [notApplicableLabel]: createDetailedLabelInfo(),
          [applicableLabel]: createDetailedLabelInfo(),
        },
      };
      assert.deepEqual(getApplicableLabels(change), [applicableLabel]);
    });

    test('same label in applicable and not applicable requirement', () => {
      const label = 'label';
      const change = {
        ...createChange(),
        submit_requirements: [
          {
            ...createSubmitRequirementResultInfo(),
            status: SubmitRequirementStatus.NOT_APPLICABLE,
            submittability_expression_result: {
              ...createSubmitRequirementExpressionInfo(),
              expression: `label:${label}=MAX`,
            },
            is_legacy: false,
          },
          {
            ...createSubmitRequirementResultInfo(),
            status: SubmitRequirementStatus.UNSATISFIED,
            submittability_expression_result: {
              ...createSubmitRequirementExpressionInfo(),
              expression: `label:${label}=MAX`,
            },
            is_legacy: false,
          },
        ],
        labels: {
          [label]: createDetailedLabelInfo(),
        },
      };
      assert.deepEqual(getApplicableLabels(change), [label]);
    });
  });

  suite('getApplicableLabels()', () => {
    test('1 not applicable', () => {
      const notApplicableLabel = 'Not-Applicable-Label';
      const change = {
        ...createChange(),
        submit_requirements: [
          {
            ...createSubmitRequirementResultInfo(),
            status: SubmitRequirementStatus.NOT_APPLICABLE,
            submittability_expression_result: {
              ...createSubmitRequirementExpressionInfo(),
              expression: `label:${notApplicableLabel}=MAX`,
            },
            is_legacy: false,
          },
        ],
        labels: {
          [notApplicableLabel]: createDetailedLabelInfo(),
        },
      };
      assert.deepEqual(getApplicableLabels(change), []);
    });
    test('1 applicable, 1 not applicable', () => {
      const applicableLabel = 'Applicable-Label';
      const notApplicableLabel = 'Not-Applicable-Label';
      const change = {
        ...createChange(),
        submit_requirements: [
          {
            ...createSubmitRequirementResultInfo(),
            status: SubmitRequirementStatus.NOT_APPLICABLE,
            submittability_expression_result: {
              ...createSubmitRequirementExpressionInfo(),
              expression: `label:${notApplicableLabel}=MAX`,
            },
            is_legacy: false,
          },
          {
            ...createSubmitRequirementResultInfo(),
            status: SubmitRequirementStatus.UNSATISFIED,
            submittability_expression_result: {
              ...createSubmitRequirementExpressionInfo(),
              expression: `label:${applicableLabel}=MAX`,
            },
            is_legacy: false,
          },
        ],
        labels: {
          [notApplicableLabel]: createDetailedLabelInfo(),
          [applicableLabel]: createDetailedLabelInfo(),
        },
      };
      assert.deepEqual(getApplicableLabels(change), [applicableLabel]);
    });

    test('same label in applicable and not applicable requirement', () => {
      const label = 'label';
      const change = {
        ...createChange(),
        submit_requirements: [
          {
            ...createSubmitRequirementResultInfo(),
            status: SubmitRequirementStatus.NOT_APPLICABLE,
            submittability_expression_result: {
              ...createSubmitRequirementExpressionInfo(),
              expression: `label:${label}=MAX`,
            },
            is_legacy: false,
          },
          {
            ...createSubmitRequirementResultInfo(),
            status: SubmitRequirementStatus.UNSATISFIED,
            submittability_expression_result: {
              ...createSubmitRequirementExpressionInfo(),
              expression: `label:${label}=MAX`,
            },
            is_legacy: false,
          },
        ],
        labels: {
          [label]: createDetailedLabelInfo(),
        },
      };
      assert.deepEqual(getApplicableLabels(change), [label]);
    });
  });

  suite('isBlockingCondition', () => {
    test('true', () => {
      const requirement: SubmitRequirementResultInfo = {
        name: 'Code-Review',
        description:
          "At least one maximum vote for label 'Code-Review' is required",
        status: SubmitRequirementStatus.UNSATISFIED,
        is_legacy: false,
        submittability_expression_result: {
          expression:
            'label:Code-Review=MAX,user=non_uploader AND -label:Code-Review=MIN',
          fulfilled: false,
          status: SubmitRequirementExpressionInfoStatus.FAIL,
          passing_atoms: ['label:Code-Review=MIN'],
          failing_atoms: ['label:Code-Review=MAX,user=non_uploader'],
        },
      };
      assert.isTrue(isBlockingCondition(requirement));
    });

    test('false', () => {
      const requirement: SubmitRequirementResultInfo = {
        name: 'Code-Review',
        description:
          "At least one maximum vote for label 'Code-Review' is required",
        status: SubmitRequirementStatus.UNSATISFIED,
        is_legacy: false,
        submittability_expression_result: {
          expression:
            'label:Code-Review=MAX,user=non_uploader AND -label:Code-Review=MIN',
          fulfilled: false,
          status: SubmitRequirementExpressionInfoStatus.FAIL,
          passing_atoms: [],
          failing_atoms: [
            'label:Code-Review=MAX,user=non_uploader',
            'label:Code-Review=MIN',
          ],
        },
      };
      assert.isFalse(isBlockingCondition(requirement));
    });
  });

  suite('valueString', () => {
    const approvalInfo = createApproval();
    test('0', () => {
      approvalInfo.value = 0;
      assert.equal(valueString(approvalInfo.value), ' 0');
    });
    test('-1', () => {
      approvalInfo.value = -1;
      assert.equal(valueString(approvalInfo.value), '-1');
    });
    test('2', () => {
      approvalInfo.value = 2;
      assert.equal(valueString(approvalInfo.value), '+2');
    });
  });

  suite('hasVotes', () => {
    const detailedLabelInfo = createDetailedLabelInfo();
    const quickLabelInfo = createQuickLabelInfo();
    test('detailedLabelInfo - neutral vote => false', () => {
      const neutralApproval = createApproval();
      neutralApproval.value = 0;
      detailedLabelInfo.all = [neutralApproval];
      assert.isFalse(hasVotes(detailedLabelInfo));
    });
    test('detailedLabelInfo - positive vote => true', () => {
      const positiveApproval = createApproval();
      positiveApproval.value = 2;
      detailedLabelInfo.all = [positiveApproval];
      assert.isTrue(hasVotes(detailedLabelInfo));
    });
    test('quickLabelInfo - neutral => false', () => {
      assert.isFalse(hasVotes(quickLabelInfo));
    });
    test('quickLabelInfo - negative => false', () => {
      quickLabelInfo.rejected = createAccountWithId();
      assert.isTrue(hasVotes(quickLabelInfo));
    });
  });

  suite('hasVoted', () => {
    const detailedLabelInfo = createDetailedLabelInfo();
    const quickLabelInfo = createQuickLabelInfo();
    const account = createAccountWithId(23);
    test('detailedLabelInfo - positive vote => true', () => {
      const positiveApproval = createApproval(account);
      positiveApproval.value = 2;
      detailedLabelInfo.all = [positiveApproval];
      assert.isTrue(hasVoted(detailedLabelInfo, account));
    });
    test('detailedLabelInfo - different account vote => true', () => {
      const differentPositiveApproval = createApproval();
      differentPositiveApproval.value = 2;
      detailedLabelInfo.all = [differentPositiveApproval];
      assert.isFalse(hasVoted(detailedLabelInfo, account));
    });
    test('quickLabelInfo - negative => false', () => {
      quickLabelInfo.rejected = account;
      assert.isTrue(hasVoted(quickLabelInfo, account));
    });
  });

  suite('orderSubmitRequirements', () => {
    test('orders priority requirements first', () => {
      const codeReview = {
        ...createSubmitRequirementResultInfo(),
        name: StandardLabels.CODE_REVIEW,
      };
      const codeOwners = {
        ...createSubmitRequirementResultInfo(),
        name: StandardLabels.CODE_OWNERS,
      };
      const presubmitVerified = {
        ...createSubmitRequirementResultInfo(),
        name: StandardLabels.PRESUBMIT_VERIFIED,
      };
      const customLabel = createSubmitRequirementResultInfo('Custom-Label');

      const requirements = [
        customLabel,
        codeReview,
        presubmitVerified,
        codeOwners,
      ];
      const ordered = orderSubmitRequirements(requirements);

      assert.deepEqual(ordered, [
        codeReview,
        codeOwners,
        presubmitVerified,
        customLabel,
      ]);
    });

    test('preserves order of non-priority requirements', () => {
      const customLabel1 = {
        ...createSubmitRequirementResultInfo(),
        name: 'Custom-Label-1',
      };
      const customLabel2 = {
        ...createSubmitRequirementResultInfo(),
        name: 'Custom-Label-2',
      };
      const customLabel3 = {
        ...createSubmitRequirementResultInfo(),
        name: 'Custom-Label-3',
      };

      const requirements = [customLabel2, customLabel1, customLabel3];
      const ordered = orderSubmitRequirements(requirements);

      assert.deepEqual(ordered, requirements);
    });
  });

  suite('getDefaultValue', () => {
    test('returns default value when label exists and has default_value', () => {
      const defaultValue = 1;
      const labels = {
        'Code-Review': {
          ...createDetailedLabelInfo(),
          default_value: defaultValue,
        },
      };
      assert.equal(getDefaultValue(labels, 'Code-Review'), defaultValue);
    });
  });

  suite('hasApprovedVote', () => {
    test('returns true for quick label info with approved status', () => {
      const quickLabelInfo = createQuickLabelInfo();
      assert.isFalse(hasApprovedVote(quickLabelInfo));
      quickLabelInfo.approved = createAccountWithId();
      assert.isTrue(hasApprovedVote(quickLabelInfo));
    });

    test('returns true for detailed label info with approved vote', () => {
      const detailedLabelInfo = createDetailedLabelInfoWithValues();
      assert.isFalse(hasApprovedVote(detailedLabelInfo));
      const approval = createApprovalWithValue(2);
      detailedLabelInfo.all = [approval];
      assert.isTrue(hasApprovedVote(detailedLabelInfo));
    });
  });

  suite('hasRejectedVote', () => {
    test('returns true for quick label info with rejected status', () => {
      const quickLabelInfo = createQuickLabelInfo();
      assert.isFalse(hasRejectedVote(quickLabelInfo));
      quickLabelInfo.rejected = createAccountWithId();
      assert.isTrue(hasRejectedVote(quickLabelInfo));
    });

    test('returns true for detailed label info with rejected vote', () => {
      const detailedLabelInfo = createDetailedLabelInfoWithValues();
      assert.isFalse(hasRejectedVote(detailedLabelInfo));
      const approval = createApprovalWithValue(-2);
      detailedLabelInfo.all = [approval];
      assert.isTrue(hasRejectedVote(detailedLabelInfo));
    });
  });

  suite('canReviewerVote', () => {
    test('returns true when reviewer has permitted voting range with max > 0', () => {
      const detailedLabelInfo = createDetailedLabelInfo();
      const account = createAccountWithId();
      const approval = createApproval(account);
      assert.isFalse(canReviewerVote(detailedLabelInfo, account));
      approval.permitted_voting_range = {min: -1, max: 1};
      detailedLabelInfo.all = [approval];
      assert.isTrue(canReviewerVote(detailedLabelInfo, account));
    });
  });

  suite('getCodeReviewLabel', () => {
    test('returns Code-Review label from label map', () => {
      const codeReviewLabel = createDetailedLabelInfoWithValues(VALUES_2);
      const labels = {
        Verified: createDetailedLabelInfoWithValues(VALUES_1),
        [StandardLabels.CODE_REVIEW]: codeReviewLabel,
        'Custom-Label': createDetailedLabelInfoWithValues(VALUES_0),
      };
      assert.deepEqual(getCodeReviewLabel(labels), codeReviewLabel);
    });
  });

  suite('orderSubmitRequirementNames', () => {
    test('orders priority requirements first, then alphabetically', () => {
      const names = [
        'Custom-Label-2',
        StandardLabels.PRESUBMIT_VERIFIED,
        'Custom-Label-1',
        StandardLabels.CODE_REVIEW,
        'Custom-Label-3',
        StandardLabels.CODE_OWNERS,
      ];
      const expected = [
        StandardLabels.CODE_REVIEW,
        StandardLabels.CODE_OWNERS,
        StandardLabels.PRESUBMIT_VERIFIED,
        'Custom-Label-1',
        'Custom-Label-2',
        'Custom-Label-3',
      ];
      assert.deepEqual(orderSubmitRequirementNames(names), expected);
    });
  });
});
