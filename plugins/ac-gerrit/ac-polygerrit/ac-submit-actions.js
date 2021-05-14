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

import { RelatedChanges } from './findGerritChanges.js';

const sanityExcluded = [
  'Orgad Shaneh',
];

function isSubModule(p) {
  if (p.includes('u-boot'))
    return false;
  return p.match(/^(TP\/(OpenSource|TrunkPackLib)|IPP\/(apps\/emsc|RX50|Lib\/Lib|C450\/C450|445|OpenSource))/);
}

function statusSanity(label) {
  if (!label)
    return true;
  for (const l of label.all)
    if (l.value == 1)
      return true;
  return false;
}

function checkSanity(change) {
  if (sanityExcluded.includes(change.owner.name))
    return null;
  const { branch } = change;
  if (!statusSanity(change.labels.DRT) && branch.match(/7\.[24]/))
    return 'DRT +1 is missing';

  if (!statusSanity(change.labels['Automation-Test']) &&
    (branch.match(/^(3\.[24]|release\/3\.2|master)/))) {
    return 'Automation-Test +1 is missing';
  }

  return null;
}

async function checkSanityInSuperModule(plugin, change, changeNum) {
  if (sanityExcluded.includes(change.owner.name))
    return null;

  const { branch } = change;
  if (change.labels.DRT && !branch.match(/^(7\.[24]|feature\/M3100)/))
    return null;

  if (change.labels['Automation-Test'] && !branch.match(/^(3\.[24]|release\/3\.2|master)/))
    return null;

  let sanityLabelExist = null;

  if (change.branch.startsWith('feature/'))
    return null;

  const labels = await plugin.restApi().get(`/changes/${changeNum}/reviewers`);
  for (const section of labels) {
    for (const label of ['DRT', 'Automation-Test']) {
      if (section.approvals[label]) {
        sanityLabelExist = label;
        if (+section.approvals[sanityLabelExist] === 1)
          return null;
      }
    }
  }

  return sanityLabelExist;
}

function disableSubmit(changeActions, message) {
  const submitButton = changeActions._el.root.host.querySelector('[data-label^="Submit"]');
  submitButton.setAttribute('disabled', true);
  submitButton.setAttribute('title', message);
}

export async function updateSubmit(plugin, change) {
  const sanityMessage = checkSanity(change);
  const changeActions = plugin.ca;
  delete plugin.otherChange;
  if (sanityMessage) {
    disableSubmit(changeActions, sanityMessage);
    return;
  }
  if (change.revisions[change.current_revision].commit.parents.length > 1)
    return;
  const currentIsSubModule = isSubModule(change.project);
  const SuperModule = ['TP/GWApp', 'IPP/SFB'];
  if (SuperModule.includes(change.project) || currentIsSubModule) {
    const output = { plugin };
    const related = new RelatedChanges;
    await related.find(output, change, 'Submit', '', '');
    for (const submodule of output.SubModules) {
      const foundProject = submodule.name;
      const changeNum = submodule.branches[0].value;
      if (!currentIsSubModule) {
        if (isSubModule(foundProject)) {
          disableSubmit(changeActions, `Submit is allowed from SubModule only: ${foundProject} change: ${changeNum}`);
          break;
        }
        continue;
      }
      if (!SuperModule.includes(foundProject))
        continue;
      if (submodule.branches.length > 2) {
        disableSubmit(changeActions, `More than one change was found in ${foundProject} with this Jira issue`);
        break;
      }
      const sanityLabelFound = await checkSanityInSuperModule(plugin, change, changeNum);
      if (sanityLabelFound) {
        disableSubmit(changeActions, `Label ${sanityLabelFound} on change: ${changeNum} in ${foundProject} needs vote +1`);
        break;
      }
      const submitButton = changeActions._el.root.host.querySelector('[data-label^="Submit"]');
      submitButton.setAttribute('title', `Submit and update reference in ${foundProject}, including submit ` +
        `of change: ${changeNum} in ${foundProject}`);
      plugin.otherChange = changeNum;
    }
  }
}
