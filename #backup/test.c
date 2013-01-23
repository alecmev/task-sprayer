#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <sys/select.h>

#include <signal.h>
#include <sys/errno.h>

#include "die.h"
#include "checksum.h"

int main(int argc, char *argv[])
{
	int 				_server, _taskStatus, _result;
	struct sockaddr_in 	_serverAddress;
	char 			   	*_taskArguments[4], _taskOutputChar, _serverBuffer[2];
	pid_t 				_taskPID;
	FILE 			   	*_taskOutputFile;
	
	printf("client began\n");
	
	if ((_server = socket(PF_INET, SOCK_STREAM, IPPROTO_TCP)) < 0)
		diePlus("socket creation failed [_server = %d]", _server);
		
	memset(&_serverAddress, 0, sizeof(_serverAddress));
	_serverAddress.sin_family      	= AF_INET;
    _serverAddress.sin_addr.s_addr 	= inet_addr("77.93.17.69");
    _serverAddress.sin_port        	= htons(8844);
    
    if (connect(_server, (struct sockaddr *) &_serverAddress, sizeof(_serverAddress)) < 0)
        die("connection attemp failed");
        
    if ((_result = recv(_server, _serverBuffer, 2, 0)) != 2)
        diePlus("read attempt failed [_result = %d]", _result);

	_taskArguments[0] = "./task"; 			// the task executable
	_taskArguments[1] = _serverBuffer[0]; 	// the number of the task to be performed
	_taskArguments[2] = _serverBuffer[1]; 	// the argument of the task
	_taskArguments[3] = NULL; 				// terminating NULL
	
	printf("task received: %d %c\n", _taskArguments[1], _taskArguments[2]);
	
	if ((_taskPID = fork()) == 0)					// = TRUE if this the child process
	{
		alarm(10);									// set timeout to 10 seconds
		execv(_taskArguments[0], _taskArguments);	// the task is executed
	}
	else if (_taskPID < 0)							// = TRUE if something went wrong while creating a child process
		diePlus("forking failed [_taskPID = %d]", _taskPID);
	
	wait(&_taskStatus);								// waiting for the task to finish
	
	if (!WIFEXITED(_taskStatus))					// = TRUE if the task was terminated by the alarm
		die("task timed out");
		
	_taskStatus = WEXITSTATUS(_taskStatus);
	
	if (_taskStatus)								// = TRUE if the task exit code isn't equal to 0
		diePlus("task failed [_taskStatus = %d]", _taskStatus);
		
	printf("task output: ");
	_taskOutputFile = fopen("./task.out", "r");
	
	while ((_taskOutputChar = getc(_taskOutputFile)) != EOF)
	{
		if ((_result = send(_server, _taskOutputChar, 1, 0)) != 1)
        	diePlus("write attempt failed [_result = %d]", _result);
        	
		printf("%c", _taskOutputChar);
	}

	fclose(_taskOutputFile);
	printf("\n");
	close(_server);
	printf("client finished\n");
	
	exit(0);
}
