#include <stdio.h>      /* for printf() and fprintf() */
#include <sys/socket.h> /* for socket(), bind(), and connect() */
#include <arpa/inet.h>  /* for sockaddr_in and inet_ntoa() */
#include <stdlib.h>     /* for atoi() and exit() */
#include <string.h>     /* for memset() */
#include <unistd.h>     /* for close() */

#include <sys/fcntl.h>

#include <signal.h>
#include <errno.h>
#include <sys/types.h>
#include <netinet/tcp.h>
#include <time.h>

#include "die.h"
#include "checksum.h"

#define INPUT_BUFFER_SIZE 1048576

volatile sig_atomic_t _waiting = 1;

struct ClientTask
{
	struct ClientTask 	*prev, *next;
	struct ClientNode	*node;
	unsigned int		number, attempts;
	char				symbol, *result;
	time_t				time;
};

struct ClientNode
{
	struct ClientNode 	*prev, *next;
	struct ClientTask	*task;
	int 				socket, id;
	char				*address;
};

struct ClientTask 	*__clientTaskCurrent = NULL, *__clientTaskCompleted = NULL, *__clientTaskCompletedTail = NULL;
struct ClientNode 	*__clientNodeTail = NULL, *__clientNodeCurrent = NULL;
int 				__nodeCounter = 0;

// ------------------------------------------------------------------------------------------------------

void addClientTask(unsigned int _number, char _symbol)
{
	struct ClientTask *_clientTask = malloc(sizeof(*_clientTask));

	_clientTask->node = NULL;
	_clientTask->number = _number;
	_clientTask->attempts = 0;
	_clientTask->symbol = _symbol;
	_clientTask->result = NULL;
	_clientTask->time = 0;

	if (__clientTaskCurrent == NULL)
	{
		__clientTaskCurrent = _clientTask;
		_clientTask->prev = _clientTask;
		_clientTask->next = _clientTask;
	}
	
	_clientTask->prev = __clientTaskCurrent->prev;
	_clientTask->next = __clientTaskCurrent;
	_clientTask->prev->next = _clientTask;
	_clientTask->next->prev = _clientTask;
}

void completeClientTask(char *_result, int _resultLength)
{
	struct ClientTask *_clientTask = __clientTaskCurrent;

	if (_result != NULL)
	{
		_clientTask->result = (char *) malloc(_resultLength);
		memcpy(_clientTask->result, _result, _resultLength);
		_clientTask->node->task = NULL;
		printf("client %d@%s completed a task: %d %c - %s\n", _clientTask->node->id, _clientTask->node->address, _clientTask->number, _clientTask->symbol, _clientTask->result);
	}
	else
	{
		_clientTask->result = NULL;
		printf("task failed: %d %c\n", _clientTask->number, _clientTask->symbol);
	}

	if (_clientTask->next != _clientTask)
	{
		__clientTaskCurrent = _clientTask->prev; // the next one will be picked automatically
		_clientTask->prev->next = _clientTask->next;
		_clientTask->next->prev = _clientTask->prev;
	}
	else
		__clientTaskCurrent = NULL;

	if (__clientTaskCompleted == NULL)
	{
		__clientTaskCompleted = _clientTask;
		_clientTask->prev = _clientTask;
		_clientTask->next = _clientTask;
	}
	
	_clientTask->prev = __clientTaskCompleted->prev;
	_clientTask->next = __clientTaskCompleted;
	_clientTask->prev->next = _clientTask;
	_clientTask->next->prev = _clientTask;
	__clientTaskCompletedTail = _clientTask;
}

// ------------------------------------------------------------------------------------------------------

void addClientNode(int _socket, char *_address)
{
	struct ClientNode *_clientNode = malloc(sizeof(*_clientNode));

	_clientNode->task = NULL;
	_clientNode->socket = _socket;
	_clientNode->id = __nodeCounter++;
	_clientNode->address = _address;
	
	if (__clientNodeTail == NULL)
	{
		__clientNodeTail = _clientNode;
		_clientNode->prev = _clientNode;
		_clientNode->next = _clientNode;
	}

	_clientNode->prev = __clientNodeTail;
	_clientNode->next = __clientNodeTail->next;
	_clientNode->prev->next = _clientNode;
	_clientNode->next->prev = _clientNode;
	__clientNodeTail = _clientNode;

	printf("client %d@%s connected\n", _clientNode->id, _clientNode->address);
}

