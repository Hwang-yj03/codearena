#include <vector>
#include <cstdio>
int main() { std::vector<char> v(200u * 1024 * 1024, 1); std::printf("%d\n", v[777]); }
