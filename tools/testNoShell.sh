#!/bin/bash

generalMessage="
The test takes five parameters to be able to execute. The first
parameter is the process that runs gerrit, this parameter helps to
access the process directory and get the number of open file
descriptors. The second parameter is the ssh port, the third parameter
is the username and the last parameter is the host.
The fifth parameter describes the number of times to trigger the command.
"
argsLength=$#

if [ "$argsLength" == 5 ]
then
  processNumber="$1"
  portNumber=$2
  username=$3
  host=$4
  times=$5

  firstResult=$(find /proc/"$processNumber"/fd| wc -l)
  for ((iterations=0; iterations<"$times"; iterations++))
   do
     ssh -p "$portNumber" "$username"@"$host"
   done
  secondResult=$(find /proc/"$processNumber"/fd | wc -l)

  firstResultMessage="The result of the first find command (before the loop) is ${firstResult}."
  secondResultMessage="The result of the second find command (after the loop) is ${secondResult}."
  echo "$firstResultMessage"
  echo "$secondResultMessage"
  terminationStatus="$?"
  if [ "$terminationStatus" -eq 0 ]
  then
    echo "Command terminated successfully"
  fi
else
  echo "$generalMessage"
fi
