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

export class RelatedChanges {
  constructor() {
    this.superModules = ['TP/GWApp', 'IPP/SFB', 'TP/Tools/VoiceAIConnector', 'GWApp'];
  }

  async find(widget, c, labelName, refspec, email) {
    widget.header = 'Search other changes';
    widget.hiddenImage = false;

    widget.SMchanges = '';

    if (!this.superModules.includes(c.project))
      refspec = c.branch;

    let addCondition = ` branch:${c.branch}`;
    if (labelName == 'Submit') {
      const footer = c.revisions[c.current_revision].commit.message;
      let issue;
      if (footer.includes('Issue:'))
        issue = footer.substring(footer.indexOf('Issue:') + 7, footer.indexOf('\nChange-Id'));
      else
        issue = footer.substring(footer.indexOf('[') + 1, footer.indexOf(']'));

      addCondition += ` message:${issue}`;
    }

    const openQuery = `/changes/?q=status:open${addCondition} owner:${c.owner.username}&` +
      `o=CURRENT_REVISION&o=COMMIT_FOOTERS&o=CURRENT_ACTIONS`;
    const openChanges = await widget.plugin.restApi().get(openQuery);
    c.AllCommits = {};
    c.repository = {};
    let forceRebase = false;

    for (const change of openChanges) {
      const SHA1 = change.current_revision;
      const footer = change.revisions[SHA1].commit_with_footers;
      const rebaseAction = change.revisions[SHA1].actions.rebase;
      const needRebase = rebaseAction && rebaseAction.enabled;
      const mergeable = change.mergeable; // If false, this is a merged conflicts change
      let issue;
      if (footer.includes('Issue:'))
        issue = ':' + footer.substring(footer.indexOf('Issue:') + 7, footer.indexOf('\nChange-Id'));
      else
        issue = ':' + footer.substring(footer.indexOf('[') + 1, footer.indexOf(']'));
      const { project } = change;
      const patch = change.revisions[SHA1]._number;
      if (c.project != project) {
        const item = {
          project,
          sha: SHA1,
          number: change._number,
          patch,
          subject: change.subject,
          issue,
          ref: change.revisions[SHA1].ref,
          rebase: needRebase,
        };
        c.AllCommits[change._number] = item;
        if (typeof c.repository[project] === 'undefined') {
          c.repository[project] = [];
        }
        c.repository[project].push(item);
      } else if (change._number == c._number) {
        if (!this.superModules.includes(project))
          if (labelName == 'Build')
            widget.SMchanges = project + ':' + SHA1 + ' ';
          else
            widget.SMchanges = project + ':' + SHA1 + ':' + c._number + '/' + patch + ' ';
        if (!mergeable && typeof mergeable !== 'undefined') {
          widget.rebase = 'Please solve your merge conflict first';
          forceRebase = true;
        } else if (needRebase) {
          if (labelName == 'Automation Test') {
            widget.rebase = 'This change is not rebased to the latest commit in branch! You must click Rebase first in ' +
              'order to start Automation Test';
            forceRebase = true;
          } else {
            widget.rebase = 'This change is not rebased to the latest commit in branch! If you want DRT/Sanity to run on the ' +
              'latest then click Rebase first';
          }
        }
      }
    }

    // Remove others bundle Commit - no dependecies Commits in SFB project and in VoiceAI Sanity
    if (labelName == 'Automation Test' || this.sanityMode == 'VoiceAI')
      c.repository = {};

    const reposArray = [];

    if (Object.keys(c.repository).length != 0) {
      widget.hidenChange = false;

      Object.keys(c.repository).forEach(key => {
        const repoElement = {};
        const item = c.repository[key];

        repoElement.branches = [];
        repoElement.name = key;

        for (const i of item) {
          let rebaseWarning = '';
          if (i.rebase)
            rebaseWarning = ' -- NEEDS rebase';
          const optionTXT = i.number + ':' + i.subject.substring(0, 125) + i.issue + rebaseWarning;
          repoElement.branches.push({
            desc: optionTXT,
            value: i.number,
          });
        }
        repoElement.selectedBranch = item[0].number;
        repoElement.branches.push({
          desc: 'Latest ' + c.branch,
          value: 0,
        });

        reposArray.push(repoElement);
      });
    }

    if (!forceRebase) {
      widget.gerritChange = c;
      widget.gerritChange.refspec = refspec;
      widget.gerritChange.email = email;
      widget.SubModules = reposArray;
      widget.header = labelName;
      widget.Start = 'Start';
      if (labelName != 'Build') widget.disabled = false;
      widget.buttonText = 'Click Start to execute ' + labelName;
    }
    widget.hiddenImage = true;

    if (labelName == 'Sanity') {
      const Allsanity = [];
      if (this.sanityMode == 'SIP') {
        Allsanity.push({ key: 'SIP Sanity', disabled: false, value: true });
        Allsanity.push({ key: 'WEB Sanity', disabled: true, value: false });
      } else
        Allsanity.push({ key: 'VoiceAI Sanity', disabled: false, value: true });
      widget.sanityCheck = Allsanity;
      widget.hidenSanity = false;
    }
  }
}