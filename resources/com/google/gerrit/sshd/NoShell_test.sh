#!/bin/bash

generalMessage="
./NoShell_test.sh -pid=process_id -port=ssh_port -user=user_name -host=host_name -times=number_of_times

Compares the number of file descriptors before and after running the NoShell command
toward the specified Gerrit process, for the given number of times. Per default, the verbosity is set to false.
However, it is possible to change this behavior with the flag -v or --verbose. Example:

./NoShell_test.sh -pid=12345 -port=29418 -user=admin -host=localhost -times=100
OR with verbose option:
./NoShell_test.sh -v -pid=12345 -port=29418 -user=admin -host=localhost -times=100
"

pid=0
port=0
user=""
host=0
times=1
argsCount=0
verbose=0

#Gets all the flags necessary for the script to run properly
while :; do
  case "$1" in
  -h | --help)
    echo "$generalMessage"
    exit 1
    ;;
  -pid=?*)
    pid=${1#*=}
    argsCount=$((argsCount + 1))
    ;;
  -port=?*)
    port=${1#*=}
    argsCount=$((argsCount + 1))
    ;;
  -user=?*)
    user=${1#*=}
    argsCount=$((argsCount + 1))
    ;;
  -host=?*)
    host=${1#*=}
    argsCount=$((argsCount + 1))
    ;;
  -times=?*)
    times=${1#*=}
    argsCount=$((argsCount + 1))
    ;;
  -v | --verbose)
    verbose=1
    ;;
  --)
    shift
    break
    ;;
  *)
    break
    ;;
  esac
  shift
done

#Ensures that the arguments necessary to run the script are collected
if [ "$argsCount" -lt 5 ]; then
  echo "$generalMessage"
  exit 1
fi

#Checks that the PID provided is running a Gerrit instance.
ReleaseWar="release.war"
GerritWar="gerrit.war"
GerritSH="gerrit.sh"
pidCommand=$(ps -p "${pid}" -o command)
GerritPidMismatchMessage="Make sure that Gerrit is up and running and that the PID corresponds to the one running gerrit."
if [[ ("$pidCommand" != *"$ReleaseWar"*) && ($pidCommand != *"$GerritWar"*) && ($pidCommand != *"$GerritSH"*) ]]; then
  echo "$GerritPidMismatchMessage"
  exit 1
fi

fdCountBeforeLoop=$(lsof -p "$pid" | wc -l)

#Checks if the connection ssh is established and that there is no connection errors.
welcomeMessage="Welcome to Gerrit Code Review"
messageReceived=$({ ssh -p "$port" "$user"@"$host"; } 2>&1)
sshErrorMessage="There is an error in the ssh connection to your site.
Please make sure that the ssh key is properly registered in Gerrit, that you entered the right ssh port
and that the host is registered as a known host. For the latter case, you can use this command:
ssh-keyscan -t rsa -p 29418 localhost > ~/.ssh/known_hosts"

if [ "$verbose" == 1 ]; then
  echo "$messageReceived"
fi

if [[ "$messageReceived" != *"$welcomeMessage"* ]]; then
  echo "$sshErrorMessage"
  exit 1
fi

#Executes the rest of the test.
for ((iterations = 1; iterations < "$times"; iterations++)); do
  if [ "$verbose" == 0 ]; then
    ssh -p "$port" "$user"@"$host" &>/dev/null
  else
    ssh -p "$port" "$user"@"$host"
  fi
done
fdCountAfterLoop=$(lsof -p "$pid" | wc -l)

beforeFdCountMessage="File descriptor count before the loop: ${fdCountBeforeLoop}."
afterFdCountMessage="File descriptor count after the loop: ${fdCountAfterLoop}."

if [ "$fdCountBeforeLoop" == "$fdCountAfterLoop" ]; then
  echo "The test is successful. The counts of file descriptors are equal."
else
  failureMessage="The test fails. The counts of file descriptors are different."
  printf "%s\n%s\n%s\n" "${failureMessage}" "${beforeFdCountMessage}" "${afterFdCountMessage}"
fi

terminationStatus="$?"
if [ "$terminationStatus" != 0 ]; then
  echo "The last executed ssh command exited with non-zero status ${terminationStatus}."
fi
