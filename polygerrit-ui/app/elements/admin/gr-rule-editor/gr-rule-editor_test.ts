/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-rule-editor';
import {GrRuleEditor} from './gr-rule-editor';
import {AccessPermissionId} from '../../../utils/access-util';
import {query, queryAll, queryAndAssert} from '../../../test/test-utils';
import {GrButton} from '../../shared/gr-button/gr-button';
import {assert, fixture, html} from '@open-wc/testing';
import {EditablePermissionRuleInfo} from '../gr-repo-access/gr-repo-access-interfaces';
import {PermissionAction} from '../../../constants/constants';
import {MdOutlinedSelect} from '@material/web/select/outlined-select';

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
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <div class="gr-form-styles" id="mainContainer">
            <div id="options">
              <md-outlined-select disabled="" id="action" value="">
                <md-select-option md-menu-item="" tabindex="0" value="ALLOW">
                  <div slot="headline">ALLOW</div>
                </md-select-option>
                <md-select-option md-menu-item="" tabindex="-1" value="DENY">
                  <div slot="headline">DENY</div>
                </md-select-option>
                <md-select-option md-menu-item="" tabindex="-1" value="BLOCK">
                  <div slot="headline">BLOCK</div>
                </md-select-option>
              </md-outlined-select>
              <a class="groupPath"> </a>
              <md-outlined-select disabled="" id="force" value="">
              </md-outlined-select>
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
        `
      );
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
      assert.dom.equal(
        queryAndAssert(element, '#options'),
        /* HTML */ `
          <div id="options">
            <md-outlined-select id="action" value="ALLOW">
              <md-select-option
                data-aria-selected="true"
                md-menu-item=""
                tabindex="0"
                value="ALLOW"
              >
                <div slot="headline">ALLOW</div>
              </md-select-option>
              <md-select-option md-menu-item="" tabindex="-1" value="DENY">
                <div slot="headline">DENY</div>
              </md-select-option>
              <md-select-option md-menu-item="" tabindex="-1" value="BLOCK">
                <div slot="headline">BLOCK</div>
              </md-select-option>
            </md-outlined-select>
            <a class="groupPath"> </a>
            <md-outlined-select class="force" id="force" value="">
              <md-select-option md-menu-item="" tabindex="0" value="false">
                <div slot="headline">Allow pushing (but not force pushing)</div>
              </md-select-option>
              <md-select-option md-menu-item="" tabindex="-1" value="true">
                <div slot="headline">Allow pushing with or without force</div>
              </md-select-option>
            </md-outlined-select>
          </div>
        `
      );
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
      await element.updateComplete;
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
        queryAndAssert<MdOutlinedSelect>(element, '#action').value,
        element.rule!.value!.action
      );
      assert.isNotOk(query<MdOutlinedSelect>(element, '#labelMin'));
      assert.isNotOk(query<MdOutlinedSelect>(element, '#labelMax'));
      assert.isFalse(
        queryAndAssert<MdOutlinedSelect>(element, '#force').classList.contains(
          'force'
        )
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
      const actionBindValue = queryAndAssert<MdOutlinedSelect>(
        element,
        '#action'
      );
      actionBindValue.value = PermissionAction.DENY;
      actionBindValue.dispatchEvent(
        new CustomEvent('change', {
          composed: true,
          bubbles: true,
        })
      );
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
        queryAndAssert<MdOutlinedSelect>(element, '#action').value,
        PermissionAction.ALLOW
      );
    });

    test('modify value', async () => {
      assert.isNotOk(element.rule!.value!.modified);
      const actionBindValue = queryAndAssert<MdOutlinedSelect>(
        element,
        '#action'
      );
      actionBindValue.value = PermissionAction.DENY;
      actionBindValue.dispatchEvent(
        new CustomEvent('change', {
          composed: true,
          bubbles: true,
        })
      );
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
      queryAndAssert<GrButton>(element, '#removeBtn').click();
      await element.updateComplete;
      assert.isTrue(
        queryAndAssert<HTMLDivElement>(
          element,
          '#deletedContainer'
        ).classList.contains('deleted')
      );
      assert.isTrue(element.deleted);
      assert.isTrue(element.rule.value!.deleted);

      queryAndAssert<GrButton>(element, '#undoRemoveBtn').click();
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
      queryAndAssert<GrButton>(element, '#removeBtn').click();
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
      await element.updateComplete;
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

      // values are set correctly
      assert.equal(
        queryAndAssert<MdOutlinedSelect>(element, '#action').value,
        expectedRuleValue.action
      );
      assert.equal(
        queryAndAssert<MdOutlinedSelect>(element, '#force').value,
        String(expectedRuleValue.force)
      );
    });

    test('modify value', async () => {
      assert.isNotOk(element.rule!.value!.modified);
      const forceBindValue = queryAndAssert<MdOutlinedSelect>(
        element,
        '#force'
      );
      forceBindValue.value = 'true';
      forceBindValue.dispatchEvent(
        new CustomEvent('change', {
          composed: true,
          bubbles: true,
        })
      );
      await element.updateComplete;
      assert.isTrue(element.rule!.value!.modified);

      // The original value should now differ from the rule values.
      assert.notDeepEqual(element.originalRuleValues, element.rule!.value);
    });

    test('remove value', async () => {
      element.editing = true;
      const removeStub = sinon.stub();
      element.addEventListener('added-rule-removed', removeStub);
      queryAndAssert<GrButton>(element, '#removeBtn').click();
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
      await element.updateComplete;
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
        queryAndAssert<MdOutlinedSelect>(element, '#action').value,
        element.rule!.value!.action
      );
      assert.equal(
        queryAndAssert<MdOutlinedSelect>(element, '#labelMin').value,
        String(element.rule!.value!.min)
      );
      assert.equal(
        queryAndAssert<MdOutlinedSelect>(element, '#labelMax').value,
        String(element.rule!.value!.max)
      );
      assert.isFalse(
        queryAndAssert<MdOutlinedSelect>(element, '#force').classList.contains(
          'force'
        )
      );
    });

    test('modify value', async () => {
      const removeStub = sinon.stub();
      element.addEventListener('added-rule-removed', removeStub);
      assert.isNotOk(element.rule!.value!.modified);
      const labelMinBindValue = queryAndAssert<MdOutlinedSelect>(
        element,
        '#labelMin'
      );
      labelMinBindValue.value = '1';
      labelMinBindValue.dispatchEvent(
        new CustomEvent('change', {
          composed: true,
          bubbles: true,
        })
      );
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
      await element.updateComplete;
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

      // values are set correctly
      assert.equal(
        queryAndAssert<MdOutlinedSelect>(element, '#action').value,
        expectedRuleValue.action
      );
      assert.equal(
        queryAndAssert<MdOutlinedSelect>(element, '#labelMin').value,
        String(expectedRuleValue.min)
      );
      assert.equal(
        queryAndAssert<MdOutlinedSelect>(element, '#labelMax').value,
        String(expectedRuleValue.max)
      );
    });

    test('modify value', async () => {
      assert.isNotOk(element.rule!.value!.modified);
      const labelMinBindValue = queryAndAssert<MdOutlinedSelect>(
        element,
        '#labelMin'
      );
      labelMinBindValue.value = '1';
      labelMinBindValue.dispatchEvent(
        new CustomEvent('change', {
          composed: true,
          bubbles: true,
        })
      );
      await element.updateComplete;
      assert.isTrue(element.rule!.value!.modified);

      // The original value should now differ from the rule values.
      assert.notDeepEqual(element.originalRuleValues, element.rule!.value);
    });
  });

  suite('already existing push rule', () => {
    setup(async () => {
      element.groupName = 'Group Name';
      element.permission = AccessPermissionId.PUSH;
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
        queryAndAssert<MdOutlinedSelect>(element, '#force').classList.contains(
          'force'
        )
      );
      assert.equal(
        queryAndAssert<MdOutlinedSelect>(element, '#action').value,
        element.rule!.value!.action
      );
      assert.equal(
        queryAndAssert(element, '#force').getAttribute('value'),
        String(element.rule!.value!.force)
      );
      assert.isNotOk(query<MdOutlinedSelect>(element, '#labelMin'));
      assert.isNotOk(query<MdOutlinedSelect>(element, '#labelMax'));
    });

    test('modify value', async () => {
      assert.isNotOk(element.rule!.value!.modified);
      const actionBindValue = queryAndAssert<MdOutlinedSelect>(
        element,
        '#action'
      );
      actionBindValue.value = PermissionAction.DENY;
      actionBindValue.dispatchEvent(
        new CustomEvent('change', {
          composed: true,
          bubbles: true,
        })
      );
      await element.updateComplete;
      assert.isTrue(element.rule!.value!.modified);

      // The original value should now differ from the rule values.
      assert.notDeepEqual(element.originalRuleValues, element.rule!.value);
    });
  });

  suite('new push rule', async () => {
    setup(async () => {
      element.groupName = 'Group Name';
      element.permission = AccessPermissionId.PUSH;
      await element.updateComplete;
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
      // values are set correctly
      assert.equal(
        queryAndAssert<MdOutlinedSelect>(element, '#action').value,
        expectedRuleValue.action
      );
      assert.equal(
        queryAndAssert(element, '#force').getAttribute('value'),
        String(expectedRuleValue.force)
      );
    });

    test('modify value', async () => {
      assert.isNotOk(element.rule!.value!.modified);
      const forceBindValue = queryAndAssert<MdOutlinedSelect>(
        element,
        '#force'
      );
      forceBindValue.value = 'true';
      forceBindValue.dispatchEvent(
        new CustomEvent('change', {
          composed: true,
          bubbles: true,
        })
      );
      await element.updateComplete;
      assert.isTrue(element.rule!.value!.modified);

      // The original value should now differ from the rule values.
      assert.notDeepEqual(element.originalRuleValues, element.rule!.value);
    });
  });

  suite('already existing edit rule', () => {
    setup(async () => {
      element.groupName = 'Group Name';
      element.permission = AccessPermissionId.EDIT_TOPIC_NAME;
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
        queryAndAssert<MdOutlinedSelect>(element, '#force').classList.contains(
          'force'
        )
      );
      assert.equal(
        queryAndAssert<MdOutlinedSelect>(element, '#action').value,
        element.rule!.value!.action
      );
      assert.equal(
        queryAndAssert(element, '#force').getAttribute('value'),
        String(element.rule!.value!.force)
      );
      assert.isNotOk(query<MdOutlinedSelect>(element, '#labelMin'));
      assert.isNotOk(query<MdOutlinedSelect>(element, '#labelMax'));
    });

    test('modify value', async () => {
      assert.isNotOk(element.rule!.value!.modified);
      const actionBindValue = queryAndAssert<MdOutlinedSelect>(
        element,
        '#action'
      );
      actionBindValue.value = PermissionAction.DENY;
      actionBindValue.dispatchEvent(
        new CustomEvent('change', {
          composed: true,
          bubbles: true,
        })
      );
      await element.updateComplete;
      assert.isTrue(element.rule!.value!.modified);

      // The original value should now differ from the rule values.
      assert.notDeepEqual(element.originalRuleValues, element.rule!.value);
    });
  });
});
