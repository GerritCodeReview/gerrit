%% Copyright (C) 2011 The Android Open Source Project
%%
%% Licensed under the Apache License, Version 2.0 (the "License");
%% you may not use this file except in compliance with the License.
%% You may obtain a copy of the License at
%%
%% http://www.apache.org/licenses/LICENSE-2.0
%%
%% Unless required by applicable law or agreed to in writing, software
%% distributed under the License is distributed on an "AS IS" BASIS,
%% WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
%% See the License for the specific language governing permissions and
%% limitations under the License.

:- package 'com.google.gerrit.rules.common'.


%% not_same
%%
test(not_same_success) :-
  not_same(ok(a), ok(b)).


%% get_legacy_approval_type(s)
%%
test(get_legacy_approval_type_CVRW) :-
  get_legacy_approval_type('Code-Review', C),
  get_legacy_approval_type('CRVW', C),
  C = approval_type('Code-Review', 'CRVW', 'MaxWithBlock', -2, 2).

test(get_legacy_approval_type_VRIF) :-
  get_legacy_approval_type('Verified', V),
  get_legacy_approval_type('VRIF', V),
  V = approval_type('Verified', 'VRIF', 'MaxWithBlock', -1, 1).

test(get_legacy_approval_types) :-
  get_legacy_approval_types(T),
  T = [C, V],
  C = approval_type('Code-Review', 'CRVW', 'MaxWithBlock', -2, 2),
  V = approval_type('Verified', 'VRIF', 'MaxWithBlock', -1, 1).


%% commit_label
%%
test(commit_label_all) :-
  findall(commit_label(L, U), commit_label(L, U), Out),
  all_commit_labels(Ls),
  Ls = Out.

test(commit_label_CodeReview) :-
  L = label('Code-Review', _),
  findall(p(L, U), commit_label(L, U), Out),
  [ p(label('Code-Review', 2), bob),
    p(label('Code-Review', 2), alice) ] == Out.


%% max_with_block
%%
test(max_with_block_success_accept_max_score) :-
  max_with_block('Code-Review', -2, 2, ok(alice)).

test(max_with_block_success_reject_min_score) :-
  max_with_block('You-Fail', -1, 1, reject(failer)).

test(max_with_block_success_need_suggest) :-
  max_with_block('Verified', -1, 1, need(1, 'Build-Bots')).

test(max_with_block_success_impossible) :-
  max_with_block('Code-Style', 0, 1, impossible(no_access)).


%% default_submit
%%
test(default_submit_fails) :-
  findall(P, default_submit(P), All),
  All = [submit(C, V)],
  C = label('Code-Review', ok(alice)),
  V = label('Verified', need(1, 'Build-Bots')).


%% can_submit
%%
test(can_submit_ok) :-
  set_commit_labels([
    commit_label( label('Code-Review', 2), alice ),
    commit_label( label('Verified', 1), builder )
  ]),
  can_submit('com.google.gerrit.rules.common':default_submit, S),
  S = ok(submit(C, V)),
  C = label('Code-Review', ok(alice)),
  V = label('Verified', ok(builder)).

test(can_submit_not_ready) :-
  can_submit('com.google.gerrit.rules.common':default_submit, S),
  S = not_ready(submit(C, V)),
  C = label('Code-Review', ok(alice)),
  V = label('Verified', need(1, 'Build-Bots')).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%
%% Supporting Data
%%
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

setup :-
  init,
  all_commit_labels(Ls),
  set_commit_labels(Ls).

all_commit_labels(Ls) :-
  Ls = [
    commit_label( label('Code-Review', 2), alice ),
    commit_label( label('Code-Review', 2), bob ),
    commit_label( label('You-Fail', -1), failer ),
    commit_label( label('You-Fail', -1), alice )
  ].


% TODO Replace this support code
%
:- package 'user'.
in_group(alice, 'Developers').
in_group(builder, 'Build-Bots').
in_group(failer, 'Fail-People').
in_group(alice, 'Fail-People').

grant_range('Code-Review', 'Developers', -2, 2).
grant_range('Verified', 'Build-Bots', -1, 1).
grant_range('You-Fail', 'Fail-People', -1, 1).
