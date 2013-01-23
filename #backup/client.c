#include <stdio.h>      /* for printf() and fprintf() */
#include <sys/socket.h> /* for socket(), connect(), send(), and recv() */
#include <arpa/inet.h>  /* for sockaddr_in and inet_addr() */
#include <stdlib.h>     /* for atoi() and exit() */
#include <string.h>     /* for memset() */
#include <unistd.h>     /* for close() */

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <sys/select.h>

#include <signal.h>
#include <errno.h>
#include <time.h>
#include <netinet/tcp.h>

#include "die.h"
#include "checksum.h"

#define OUTPUT_BUFFER_SIZE 1048576

int main(int argc, char *argv[])
{
	int 				_server = -1, _taskStatus, _result, _outputLength;
	struct sockaddr_in 	_serverAddress;
	char 			   	*_taskArguments[4], _taskOutputChar, _inputBuffer[2], _outputBuffer[OUTPUT_BUFFER_SIZE];
	pid_t 				_taskPID;
	FILE 			   	*_taskOutputFile;
	struct timeval		_time, _timeAfter;
	fd_set				_fileDescriptors;
	socklen_t			_socketLength;
		
	memset(&_serverAddress, 0, sizeof(_serverAddress));
	_serverAddress.sin_family      	= AF_INET;
    _serverAddress.sin_addr.s_addr 	= inet_addr("77.93.17.69"); // 127.0.0.1
    _serverAddress.sin_port        	= htons(8844);

    while (1)
	{
		if (_server == -1)
		{
			_server = socket(PF_INET, SOCK_STREAM, IPPROTO_TCP);
			fcntl(_server, F_SETFL, fcntl(_server, F_GETFL, NULL) | O_NONBLOCK);
			setsockopt(_server, SOL_TCP, TCP_NODELAY, &_result, sizeof(_result)); // MAY lower the throughput
			setsockopt(_server, SOL_TCP, TCP_QUICKACK, &_result, sizeof(_result)); // MAY lower the throughput
		}
		
		if ((connect(_server, (struct sockaddr *) &_serverAddress, sizeof(_serverAddress)) < 0) && (errno != EISCONN))
		{
			if (errno == EINPROGRESS)
			{
				printf("connecting to server... ");
				fflush(stdout);
				_time.tv_sec = 5;
				_time.tv_usec = 0;
				FD_ZERO(&_fileDescriptors);
				FD_SET(_server, &_fileDescriptors);
				
				if (select(_server + 1, &_fileDescriptors, NULL, NULL, &_time) > 0)
				{
					_socketLength = sizeof(int);
					getsockopt(_server, SOL_SOCKET, SO_ERROR, &_result, &_socketLength);

					if (_result || (recv(_server, _inputBuffer, 2, MSG_DONTWAIT | MSG_PEEK) == 0))
					{
						recv(_server, _inputBuffer, 2, MSG_DONTWAIT);
						_server = -1;
						printf("error\n");
						continue;
					}

					printf("success!\n");
				}
				else
				{
					_server = -1;
					printf("timeout\n");
					continue;
				}
			}
			else
			{
				_server = -1;
				printf("socket error [%s]\n", strerror(errno));
				continue;
			}
		}

		fcntl(_server, F_SETFL, fcntl(_server, F_GETFL, NULL) & (~O_NONBLOCK));
		
		if ((_result = recv(_server, _inputBuffer, 2, 0)) != 2)
		{
			close(_server);
			
			if (_result == -1)
				printf("task acquisition failed [%s]\n", strerror(errno));
			else
				printf("task acquisition failed\n");
				
			continue;
		}

		if (_inputBuffer[0] == -1 && _inputBuffer[1] == -1)
		{
			close(_server);
			printf("server disconnected\n");
			_server = -1;
			continue;
		}

		_taskArguments[0] = "./task"; 			// the task executable
		_taskArguments[1] = &_inputBuffer[0]; 	// the number of the task to be performed
		_taskArguments[2] = &_inputBuffer[1]; 	// the argument of the task
		_taskArguments[3] = NULL; 				// terminating NULL
		
		printf("task received: %d %c\n", (int) *_taskArguments[1], *_taskArguments[2]);
		gettimeofday(&_time, NULL);
		
		if ((_taskPID = fork()) == 0)					// = TRUE if this the child process
		{
			alarm(10);									// set timeout to 10 seconds
			execv(_taskArguments[0], _taskArguments);	// the task is executed
		}
		else if (_taskPID < 0)							// = TRUE if something went wrong while creating a child process
		{
			close(_server);
			printf("forking failed [%s]\n", strerror(errno));
			continue;
		}
		
		wait(&_taskStatus);								// waiting for the task to finish
		gettimeofday(&_timeAfter, NULL);
		
		if (!WIFEXITED(_taskStatus))					// = TRUE if the task was terminated by the alarm
		{
			close(_server);
			printf("task timed out [%s]\n", strerror(errno));
			continue;
		}
			
		_taskStatus = WEXITSTATUS(_taskStatus);
		
		if (_taskStatus)								// = TRUE if the task exit code isn't equal to 0
		{
			close(_server);
			printf("task failed: %d [%s]\n", _taskStatus, strerror(errno));
			continue;
		}
			
		printf("task completed in %ldms: %d %c - ", ((_timeAfter.tv_sec - _time.tv_sec) * 1000 + (_timeAfter.tv_usec - _time.tv_usec) / 1000), (int) *_taskArguments[1], *_taskArguments[2]);
		_taskOutputFile = fopen("./task.out", "r");

		if (fscanf(_taskOutputFile, "%s\n", _outputBuffer) <= 0)
		{
			fclose(_taskOutputFile);
			close(_server);
			printf("file corrupted or task failed [%s]", strerror(errno));
			continue;
		}

		_outputLength = strlen(_outputBuffer) + 1;
		
		if (send(_server, _outputBuffer, _outputLength, 0) != _outputLength)
		{
			fclose(_taskOutputFile);
			close(_server);
			printf("send attempt failed [%s]", strerror(errno));
			continue;
		}

		fclose(_taskOutputFile);
		printf("%s\n", _outputBuffer);
	}
	
	exit(0);
}
