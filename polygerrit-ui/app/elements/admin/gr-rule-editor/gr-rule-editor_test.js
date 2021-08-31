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

import '../../../test/common-test-setup-karma.js';
import './gr-rule-editor.js';

const basicFixture = fixtureFromElement('gr-rule-editor');

suite('gr-rule-editor tests', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();
  });

  suite('unit tests', () => {
    test('_computeForce, _computeForceClass, and _computeForceOptions',
        () => {
          const ForcePushOptions = {
            ALLOW: [
              {name: 'Allow pushing (but not force pushing)', value: false},
              {name: 'Allow pushing with or without force', value: true},
            ],
            BLOCK: [
              {name: 'Block pushing with or without force', value: false},
              {name: 'Block force pushing', value: true},
            ],
          };

          const FORCE_EDIT_OPTIONS = [
            {
              name: 'No Force Edit',
              value: false,
            },
            {
              name: 'Force Edit',
              value: true,
            },
          ];
          let permission = 'push';
          let action = 'ALLOW';
          assert.isTrue(element._computeForce(permission, action));
          assert.equal(element._computeForceClass(permission, action),
              'force');
          assert.deepEqual(element._computeForceOptions(permission, action),
              ForcePushOptions.ALLOW);

          action = 'BLOCK';
          assert.isTrue(element._computeForce(permission, action));
          assert.equal(element._computeForceClass(permission, action),
              'force');
          assert.deepEqual(element._computeForceOptions(permission, action),
              ForcePushOptions.BLOCK);

          action = 'DENY';
          assert.isFalse(element._computeForce(permission, action));
          assert.equal(element._computeForceClass(permission, action), '');
          assert.equal(
              element._computeForceOptions(permission, action).length, 0);

          permission = 'editTopicName';
          assert.isTrue(element._computeForce(permission));
          assert.equal(element._computeForceClass(permission), 'force');
          assert.deepEqual(element._computeForceOptions(permission),
              FORCE_EDIT_OPTIONS);
          permission = 'submit';
          assert.isFalse(element._computeForce(permission));
          assert.equal(element._computeForceClass(permission), '');
          assert.deepEqual(element._computeForceOptions(permission), []);
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
      assert.equal(element._computeSectionClass(editing, deleted),
          'editing deleted');
    });

    test('_getDefaultRuleValues', () => {
      let permission = 'priority';
      let label;
      assert.deepEqual(element._getDefaultRuleValues(permission, label),
          {action: 'BATCH'});
      permission = 'label-Code-Review';
      label = {values: [
        {value: -2, text: 'This shall not be merged'},
        {value: -1, text: 'I would prefer this is not merged as is'},
        {value: -0, text: 'No score'},
        {value: 1, text: 'Looks good to me, but someone else must approve'},
        {value: 2, text: 'Looks good to me, approved'},
      ]};
      assert.deepEqual(element._getDefaultRuleValues(permission, label),
          {action: 'ALLOW', max: 2, min: -2});
      permission = 'push';
      label = undefined;
      assert.deepEqual(element._getDefaultRuleValues(permission, label),
          {action: 'ALLOW', force: false});
      permission = 'submit';
      assert.deepEqual(element._getDefaultRuleValues(permission, label),
          {action: 'ALLOW'});
    });

    test('_setDefaultRuleValues', () => {
      element.rule = {id: 123};
      const defaultValue = {action: 'ALLOW'};
      sinon.stub(element, '_getDefaultRuleValues').returns(defaultValue);
      element._setDefaultRuleValues();
      assert.isTrue(element._getDefaultRuleValues.called);
      assert.equal(element.rule.value, defaultValue);
    });

    test('_computeOptions', () => {
      const PRIORITY_OPTIONS = [
        'BATCH',
        'INTERACTIVE',
      ];
      const DROPDOWN_OPTIONS = [
        'ALLOW',
        'DENY',
        'BLOCK',
      ];
      let permission = 'priority';
      assert.deepEqual(element._computeOptions(permission), PRIORITY_OPTIONS);
      permission = 'submit';
      assert.deepEqual(element._computeOptions(permission), DROPDOWN_OPTIONS);
    });

    test('_handleValueChange', () => {
      const modifiedHandler = sinon.stub();
      element.rule = {value: {}};
      element.addEventListener('access-modified', modifiedHandler);
      element._handleValueChange();
      assert.isNotOk(element.rule.value.modified);
      element._originalRuleValues = {};
      element._handleValueChange();
      assert.isTrue(element.rule.value.modified);
      assert.isTrue(modifiedHandler.called);
    });

    test('_handleAccessSaved', () => {
      const originalValue = {action: 'DENY'};
      const newValue = {action: 'ALLOW'};
      element._originalRuleValues = originalValue;
      element.rule = {value: newValue};
      element._handleAccessSaved();
      assert.deepEqual(element._originalRuleValues, newValue);
    });

    test('_setOriginalRuleValues', () => {
      const value = {
        action: 'ALLOW',
        force: false,
      };
      element._setOriginalRuleValues(value);
      assert.deepEqual(element._originalRuleValues, value);
    });
  });

  suite('already existing generic rule', () => {
    setup(async () => {
      element.group = 'Group Name';
      element.permission = 'submit';
      element.rule = {
        id: '123',
        value: {
          action: 'ALLOW',
          force: false,
        },
      };
      element.section = 'refs/*';

      // Typically called on ready since elements will have properties defined
      // by the parent element.
      element._setupValues(element.rule);
      await flush();
      element.connectedCallback();
    });

    test('_ruleValues and _originalRuleValues are set correctly', () => {
      assert.deepEqual(element._originalRuleValues, element.rule.value);
    });

    test('values are set correctly', () => {
      assert.equal(element.$.action.bindValue, element.rule.value.action);
      assert.isNotOk(element.root.querySelector('#labelMin'));
      assert.isNotOk(element.root.querySelector('#labelMax'));
      assert.isFalse(element.$.force.classList.contains('force'));
    });

    test('modify and cancel restores original values', () => {
      element.editing = true;
      assert.notEqual(getComputedStyle(element.$.removeBtn).display, 'none');
      assert.isNotOk(element.rule.value.modified);
      element.$.action.bindValue = 'DENY';
      assert.isTrue(element.rule.value.modified);
      element.editing = false;
      assert.equal(getComputedStyle(element.$.removeBtn).display, 'none');
      assert.deepEqual(element._originalRuleValues, element.rule.value);
      assert.equal(element.$.action.bindValue, 'ALLOW');
      assert.isNotOk(element.rule.value.modified);
    });

    test('modify value', () => {
      assert.isNotOk(element.rule.value.modified);
      element.$.action.bindValue = 'DENY';
      flush();
      assert.isTrue(element.rule.value.modified);

      // The original value should now differ from the rule values.
      assert.notDeepEqual(element._originalRuleValues, element.rule.value);
    });

    test('all selects are disabled when not in edit mode', () => {
      const selects = element.root.querySelectorAll('select');
      for (const select of selects) {
        assert.isTrue(select.disabled);
      }
      element.editing = true;
      for (const select of selects) {
        assert.isFalse(select.disabled);
      }
    });

    test('remove rule and undo remove', () => {
      element.editing = true;
      element.rule = {id: 123, value: {action: 'ALLOW'}};
      assert.isFalse(
          element.$.deletedContainer.classList.contains('deleted'));
      MockInteractions.tap(element.$.removeBtn);
      assert.isTrue(element.$.deletedContainer.classList.contains('deleted'));
      assert.isTrue(element._deleted);
      assert.isTrue(element.rule.value.deleted);

      MockInteractions.tap(element.$.undoRemoveBtn);
      assert.isFalse(element._deleted);
      assert.isNotOk(element.rule.value.deleted);
    });

    test('remove rule and cancel', () => {
      element.editing = true;
      assert.notEqual(getComputedStyle(element.$.removeBtn).display, 'none');
      assert.equal(getComputedStyle(element.$.deletedContainer).display,
          'none');

      element.rule = {id: 123, value: {action: 'ALLOW'}};
      MockInteractions.tap(element.$.removeBtn);
      assert.notEqual(getComputedStyle(element.$.removeBtn).display, 'none');
      assert.notEqual(getComputedStyle(element.$.deletedContainer).display,
          'none');
      assert.isTrue(element._deleted);
      assert.isTrue(element.rule.value.deleted);

      element.editing = false;
      assert.isFalse(element._deleted);
      assert.isNotOk(element.rule.value.deleted);
      assert.isNotOk(element.rule.value.modified);

      assert.deepEqual(element._originalRuleValues, element.rule.value);
      assert.equal(getComputedStyle(element.$.removeBtn).display, 'none');
      assert.equal(getComputedStyle(element.$.deletedContainer).display,
          'none');
    });

    test('_computeGroupPath', () => {
      const group = '123';
      assert.equal(element._computeGroupPath(group),
          `/admin/groups/123`);
    });
  });

  suite('new edit rule', () => {
    setup(async () => {
      element.group = 'Group Name';
      element.permission = 'editTopicName';
      element.rule = {
        id: '123',
      };
      element.section = 'refs/*';
      element._setupValues(element.rule);
      await flush();
      element.rule.value.added = true;
      await flush();
      element.connectedCallback();
    });

    test('_ruleValues and _originalRuleValues are set correctly', () => {
      // Since the element does not already have default values, they should
      // be set. The original values should be set to those too.
      assert.isNotOk(element.rule.value.modified);
      const expectedRuleValue = {
        action: 'ALLOW',
        force: false,
        added: true,
      };
      assert.deepEqual(element.rule.value, expectedRuleValue);
      test('values are set correctly', () => {
        assert.equal(element.$.action.bindValue, expectedRuleValue.action);
        assert.equal(element.$.force.bindValue, expectedRuleValue.action);
      });
    });

    test('modify value', () => {
      assert.isNotOk(element.rule.value.modified);
      element.$.force.bindValue = true;
      flush();
      assert.isTrue(element.rule.value.modified);

      // The original value should now differ from the rule values.
      assert.notDeepEqual(element._originalRuleValues, element.rule.value);
    });

    test('remove value', () => {
      element.editing = true;
      const removeStub = sinon.stub();
      element.addEventListener('added-rule-removed', removeStub);
      MockInteractions.tap(element.$.removeBtn);
      flush();
      assert.isTrue(removeStub.called);
    });
  });

  suite('already existing rule with labels', () => {
    setup(async () => {
      element.label = {values: [
        {value: -2, text: 'This shall not be merged'},
        {value: -1, text: 'I would prefer this is not merged as is'},
        {value: -0, text: 'No score'},
        {value: 1, text: 'Looks good to me, but someone else must approve'},
        {value: 2, text: 'Looks good to me, approved'},
      ]};
      element.group = 'Group Name';
      element.permission = 'label-Code-Review';
      element.rule = {
        id: '123',
        value: {
          action: 'ALLOW',
          force: false,
          max: 2,
          min: -2,
        },
      };
      element.section = 'refs/*';
      element._setupValues(element.rule);
      await flush();
      element.connectedCallback();
    });

    test('_ruleValues and _originalRuleValues are set correctly', () => {
      assert.deepEqual(element._originalRuleValues, element.rule.value);
    });

    test('values are set correctly', () => {
      assert.equal(element.$.action.bindValue, element.rule.value.action);
      assert.equal(
          element.root.querySelector('#labelMin').bindValue,
          element.rule.value.min);
      assert.equal(
          element.root.querySelector('#labelMax').bindValue,
          element.rule.value.max);
      assert.isFalse(element.$.force.classList.contains('force'));
    });

    test('modify value', () => {
      const removeStub = sinon.stub();
      element.addEventListener('added-rule-removed', removeStub);
      assert.isNotOk(element.rule.value.modified);
      element.root.querySelector('#labelMin').bindValue = 1;
      flush();
      assert.isTrue(element.rule.value.modified);
      assert.isFalse(removeStub.called);

      // The original value should now differ from the rule values.
      assert.notDeepEqual(element._originalRuleValues, element.rule.value);
    });
  });

  suite('new rule with labels', () => {
    setup(async () => {
      sinon.spy(element, '_setDefaultRuleValues');
      element.label = {values: [
        {value: -2, text: 'This shall not be merged'},
        {value: -1, text: 'I would prefer this is not merged as is'},
        {value: -0, text: 'No score'},
        {value: 1, text: 'Looks good to me, but someone else must approve'},
        {value: 2, text: 'Looks good to me, approved'},
      ]};
      element.group = 'Group Name';
      element.permission = 'label-Code-Review';
      element.rule = {
        id: '123',
      };
      element.section = 'refs/*';
      element._setupValues(element.rule);
      await flush();
      element.rule.value.added = true;
      await flush();
      element.connectedCallback();
    });

    test('_ruleValues and _originalRuleValues are set correctly', () => {
      // Since the element does not already have default values, they should
      // be set. The original values should be set to those too.
      assert.isNotOk(element.rule.value.modified);
      assert.isTrue(element._setDefaultRuleValues.called);

      const expectedRuleValue = {
        max: element.label.values[element.label.values.length - 1].value,
        min: element.label.values[0].value,
        action: 'ALLOW',
        added: true,
      };
      assert.deepEqual(element.rule.value, expectedRuleValue);
      test('values are set correctly', () => {
        assert.equal(
            element.$.action.bindValue,
            expectedRuleValue.action);
        assert.equal(
            element.root.querySelector('#labelMin').bindValue,
            expectedRuleValue.min);
        assert.equal(
            element.root.querySelector('#labelMax').bindValue,
            expectedRuleValue.max);
      });
    });

    test('modify value', () => {
      assert.isNotOk(element.rule.value.modified);
      element.root.querySelector('#labelMin').bindValue = 1;
      flush();
      assert.isTrue(element.rule.value.modified);

      // The original value should now differ from the rule values.
      assert.notDeepEqual(element._originalRuleValues, element.rule.value);
    });
  });

  suite('already existing push rule', () => {
    setup(async () => {
      element.group = 'Group Name';
      element.permission = 'push';
      element.rule = {
        id: '123',
        value: {
          action: 'ALLOW',
          force: true,
        },
      };
      element.section = 'refs/*';
      element._setupValues(element.rule);
      await flush();
      element.connectedCallback();
    });

    test('_ruleValues and _originalRuleValues are set correctly', () => {
      assert.deepEqual(element._originalRuleValues, element.rule.value);
    });

    test('values are set correctly', () => {
      assert.isTrue(element.$.force.classList.contains('force'));
      assert.equal(element.$.action.bindValue, element.rule.value.action);
      assert.equal(
          element.root.querySelector('#force').bindValue,
          element.rule.value.force);
      assert.isNotOk(element.root.querySelector('#labelMin'));
      assert.isNotOk(element.root.querySelector('#labelMax'));
    });

    test('modify value', () => {
      assert.isNotOk(element.rule.value.modified);
      element.$.action.bindValue = false;
      flush();
      assert.isTrue(element.rule.value.modified);

      // The original value should now differ from the rule values.
      assert.notDeepEqual(element._originalRuleValues, element.rule.value);
    });
  });

  suite('new push rule', () => {
    setup(async () => {
      element.group = 'Group Name';
      element.permission = 'push';
      element.rule = {
        id: '123',
      };
      element.section = 'refs/*';
      element._setupValues(element.rule);
      await flush();
      element.rule.value.added = true;
      await flush();
      element.connectedCallback();
    });

    test('_ruleValues and _originalRuleValues are set correctly', () => {
      // Since the element does not already have default values, they should
      // be set. The original values should be set to those too.
      assert.isNotOk(element.rule.value.modified);
      const expectedRuleValue = {
        action: 'ALLOW',
        force: false,
        added: true,
      };
      assert.deepEqual(element.rule.value, expectedRuleValue);
      test('values are set correctly', () => {
        assert.equal(element.$.action.bindValue, expectedRuleValue.action);
        assert.equal(element.$.force.bindValue, expectedRuleValue.action);
      });
    });

    test('modify value', () => {
      assert.isNotOk(element.rule.value.modified);
      element.$.force.bindValue = true;
      flush();
      assert.isTrue(element.rule.value.modified);

      // The original value should now differ from the rule values.
      assert.notDeepEqual(element._originalRuleValues, element.rule.value);
    });
  });

  suite('already existing edit rule', () => {
    setup(async () => {
      element.group = 'Group Name';
      element.permission = 'editTopicName';
      element.rule = {
        id: '123',
        value: {
          action: 'ALLOW',
          force: true,
        },
      };
      element.section = 'refs/*';
      element._setupValues(element.rule);
      await flush();
      element.connectedCallback();
    });

    test('_ruleValues and _originalRuleValues are set correctly', () => {
      assert.deepEqual(element._originalRuleValues, element.rule.value);
    });

    test('values are set correctly', () => {
      assert.isTrue(element.$.force.classList.contains('force'));
      assert.equal(element.$.action.bindValue, element.rule.value.action);
      assert.equal(
          element.root.querySelector('#force').bindValue,
          element.rule.value.force);
      assert.isNotOk(element.root.querySelector('#labelMin'));
      assert.isNotOk(element.root.querySelector('#labelMax'));
    });

    test('modify value', async () => {
      assert.isNotOk(element.rule.value.modified);
      element.$.action.bindValue = false;
      await flush();
      assert.isTrue(element.rule.value.modified);

      // The original value should now differ from the rule values.
      assert.notDeepEqual(element._originalRuleValues, element.rule.value);
    });
  });
});

