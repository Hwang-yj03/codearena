#include <vector>
#include <cstdio>
int main(){ std::vector<char> v(300u*1024*1024, 1); std::printf("%d", v[12345]); }
