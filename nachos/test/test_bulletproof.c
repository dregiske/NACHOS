/* test_bulletproof.c
 * Tests that bad arguments don't crash the kernel.
 * Every call below should return -1 without crashing.
 */

#include "syscall.h"
#include "stdio.h"

int main() {
    int r;

    /* NULL pointer arguments */
    r = open(0);
    if (r != -1) { printf("FAIL: open(NULL) returned %d\n", r); halt(); }

    r = creat(0);
    if (r != -1) { printf("FAIL: creat(NULL) returned %d\n", r); halt(); }

    r = unlink(0);
    if (r != -1) { printf("FAIL: unlink(NULL) returned %d\n", r); halt(); }

    /* bad file descriptors */
    r = read(-1, 0, 10);
    if (r != -1) { printf("FAIL: read(-1) returned %d\n", r); halt(); }

    r = write(100, 0, 10);
    if (r != -1) { printf("FAIL: write(100) returned %d\n", r); halt(); }

    r = close(-1);
    if (r != -1) { printf("FAIL: close(-1) returned %d\n", r); halt(); }

    /* join on non-child pid */
    r = join(999, 0);
    if (r != -1) { printf("FAIL: join(999) returned %d\n", r); halt(); }

    /* non-root halt should return -1 (only works if this is not pid 0) */

    printf("PASS: bulletproofing\n");
    halt();
}