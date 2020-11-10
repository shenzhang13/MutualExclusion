#!/usr/bin/env bash

# Change this to your netid
netid=sxz162330

# Root directory of your project
PROJDIR=/home/012/s/sx/sxz162330/AOS/Project2

# Directory where the config file is located on your local system
#CONFIGLOCAL=$HOME/src/config.txt
CONFIGLOCAL=$PROJDIR/configuration.txt

# Directory your java classes are in
BINDIR=$PROJDIR

# Your main project class
PROG=MutualExclusion


n=0

cat $CONFIGLOCAL | sed -e "s/#.*//" | sed -e "/^\s*$/d" |
(
  # read the number of nodes
  read -r firstLine
  # first line has info of numOfNode, interRequestDelay, csExecutionTime, numOfRequest
  numOfNode=$(echo "$firstLine" | awk '{ print $1 }')
  interRequestDelay=$(echo "$firstLine" | awk '{ print $2 }')
  csExecutionTime=$(echo "$firstLine" | awk '{ print $3 }')
  numOfRequest=$(echo "$firstLine" | awk '{ print $4 }')

  while [[ $n -lt $numOfNode ]]; do # loop for numOfNode times
    read -r line
    echo "$line"
    # each line has info of node id, host, and port number
    node=$(echo "$line" | awk '{ print $1 }')
    host=$(echo "$line" | awk '{ print $2 }')
    port=$(echo "$line" | awk '{ print $3 }')


    # initialize each node as a server by calling SCTPServer
    gnome-terminal -- sh -c "ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no $netid@$host java -cp $BINDIR $PROG $node $host $port; exec bash" &

    n=$((n + 1))
  done


)
