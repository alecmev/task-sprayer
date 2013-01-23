#include "die.h"

#include <stdio.h>
#include <stdlib.h>

void die(char *message)
{
	fprintf(stderr, "ERROR: %s\n", message);
	
	exit(1);
}

void diePlus(char *message, char *data)
{
	fprintf(stderr, "ERROR: ");
	fprintf(stderr, message, data);
	fprintf(stderr, "\n");
	
	exit(1);
}

