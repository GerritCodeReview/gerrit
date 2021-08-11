/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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

const basicFixture = fixtureFromElement('gr-cla-view');

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
    element = basicFixture.instantiate();
    await element.loadData();
    await flush();
  });

  test('renders as expected with signed agreement', () => {
    const agreementSections = queryAll(element, '.contributorAgreementButton');
    const agreementSubmittedTexts = queryAll(element, '.alreadySubmittedText');
    assert.equal(agreementSections.length, 2);
    assert.isFalse(
      queryAndAssert<HTMLInputElement>(agreementSections[0], 'input').disabled
    );
    assert.equal(getComputedStyle(agreementSubmittedTexts[0]).display, 'none');
    assert.isTrue(
      queryAndAssert<HTMLInputElement>(agreementSections[1], 'input').disabled
    );
    assert.notEqual(
      getComputedStyle(agreementSubmittedTexts[1]).display,
      'none'
    );
  });

  test('_disableAgreements', () => {
    // In the auto verify group and have not yet signed agreement
    assert.isTrue(element._disableAgreements(auth, groups, signedAgreements));
    // Not in the auto verify group and have not yet signed agreement
    assert.isFalse(element._disableAgreements(auth2, groups, signedAgreements));
    // Not in the auto verify group, have signed agreement
    assert.isTrue(element._disableAgreements(auth3, groups, signedAgreements));
    // Make sure the undefined check works
    assert.isFalse(
      element._disableAgreements(auth, undefined, signedAgreements)
    );
  });

  test('_hideAgreements', () => {
    // Not in the auto verify group and have not yet signed agreement
    assert.equal(element._hideAgreements(auth, groups, signedAgreements), '');
    // In the auto verify group
    assert.equal(
      element._hideAgreements(auth2, groups, signedAgreements),
      'hide'
    );
    // Not in the auto verify group, have signed agreement
    assert.equal(element._hideAgreements(auth3, groups, signedAgreements), '');
  });

  test('_disableAgreementsText', () => {
    assert.isFalse(element._disableAgreementsText('I AGREE'));
    assert.isTrue(element._disableAgreementsText('I DO NOT AGREE'));
  });

  test('_computeHideAgreementClass', () => {
    assert.equal(
      element._computeHideAgreementClass(
        auth.name,
        config.auth.contributor_agreements
      ),
      'hideAgreementsTextBox'
    );
    assert.isNotOk(
      element._computeHideAgreementClass(
        auth.name,
        config2.auth.contributor_agreements
      )
    );
  });

  test('_getAgreementsUrl', () => {
    assert.equal(
      element._getAgreementsUrl('http://test.org/test.html'),
      'http://test.org/test.html'
    );
    assert.equal(element._getAgreementsUrl('test_cla.html'), '/test_cla.html');
  });
});
