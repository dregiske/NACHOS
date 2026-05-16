/* test_exec.c
 * Tests exec and join syscalls.
 */

#include "syscall.h"
#include "stdio.h"

int main() {
    int pid, result, status;

    /* exec halt.coff as a child */
    pid = exec("halt.coff", 0, 0);
    if (pid < 0) { printf("FAIL: exec returned %d\n", pid); halt(); }

    /* join the child */
    result = join(pid, &status);
    if (result != 1) { printf("FAIL: join returned %d\n", result); halt(); }

    /* join same pid again should fail (already disowned) */
    result = join(pid, &status);
    if (result != -1) { printf("FAIL: second join returned %d\n", result); halt(); }

    printf("PASS: exec/join\n");
    halt();
}