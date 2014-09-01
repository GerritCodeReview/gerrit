const StatusSanity = function(label) {
  if (!label)
    return true;
  for (var j = 0; j < label.all.length; j++)
    if (label.all[j].value == 1) return true;
  return false;
}

const findGerritChanges = async function(widget, c, labelName, refspec, email) {

  widget.header = 'Search other changes';
  widget.hiddenImage = false;

  SMchanges = "";

  if (!superModules.includes(c.project)) {
    refspec = c.branch;
  }

  let addCondition = ` branch:${c.branch}`;
  if (labelName == "Submit") {
    var footer = c.revisions[c.current_revision].commit.message;
    if (footer.includes("Issue:") == true)
      var issue = footer.substring(footer.indexOf("Issue:")+7,footer.indexOf("\nChange-Id"));
    else
      var issue = footer.substring(footer.indexOf("[")+1,footer.indexOf("]"));

    addCondition += ` message:${issue}`;
  }

  const openQuery = `/changes/?q=status:open${addCondition} owner:${c.owner.username}&o=CURRENT_REVISION&o=COMMIT_FOOTERS&o=CURRENT_ACTIONS`;
  const openChanges = await widget.plugin.restApi().get(openQuery);
  c.AllCommits = {};
  c.repository = {};
  let forceRebase = false;

  for (const change of openChanges) {
    var SHA1 = change.current_revision;
    var footer = change.revisions[SHA1].commit_with_footers;
    var rebaseAction = change.revisions[SHA1].actions.rebase;
    var needRebase = rebaseAction && rebaseAction.enabled;
    var mergeable = change.mergeable; // If false, this is a merged conflicts change
    var issue=""
    if (footer.includes("Issue:") == true)
        issue=":" + footer.substring(footer.indexOf("Issue:")+7,footer.indexOf("\nChange-Id"));
    else
        issue=":" + footer.substring(footer.indexOf("[")+1,footer.indexOf("]"));
    var project = change.project;
    let patch = change.revisions[SHA1]._number;
    if (c.project != project) {
        const item = {
          project,
          sha: SHA1,
          number: change._number,
          patch,
          subject: change.subject,
          issue,
          ref: change.revisions[SHA1].ref,
          rebase: needRebase
        }
        c.AllCommits[change._number] = item;
        if (typeof c.repository[project] === "undefined") {
          c.repository[project] = [];
        }
        c.repository[project].push(item);
    } else if (change._number == c._number) {
        if (!superModules.includes(project))
          if (labelName == "Build")
            SMchanges = project + ':' + SHA1 + ' ';
          else
            SMchanges = project + ':' + SHA1 + ':' + c._number + '/' + patch + ' ';
        if (!mergeable && typeof mergeable !== 'undefined') {
          widget.rebase = 'Please solve your merge conflict first';
          forceRebase = true;
        } else if (needRebase) {
          if (labelName == "Automation Test") {
            widget.rebase = 'This change is not rebased to the latest commit in branch! You must click Rebase first in order to start Automation Test';
            forceRebase = true;
          } else
            widget.rebase = 'This change is not rebased to the latest commit in branch! If you want DRT/Sanity to run on the latest then click Rebase first';
        }
    }
  }

  // Remove others bundle Commit - no dependecies Commits in SFB project and in VoiceAI Sanity
  if (labelName == "Automation Test" || sanityMode == "VoiceAI" )
    c.repository = {};

  var reposArray = [];

  if (Object.keys(c.repository).length != 0) {
     widget.hidenChange = false;

     Object.keys(c.repository).forEach( key => {

        var repoElement = {};
        var item = c.repository[key];

        repoElement.branches = [];
        repoElement.name = key;

        for (var j = 0; j < item.length; j++) {
            var rebaseWarning = "";
            if (item[j].rebase)
                rebaseWarning = " -- NEEDS rebase";
            var optionTXT = item[j].number + ":" + item[j].subject.substring(0, 125) + item[j].issue + rebaseWarning;
            repoElement.branches.push({
              desc: optionTXT,
              value: item[j].number
            });
        }
        repoElement.selectedBranch = item[0].number;
        repoElement.branches.push({
          desc: "Latest " + c.branch,
          value: 0
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
    widget.Start = "Start";
    if (labelName != "Build") widget.disabled = false;
    widget.buttonText = 'Click Start to execute ' + labelName;
  }
  widget.hiddenImage = true;

  if (labelName == "Sanity") {
    var Allsanity = [];
    if (sanityMode == "SIP") {
      Allsanity.push({ "key": 'SIP Sanity', "disabled": false, "value": true });
      Allsanity.push({ "key": 'WEB Sanity', "disabled": true, "value": false });
    } else
      Allsanity.push({ "key": 'VoiceAI Sanity', "disabled": false, "value": true });
    widget.sanityCheck = Allsanity;
    widget.hidenSanity = false;
  }
};

