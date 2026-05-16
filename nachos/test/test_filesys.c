/* test_filesys.c
 * Tests creat, open, read, write, close, unlink syscalls.
 */

#include "syscall.h"
#include "stdio.h"

int main() {
    char buf[10];
    int fd, n;

    /* create and write */
    fd = creat("testfile.txt");
    if (fd < 0) { printf("FAIL: creat returned %d\n", fd); halt(); }

    n = write(fd, "hello", 5);
    if (n != 5) { printf("FAIL: write returned %d\n", n); halt(); }

    close(fd);

    /* open and read back */
    fd = open("testfile.txt");
    if (fd < 0) { printf("FAIL: open returned %d\n", fd); halt(); }

    n = read(fd, buf, 5);
    if (n != 5) { printf("FAIL: read returned %d\n", n); halt(); }

    close(fd);

    /* unlink */
    n = unlink("testfile.txt");
    if (n != 0) { printf("FAIL: unlink returned %d\n", n); halt(); }

    /* open after unlink should fail */
    fd = open("testfile.txt");
    if (fd >= 0) { printf("FAIL: open after unlink returned %d\n", fd); halt(); }

    printf("PASS: file syscalls\n");
    halt();
}