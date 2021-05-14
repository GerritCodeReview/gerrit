/**
 * @license
 * Copyright (C) 2021 AudioCodes Ltd.
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

import './ac-submit-actions.js';
import './ac-build.js';
import './ac-confirm-submit.js';
import './ac-drt.js';
import './ac-submit-actions.js';
import './ac-submit-with-super.js';

Gerrit.install(plugin => {
  plugin.registerCustomComponent('confirm-submit-change', 'submit-for-another');

  const BUTTONS = {
    'ac-gerrit~build': {
      action_name: 'Build',
      popup: 'ac-build-dialog',
    },
    'ac-gerrit~sanity': {
      action_name: 'sanity',
      popup: 'ac-drt-dialog',
    },
    'ac-gerrit~full-sanity': {
      action_name: 'Sanity',
      popup: 'ac-drt-dialog',
    },
    'ac-gerrit~submit-with-super': {
      action_name: 'submit',
      replaces: 'submit',
      popup: 'ac-submit-with-super-dialog',
      priority: 0,
    },
  };

  plugin.custom_popup = null;
  plugin.custom_popup_promise = null;
  plugin.buttons = null;
  let revision = null;

  const isMerge = change => {
    try {
      return change.revisions[change.current_revision].commit.parents.length > 1;
    } catch (e) {
      return false;
    }
  };

  const updateButtons = (actions, changeInfo) => {
    plugin.ca = plugin.changeActions();
    if (changeInfo.submittable)
      setTimeout(() => updateSubmit(plugin, changeInfo), 100);

    // Remove any existing buttons
    if (plugin.buttons) {
      for (const key in BUTTONS) {
        if (BUTTONS.hasOwnProperty(key)) {
          if (typeof plugin.buttons[key] !== 'undefined' && plugin.buttons[key] !== null) {
            plugin.ca.removeTapListener(plugin.buttons[key], param => { });
            plugin.ca.remove(plugin.buttons[key]);
            plugin.buttons[key] = null;
          }
        }
      }
    } else plugin.buttons = [];

    // Add buttons based on server response
    for (const key in BUTTONS) {
      if (BUTTONS.hasOwnProperty(key)) {
        const button = BUTTONS[key];
        const action = actions[key];
        button.action = action;
        if (button.replaces && !isMerge(changeInfo))
          plugin.ca.setActionHidden('revision', button.replaces, !!action);
        if (!action)
          continue;
        plugin.ca.setActionHidden(action.__type, action.__key, true);
        button.enabled = action.enabled;
        button.header = action.label;
        plugin.buttons[key] = plugin.ca.add('revision', action.label);
        if (button.priority !== undefined)
          plugin.ca.setActionPriority(action.__type, plugin.buttons[key], button.priority);
        plugin.ca.setTitle(plugin.buttons[key], action.title);
        plugin.ca.addTapListener(plugin.buttons[key], buttonEventCallback);
      }
    }

    function buttonEventCallback(event) {
      const button_key = event.type.substring(0, event.type.indexOf('-tap'));
      let button;
      for (const k in plugin.buttons) {
        if (plugin.buttons[k] === button_key) {
          button = BUTTONS[k];
          break;
        }
      }
      if (!button)
        return;
      plugin.popup(button.popup).then(param => {
        plugin.custom_popup_promise = param;
        const popup = plugin.custom_popup;
        popup.parentElement.style.top = '150px';
        popup.set('header', button.header);
        popup.set('subject', changeInfo.subject);
        popup.set('action_name', button.action_name);
        popup.set('change', changeInfo);
        popup.set('action', button.action);
        if (popup.init)
          popup.init(changeInfo, revision, button);
      }).catch(param => console.error('unexpected error:', param.message));
    }
  };

  function onSubmitOpenSource(c, r) {
    if (!c.project.includes('TP/OpenSource'))
      return true;
    if (c.project.includes('u-boot'))
      return true;
    alert('You must use the \'Submit and update TPApp\' button ... Aborted');
    return false;
  }

  function onSubmitCheckDRT(c, r) {
    if (c.owner.name === 'Orgad Shaneh')
      return true;
    if ((c.branch.includes('7.4') || c.branch.includes('7.2')) && c.labels.DRT && !c.labels.DRT.approved) {
      alert('This branch need DRT +1 ... Aborted');
      return false;
    }

    const autoTest = c.labels['Automation-Test'];
    if (autoTest && (c.match(/^(3\.[24]\|release\/3\.2|master)/) && !autoTest.approved)) {
      alert('This branch need Automation-Test +1 ... Aborted');
      return false;
    }
    return true;
  }

  plugin.on('submitchange', onSubmitOpenSource);
  plugin.on('submitchange', onSubmitCheckDRT);

  plugin.on('showchange', (_, revisionInfo) => {
    revision = revisionInfo;
  });
  plugin.on('show-revision-actions', updateButtons);
});
