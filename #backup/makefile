all: server client task

server: die.o checksum.o server.c
	gcc -o server die.o checksum.o server.c
	
client: die.o checksum.o client.c
	gcc -o client die.o checksum.o client.c

task: die.o task.c
	gcc -o task die.o task.c

die.o: die.c
	gcc -c die.c
	
checksum.o: checksum.c
	gcc -c checksum.c
