/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-cla-view';
import {stubRestApi} from '../../../test/test-utils';
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
import {assert, fixture, html} from '@open-wc/testing';

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

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <main>
          <h1 class="heading-1">New Contributor Agreement</h1>
          <h3 class="heading-3">Select an agreement type:</h3>
          <span class="contributorAgreementButton">
            <md-radio
              data-name="Individual"
              data-url="static/cla_individual.html"
              id="claNewAgreementsInputIndividual"
              name="claNewAgreementsRadio"
            >
            </md-radio>
            <label id="claNewAgreementsLabel"> Individual </label>
          </span>
          <div class="agreementsUrl">test-description</div>
          <span class="contributorAgreementButton">
            <md-radio
              data-name="CLA"
              data-url="static/cla.html"
              disabled=""
              id="claNewAgreementsInputCLA"
              name="claNewAgreementsRadio"
            >
            </md-radio>
            <label id="claNewAgreementsLabel"> CLA </label>
          </span>
          <div class="alreadySubmittedText">Agreement already submitted.</div>
          <div class="agreementsUrl">Contributor License Agreement</div>
        </main>
      `,
      {ignoreAttributes: ['tabindex']}
    );
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
