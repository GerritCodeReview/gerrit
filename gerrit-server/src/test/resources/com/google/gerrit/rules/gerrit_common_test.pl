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

:- package gerrit.


%% not_same
%%
test(not_same_success) :-
  not_same(ok(a), ok(b)),
  not_same(label(e, ok(a)), label(e, ok(b))).


%% get_legacy_label_types
%%
test(get_legacy_label_types) :-
  get_legacy_label_types(T),
  T = [C, V],
  C = label_type('Code-Review', 'MaxWithBlock', -2, 2),
  V = label_type('Verified', 'MaxWithBlock', -1, 1).


%% commit_label
%%
test(commit_label_all) :-
  findall(commit_label(L, U), commit_label(L, U), Out),
  all_commit_labels(Ls),
  Ls = Out.

test(commit_label_CodeReview) :-
  L = label('Code-Review', _),
  findall(p(L, U), commit_label(L, U), Out),
  [ p(label('Code-Review', 2), test_user(bob)),
    p(label('Code-Review', 2), test_user(alice)) ] == Out.


%% max_with_block
%%
test(max_with_block_success_accept_max_score) :-
  max_with_block('Code-Review', -2, 2, ok(test_user(alice))).

test(max_with_block_success_reject_min_score) :-
  max_with_block('You-Fail', -1, 1, reject(test_user(failer))).

test(max_with_block_success_need_suggest) :-
  max_with_block('Verified', -1, 1, need(1)).

skip_test(max_with_block_success_impossible) :-
  max_with_block('Code-Style', 0, 1, impossible(no_access)).


%% default_submit
%%
test(default_submit_fails) :-
  findall(P, default_submit(P), All),
  All = [submit(C, V)],
  C = label('Code-Review', ok(test_user(alice))),
  V = label('Verified', need(1)).


%% can_submit
%%
test(can_submit_ok) :-
  set_commit_labels([
    commit_label( label('Code-Review', 2), test_user(alice) ),
    commit_label( label('Verified', 1), test_user(builder) )
  ]),
  can_submit(gerrit:default_submit, S),
  S = ok(submit(C, V)),
  C = label('Code-Review', ok(test_user(alice))),
  V = label('Verified', ok(test_user(builder))).

test(can_submit_not_ready) :-
  can_submit(gerrit:default_submit, S),
  S = not_ready(submit(C, V)),
  C = label('Code-Review', ok(test_user(alice))),
  V = label('Verified', need(1)).

test(can_submit_only_verified_not_ready) :-
  can_submit(submit_only_verified, S),
  S = not_ready(submit(V)),
  V = label('Verified', need(1)).


%% filter_submit_results
%%
test(filter_submit_remove_verified) :-
  can_submit(gerrit:default_submit, R),
  filter_submit_results(filter_out_v, [R], S),
  S = [ok(submit(C))],
  C = label('Code-Review', ok(test_user(alice))).

test(filter_submit_add_code_review) :-
  set_commit_labels([
    commit_label( label('Code-Review', 2), test_user(alice) ),
    commit_label( label('Verified', 1), test_user(builder) )
  ]),
  can_submit(submit_only_verified, R),
  filter_submit_results(filter_in_cr, [R], S),
  S = [ok(submit(C, V))],
  C = label('Code-Review', ok(test_user(alice))),
  V = label('Verified', ok(test_user(builder))).


%% find_label
%%
test(find_default_code_review) :-
  can_submit(gerrit:default_submit, R),
  arg(1, R, S),
  find_label(S, 'Code-Review', L),
  L = label('Code-Review', ok(test_user(alice))).

test(find_default_verified) :-
  can_submit(gerrit:default_submit, R),
  arg(1, R, S),
  find_label(S, 'Verified', L),
  L = label('Verified', need(1)).


%% remove_label
%%
test(remove_default_code_review) :-
  can_submit(gerrit:default_submit, R),
  arg(1, R, S),
  C = label('Code-Review', ok(test_user(alice))),
  remove_label(S, C, Out),
  Out = submit(V),
  V = label('Verified', need(1)).


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
    commit_label( label('Code-Review', 2), test_user(alice) ),
    commit_label( label('Code-Review', 2), test_user(bob) ),
    commit_label( label('You-Fail', -1), test_user(failer) ),
    commit_label( label('You-Fail', -1), test_user(alice) )
  ].

submit_only_verified(P) :-
  max_with_block('Verified', -1, 1, Status),
  P = submit(label('Verified', Status)).

filter_out_v(R, S) :-
  find_label(R, 'Verified', Verified), !,
  remove_label(R, Verified, S).
filter_out_v(R, S).

filter_in_cr(R, S) :-
  R =.. [submit | Labels],
  max_with_block('Code-Review', -2, 2, Status),
  CR = label('Code-Review', Status),
  S =.. [submit , CR | Labels].

:- package user.
test_grant('Code-Review', test_user(alice), range(-2, 2)).
test_grant('Verified', test_user(builder), range(-1, 1)).
test_grant('You-Fail', test_user(alice), range(-1, 1)).
test_grant('You-Fail', test_user(failer), range(-1, 1)).
