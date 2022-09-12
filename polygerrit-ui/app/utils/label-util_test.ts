/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup';
import {
  extractAssociatedLabels,
  getApprovalInfo,
  getLabelStatus,
  getMaxAccounts,
  getRepresentativeValue,
  getVotingRange,
  getVotingRangeOrDefault,
  getRequirements,
  getTriggerVotes,
  hasNeutralStatus,
  labelCompare,
  LabelStatus,
  computeLabels,
  mergeLabelMaps,
  computeOrderedLabelValues,
  mergeLabelInfoMaps,
  getApplicableLabels,
  isBlockingCondition,
  valueString,
  hasVotes,
  hasVoted,
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
  createChange,
  createSubmitRequirementExpressionInfo,
  createSubmitRequirementResultInfo,
  createNonApplicableSubmitRequirementResultInfo,
  createDetailedLabelInfo,
  createAccountWithId,
  createQuickLabelInfo,
  createApproval,
} from '../test/test-data-generators';
import {
  SubmitRequirementResultInfo,
  SubmitRequirementStatus,
  LabelNameToInfoMap,
  SubmitRequirementExpressionInfoStatus,
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
});
