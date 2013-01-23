#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <time.h>

#include "die.h"

int main(int argc, char *argv[])
{
	int 	_taskNumber, _i;
	char 	_printingSymbol;
	FILE 	*_outputFile;

	srand(time(NULL));
	sleep(1 + rand() / (RAND_MAX / 2)); // heavy calculations emulation

	if (argc != 3)
		die("invalid argument count (2 needed)");
		
	_taskNumber = (int) *argv[1];
	
	if (_taskNumber <= 0)
		die("invalid task number (>0 needed)");
		
	_printingSymbol = toupper(argv[2][0]);
	_outputFile = fopen("./task.out", "w");
	
	for (_i = 0; _i < _taskNumber; ++_i)
	{
		fprintf(_outputFile, "%c", _printingSymbol);
		//printf("%c", _printingSymbol);
	}

	fprintf(_outputFile, "\n");
	fclose(_outputFile);
	//printf("\ntask finished\n");
	exit(0);
}
