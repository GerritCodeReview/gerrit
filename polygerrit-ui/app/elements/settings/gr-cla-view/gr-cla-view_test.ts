/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-cla-view';
import {queryAll, queryAndAssert, stubRestApi} from '../../../test/test-utils';
import {GrClaView} from './gr-cla-view';
import {
  ContributorAgreementInfo,
  GroupId,
  GroupInfo,
  GroupName,
  ServerInfo,
} from '../../../types/common';
import {AuthType} from '../../../constants/constants';
import {createServerInfo} from '../../../test/test-data-generators';
import {fixture, html} from '@open-wc/testing-helpers';

suite('gr-cla-view tests', () => {
  let element: GrClaView;
  const signedAgreements = [
    {
      name: 'CLA',
      description: 'Contributor License Agreement',
      url: 'static/cla.html',
    },
  ];
  const auth: ContributorAgreementInfo = {
    name: 'Individual',
    description: 'test-description',
    url: 'static/cla_individual.html',
    auto_verify_group: {
      url: '#/admin/groups/uuid-e9aaddc47f305be7661ad4db9b66f9b707bd19a0',
      options: {
        visible_to_all: true,
      },
      group_id: 20,
      owner: 'CLA Accepted - Individual',
      owner_id: 'e9aaddc47f305be7661ad4db9b66f9b707bd19a0',
      created_on: '2017-07-31 15:11:04.000000000',
      id: 'e9aaddc47f305be7661ad4db9b66f9b707bd19a0' as GroupId,
      name: 'CLA Accepted - Individual' as GroupName,
    },
  };

  const auth2: ContributorAgreementInfo = {
    name: 'Individual2',
    description: 'test-description2',
    url: 'static/cla_individual2.html',
    auto_verify_group: {
      url: '#/admin/groups/uuid-bc53f2738ef8ad0b3a4f53846ff59b05822caecb',
      options: {
        visible_to_all: false,
      },
      group_id: 21,
      owner: 'CLA Accepted - Individual2',
      owner_id: 'bc53f2738ef8ad0b3a4f53846ff59b05822caecb',
      created_on: '2017-07-31 15:25:42.000000000',
      id: 'bc53f2738ef8ad0b3a4f53846ff59b05822caecb' as GroupId,
      name: 'CLA Accepted - Individual2' as GroupName,
    },
  };

  const auth3: ContributorAgreementInfo = {
    name: 'CLA',
    description: 'Contributor License Agreement',
    url: 'static/cla_individual.html',
  };

  const config: ServerInfo = {
    ...createServerInfo(),
    auth: {
      auth_type: AuthType.HTTP,
      editable_account_fields: [],
      use_contributor_agreements: true,
      contributor_agreements: [
        {
          name: 'Individual',
          description: 'test-description',
          url: 'static/cla_individual.html',
        },
        {
          name: 'CLA',
          description: 'Contributor License Agreement',
          url: 'static/cla.html',
        },
      ],
    },
  };
  const config2: ServerInfo = {
    ...createServerInfo(),
    auth: {
      auth_type: AuthType.HTTP,
      editable_account_fields: [],
      use_contributor_agreements: true,
      contributor_agreements: [
        {
          name: 'Individual2',
          description: 'test-description2',
          url: 'static/cla_individual2.html',
        },
      ],
    },
  };
  const groups: GroupInfo[] = [
    {
      options: {visible_to_all: true},
      id: 'e9aaddc47f305be7661ad4db9b66f9b707bd19a0' as GroupId,
      group_id: 3,
      name: 'CLA Accepted - Individual' as GroupName,
    },
  ];

  setup(async () => {
    stubRestApi('getConfig').returns(Promise.resolve(config));
    stubRestApi('getAccountGroups').returns(Promise.resolve(groups));
    stubRestApi('getAccountAgreements').returns(
      Promise.resolve(signedAgreements)
    );
    element = await fixture<GrClaView>(html` <gr-cla-view></gr-cla-view> `);
    await element.loadData();
    await element.updateComplete;
  });

  test('renders as expected with signed agreement', () => {
    const agreementSections = queryAll(element, '.contributorAgreementButton');
    const agreementSubmittedTexts = queryAll(element, '.alreadySubmittedText');
    assert.equal(agreementSections.length, 2);
    assert.isFalse(
      queryAndAssert<HTMLInputElement>(agreementSections[0], 'input').disabled
    );
    assert.isOk(agreementSubmittedTexts[0]);
    assert.isTrue(
      queryAndAssert<HTMLInputElement>(agreementSections[1], 'input').disabled
    );
    assert.isNotOk(agreementSubmittedTexts[1]);
  });

  test('disableAgreements', () => {
    element.groups = groups;
    element.signedAgreements = signedAgreements;
    // In the auto verify group and have not yet signed agreement
    assert.isTrue(element.disableAgreements(auth));
    // Not in the auto verify group and have not yet signed agreement
    assert.isFalse(element.disableAgreements(auth2));
    // Not in the auto verify group, have signed agreement
    assert.isTrue(element.disableAgreements(auth3));
    element.groups = undefined;
    // Make sure the undefined check works
    assert.isFalse(element.disableAgreements(auth));
  });

  test('computeHideAgreementTextbox', () => {
    element.agreementName = auth.name;
    element.serverConfig = config;
    assert.isTrue(element.computeHideAgreementTextbox());
    element.serverConfig = config2;
    assert.isFalse(element.computeHideAgreementTextbox());
  });

  test('getAgreementsUrl', () => {
    assert.equal(
      element.getAgreementsUrl('http://test.org/test.html'),
      'http://test.org/test.html'
    );
    assert.equal(element.getAgreementsUrl('test_cla.html'), '/test_cla.html');
  });
});
