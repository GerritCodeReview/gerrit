/*
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


// utilities
// =============================================================================

function trim(str)
{
    return str.replace(/^\s+|\s+$/g, '');
}

function add_input(mom, type, name, value)
{
    var input = document.createElement("input");
    input.type = type;
    if (name) input.name = name
    if (value) input.value = value;
    mom.appendChild(input);
    return input;
}

function add_option(select, text, value, selected)
{
    var opt = document.createElement("option");
    opt.text = text;
    opt.value = value;
    opt.selected = selected;
    select.appendChild(opt);
    return opt;
}

function set_error(me, error)
{
    var o = me.error_div;
    if (error) {
        var N = o.childNodes.length;
        for (var i=0; i<N; i++) {
            o.removeChild(o.childNodes[0]);
        }
        o.appendChild(document.createTextNode(error));
        o.style.display = "block";
    } else {
        o.style.display = "none";
    }
}

// inline confirmation dialogs
// =============================================================================
function show_confirmation_dialog(button, dialog) {
    document.getElementById(dialog).style.display = 'block';
    document.getElementById(button).style.display = 'none';
}

function hide_confirmation_dialog(button, dialog) {
    document.getElementById(dialog).style.display = 'none';
    document.getElementById(button).style.display = 'inline';
}



// UserGroupField
// =============================================================================

function UserGroupField_add_keydown(ev, me)
{
    if (ev.keyCode == 13) {
        // enter
        UserGroupField_add(me);
        return false;
    }
    return true;
}

function UserGroupField_insertEntry(me, data, read_only)
{
    var entry = new Object();
    entry.type = data["type"];
    entry.key = data["key"];
    if (entry.type == "user") {
        entry.display_name = data["real_name"] + " <" + data["email"] + ">";
    } else if (entry.type == "group") {
        entry.display_name = "Group: " + data["name"];
    }

    entry.tr = me.table.insertRow(me.table.rows.length-1);
    var td = entry.tr.insertCell(-1);
    td.appendChild(document.createTextNode(entry.display_name));
    var td = entry.tr.insertCell(-1);

    if (read_only) {
      me.read_only[me.read_only.length] = entry;
    } else {
      var input = document.createElement("input");
      var input = add_input(td, "button", null, "remove");
      input.onclick = function () { UserGroupField_remove(me, entry); };
      add_input(td, "hidden", me.name + "_keys", entry.key);

      me.people[me.people.length] = entry;
    }
}

function UserGroupField_insertField(mom, name, allow_users, allow_groups,
    initial, read_only)
{
    var me = new Object();
    me.name = name;
    me.allow_users = allow_users;
    me.allow_groups = allow_groups;
    me.people = []; // The users and groups already added
    me.read_only = []; // the read-only users & groups

    // the table
    me.table = document.createElement("table");

    // the add field button
    var tr = me.table.insertRow(-1);
    var td = tr.insertCell(-1);
    me.add_text = add_input(td, "text", name + "_add", "");
    me.add_text.onkeypress = function(ev) {
            return UserGroupField_add_keydown(ev, me);
        };
    var td = tr.insertCell(-1);
    var add_button = add_input(td, "button", null, "add");
    add_button.onclick = function() {
            UserGroupField_add(me);
        };
    var td = tr.insertCell(-1);
    me.error_div = document.createElement("div");
    me.error_div.style.display = "none";
    me.error_div.style.color = "red"; // TODO(joeo) CSS!
    td.appendChild(me.error_div);

    for (var i=0; i<read_only.length; i++) {
        UserGroupField_insertEntry(me, read_only[i], true);
    }

    for (var i=0; i<initial.length; i++) {
        UserGroupField_insertEntry(me, initial[i], false);
    }

    mom.appendChild(me.table);
}

function UserGroupField_userInList(me, key, type, list) {
    for (var i=0; i<list.length; i++) {
        if (list[i].key == key) {
            if (type == "user") {
                set_error(me, "That user is already added.");
            } else {
                set_error(me, "That group is already added.");
            }
            return true;
        }
    }
    return false;
}

function UserGroupField_add(me)
{
    // figure out the value they want
    var val = trim(me.add_text.value);
    if (val.length == 0) {
        // bail if it is empty
        me.add_text.value = "";
        return;
    }

    if (me.add_text.ajaxController) {
        me.add_text.ajaxController.cancel();
    }
    me.add_text.ajaxController = get_json(
            "/user_info?op=get_user_info&id=" + escape(val)
                + "&allow_users=" + me.allow_users + "&allow_groups=" + me.allow_groups,
            function(success, result) {
                if (!success) {
                    show_bad_connection_error("UserGroupField_add_callback");
                    return;
                }
                
                if (!result) {
                    if (me.allow_users && me.allow_groups) {
                        set_error(me, "Not a registered email address or group name.");
                    } else if (me.allow_users) {
                        set_error(me, "Not a registered email address.");
                    } else if (me.allow_groups) {
                        set_error(me, "Not a registered group name.");
                    }
                    return;
                }

                // check to make sure it is not a duplicate
                var key = result["key"];
                var type = result["type"];
                if (UserGroupField_userInList(me, key, type, me.people)
                        || UserGroupField_userInList(me, key, type, me.read_only)) {
                    return;
                }

                // add the new row and hidden inputs
                UserGroupField_insertEntry(me, result, false);

                // clear the error and text box
                set_error(me, null);
                me.add_text.value = "";
            });
}

function UserGroupField_remove(me, entry)
{
    for (var i in me.people) {
        if (me.people[i] == entry) {
            entry.tr.parentNode.removeChild(entry.tr)
            me.people.splice(i, 1);
            break;
        }
    }
}


// ApproversField
// =============================================================================

function ApproversField_insertEntry(me, entry)
{
    var files;
    var key;
    var approvers;
    var verifiers;
    var submitters;
    var bad_files;
    if (entry) {
        files = entry["files"];
        bad_files = entry["bad_files"];
        key = entry["key"];
        approvers = entry["approvers"];
        verifiers = entry["verifiers"];
        submitters = entry["submitters"];
    } else {
        files = "";
        bad_files = "";
        key = "added_" + me.key_index;
        me.key_index = me.key_index + 1;
        approvers = [];
        verifiers = [];
        submitters = [];
    }
    var field_key = me.name + "_" + key;

    var tr = me.table.insertRow(me.table.rows.length);
    var td = tr.insertCell(-1);

    var style = me.styles["approval"];
    if (style) {
        td.className = style;
    }

    if (bad_files.length > 0) {
        var bad_files_div = document.createElement("div");
        td.appendChild(bad_files_div);
        bad_files_div.className = "bad_files";
        bad_files_div.appendChild(document.createTextNode("Illegal file paths:"));
        var bad_files_ul = document.createElement("ul");
        bad_files_div.appendChild(bad_files_ul);
        for (var i in bad_files) {
            var bad_file_li = document.createElement("li");
            bad_files_ul.appendChild(bad_file_li);
            bad_file_li.appendChild(document.createTextNode(bad_files[i]));
        }
    }

    var textarea = document.createElement("textarea");
    textarea.name = field_key + "_files";
    textarea.cols = 100;
    textarea.rows = 5;
    textarea.appendChild(document.createTextNode(files));
    td.appendChild(textarea);

    me.keys_field = add_input(td, "hidden", me.name + "_keys", key);

    var field_table = document.createElement("table");
    field_table.width = "100%";
    td.appendChild(field_table);
    var field_tr = field_table.insertRow(-1);
    var field_td = document.createElement("th");
    field_tr.appendChild(field_td);
    field_td.appendChild(document.createTextNode("Approvers"));

    field_td = document.createElement("th");
    field_tr.appendChild(field_td);
    field_td.appendChild(document.createTextNode("Verifiers"));

    field_td = document.createElement("th");
    field_tr.appendChild(field_td);
    field_td.appendChild(document.createTextNode("Submitters"));

    var field_tr = field_table.insertRow(-1);
    field_td = field_tr.insertCell(-1);
    UserGroupField_insertField(field_td, field_key + "_approvers", true, true,
            approvers, []);
    
    field_td = field_tr.insertCell(-1);
    UserGroupField_insertField(field_td, field_key + "_verifiers", true, true,
            verifiers, []);

    field_td = field_tr.insertCell(-1);
    UserGroupField_insertField(field_td, field_key + "_submitters", true, true,
            submitters, []);
    
    var td = tr.insertCell(-1);
    td.vAlign = "top";
    var input = add_input(td, "button", null, "remove");
    input.onclick = function () { ApproversField_remove(me, tr); };
    if (style) {
        td.className = style;
    }
}

function ApproversField_insertField(mom, name, initial, styles)
{
    var me = new Object();
    me.name = name;
    me.key_index = 0;
    me.styles = styles;

    mom = document.getElementById(mom);

    me.div = document.createElement("div");
    me.table = document.createElement("table");
    me.div.appendChild(me.table);

    for (var i=0; i<initial.length; i++) {
        ApproversField_insertEntry(me, initial[i]);
    }

    var add_button = add_input(me.div, "button", null, "Add Approver");
    add_button.onclick = function() { ApproversField_add(me); };

    mom.appendChild(me.div);
}

function ApproversField_add(me)
{
    ApproversField_insertEntry(me, null);
}

function ApproversField_remove(me, tr)
{
    me.table.deleteRow(tr);
}


// ApproversField
// =============================================================================

function ProjectField_insertEntry(me, name, project_list, value)
{
    var s = document.createElement("select");
    s.name = name;
    s.className = "project-field"
    me.mom.appendChild(s);
    add_option(s, "--", null, value == null);
    for (var i in project_list) {
        var p = project_list[i];
        add_option(s, p["name"], p["key"], p["key"] == value);
    }
    me.last = s;

    s.onclick = function() {
            if (s.selectedIndex == 0) {
                // the -- item
                if (s != me.last) {
                    me.mom.removeChild(s);
                }
            } else {
                if (s == me.last) {
                    ProjectField_insertEntry(me, name, project_list, "--");
                }
            }
        };
}

function ProjectField_insertField(mom, name, project_list, initial)
{
    var me = new Object();
    me.mom = mom;

    var current = new Array(initial.length);
    for (var i in initial) {
        current[i] = initial[i];
    }

    for (var i in current) {
        ProjectField_insertEntry(me, name, project_list, current[i]);
    }
    ProjectField_insertEntry(me, name, project_list, null);
}

