#include <cstdio>
int main() { int n; long long s = 0, x; std::scanf("%d", &n); while (n--) { std::scanf("%lld", &x); s += x; } std::printf("%lld\n", s); }