void removeClientNode()
{
	struct ClientNode *_clientNode = __clientNodeCurrent;
	
	printf("client %d@%s disconnected\n", _clientNode->id, _clientNode->address);
	close(_clientNode->socket);
	
	if (_clientNode->next != _clientNode)
	{
		_clientNode->prev->next = _clientNode->next;
		_clientNode->next->prev = _clientNode->prev;
		__clientNodeCurrent = _clientNode->prev;
	}
	else
		__clientNodeCurrent = NULL;

	if (__clientNodeTail == _clientNode)
		__clientNodeTail = __clientNodeCurrent;

	//free(_clientNode->address); // address is static
	free(_clientNode);
}

// ------------------------------------------------------------------------------------------------------

int main(int argc, char *argv[])
{
	FILE 				*_file;
	int 				_number, _server, _client, _result;
	struct ClientTask	*_clientTask;
	struct ClientNode	*_clientNode;
	struct sockaddr_in 	_serverAddress, _clientAddress;
	unsigned int		_clientAddressLength;
	char				_symbol, _outputBuffer[2], _inputBuffer[INPUT_BUFFER_SIZE], _repeated;

	if ((_file = fopen("./server.in", "r")) == NULL)
		diePlus("socket creation failed [%s]", strerror(errno));

	while (fscanf(_file, "%d\n%c", &_number, &_symbol) != EOF)
		addClientTask((unsigned int) _number, _symbol);

	fclose(_file);

	if (__clientTaskCurrent == NULL)
		die("nothing to do");
        
    if ((_server = socket(PF_INET, SOCK_STREAM, IPPROTO_TCP)) < 0)
		diePlus("socket creation failed [%s]", strerror(errno));

	fcntl(_server, F_SETFL, O_NONBLOCK);
	//setsockopt(_server, SOL_SOCKET, SO_REUSEADDR, (void *) &_result, sizeof(_result));

	memset(&_serverAddress, 0, sizeof(_serverAddress));
	_serverAddress.sin_family      	= AF_INET;
    _serverAddress.sin_addr.s_addr 	= htonl(INADDR_ANY);
    _serverAddress.sin_port        	= htons(8844);

	if (bind(_server, (struct sockaddr *) &_serverAddress, sizeof(_serverAddress)) < 0)
        diePlus("socket binding failed [%s]", strerror(errno));

	if (listen(_server, 256) < 0)
        diePlus("socket listening failed [%s]", strerror(errno));

	__clientTaskCurrent = __clientTaskCurrent->prev;

	while (__clientTaskCurrent != NULL)
	{
		__clientTaskCurrent = __clientTaskCurrent->next;

		if (__clientTaskCurrent->node == NULL)
		{
			while (1) // accepts all pending connections
			{
				_clientAddressLength = sizeof(_clientAddress); // accept overwrites it every time
				
				if ((_client = accept(_server, (struct sockaddr *) &_clientAddress, &_clientAddressLength)) < 0)
				{
					if (__clientNodeTail == NULL) // continue while we have no nodes at all
						continue;
					
					if (errno == EAGAIN || errno == EWOULDBLOCK) // no new connections available
						break;
						
					printf("client connection failed [%s]", strerror(errno));
					continue;
				}

				//_result = 5; 	setsockopt(_client, SOL_TCP, TCP_KEEPIDLE, &_result, sizeof(_result)); // send ping every 60 seconds
				//_result = 4; 	setsockopt(_client, SOL_TCP, TCP_KEEPCNT, &_result, sizeof(_result)); // if no pong received - retry 4 times
				//_result = 1; 	setsockopt(_client, SOL_TCP, TCP_KEEPINTVL, &_result, sizeof(_result)); // wait 5 seconds between attempts
				//_result = 1; 	setsockopt(_client, SOL_SOCKET, SO_KEEPALIVE, &_result, sizeof(_result));
				//_result = 1;	setsockopt(_client, SOL_SOCKET, SO_REUSEADDR, &_result, sizeof(_result));
								setsockopt(_client, SOL_TCP, TCP_NODELAY, &_result, sizeof(_result)); // MAY lower the throughput
								setsockopt(_client, SOL_TCP, TCP_QUICKACK, &_result, sizeof(_result)); // MAY lower the throughput
				
				addClientNode(_client, inet_ntoa(_clientAddress.sin_addr));
				recv(_client, &_inputBuffer, INPUT_BUFFER_SIZE, MSG_DONTWAIT); // clear the input stack
			}

			__clientNodeCurrent = __clientNodeTail;

			do
			{
				__clientNodeCurrent = __clientNodeCurrent->next;

				if (__clientNodeCurrent->task == NULL)
				{
					_outputBuffer[0] = (char) __clientTaskCurrent->number;
					_outputBuffer[1] = __clientTaskCurrent->symbol;
					
					if ((_result = send(__clientNodeCurrent->socket, _outputBuffer, 2, 0)) != 2)
					{
						removeClientNode();
						continue; // checks the while statement anyway
					}

					__clientNodeCurrent->task = __clientTaskCurrent;
					__clientTaskCurrent->node = __clientNodeCurrent;
					++__clientTaskCurrent->attempts;
					__clientTaskCurrent->time = time(NULL);
					break;
				}
			}
			while (__clientNodeCurrent != __clientNodeTail);
		}
		else
		{
			_repeated = 0;
			
			while((_result = recv(__clientTaskCurrent->node->socket, &_inputBuffer, INPUT_BUFFER_SIZE, MSG_DONTWAIT | MSG_PEEK)) > 0)
			{
				if (_inputBuffer[_result - 1] != '\0')
				{
					if (_repeated == 1) // client is too slow
					{
						_result = -1;
						break;
					}
					
					_repeated = 1;
					sleep(1); // give client plenty of time to submit all leftovers
					continue;
				}
				
				_result = recv(__clientTaskCurrent->node->socket, &_inputBuffer, INPUT_BUFFER_SIZE, 0);
				break;
			}

			if (_result > 0)
				completeClientTask(_inputBuffer, _result);
			else if ((_result == -1 && errno != EAGAIN && errno != EWOULDBLOCK) || ((time(NULL) - __clientTaskCurrent->time) > 10))
			{
				__clientNodeCurrent = __clientTaskCurrent->node;
				__clientTaskCurrent->node = NULL;
				removeClientNode();
				
				if (__clientTaskCurrent->attempts == 4) // each task has 4 attempts
					completeClientTask(NULL, 0); // NULL result = FAIL
			}
		}
	}

    printf("DONE\n");
	__clientNodeCurrent = __clientNodeTail;

	do
	{
		__clientNodeCurrent = __clientNodeCurrent->next;
		recv(__clientNodeCurrent->socket, &_inputBuffer, INPUT_BUFFER_SIZE, MSG_DONTWAIT); // clear the input stack
		_outputBuffer[0] = -1;
		_outputBuffer[1] = -1;
		send(__clientNodeCurrent->socket, _outputBuffer, 2, 0);
	}
	while (__clientNodeCurrent != __clientNodeTail);

	sleep(1);
	close(_server);

	_file = fopen("./server.out", "w");
	__clientTaskCurrent = __clientTaskCompletedTail;

	do
	{
		__clientTaskCurrent = __clientTaskCurrent->next;
		fprintf(_file, "%d\n%c\n", __clientTaskCurrent->number, __clientTaskCurrent->symbol);

		if (__clientTaskCurrent->result != NULL)
			fprintf(_file, "%s\n\n", __clientTaskCurrent->result);
		else
			fprintf(_file, ">> FAILED <<\n\n");
	}
	while (__clientTaskCurrent != __clientTaskCompletedTail);

	fclose(_file);
    exit(0);
}
