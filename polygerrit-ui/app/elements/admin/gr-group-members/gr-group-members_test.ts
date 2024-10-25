/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-group-members';
import {GrGroupMembers, ItemType} from './gr-group-members';
import {
  addListenerForTest,
  mockPromise,
  queryAll,
  queryAndAssert,
  stubBaseUrl,
  stubRestApi,
  waitUntil,
} from '../../../test/test-utils';
import {
  AccountId,
  AccountInfo,
  EmailAddress,
  GroupId,
  GroupInfo,
  GroupName,
} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrAutocomplete} from '../../shared/gr-autocomplete/gr-autocomplete';
import {PageErrorEvent} from '../../../types/events';
import {fixture, html, assert} from '@open-wc/testing';
import {createServerInfo} from '../../../test/test-data-generators';

suite('gr-group-members tests', () => {
  let element: GrGroupMembers;

  let groups: GroupInfo;
  let groupMembers: AccountInfo[];
  let includedGroups: GroupInfo[];
  let groupStub: sinon.SinonStub;

  setup(async () => {
    groups = {
      id: 'testId1' as GroupId,
      name: 'Administrators' as GroupName,
      owner: 'Administrators',
      group_id: 1,
    };

    groupMembers = [
      {
        _account_id: 1000097 as AccountId,
        name: 'Jane Roe',
        email: 'jane.roe@example.com' as EmailAddress,
        username: 'jane',
      },
      {
        _account_id: 1000096 as AccountId,
        name: 'Test User',
        email: 'john.doe@example.com' as EmailAddress,
      },
      {
        _account_id: 1000095 as AccountId,
        name: 'Gerrit',
      },
      {
        _account_id: 1000098 as AccountId,
      },
    ];

    includedGroups = [
      {
        url: 'https://group/url',
        options: {
          visible_to_all: false,
        },
        id: 'testId' as GroupId,
        name: 'testName' as GroupName,
      },
      {
        url: '/group/url',
        options: {
          visible_to_all: false,
        },
        id: 'testId2' as GroupId,
        name: 'testName2' as GroupName,
      },
      {
        url: '#/group/url',
        options: {
          visible_to_all: false,
        },
        id: 'testId3' as GroupId,
        name: 'testName3' as GroupName,
      },
    ];

    stubRestApi('queryAccounts').callsFake(input => {
      if (input.startsWith('test')) {
        return Promise.resolve([
          {
            _account_id: 1000096 as AccountId,
            name: 'test-account',
            email: 'test.account@example.com' as EmailAddress,
            username: 'test123',
            display_name: 'display-test-account',
          },
          {
            _account_id: 1001439 as AccountId,
            name: 'test-admin',
            email: 'test.admin@example.com' as EmailAddress,
            username: 'test_admin',
          },
          {
            _account_id: 1001439 as AccountId,
            name: 'test-git',
            username: 'test_git',
          },
        ]);
      } else {
        return Promise.resolve([]);
      }
    });
    stubRestApi('getSuggestedGroups').callsFake(input => {
      if (input.startsWith('test')) {
        return Promise.resolve({
          'test-admin': {
            id: '1ce023d3fb4e4260776fb92cd08b52bbd21ce70a',
          },
          'test/Administrator (admin)': {
            id: 'test%3Aadmin',
          },
        });
      } else {
        return Promise.resolve({});
      }
    });
    stubRestApi('getGroupMembers').returns(Promise.resolve(groupMembers));
    stubRestApi('getIsGroupOwner').returns(Promise.resolve(true));
    stubRestApi('getIncludedGroup').returns(Promise.resolve(includedGroups));
    element = await fixture(html`<gr-group-members></gr-group-members>`);
    stubBaseUrl('https://test/site');
    element.groupId = 'testId1' as GroupId;
    groupStub = stubRestApi('getGroupConfig').returns(Promise.resolve(groups));
    return element.loadGroupDetails();
  });

  test('render', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="gr-form-styles main">
          <div id="loading">Loading...</div>
          <div id="loadedContent">
            <h1 class="heading-1" id="Title">Administrators</h1>
            <div id="form">
              <h3 class="heading-3" id="members">Members</h3>
              <fieldset>
                <span class="value">
                  <gr-autocomplete
                    id="groupMemberSearchInput"
                    placeholder="Name Or Email"
                  >
                  </gr-autocomplete>
                </span>
                <gr-button
                  aria-disabled="true"
                  disabled=""
                  id="saveGroupMember"
                  role="button"
                  tabindex="-1"
                >
                  Add
                </gr-button>
                <table id="groupMembers">
                  <tbody>
                    <tr class="headerRow">
                      <th class="nameHeader">Name</th>
                      <th class="emailAddressHeader">Email Address</th>
                      <th class="deleteHeader">Delete Member</th>
                    </tr>
                  </tbody>
                  <tbody>
                    <tr>
                      <td class="nameColumn">
                        <gr-account-label clickable="" deselected="">
                        </gr-account-label>
                      </td>
                      <td>jane.roe@example.com</td>
                      <td class="deleteColumn">
                        <gr-button
                          aria-disabled="false"
                          class="deleteMembersButton"
                          data-index="0"
                          role="button"
                          tabindex="0"
                        >
                          Delete
                        </gr-button>
                      </td>
                    </tr>
                    <tr>
                      <td class="nameColumn">
                        <gr-account-label clickable="" deselected="">
                        </gr-account-label>
                      </td>
                      <td>john.doe@example.com</td>
                      <td class="deleteColumn">
                        <gr-button
                          aria-disabled="false"
                          class="deleteMembersButton"
                          data-index="1"
                          role="button"
                          tabindex="0"
                        >
                          Delete
                        </gr-button>
                      </td>
                    </tr>
                    <tr>
                      <td class="nameColumn">
                        <gr-account-label clickable="" deselected="">
                        </gr-account-label>
                      </td>
                      <td></td>
                      <td class="deleteColumn">
                        <gr-button
                          aria-disabled="false"
                          class="deleteMembersButton"
                          data-index="2"
                          role="button"
                          tabindex="0"
                        >
                          Delete
                        </gr-button>
                      </td>
                    </tr>
                    <tr>
                      <td class="nameColumn">
                        <gr-account-label clickable="" deselected="">
                        </gr-account-label>
                      </td>
                      <td></td>
                      <td class="deleteColumn">
                        <gr-button
                          aria-disabled="false"
                          class="deleteMembersButton"
                          data-index="3"
                          role="button"
                          tabindex="0"
                        >
                          Delete
                        </gr-button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </fieldset>
              <h3 class="heading-3" id="includedGroups">Included Groups</h3>
              <fieldset>
                <span class="value">
                  <gr-autocomplete
                    id="includedGroupSearchInput"
                    placeholder="Group Name"
                  >
                  </gr-autocomplete>
                </span>
                <gr-button
                  aria-disabled="true"
                  disabled=""
                  id="saveIncludedGroups"
                  role="button"
                  tabindex="-1"
                >
                  Add
                </gr-button>
                <table id="includedGroups">
                  <tbody>
                    <tr class="headerRow">
                      <th class="groupNameHeader">Group Name</th>
                      <th class="descriptionHeader">Description</th>
                      <th class="deleteIncludedHeader">Delete Group</th>
                    </tr>
                  </tbody>
                  <tbody>
                    <tr>
                      <td class="nameColumn">
                        <a href="https://group/url" rel="noopener">
                          testName
                        </a>
                      </td>
                      <td></td>
                      <td class="deleteColumn">
                        <gr-button
                          aria-disabled="false"
                          class="deleteIncludedGroupButton"
                          data-index="0"
                          role="button"
                          tabindex="0"
                        >
                          Delete
                        </gr-button>
                      </td>
                    </tr>
                    <tr>
                      <td class="nameColumn">
                        <a href="https://test/site/group/url" rel="noopener">
                          testName2
                        </a>
                      </td>
                      <td></td>
                      <td class="deleteColumn">
                        <gr-button
                          aria-disabled="false"
                          class="deleteIncludedGroupButton"
                          data-index="1"
                          role="button"
                          tabindex="0"
                        >
                          Delete
                        </gr-button>
                      </td>
                    </tr>
                    <tr>
                      <td class="nameColumn">
                        <a href="https://test/site/group/url" rel="noopener">
                          testName3
                        </a>
                      </td>
                      <td></td>
                      <td class="deleteColumn">
                        <gr-button
                          aria-disabled="false"
                          class="deleteIncludedGroupButton"
                          data-index="2"
                          role="button"
                          tabindex="0"
                        >
                          Delete
                        </gr-button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </fieldset>
            </div>
          </div>
        </div>
        <dialog id="modal" tabindex="-1">
          <gr-confirm-delete-item-dialog class="confirmDialog">
          </gr-confirm-delete-item-dialog>
        </dialog>
      `
    );
  });

  test('includedGroups', () => {
    assert.equal(element.includedGroups!.length, 3);
    assert.equal(
      queryAll<HTMLAnchorElement>(element, '.nameColumn a')[0].href,
      includedGroups[0].url
    );
    assert.equal(
      queryAll<HTMLAnchorElement>(element, '.nameColumn a')[1].href,
      'https://test/site/group/url'
    );
    assert.equal(
      queryAll<HTMLAnchorElement>(element, '.nameColumn a')[2].href,
      'https://test/site/group/url'
    );
  });

  test('save members correctly', async () => {
    element.groupOwner = true;

    const memberName = 'test-admin';

    const saveStub = stubRestApi('saveGroupMember').callsFake(() =>
      Promise.resolve({})
    );

    const button = queryAndAssert<GrButton>(element, '#saveGroupMember');

    assert.isTrue(button.hasAttribute('disabled'));

    const groupMemberSearchInput = queryAndAssert<GrAutocomplete>(
      element,
      '#groupMemberSearchInput'
    );
    groupMemberSearchInput.text = memberName;
    groupMemberSearchInput.value = '1234';

    await waitUntil(() => !button.hasAttribute('disabled'));

    return element.handleSavingGroupMember().then(() => {
      assert.isTrue(button.hasAttribute('disabled'));
      assert.isFalse(
        queryAndAssert<HTMLHeadingElement>(
          element,
          '#Title'
        ).classList.contains('edited')
      );
      assert.equal(saveStub.lastCall.args[0], 'Administrators');
      assert.equal(saveStub.lastCall.args[1], 1234);
    });
  });

  test('save included groups correctly', async () => {
    element.groupOwner = true;

    const includedGroupName = 'testName';

    const saveIncludedGroupStub = stubRestApi('saveIncludedGroup').callsFake(
      () => Promise.resolve({id: '0' as GroupId})
    );

    const button = queryAndAssert<GrButton>(element, '#saveIncludedGroups');

    await waitUntil(() => button.hasAttribute('disabled'));

    const includedGroupSearchInput = queryAndAssert<GrAutocomplete>(
      element,
      '#includedGroupSearchInput'
    );
    includedGroupSearchInput.text = includedGroupName;
    includedGroupSearchInput.value = 'testId';

    await waitUntil(() => !button.hasAttribute('disabled'));

    return element.handleSavingIncludedGroups().then(() => {
      assert.isTrue(button.hasAttribute('disabled'));
      assert.isFalse(
        queryAndAssert<HTMLHeadingElement>(
          element,
          '#Title'
        ).classList.contains('edited')
      );
      assert.equal(saveIncludedGroupStub.lastCall.args[0], 'Administrators');
      assert.equal(saveIncludedGroupStub.lastCall.args[1], 'testId');
    });
  });

  test('add included group 404 shows helpful error text', async () => {
    element.groupOwner = true;
    element.groupName = 'test' as GroupName;

    const memberName = 'bad-name';
    const alertStub = sinon.stub();
    element.addEventListener('show-alert', alertStub);
    const errorResponse = {...new Response(), status: 404, ok: false};
    stubRestApi('saveIncludedGroup').callsFake((_, _non, errFn) => {
      if (errFn !== undefined) {
        errFn(errorResponse);
      } else {
        assert.fail('errFn is undefined');
      }
      return Promise.resolve(undefined);
    });

    const groupMemberSearchInput = queryAndAssert<GrAutocomplete>(
      element,
      '#groupMemberSearchInput'
    );
    groupMemberSearchInput.text = memberName;
    groupMemberSearchInput.value = '1234';

    await element.updateComplete;
    element.handleSavingIncludedGroups().then(() => {
      assert.isTrue(alertStub.called);
    });
  });

  test('add included group network-error throws an exception', async () => {
    element.groupOwner = true;
    const memberName = 'bad-name';
    stubRestApi('saveIncludedGroup').throws(new Error());

    const groupMemberSearchInput = queryAndAssert<GrAutocomplete>(
      element,
      '#groupMemberSearchInput'
    );
    groupMemberSearchInput.text = memberName;
    groupMemberSearchInput.value = '1234';

    let exceptionThrown = false;
    try {
      await element.handleSavingIncludedGroups();
    } catch (e) {
      exceptionThrown = true;
    }
    assert.isTrue(exceptionThrown);
  });

  test('getAccountSuggestions empty', async () => {
    const accounts = await element.getAccountSuggestions(
      'nonexistent',
      createServerInfo()
    );
    assert.equal(accounts.length, 0);
  });

  test('getAccountSuggestions non-empty', async () => {
    const accounts = await element.getAccountSuggestions(
      'test-',
      createServerInfo()
    );
    assert.equal(accounts.length, 3);
    assert.equal(
      accounts[0].name,
      'display-test-account <test.account@example.com>'
    );
    assert.equal(accounts[1].name, 'test-admin <test.admin@example.com>');
    assert.equal(accounts[2].name, 'test-git');
  });

  test('getGroupSuggestions empty', async () => {
    const groups = await element.getGroupSuggestions('nonexistent');

    assert.equal(groups.length, 0);
  });

  test('getGroupSuggestions non-empty', async () => {
    const groups = await element.getGroupSuggestions('test');

    assert.equal(groups.length, 2);
    assert.equal(groups[0].name, 'test-admin');
    assert.equal(groups[1].name, 'test/Administrator (admin)');
  });

  test('delete member', () => {
    const deleteBtns = queryAll<GrButton>(element, '.deleteMembersButton');
    deleteBtns[0].click();
    assert.equal(element.itemId, 1000097 as AccountId);
    assert.equal(element.itemName, 'jane');
    queryAndAssert<HTMLDialogElement>(element, 'dialog').close();

    deleteBtns[1].click();
    assert.equal(element.itemId, 1000096 as AccountId);
    assert.equal(element.itemName, 'Test User');
    queryAndAssert<HTMLDialogElement>(element, 'dialog').close();

    deleteBtns[2].click();
    assert.equal(element.itemId, 1000095 as AccountId);
    assert.equal(element.itemName, 'Gerrit');
    queryAndAssert<HTMLDialogElement>(element, 'dialog').close();

    deleteBtns[3].click();
    assert.equal(element.itemId, 1000098 as AccountId);
    assert.equal(element.itemName, '1000098');
  });

  test('delete included groups', () => {
    const deleteBtns = queryAll<GrButton>(
      element,
      '.deleteIncludedGroupButton'
    );
    deleteBtns[0].click();
    assert.equal(element.itemId, 'testId' as GroupId);
    assert.equal(element.itemName, 'testName');
    queryAndAssert<HTMLDialogElement>(element, 'dialog').close();

    deleteBtns[1].click();
    assert.equal(element.itemId, 'testId2' as GroupId);
    assert.equal(element.itemName, 'testName2');
    queryAndAssert<HTMLDialogElement>(element, 'dialog').close();

    deleteBtns[2].click();
    assert.equal(element.itemId, 'testId3' as GroupId);
    assert.equal(element.itemName, 'testName3');
  });

  test('computeGroupUrl', () => {
    assert.isUndefined(element.computeGroupUrl(undefined));

    let url = '#/admin/groups/uuid-529b3c2605bb1029c8146f9de4a91c776fe64498';
    assert.equal(
      element.computeGroupUrl(url),
      'https://test/site/admin/groups/' +
        'uuid-529b3c2605bb1029c8146f9de4a91c776fe64498'
    );

    url =
      'https://gerrit.local/admin/groups/' +
      'uuid-529b3c2605bb1029c8146f9de4a91c776fe64498';
    assert.equal(element.computeGroupUrl(url), url);
  });

  test('fires page-error', async () => {
    groupStub.restore();

    element.groupId = 'testId1' as GroupId;

    const response = {...new Response(), status: 404};
    stubRestApi('getGroupConfig').callsFake((_, errFn) => {
      if (errFn !== undefined) {
        errFn(response);
      }
      return Promise.resolve(undefined);
    });
    const promise = mockPromise();
    addListenerForTest(document, 'page-error', e => {
      assert.deepEqual((e as PageErrorEvent).detail.response, response);
      promise.resolve();
    });

    element.loadGroupDetails();
    await promise;
  });

  test('_computeItemName', () => {
    assert.equal(element.computeItemTypeName(ItemType.MEMBER), 'Member');
    assert.equal(
      element.computeItemTypeName(ItemType.INCLUDED_GROUP),
      'Included Group'
    );
  });
});
