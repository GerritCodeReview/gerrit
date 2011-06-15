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

:- package 'gerrit'.
'$init' :- init.


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%
%% init:
%%
%%   Initialize the module's private state. These typically take the form of global
%%   aliased hashes carrying "constant" data about the current change for any
%%   predicate that needs to obtain it.
%%
init :-
  define_hash(commit_labels),
  define_hash(current_user).

define_hash(A) :- hash_exists(A), !, hash_clear(A).
define_hash(A) :- atom(A), !, new_hash(_, [alias(A)]).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%
%% commit_label/2:
%%
%% During rule evaluation of a change, this predicate is defined to
%% be a table of labels that pertain to the commit of interest.
%%
%%   commit_label( label('Code-Review', 2), user(12345789) ).
%%   commit_label( label('Verified', -1), user(8181) ).
%%
:- public commit_label/2.
%%
commit_label(L, User) :- L = label(H, _),
  atom(H),
  !,
  hash_get(commit_labels, H, Cached),
  ( [] == Cached ->
    get_commit_labels(_),
    hash_get(commit_labels, H, Rs), !
    ;
    Rs = Cached
  ),
  scan_commit_labels(Rs, L, User)
  .
commit_label(Label, User) :-
  get_commit_labels(Rs),
  scan_commit_labels(Rs, Label, User).

scan_commit_labels([R | Rs], L, U) :- R = commit_label(L, U).
scan_commit_labels([_ | Rs], L, U) :- scan_commit_labels(Rs, L, U).
scan_commit_labels([], _, _) :- fail.

get_commit_labels(Rs) :-
  hash_contains_key(commit_labels, '$all'),
  !,
  hash_get(commit_labels, '$all', Rs)
  .
get_commit_labels(Rs) :-
  '$load_commit_labels'(Rs),
  set_commit_labels(Rs).

set_commit_labels(Rs) :-
  define_hash(commit_labels),
  hash_put(commit_labels, '$all', Rs),
  index_commit_labels(Rs).

index_commit_labels([]).
index_commit_labels([R | Rs]) :-
  R = commit_label(label(H, _), _),
  atom(H),
  !,
  hash_get(commit_labels, H, Tmp),
  hash_put(commit_labels, H, [R | Tmp]),
  index_commit_labels(Rs)
  .
index_commit_labels([_ | Rs]) :-
  index_commit_labels(Rs).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%
%% current_user/2:
%%
%%   Locate the CurrentUser object caching group memberships, account data.
%%
current_user(user(AccountId), User) :-
  hash_get(current_user, AccountId, User),
  User \== [],
  !
  .
current_user(user(AccountId), User) :-
  integer(AccountId),
  '$current_user'(AccountId, User),
  hash_put(current_user, AccountId, User).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%
%% user_label_range/4:
%%
%%   Lookup the range allowed to be used.
%%
user_label_range(Label, test_user(Name), Min, Max) :-
  % TODO Replace this hack clause when RefControl is rewritten.
  !,
  clause(user:test_grant(Label, test_user(Name), range(Min, Max)), _)
  .
user_label_range(Label, Who, Min, Max) :-
  atom(Label),
  current_user(Who, User),
  '$user_label_range'(Label, User, Min, Max).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%
%% not_same/2:
%%
:- public not_same/2.
%%
not_same(ok(A), ok(B)) :- !, A \= B.
not_same(label(_, ok(A)), label(_, ok(B))) :- !, A \= B.
not_same(_, _).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%
%% can_submit/2:
%%
%%   Executes the SubmitRule for each solution until one where all of the
%%   states has the format label(_, ok(_)) is found, then cut away any
%%   remaining choice points leaving this as the last solution.
%%
:- public can_submit/2.
%%
can_submit(SubmitRule, S) :-
  call_submit_rule(SubmitRule, Tmp),
  Tmp =.. [submit | Ls],
  ( is_all_ok(Ls) -> S = ok(Tmp), ! ; S = not_ready(Tmp) ).

call_submit_rule(P:X, Arg) :- !, F =.. [X, Arg], P:F.
call_submit_rule(X, Arg) :- !, F =.. [X, Arg], F.

