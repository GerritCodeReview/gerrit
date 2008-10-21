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

var PROGRESS_ELEMENT_ID = "ajax_progress";
var CONNECTION_ERROR_ELEMENT_ID = "ajax_error";

var g_filesLoading = 0;

/**
 * Asynchronously get a url that contains a json file, and call the callback
 * when done.
 *
 * The callback should have the following prototype:
 *  callback(boolean success, Object result)
 *
 * Returns an object that can be used to cancel the request.
 */
function get_file_contents(url, callback) {
    var running = true;

    function set_progress_visibility(visible) {
        var progress = document.getElementById(PROGRESS_ELEMENT_ID);
        if (progress) {
            progress.style.display = visible ? "block" : "none";
        }
    }

    show_bad_connection_error(null);

    var req;
    if (window.XMLHttpRequest) {
        req = new XMLHttpRequest();
    } else if (window.ActiveXObject) {
        req = new ActiveXObject("Microsoft.XMLHTTP");
    } else {
        return;
    }

    g_filesLoading++;
    if (g_filesLoading > 0) {
        set_progress_visibility(true);
    }

    req.open("GET", url, true);

    req.onreadystatechange = function() {
        if (req.readyState == 4) {
            if (req.status == 200) {
                callback(true, req.responseText);
            } else {
                callback(false, null);
            }
            g_filesLoading--;
            if (g_filesLoading == 0) {
                set_progress_visibility(false);
            }
            running = false;
        }
    };

    req.send(null);


    var controller = new Object();
    controller.cancel = function() {
        req.abort();
        if (running) {
            g_filesLoading--;
            if (g_filesLoading == 0) {
                set_progress_visibility(false);
            }
            running = false;
        }
    };
    return controller;
}

/**
 * like get_file_contents, but converts the result to a json object
 */
function get_json(url, callback) {
    return get_file_contents(url, function(success, data) {
                callback(success, eval("(" + data + ")"));
            });
}

function show_bad_connection_error(err) {
    var errorDiv = document.getElementById(CONNECTION_ERROR_ELEMENT_ID);
    if (errorDiv) {
        if (err) {
            errorDiv.style.display = "block";
        } else {
            errorDiv.style.display = "none";
        }
    }
}


