/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-rule-editor';
import {GrRuleEditor} from './gr-rule-editor';
import {AccessPermissionId} from '../../../utils/access-util';
import {query, queryAll, queryAndAssert} from '../../../test/test-utils';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrSelect} from '../../shared/gr-select/gr-select';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {fixture, html} from '@open-wc/testing-helpers';
import {EditablePermissionRuleInfo} from '../gr-repo-access/gr-repo-access-interfaces';
import {PermissionAction} from '../../../constants/constants';

suite('gr-rule-editor tests', () => {
  let element: GrRuleEditor;

  setup(async () => {
    element = await fixture<GrRuleEditor>(html`
      <gr-rule-editor></gr-rule-editor>
    `);
    await element.updateComplete;
  });

  suite('dom tests', () => {
    test('default', () => {
      expect(element).shadowDom.to.equal(/* HTML */ `
        <div class="gr-form-styles" id="mainContainer">
          <div id="options">
            <gr-select id="action">
              <select disabled="">
                <option value="ALLOW">ALLOW</option>
                <option value="DENY">DENY</option>
                <option value="BLOCK">BLOCK</option>
              </select>
            </gr-select>
            <a class="groupPath"> </a>
            <gr-select id="force">
              <select disabled=""></select>
            </gr-select>
          </div>
          <gr-button
            aria-disabled="false"
            id="removeBtn"
            link=""
            role="button"
            tabindex="0"
          >
            Remove
          </gr-button>
        </div>
        <div class="gr-form-styles" id="deletedContainer">
          was deleted
          <gr-button
            aria-disabled="false"
            id="undoRemoveBtn"
            link=""
            role="button"
            tabindex="0"
          >
            Undo
          </gr-button>
        </div>
      `);
    });

    test('push options', async () => {
      const rule: {value: EditablePermissionRuleInfo} = {
        value: {
          action: PermissionAction.ALLOW,
        },
      };
      element = await fixture<GrRuleEditor>(html`
        <gr-rule-editor
          .editing=${true}
          .rule=${rule}
          .permission=${AccessPermissionId.PUSH}
        ></gr-rule-editor>
      `);
      expect(queryAndAssert(element, '#options')).dom.to.equal(/* HTML */ `
        <div id="options">
          <gr-select id="action">
            <select>
              <option value="ALLOW">ALLOW</option>
              <option value="DENY">DENY</option>
              <option value="BLOCK">BLOCK</option>
            </select>
          </gr-select>
          <a class="groupPath"> </a>
          <gr-select class="force" id="force">
            <select>
              <option value="false">
                Allow pushing (but not force pushing)
              </option>
              <option value="true">Allow pushing with or without force</option>
            </select>
          </gr-select>
        </div>
      `);
    });
  });

  suite('unit tests', () => {
    test('computeForce and computeForceOptions', () => {
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
      element.permission = 'push' as AccessPermissionId;
      let action = PermissionAction.ALLOW;
      assert.isTrue(element.computeForce(action));
      assert.deepEqual(
        element.computeForceOptions(action),
        ForcePushOptions.ALLOW
      );

      action = PermissionAction.BLOCK;
      assert.isTrue(element.computeForce(action));
      assert.deepEqual(
        element.computeForceOptions(action),
        ForcePushOptions.BLOCK
      );

      action = PermissionAction.DENY;
      assert.isFalse(element.computeForce(action));
      assert.equal(element.computeForceOptions(action).length, 0);

      element.permission = 'editTopicName' as AccessPermissionId;
      assert.isTrue(element.computeForce());
      assert.deepEqual(element.computeForceOptions(), FORCE_EDIT_OPTIONS);
      element.permission = 'submit' as AccessPermissionId;
      assert.isFalse(element.computeForce());
      assert.deepEqual(element.computeForceOptions(), []);
    });

    test('computeSectionClass', () => {
      element.deleted = true;
      element.editing = false;
      assert.equal(element.computeSectionClass(), 'deleted');

      element.deleted = false;
      assert.equal(element.computeSectionClass(), '');

      element.editing = true;
      assert.equal(element.computeSectionClass(), 'editing');

      element.deleted = true;
      assert.equal(element.computeSectionClass(), 'editing deleted');
    });

    test('getDefaultRuleValues', () => {
      element.permission = 'priority' as AccessPermissionId;
      assert.deepEqual(element.getDefaultRuleValues(), {
        action: PermissionAction.BATCH,
      });
      element.permission = 'label-Code-Review' as AccessPermissionId;
      element.label = {
        values: [
          {value: -2, text: 'This shall not be submitted'},
          {value: -1, text: 'I would prefer this is not submitted as is'},
          {value: -0, text: 'No score'},
          {value: 1, text: 'Looks good to me, but someone else must approve'},
          {value: 2, text: 'Looks good to me, approved'},
        ],
      };
      assert.deepEqual(element.getDefaultRuleValues(), {
        action: PermissionAction.ALLOW,
        max: 2,
        min: -2,
      });
      element.permission = 'push' as AccessPermissionId;
      element.label = undefined;
      assert.deepEqual(element.getDefaultRuleValues(), {
        action: PermissionAction.ALLOW,
        force: false,
      });
      element.permission = 'submit' as AccessPermissionId;
      assert.deepEqual(element.getDefaultRuleValues(), {
        action: PermissionAction.ALLOW,
      });
    });

    test('setDefaultRuleValues', async () => {
      element.rule = {};
      const defaultValue = {action: PermissionAction.ALLOW};
      const getDefaultRuleValuesStub = sinon
        .stub(element, 'getDefaultRuleValues')
        .returns(defaultValue);
      element.setDefaultRuleValues();
      assert.isTrue(getDefaultRuleValuesStub.called);
      assert.equal(element.rule.value, defaultValue);
    });

    test('computeOptions', () => {
      const PRIORITY_OPTIONS = ['BATCH', 'INTERACTIVE'];
      const DROPDOWN_OPTIONS = [
        PermissionAction.ALLOW,
        PermissionAction.DENY,
        PermissionAction.BLOCK,
      ];
      element.permission = 'priority' as AccessPermissionId;
      assert.deepEqual(element.computeOptions(), PRIORITY_OPTIONS);
      element.permission = 'submit' as AccessPermissionId;
      assert.deepEqual(element.computeOptions(), DROPDOWN_OPTIONS);
    });

    test('handleValueChange', () => {
      const modifiedHandler = sinon.stub();
      element.rule = {
        value: {action: PermissionAction.ALLOW},
      };
      element.addEventListener('access-modified', modifiedHandler);
      element.handleValueChange();
      assert.isNotOk(element.rule.value!.modified);
      element.originalRuleValues = {action: PermissionAction.ALLOW};
      element.handleValueChange();
      assert.isTrue(element.rule.value!.modified);
      assert.isTrue(modifiedHandler.called);
    });

    test('handleAccessSaved', () => {
      const originalValue = {action: PermissionAction.DENY};
      const newValue = {action: PermissionAction.ALLOW};
      element.originalRuleValues = originalValue;
      element.rule = {value: newValue};
      element.handleAccessSaved();
      assert.deepEqual(element.originalRuleValues, newValue);
    });

    test('setOriginalRuleValues', () => {
      element.rule = {
        value: {
          action: PermissionAction.ALLOW,
          force: false,
        },
      };
      element.setOriginalRuleValues();
      assert.deepEqual(element.originalRuleValues, element.rule.value);
    });
  });

  suite('already existing generic rule', () => {
    setup(async () => {
      element.groupName = 'Group Name';
      element.permission = 'submit' as AccessPermissionId;
      element.rule = {
        value: {
          action: PermissionAction.ALLOW,
          force: false,
        },
      };
      element.section = 'refs/*';
      element.setupValues();
      element.setOriginalRuleValues();
      await element.updateComplete;
    });

    test('_ruleValues and originalRuleValues are set correctly', () => {
      assert.deepEqual(element.originalRuleValues, element.rule!.value);
    });

    test('values are set correctly', () => {
      assert.equal(
        queryAndAssert<GrSelect>(element, '#action').bindValue,
        element.rule!.value!.action
      );
      assert.isNotOk(query<GrSelect>(element, '#labelMin'));
      assert.isNotOk(query<GrSelect>(element, '#labelMax'));
      assert.isFalse(
        queryAndAssert<GrSelect>(element, '#force').classList.contains('force')
      );
    });

    test('modify and cancel restores original values', async () => {
      element.rule = {value: {action: PermissionAction.ALLOW}};
      element.setOriginalRuleValues();
      element.editing = true;
      await element.updateComplete;
      assert.notEqual(
        getComputedStyle(queryAndAssert<GrButton>(element, '#removeBtn'))
          .display,
        'none'
      );
      assert.isNotOk(element.rule.value!.modified);
      const actionBindValue = queryAndAssert<GrSelect>(element, '#action');
      actionBindValue.bindValue = PermissionAction.DENY;
      await element.updateComplete;
      assert.isTrue(element.rule.value!.modified);
      element.editing = false;
      await element.updateComplete;
      assert.equal(
        getComputedStyle(queryAndAssert<GrButton>(element, '#removeBtn'))
          .display,
        'none'
      );
      assert.deepEqual(element.originalRuleValues, element.rule.value);
      assert.isNotOk(element.rule.value!.modified);
      assert.equal(element.rule?.value?.action, PermissionAction.ALLOW);
      assert.equal(
        queryAndAssert<GrSelect>(element, '#action').bindValue,
        PermissionAction.ALLOW
      );
    });

    test('modify value', async () => {
      assert.isNotOk(element.rule!.value!.modified);
      const actionBindValue = queryAndAssert<GrSelect>(element, '#action');
      actionBindValue.bindValue = PermissionAction.DENY;
      await element.updateComplete;
      assert.isTrue(element.rule!.value!.modified);

      // The original value should now differ from the rule values.
      assert.notDeepEqual(element.originalRuleValues, element.rule!.value);
    });

    test('all selects are disabled when not in edit mode', async () => {
      const selects = queryAll<HTMLSelectElement>(element, 'select');
      for (const select of selects) {
        assert.isTrue(select.disabled);
      }
      element.editing = true;
      await element.updateComplete;
      for (const select of selects) {
        assert.isFalse(select.disabled);
      }
    });

    test('remove rule and undo remove', async () => {
      element.editing = true;
      element.rule = {value: {action: PermissionAction.ALLOW}};
      await element.updateComplete;
      assert.isFalse(
        queryAndAssert<HTMLDivElement>(
          element,
          '#deletedContainer'
        ).classList.contains('deleted')
      );
      MockInteractions.tap(queryAndAssert<GrButton>(element, '#removeBtn'));
      await element.updateComplete;
      assert.isTrue(
        queryAndAssert<HTMLDivElement>(
          element,
          '#deletedContainer'
        ).classList.contains('deleted')
      );
      assert.isTrue(element.deleted);
      assert.isTrue(element.rule.value!.deleted);

      MockInteractions.tap(queryAndAssert<GrButton>(element, '#undoRemoveBtn'));
      await element.updateComplete;
      assert.isFalse(element.deleted);
      assert.isNotOk(element.rule.value!.deleted);
    });

    test('remove rule and cancel', async () => {
      element.editing = true;
      await element.updateComplete;
      assert.notEqual(
        getComputedStyle(queryAndAssert<GrButton>(element, '#removeBtn'))
          .display,
        'none'
      );
      assert.equal(
        getComputedStyle(
          queryAndAssert<HTMLDivElement>(element, '#deletedContainer')
        ).display,
        'none'
      );

      element.rule = {value: {action: PermissionAction.ALLOW}};
      await element.updateComplete;
      MockInteractions.tap(queryAndAssert<GrButton>(element, '#removeBtn'));
      await element.updateComplete;
      assert.notEqual(
        getComputedStyle(queryAndAssert<GrButton>(element, '#removeBtn'))
          .display,
        'none'
      );
      assert.notEqual(
        getComputedStyle(
          queryAndAssert<HTMLDivElement>(element, '#deletedContainer')
        ).display,
        'none'
      );
      assert.isTrue(element.deleted);
      assert.isTrue(element.rule.value!.deleted);

      element.editing = false;
      await element.updateComplete;
      assert.isFalse(element.deleted);
      assert.isNotOk(element.rule.value!.deleted);
      assert.isNotOk(element.rule.value!.modified);

      assert.deepEqual(element.originalRuleValues, element.rule.value);
      assert.equal(
        getComputedStyle(queryAndAssert<GrButton>(element, '#removeBtn'))
          .display,
        'none'
      );
      assert.equal(
        getComputedStyle(
          queryAndAssert<HTMLDivElement>(element, '#deletedContainer')
        ).display,
        'none'
      );
    });

    test('computeGroupPath', () => {
      const group = '123';
      assert.equal(element.computeGroupPath(group), '/admin/groups/123');
    });
  });

  suite('new edit rule', () => {
    setup(async () => {
      element.groupName = 'Group Name';
      element.permission = 'editTopicName' as AccessPermissionId;
      element.rule = {};
      element.section = 'refs/*';
      element.setupValues();
      await element.updateComplete;
      element.rule.value!.added = true;
      await element.updateComplete;
      element.connectedCallback();
    });

    test('_ruleValues and originalRuleValues are set correctly', () => {
      // Since the element does not already have default values, they should
      // be set. The original values should be set to those too.
      assert.isNotOk(element.rule!.value!.modified);
      const expectedRuleValue = {
        action: PermissionAction.ALLOW,
        force: false,
        added: true,
      };
      assert.deepEqual(element.rule!.value, expectedRuleValue);
      test('values are set correctly', () => {
        assert.equal(
          queryAndAssert<GrSelect>(element, '#action').bindValue,
          expectedRuleValue.action
        );
        assert.equal(
          queryAndAssert<GrSelect>(element, '#force').bindValue,
          expectedRuleValue.action
        );
      });
    });

    test('modify value', async () => {
      assert.isNotOk(element.rule!.value!.modified);
      const forceBindValue = queryAndAssert<GrSelect>(element, '#force');
      forceBindValue.bindValue = 'true';
      await element.updateComplete;
      assert.isTrue(element.rule!.value!.modified);

      // The original value should now differ from the rule values.
      assert.notDeepEqual(element.originalRuleValues, element.rule!.value);
    });

    test('remove value', async () => {
      element.editing = true;
      const removeStub = sinon.stub();
      element.addEventListener('added-rule-removed', removeStub);
      MockInteractions.tap(queryAndAssert<GrButton>(element, '#removeBtn'));
      await element.updateComplete;
      assert.isTrue(removeStub.called);
    });
  });

  suite('already existing rule with labels', () => {
    setup(async () => {
      element.label = {
        values: [
          {value: -2, text: 'This shall not be submitted'},
          {value: -1, text: 'I would prefer this is not submitted as is'},
          {value: -0, text: 'No score'},
          {value: 1, text: 'Looks good to me, but someone else must approve'},
          {value: 2, text: 'Looks good to me, approved'},
        ],
      };
      element.groupName = 'Group Name';
      element.permission = 'label-Code-Review' as AccessPermissionId;
      element.rule = {
        value: {
          action: PermissionAction.ALLOW,
          force: false,
          max: 2,
          min: -2,
        },
      };
      element.section = 'refs/*';
      element.setupValues();
      await element.updateComplete;
      element.connectedCallback();
    });

    test('_ruleValues and originalRuleValues are set correctly', () => {
      assert.deepEqual(element.originalRuleValues, element.rule!.value);
    });

    test('values are set correctly', () => {
      assert.equal(
        queryAndAssert<GrSelect>(element, '#action').bindValue,
        element.rule!.value!.action
      );
      assert.equal(
        queryAndAssert<GrSelect>(element, '#labelMin').bindValue,
        element.rule!.value!.min
      );
      assert.equal(
        queryAndAssert<GrSelect>(element, '#labelMax').bindValue,
        element.rule!.value!.max
      );
      assert.isFalse(
        queryAndAssert<GrSelect>(element, '#force').classList.contains('force')
      );
    });

    test('modify value', async () => {
      const removeStub = sinon.stub();
      element.addEventListener('added-rule-removed', removeStub);
      assert.isNotOk(element.rule!.value!.modified);
      const labelMinBindValue = queryAndAssert<GrSelect>(element, '#labelMin');
      labelMinBindValue.bindValue = 1;
      await element.updateComplete;
      assert.isTrue(element.rule!.value!.modified);
      assert.isFalse(removeStub.called);

      // The original value should now differ from the rule values.
      assert.notDeepEqual(element.originalRuleValues, element.rule!.value);
    });
  });

  suite('new rule with labels', () => {
    let setDefaultRuleValuesSpy: sinon.SinonSpy;

    setup(async () => {
      setDefaultRuleValuesSpy = sinon.spy(element, 'setDefaultRuleValues');
      element.label = {
        values: [
          {value: -2, text: 'This shall not be submitted'},
          {value: -1, text: 'I would prefer this is not submitted as is'},
          {value: -0, text: 'No score'},
          {value: 1, text: 'Looks good to me, but someone else must approve'},
          {value: 2, text: 'Looks good to me, approved'},
        ],
      };
      element.groupName = 'Group Name';
      element.permission = 'label-Code-Review' as AccessPermissionId;
      element.rule = {};
      element.section = 'refs/*';
      element.setupValues();
      await element.updateComplete;
      element.rule.value!.added = true;
      await element.updateComplete;
      element.connectedCallback();
    });

    test('_ruleValues and originalRuleValues are set correctly', () => {
      // Since the element does not already have default values, they should
      // be set. The original values should be set to those too.
      assert.isNotOk(element.rule!.value!.modified);
      assert.isTrue(setDefaultRuleValuesSpy.called);

      const expectedRuleValue = {
        max: element.label!.values[element.label!.values.length - 1].value,
        min: element.label!.values[0].value,
        action: PermissionAction.ALLOW,
        added: true,
      };
      assert.deepEqual(element.rule!.value, expectedRuleValue);
      test('values are set correctly', () => {
        assert.equal(
          queryAndAssert<GrSelect>(element, '#action').bindValue,
          expectedRuleValue.action
        );
        assert.equal(
          queryAndAssert<GrSelect>(element, '#labelMin').bindValue,
          expectedRuleValue.min
        );
        assert.equal(
          queryAndAssert<GrSelect>(element, '#labelMax').bindValue,
          expectedRuleValue.max
        );
      });
    });

    test('modify value', async () => {
      assert.isNotOk(element.rule!.value!.modified);
      const labelMinBindValue = queryAndAssert<GrSelect>(element, '#labelMin');
      labelMinBindValue.bindValue = 1;
      await element.updateComplete;
      assert.isTrue(element.rule!.value!.modified);

      // The original value should now differ from the rule values.
      assert.notDeepEqual(element.originalRuleValues, element.rule!.value);
    });
  });

  suite('already existing push rule', () => {
    setup(async () => {
      element.groupName = 'Group Name';
      element.permission = 'push' as AccessPermissionId;
      element.rule = {
        value: {
          action: PermissionAction.ALLOW,
          force: true,
        },
      };
      element.section = 'refs/*';
      element.setupValues();
      await element.updateComplete;
      element.connectedCallback();
    });

    test('_ruleValues and originalRuleValues are set correctly', () => {
      assert.deepEqual(element.originalRuleValues, element.rule!.value);
    });

    test('values are set correctly', () => {
      assert.isTrue(
        queryAndAssert<GrSelect>(element, '#force').classList.contains('force')
      );
      assert.equal(
        queryAndAssert<GrSelect>(element, '#action').bindValue,
        element.rule!.value!.action
      );
      assert.equal(
        queryAndAssert<GrSelect>(element, '#force').bindValue,
        element.rule!.value!.force
      );
      assert.isNotOk(query<GrSelect>(element, '#labelMin'));
      assert.isNotOk(query<GrSelect>(element, '#labelMax'));
    });

    test('modify value', async () => {
      assert.isNotOk(element.rule!.value!.modified);
      const actionBindValue = queryAndAssert<GrSelect>(element, '#action');
      actionBindValue.bindValue = false;
      await element.updateComplete;
      assert.isTrue(element.rule!.value!.modified);

      // The original value should now differ from the rule values.
      assert.notDeepEqual(element.originalRuleValues, element.rule!.value);
    });
  });

  suite('new push rule', async () => {
    setup(async () => {
      element.groupName = 'Group Name';
      element.permission = 'push' as AccessPermissionId;
      element.rule = {};
      element.section = 'refs/*';
      element.setupValues();
      await element.updateComplete;
      element.rule.value!.added = true;
      await element.updateComplete;
      element.connectedCallback();
    });

    test('_ruleValues and originalRuleValues are set correctly', () => {
      // Since the element does not already have default values, they should
      // be set. The original values should be set to those too.
      assert.isNotOk(element.rule!.value!.modified);
      const expectedRuleValue = {
        action: PermissionAction.ALLOW,
        force: false,
        added: true,
      };
      assert.deepEqual(element.rule!.value, expectedRuleValue);
      test('values are set correctly', () => {
        assert.equal(
          queryAndAssert<GrSelect>(element, '#action').bindValue,
          expectedRuleValue.action
        );
        assert.equal(
          queryAndAssert<GrSelect>(element, '#force').bindValue,
          expectedRuleValue.action
        );
      });
    });

    test('modify value', async () => {
      assert.isNotOk(element.rule!.value!.modified);
      const forceBindValue = queryAndAssert<GrSelect>(element, '#force');
      forceBindValue.bindValue = true;
      await element.updateComplete;
      assert.isTrue(element.rule!.value!.modified);

      // The original value should now differ from the rule values.
      assert.notDeepEqual(element.originalRuleValues, element.rule!.value);
    });
  });

  suite('already existing edit rule', () => {
    setup(async () => {
      element.groupName = 'Group Name';
      element.permission = 'editTopicName' as AccessPermissionId;
      element.rule = {
        value: {
          action: PermissionAction.ALLOW,
          force: true,
        },
      };
      element.section = 'refs/*';
      element.setupValues();
      await element.updateComplete;
      element.connectedCallback();
    });

    test('_ruleValues and originalRuleValues are set correctly', () => {
      assert.deepEqual(element.originalRuleValues, element.rule!.value);
    });

    test('values are set correctly', () => {
      assert.isTrue(
        queryAndAssert<GrSelect>(element, '#force').classList.contains('force')
      );
      assert.equal(
        queryAndAssert<GrSelect>(element, '#action').bindValue,
        element.rule!.value!.action
      );
      assert.equal(
        queryAndAssert<GrSelect>(element, '#force').bindValue,
        element.rule!.value!.force
      );
      assert.isNotOk(query<GrSelect>(element, '#labelMin'));
      assert.isNotOk(query<GrSelect>(element, '#labelMax'));
    });

    test('modify value', async () => {
      assert.isNotOk(element.rule!.value!.modified);
      const actionBindValue = queryAndAssert<GrSelect>(element, '#action');
      actionBindValue.bindValue = false;
      await element.updateComplete;
      assert.isTrue(element.rule!.value!.modified);

      // The original value should now differ from the rule values.
      assert.notDeepEqual(element.originalRuleValues, element.rule!.value);
    });
  });
});
