% If you have 1.4.3 or older Prolog-Cafe, you need to
% use (consult(load), load(load)) to get definition of load.
% Then use load([f1,f2,...]) to load multiple source files.

% Input is a list of file names or a single file name.
% Use a conditional expression style without cut operator.
load(X) :-
  ( (X = [])
  -> true
  ; ( (X = [H|T])
    -> (load_file(H), load(T))
    ;  load_file(X)
    )
  ).

% load_file is '$consult' without the bug of unbound 'File' variable.
% For repeated unit tests, skip statistics and print_message.
load_file(F) :- atom(F), !,
  '$prolog_file_name'(F, PF),
  open(PF, read, In),
  % print_message(info, [loading,PF,'...']),
  % statistics(runtime, _),
  consult_stream(PF, In),
  % statistics(runtime, [_,T]),
  % print_message(info, [PF,'loaded in',T,msec]),
  close(In).
