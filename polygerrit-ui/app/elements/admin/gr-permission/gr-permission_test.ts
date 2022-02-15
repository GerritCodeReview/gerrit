/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
import './gr-permission';
import {GrPermission} from './gr-permission';
import {stubRestApi} from '../../../test/test-utils';
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
    test('_sortPermission', () => {
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

      element._sortPermission(permission);
      assert.deepEqual(element._rules, expectedRules);
    });

    test('_computeLabel and _computeLabelValues', () => {
      const labels = {
        'Code-Review': {
          default_value: 0,
          values: {
            ' 0': 'No score',
            '-1': 'I would prefer this is not submitted as is',
            '-2': 'This shall not be merged',
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
        {value: -2, text: 'This shall not be merged'},
        {value: -1, text: 'I would prefer this is not submitted as is'},
        {value: 0, text: 'No score'},
        {value: 1, text: 'Looks good to me, but someone else must approve'},
        {value: 2, text: 'Looks good to me, approved'},
      ];

      const expectedLabel = {
        name: 'Code-Review',
        values: expectedLabelValues,
      };

      assert.deepEqual(
        element._computeLabelValues(labels['Code-Review'].values),
        expectedLabelValues
      );

      assert.deepEqual(
        element._computeLabel(permission, labels),
        expectedLabel
      );

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

      assert.isNotOk(element._computeLabel(permission, labels));
    });

    test('_computeSectionClass', () => {
      let deleted = true;
      let editing = false;
      assert.equal(element._computeSectionClass(editing, deleted), 'deleted');

      deleted = false;
      assert.equal(element._computeSectionClass(editing, deleted), '');

      editing = true;
      assert.equal(element._computeSectionClass(editing, deleted), 'editing');

      deleted = true;
      assert.equal(
        element._computeSectionClass(editing, deleted),
        'editing deleted'
      );
    });

    test('_computeGroupName', () => {
      const groups = {
        abc123: {id: '1' as GroupId, name: 'test group' as GroupName},
        bcd234: {id: '1' as GroupId},
      };
      assert.equal(
        element._computeGroupName(groups, 'abc123' as GroupId),
        'test group' as GroupName
      );
      assert.equal(
        element._computeGroupName(groups, 'bcd234' as GroupId),
        'bcd234' as GroupName
      );
    });

    test('_computeGroupsWithRules', () => {
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
      assert.deepEqual(element._computeGroupsWithRules(rules), groupsWithRules);
    });

    test('_getGroupSuggestions without existing rules', async () => {
      element._groupsWithRules = {};

      const groups = await element._getGroupSuggestions();
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

    test('_getGroupSuggestions with existing rules filters them', async () => {
      element._groupsWithRules = {
        '4c97682e6ce61b7247f3381b6f1789356666de7f': true,
      };

      const groups = await element._getGroupSuggestions();
      assert.deepEqual(groups, [
        {
          name: 'Anonymous Users',
          value: 'global%3AAnonymous-Users',
        },
      ]);
    });

    test('_handleRemovePermission', () => {
      element.editing = true;
      element.permission = {id: 'test' as GitRef, value: {rules: {}}};
      element._handleRemovePermission();
      assert.isTrue(element._deleted);
      assert.isTrue(element.permission.value.deleted);

      element.editing = false;
      assert.isFalse(element._deleted);
      assert.isNotOk(element.permission.value.deleted);
    });

    test('_handleUndoRemove', () => {
      element.permission = {
        id: 'test' as GitRef,
        value: {deleted: true, rules: {}},
      };
      element._handleUndoRemove();
      assert.isFalse(element._deleted);
      assert.isNotOk(element.permission.value.deleted);
    });

    test('_computeHasRange', () => {
      assert.isTrue(element._computeHasRange('Query Limit'));

      assert.isTrue(element._computeHasRange('Batch Changes Limit'));

      assert.isFalse(element._computeHasRange('test'));
    });
  });

  suite('interactions', () => {
    setup(() => {
      sinon.spy(element, '_computeLabel');
      element.name = 'Priority';
      element.section = 'refs/*' as GitRef;
      element.labels = {
        'Code-Review': {
          values: {
            ' 0': 'No score',
            '-1': 'I would prefer this is not submitted as is',
            '-2': 'This shall not be merged',
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
      element._setupValues();
      flush();
    });

    test('adding a rule', () => {
      element.name = 'Priority';
      element.section = 'refs/*' as GitRef;
      element.groups = {};
      queryAndAssert<GrAutocomplete>(element, '#groupAutocomplete').text =
        'ldap/tests te.st';
      const e = {
        detail: {
          value: 'ldap:CN=test+te.st',
        },
      } as CustomEvent<AutocompleteCommitEventDetail>;
      element.editing = true;
      assert.equal(element._rules!.length, 2);
      assert.equal(Object.keys(element._groupsWithRules!).length, 2);
      element._handleAddRuleItem(e);
      flush();
      assert.deepEqual(element.groups, {
        'ldap:CN=test te.st': {
          name: 'ldap/tests te.st',
        },
      });
      assert.equal(element._rules!.length, 3);
      assert.equal(Object.keys(element._groupsWithRules!).length, 3);
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
      assert.equal(element._rules!.length, 2);
      assert.equal(Object.keys(element.permission!.value.rules).length, 2);
    });

    test('removing an added rule', async () => {
      element.name = 'Priority';
      element.section = 'refs/*' as GitRef;
      element.groups = {};
      queryAndAssert<GrAutocomplete>(element, '#groupAutocomplete').text =
        'new group name';
      assert.equal(element._rules!.length, 2);
      queryAndAssert<GrRuleEditor>(element, 'gr-rule-editor').dispatchEvent(
        new CustomEvent('added-rule-removed', {
          composed: true,
          bubbles: true,
        })
      );
      await flush();
      assert.equal(element._rules!.length, 1);
    });

    test('removing an added permission', () => {
      const removeStub = sinon.stub();
      element.addEventListener('added-permission-removed', removeStub);
      element.editing = true;
      element.name = 'Priority';
      element.section = 'refs/*' as GitRef;
      element.permission!.value.added = true;
      MockInteractions.tap(queryAndAssert<GrButton>(element, '#removeBtn'));
      assert.isTrue(removeStub.called);
    });

    test('removing the permission', () => {
      element.editing = true;
      element.name = 'Priority';
      element.section = 'refs/*' as GitRef;

      const removeStub = sinon.stub();
      element.addEventListener('added-permission-removed', removeStub);

      assert.isFalse(
        queryAndAssert(element, '#permission').classList.contains('deleted')
      );
      assert.isFalse(element._deleted);
      MockInteractions.tap(queryAndAssert<GrButton>(element, '#removeBtn'));
      assert.isTrue(
        queryAndAssert(element, '#permission').classList.contains('deleted')
      );
      assert.isTrue(element._deleted);
      MockInteractions.tap(queryAndAssert<GrButton>(element, '#undoRemoveBtn'));
      assert.isFalse(
        queryAndAssert(element, '#permission').classList.contains('deleted')
      );
      assert.isFalse(element._deleted);
      assert.isFalse(removeStub.called);
    });

    test('modify a permission', () => {
      element.editing = true;
      element.name = 'Priority';
      element.section = 'refs/*' as GitRef;

      assert.isFalse(element._originalExclusiveValue);
      assert.isNotOk(element.permission!.value.modified);
      queryAndAssert(element, '#exclusiveToggle');
      MockInteractions.tap(queryAndAssert(element, '#exclusiveToggle'));
      flush();
      assert.isTrue(element.permission!.value.exclusive);
      assert.isTrue(element.permission!.value.modified);
      assert.isFalse(element._originalExclusiveValue);
      element.editing = false;
      assert.isFalse(element.permission!.value.exclusive);
    });

    test('_handleValueChange', () => {
      const modifiedHandler = sinon.stub();
      element.permission = {id: '0' as GitRef, value: {rules: {}}};
      element.addEventListener('access-modified', modifiedHandler);
      assert.isNotOk(element.permission.value.modified);
      element._handleValueChange();
      assert.isTrue(element.permission.value.modified);
      assert.isTrue(modifiedHandler.called);
    });

    test('Exclusive hidden for owner permission', () => {
      queryAndAssert(element, '#exclusiveToggle');
      assert.equal(
        getComputedStyle(queryAndAssert(element, '#exclusiveToggle')).display,
        'flex'
      );
      element.set(['permission', 'id'], 'owner');
      flush();
      assert.equal(
        getComputedStyle(queryAndAssert(element, '#exclusiveToggle')).display,
        'none'
      );
    });

    test('Exclusive hidden for any global permissions', () => {
      assert.equal(
        getComputedStyle(queryAndAssert(element, '#exclusiveToggle')).display,
        'flex'
      );
      element.section = 'GLOBAL_CAPABILITIES' as GitRef;
      flush();
      assert.equal(
        getComputedStyle(queryAndAssert(element, '#exclusiveToggle')).display,
        'none'
      );
    });
  });
});
