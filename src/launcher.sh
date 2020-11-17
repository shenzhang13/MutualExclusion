#!/bin/bash

# Your netid
NETID=qxw170003
# name of the directory when the project is located
PROJECT_DIRECTORY="/home/012/q/qx/qxw170003/AOS_P2"
# name of the configuration file
CONFIG_FILE="config.txt"
# name of the program to be run
PROGRAM_FILE=Application
# initialize iteration variable
i=0
# read the configuration file
# replace any phrase starting with "#" with an empty string and then delete any empty lines
cat "$CONFIG_FILE" | sed -e "s/#.*//" | sed "/^$/d" |
{
	read -r firstLine
  # first line has info of numOfNode, interRequestDelay, csExecutionTime, numOfRequest
  numOfNode=$(echo "$firstLine" | awk '{ print $1 }')
  echo "system contains" $numOfNode "nodes"
  interRequestDelay=$(echo "$firstLine" | awk '{ print $2 }')
  csExecutionTime=$(echo "$firstLine" | awk '{ print $3 }')
  maxNumOfRequest=$(echo "$firstLine" | awk '{ print $4 }')

	# read the location of each node one by one
	while [[ $i -lt $numOfNode ]]
	do
		# read a line
		read line
		# echo $line
		# extract the node identifier
		IDENTIFIER=$( echo $line | awk '{ print $1 }' )
		# extract the machine name
		HOST=$( echo $line | awk '{ print $2 }' )
		# extract the port identifieR
		PORT=$( echo $line | awk '{ print $3 }' )

		echo "spawning node" $IDENTIFIER on "machine" $HOST "at PORT" $PORT
		# construct the string specifying the program to be run by the ssh command
		ARGUMENTS="java -classpath \"$PROJECT_DIRECTORY\" $PROGRAM_FILE $IDENTIFIER $HOST $PORT \"$CONFIG_FILE\""
		# spawn the node
		# any error message will be stored in the log files
		xterm -e "ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no $NETID@$HOST '$ARGUMENTS' 2> log.launcher.$IDENTIFIER"   &
		i=$((i+1))
	done
}