is_all_ok([]).
is_all_ok([label(_, ok(__)) | Ls]) :- is_all_ok(Ls).
is_all_ok(_) :- fail.


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%
%% locate_submit_rule/1:
%%
%%   Finds a submit_rule depending on what rules are available.
%%   If none are available, use default_submit/1.
%%
:- public locate_submit_rule/1.
%%

locate_submit_rule(RuleName) :-
  clause(user:submit_rule(_), _),
  !,
  RuleName = user:submit_rule
  .
locate_submit_rule(RuleName) :-
  '$compiled_predicate'(user, submit_rule, 1),
  !,
  RuleName = user:submit_rule
  .
locate_submit_rule(RuleName) :-
  RuleName = 'com.google.gerrit.rules.common':default_submit.


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%
%% default_submit/1:
%%
:- public default_submit/1.
%%
default_submit(P) :-
  get_legacy_approval_types(ApprovalTypes),
  default_submit(ApprovalTypes, P).

% Apply the old "all approval categories must be satisfied"
% loop by scanning over all of the approval types to build
% up the submit record.
%
default_submit(ApprovalTypes, P) :-
  default_submit(ApprovalTypes, [], Tmp),
  reverse(Tmp, Ls),
  P =.. [ submit | Ls].

default_submit([], Out, Out).
default_submit([Type | Types], Tmp, Out) :-
  approval_type(Label, Id, Fun, Min, Max) = Type,
  legacy_submit_rule(Fun, Label, Id, Min, Max, Status),
  R = label(Label, Status),
  default_submit(Types, [R | Tmp], Out).


%% legacy_submit_rule:
%%
%% Apply the old -2..+2 style logic.
%%
legacy_submit_rule('MaxWithBlock', Label, Id, Min, Max, T) :- !, max_with_block(Label, Min, Max, T).
legacy_submit_rule('MaxNoBlock', Label, Id, Min, Max, T) :- !, max_no_block(Label, Max, T).
legacy_submit_rule('NoBlock', Label, Id, Min, Max, T) :- true.
legacy_submit_rule('NoOp', Label, Id, Min, Max, T) :- true.
legacy_submit_rule(Fun, Label, Id, Min, Max, T) :- T = impossible(unsupported(Fun)).


%% max_with_block:
%%
%% - The minimum is never used.
%% - At least one maximum is used.
%%
max_with_block(Label, Min, Max, reject(Who)) :-
  check_label_range_permission(Label, Min, ok(Who)),
  !
  .
max_with_block(Label, Min, Max, ok(Who)) :-
  \+ check_label_range_permission(Label, Min, ok(_)),
  check_label_range_permission(Label, Max, ok(Who)),
  !
  .
max_with_block(Label, Min, Max, need(Max)) :-
  true
  .
%TODO Uncomment this clause when group suggesting is possible.
%max_with_block(Label, Min, Max, need(Max, Group)) :-
%  \+ check_label_range_permission(Label, Max, ok(_)),
%  check_label_range_permission(Label, Max, ask(Group))
%  .
%max_with_block(Label, Min, Max, impossible(no_access)) :-
%  \+ check_label_range_permission(Label, Max, ask(Group))
%  .


%% max_no_block:
%%
%% - At least one maximum is used.
%%
max_no_block(Label, Max, ok(Who)) :-
  check_label_range_permission(Label, Max, ok(Who)),
  !
  .
max_no_block(Label, Max, need(Max)) :-
  true
  .
%TODO Uncomment this clause when group suggesting is possible.
%max_no_block(Label, Max, need(Max, Group)) :-
%  check_label_range_permission(Label, Max, ask(Group))
%  .
%max_no_block(Label, Max, impossible(no_access)) :-
%  \+ check_label_range_permission(Label, Max, ask(Group))
%  .


%% check_label_range_permission:
%%
check_label_range_permission(Label, ExpValue, ok(Who)) :-
  commit_label(label(Label, ExpValue), Who),
  user_label_range(Label, Who, Min, Max),
  Min @=< ExpValue, ExpValue @=< Max
  .
%TODO Uncomment this clause when group suggesting is possible.
%check_label_range_permission(Label, ExpValue, ask(Group)) :-
%  grant_range(Label, Group, Min, Max),
%  Min @=< ExpValue, ExpValue @=< Max
%  .
