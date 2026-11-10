// luka: lets the Linux library load on older glibc.
//
// With glibc 2.38+ headers and GCC's default _GNU_SOURCE, strtol, strtoul, strtoll and strtoull calls are routed to the C23 versions
// __isoc23_strtol and __isoc23_strtoul, which only exist from glibc 2.38 on. Defining them here, forwarding to the
// plain functions, keeps the library working on glibc 2.35 (Ubuntu 22.04, Debian 12) as well. The C23 versions
// only differ in also reading "0b" binary prefixes, which nothing here relies on. "used" and default visibility
// keep link-time optimisation from dropping or localising them before the static C++ runtime, which also calls
// them, is linked. Java loads the library privately, so exporting them changes nothing for other libraries.
#if defined(__linux__)
#include <features.h>  // defines __GLIBC__
#endif
#if defined(__linux__) && defined(__GLIBC__)

extern "C" {
long luka_plain_strtol(const char *text, char **end, int base) __asm__("strtol");
unsigned long luka_plain_strtoul(const char *text, char **end, int base) __asm__("strtoul");
long long luka_plain_strtoll(const char *text, char **end, int base) __asm__("strtoll");
unsigned long long luka_plain_strtoull(const char *text, char **end, int base) __asm__("strtoull");

__attribute__((used, visibility("default"))) long __isoc23_strtol(const char *text, char **end, int base) {
    return luka_plain_strtol(text, end, base);
}

__attribute__((used, visibility("default"))) unsigned long __isoc23_strtoul(const char *text, char **end, int base) {
    return luka_plain_strtoul(text, end, base);
}

__attribute__((used, visibility("default"))) long long __isoc23_strtoll(const char *text, char **end, int base) {
    return luka_plain_strtoll(text, end, base);
}

__attribute__((used, visibility("default"))) unsigned long long __isoc23_strtoull(const char *text, char **end, int base) {
    return luka_plain_strtoull(text, end, base);
}
}

#endif
