// int 오버플로: 1, 2번 케이스는 통과하고 3번(20억+20억)에서 WA가 나야 한다
#include <iostream>
int main() { int a, b; std::cin >> a >> b; std::cout << a + b << "\n"; }
