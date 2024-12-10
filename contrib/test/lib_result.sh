#
# SPDX-FileCopyrightText: Copyright (c) 2024 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

RESULT=0
result() { # ?$ <testname> [<failtext>] > PASS <testname> | FAIL <testname> <failtext>
    local rtn=$?
    if [ $rtn -eq 0 ] ; then
        echo "PASS $1"
        [ -z "$RESULT" ] && RESULT=0
    else
        echo "FAIL $1"
        [ -n "$2" ] && echo "     $2"
        RESULT=1
    fi
    RESULT_NAME=$1
    return $rtn
}

result_out() { # <testname> <expected> <actual> > PASS <testname> | <FAIL> <testname> <summary>
    local fail="$(echo "Expected:$2|" ; echo "       Actual:$3|")"
    [ "$2" = "$3" ]
    result "$1" "$fail"
}

result_program() { # <name> <test_program> [<args>...]
    echo "--- Running tests for $1  ---" ; shift
    "$@" || RESULT=1
}

result_success() { # $? <test_name>
    if [ $? -ne 0 ] ; then
        RESULT_UNEXPECTED_ERRORS=$(echo "$RESULT_UNEXPECTED_ERRORS" ; echo "    $1")
    fi
    RESULT_NAME=$1
}

result_error() { # $? <test_name>
    if [ $? -eq 0 ] ; then
        RESULT_UNEXPECTED_SUCCESSES=$(echo "$RESULT_UNEXPECTED_SUCCESSES" ; echo "    $1")
    fi
    RESULT_NAME=$1
}

RESULT_UNEXPECTED_ERRORS=''
result_successes() {
    [ -z "$RESULT_UNEXPECTED_ERRORS" ]
    result "exits with zero when expected" "$RESULT_UNEXPECTED_ERRORS"
}

RESULT_UNEXPECTED_SUCCESSES=''
result_errors() {
    [ -z "$RESULT_UNEXPECTED_SUCCESSES" ]
    result "exits with error when expected" "$RESULT_UNEXPECTED_SUCCESSES"
}

