/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../../test/common-test-setup-karma';
import './gr-permission';
import {GrPermission} from './gr-permission';
import {query, stubRestApi} from '../../../test/test-utils';
import {GitRef, GroupId, GroupName} from '../../../types/common';
import {PermissionAction} from '../../../constants/constants';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {
  AutocompleteCommitEventDetail,
  GrAutocomplete,
} from '../../shared/gr-autocomplete/gr-autocomplete';
import {queryAndAssert} from '../../../test/test-utils';
import {GrRuleEditor} from '../gr-rule-editor/gr-rule-editor';
import {GrButton} from '../../shared/gr-button/gr-button';

const basicFixture = fixtureFromElement('gr-permission');

suite('gr-permission tests', () => {
  let element: GrPermission;

  setup(() => {
    element = basicFixture.instantiate();
    stubRestApi('getSuggestedGroups').returns(
      Promise.resolve({
        Administrators: {
          id: '4c97682e6ce61b7247f3381b6f1789356666de7f' as GroupId,
        },
        'Anonymous Users': {
          id: 'global%3AAnonymous-Users' as GroupId,
        },
      })
    );
  });

  suite('unit tests', () => {
    test('sortPermission', async () => {
      const permission = {
        id: 'submit' as GitRef,
        value: {
          rules: {
            'global:Project-Owners': {
              action: PermissionAction.ALLOW,
              force: false,
            },
            '4c97682e6ce6b7247f3381b6f1789356666de7f': {
              action: PermissionAction.ALLOW,
              force: false,
            },
          },
        },
      };

      const expectedRules = [
        {
          id: '4c97682e6ce6b7247f3381b6f1789356666de7f' as GitRef,
          value: {action: PermissionAction.ALLOW, force: false},
        },
        {
          id: 'global:Project-Owners' as GitRef,
          value: {action: PermissionAction.ALLOW, force: false},
        },
      ];

      element.sortPermission(permission);
      await element.updateComplete;
      assert.deepEqual(element.rules, expectedRules);
    });

    test('computeLabel and computeLabelValues', async () => {
      const labels = {
        'Code-Review': {
          default_value: 0,
          values: {
            ' 0': 'No score',
            '-1': 'I would prefer this is not submitted as is',
            '-2': 'This shall not be submitted',
            '+1': 'Looks good to me, but someone else must approve',
            '+2': 'Looks good to me, approved',
          },
        },
      };
      let permission = {
        id: 'label-Code-Review' as GitRef,
        value: {
          label: 'Code-Review',
          rules: {
            'global:Project-Owners': {
              action: PermissionAction.ALLOW,
              force: false,
              min: -2,
              max: 2,
            },
            '4c97682e6ce6b7247f3381b6f1789356666de7f': {
              action: PermissionAction.ALLOW,
              force: false,
              min: -2,
              max: 2,
            },
          },
        },
      };

      const expectedLabelValues = [
        {value: -2, text: 'This shall not be submitted'},
        {value: -1, text: 'I would prefer this is not submitted as is'},
        {value: 0, text: 'No score'},
        {value: 1, text: 'Looks good to me, but someone else must approve'},
        {value: 2, text: 'Looks good to me, approved'},
      ];

      const expectedLabel = {
        name: 'Code-Review',
        values: expectedLabelValues,
      };

      element.permission = permission;
      element.labels = labels;
      await element.updateComplete;

      assert.deepEqual(
        element.computeLabelValues(labels['Code-Review'].values),
        expectedLabelValues
      );

      assert.deepEqual(element.computeLabel(), expectedLabel);

      permission = {
        id: 'label-reviewDB' as GitRef,
        value: {
          label: 'reviewDB',
          rules: {
            'global:Project-Owners': {
              action: PermissionAction.ALLOW,
              force: false,
              min: 0,
              max: 0,
            },
            '4c97682e6ce6b7247f3381b6f1789356666de7f': {
              action: PermissionAction.ALLOW,
              force: false,
              min: 0,
              max: 0,
            },
          },
        },
      };

      element.permission = permission;
      await element.updateComplete;

      assert.isNotOk(element.computeLabel());
    });

    test('computeSectionClass', async () => {
      let deleted = true;
      let editing = false;
      assert.equal(element.computeSectionClass(editing, deleted), 'deleted');

      deleted = false;
      assert.equal(element.computeSectionClass(editing, deleted), '');

      editing = true;
      assert.equal(element.computeSectionClass(editing, deleted), 'editing');

      deleted = true;
      assert.equal(
        element.computeSectionClass(editing, deleted),
        'editing deleted'
      );
    });

    test('computeGroupName', async () => {
      const groups = {
        abc123: {id: '1' as GroupId, name: 'test group' as GroupName},
        bcd234: {id: '1' as GroupId},
      };
      assert.equal(
        element.computeGroupName(groups, 'abc123' as GitRef),
        'test group' as GroupName
      );
      assert.equal(
        element.computeGroupName(groups, 'bcd234' as GitRef),
        'bcd234' as GroupName
      );
    });

    test('computeGroupsWithRules', async () => {
      const rules = [
        {
          id: '4c97682e6ce6b7247f3381b6f1789356666de7f' as GitRef,
          value: {action: PermissionAction.ALLOW, force: false},
        },
        {
          id: 'global:Project-Owners' as GitRef,
          value: {action: PermissionAction.ALLOW, force: false},
        },
      ];
      const groupsWithRules = {
        '4c97682e6ce6b7247f3381b6f1789356666de7f': true,
        'global:Project-Owners': true,
      };
      assert.deepEqual(element.computeGroupsWithRules(rules), groupsWithRules);
    });

    test('getGroupSuggestions without existing rules', async () => {
      element.groupsWithRules = {};
      await element.updateComplete;

      const groups = await element.getGroupSuggestions();
      assert.deepEqual(groups, [
        {
          name: 'Administrators',
          value: '4c97682e6ce61b7247f3381b6f1789356666de7f',
        },
        {
          name: 'Anonymous Users',
          value: 'global%3AAnonymous-Users',
        },
      ]);
    });

    test('getGroupSuggestions with existing rules filters them', async () => {
      element.groupsWithRules = {
        '4c97682e6ce61b7247f3381b6f1789356666de7f': true,
      };
      await element.updateComplete;

      const groups = await element.getGroupSuggestions();
      assert.deepEqual(groups, [
        {
          name: 'Anonymous Users',
          value: 'global%3AAnonymous-Users',
        },
      ]);
    });

    test('handleRemovePermission', async () => {
      element.editing = true;
      element.permission = {id: 'test' as GitRef, value: {rules: {}}};
      element.handleRemovePermission();
      await element.updateComplete;

      assert.isTrue(element.deleted);
      assert.isTrue(element.permission.value.deleted);

      element.editing = false;
      await element.updateComplete;
      assert.isFalse(element.deleted);
      assert.isNotOk(element.permission.value.deleted);
    });

    test('handleUndoRemove', async () => {
      element.permission = {
        id: 'test' as GitRef,
        value: {deleted: true, rules: {}},
      };
      element.handleUndoRemove();
      await element.updateComplete;

      assert.isFalse(element.deleted);
      assert.isNotOk(element.permission.value.deleted);
    });

    test('computeHasRange', async () => {
      assert.isTrue(element.computeHasRange('Query Limit'));

      assert.isTrue(element.computeHasRange('Batch Changes Limit'));

      assert.isFalse(element.computeHasRange('test'));
    });
  });

  suite('interactions', () => {
    setup(async () => {
      sinon.spy(element, 'computeLabel');
      element.name = 'Priority';
      element.section = 'refs/*' as GitRef;
      element.labels = {
        'Code-Review': {
          values: {
            ' 0': 'No score',
            '-1': 'I would prefer this is not submitted as is',
            '-2': 'This shall not be submitted',
            '+1': 'Looks good to me, but someone else must approve',
            '+2': 'Looks good to me, approved',
          },
          default_value: 0,
        },
      };
      element.permission = {
        id: 'label-Code-Review' as GitRef,
        value: {
          label: 'Code-Review',
          rules: {
            'global:Project-Owners': {
              action: PermissionAction.ALLOW,
              force: false,
              min: -2,
              max: 2,
            },
            '4c97682e6ce6b7247f3381b6f1789356666de7f': {
              action: PermissionAction.ALLOW,
              force: false,
              min: -2,
              max: 2,
            },
          },
        },
      };
      element.setupValues();
      await element.updateComplete;
      flush();
    });

    test('adding a rule', async () => {
      element.name = 'Priority';
      element.section = 'refs/*' as GitRef;
      element.groups = {};
      await element.updateComplete;

      queryAndAssert<GrAutocomplete>(element, '#groupAutocomplete').text =
        'ldap/tests te.st';
      const e = {
        detail: {
          value: 'ldap:CN=test+te.st',
        },
      } as CustomEvent<AutocompleteCommitEventDetail>;
      element.editing = true;
      assert.equal(element.rules!.length, 2);
      assert.equal(Object.keys(element.groupsWithRules!).length, 2);
      await element.handleAddRuleItem(e);
      assert.deepEqual(element.groups, {
        'ldap:CN=test te.st': {
          name: 'ldap/tests te.st',
        },
      });
      assert.equal(element.rules!.length, 3);
      assert.equal(Object.keys(element.groupsWithRules!).length, 3);
      assert.deepEqual(element.permission!.value.rules['ldap:CN=test te.st'], {
        action: PermissionAction.ALLOW,
        min: -2,
        max: 2,
        added: true,
      });
      assert.equal(
        queryAndAssert<GrAutocomplete>(element, '#groupAutocomplete').text,
        ''
      );
      // New rule should be removed if cancel from editing.
      element.editing = false;
      await element.updateComplete;
      assert.equal(element.rules!.length, 2);
      assert.equal(Object.keys(element.permission!.value.rules).length, 2);
    });

    test('removing an added rule', async () => {
      element.name = 'Priority';
      element.section = 'refs/*' as GitRef;
      element.groups = {};
      await element.updateComplete;
      queryAndAssert<GrAutocomplete>(element, '#groupAutocomplete').text =
        'new group name';
      assert.equal(element.rules!.length, 2);
      queryAndAssert<GrRuleEditor>(element, 'gr-rule-editor').dispatchEvent(
        new CustomEvent('added-rule-removed', {
          composed: true,
          bubbles: true,
        })
      );
      await flush();
      assert.equal(element.rules!.length, 1);
    });

    test('removing an added permission', async () => {
      const removeStub = sinon.stub();
      element.addEventListener('added-permission-removed', removeStub);
      element.editing = true;
      element.name = 'Priority';
      element.section = 'refs/*' as GitRef;
      element.permission!.value.added = true;
      await element.updateComplete;
      MockInteractions.tap(queryAndAssert<GrButton>(element, '#removeBtn'));
      await element.updateComplete;
      assert.isTrue(removeStub.called);
    });

    test('removing the permission', async () => {
      element.editing = true;
      element.name = 'Priority';
      element.section = 'refs/*' as GitRef;
      await element.updateComplete;

      const removeStub = sinon.stub();
      element.addEventListener('added-permission-removed', removeStub);

      assert.isFalse(
        queryAndAssert(element, '#permission').classList.contains('deleted')
      );
      assert.isFalse(element.deleted);
      MockInteractions.tap(queryAndAssert<GrButton>(element, '#removeBtn'));
      await element.updateComplete;
      assert.isTrue(
        queryAndAssert(element, '#permission').classList.contains('deleted')
      );
      assert.isTrue(element.deleted);
      MockInteractions.tap(queryAndAssert<GrButton>(element, '#undoRemoveBtn'));
      await element.updateComplete;
      assert.isFalse(
        queryAndAssert(element, '#permission').classList.contains('deleted')
      );
      assert.isFalse(element.deleted);
      assert.isFalse(removeStub.called);
    });

    test('modify a permission', async () => {
      element.editing = true;
      element.name = 'Priority';
      element.section = 'refs/*' as GitRef;
      await element.updateComplete;

      assert.isFalse(element.originalExclusiveValue);
      assert.isNotOk(element.permission!.value.modified);
      MockInteractions.tap(queryAndAssert(element, '#exclusiveToggle'));
      await element.updateComplete;
      assert.isTrue(element.permission!.value.exclusive);
      assert.isTrue(element.permission!.value.modified);
      assert.isFalse(element.originalExclusiveValue);
      element.editing = false;
      await element.updateComplete;
      assert.isFalse(element.permission!.value.exclusive);
    });

    test('modifying emits access-modified event', async () => {
      const modifiedHandler = sinon.stub();
      element.editing = true;
      element.name = 'Priority';
      element.section = 'refs/*' as GitRef;
      element.permission = {id: '0' as GitRef, value: {rules: {}}};
      element.addEventListener('access-modified', modifiedHandler);
      await element.updateComplete;
      assert.isNotOk(element.permission.value.modified);
      MockInteractions.tap(queryAndAssert(element, '#exclusiveToggle'));
      await element.updateComplete;
      assert.isTrue(element.permission.value.modified);
      assert.isTrue(modifiedHandler.called);
    });

    test('Exclusive hidden for owner permission', async () => {
      queryAndAssert(element, '#exclusiveToggle');

      element.permission!.id = 'owner' as GitRef;
      element.requestUpdate();
      await element.updateComplete;

      assert.notOk(query(element, '#exclusiveToggle'));
    });

    test('Exclusive hidden for any global permissions', async () => {
      queryAndAssert(element, '#exclusiveToggle');

      element.section = 'GLOBAL_CAPABILITIES' as GitRef;
      await element.updateComplete;

      assert.notOk(query(element, '#exclusiveToggle'));
    });
  });
});
