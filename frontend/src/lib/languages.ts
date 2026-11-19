import type { LanguageKey } from '../api/types'

type LanguageOption = { key: LanguageKey; label: string; monaco: string; template: string }

export const LANGUAGES: LanguageOption[] = [
  {
    key: 'cpp',
    label: 'C++17',
    monaco: 'cpp',
    template: `#include <bits/stdc++.h>
using namespace std;

int main() {
    ios::sync_with_stdio(false);
    cin.tie(nullptr);

    return 0;
}
`,
  },
  {
    key: 'python',
    label: 'Python 3',
    monaco: 'python',
    template: `import sys
input = sys.stdin.readline

`,
  },
  {
    key: 'java',
    label: 'Java 21',
    monaco: 'java',
    template: `import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    }
}
`,
  },
]

export const languageOf = (key: LanguageKey) => LANGUAGES.find((l) => l.key === key) ?? LANGUAGES[0]

/** 문제·언어별로 작성 중인 코드를 브라우저에 보관한다 (새로고침해도 유지) */
export const drafts = {
  key: (problemId: number, lang: LanguageKey) => `codearena.draft.${problemId}.${lang}`,
  load(problemId: number, lang: LanguageKey): string {
    try {
      return localStorage.getItem(this.key(problemId, lang)) ?? languageOf(lang).template
    } catch {
      return languageOf(lang).template
    }
  },
  save(problemId: number, lang: LanguageKey, code: string) {
    try {
      localStorage.setItem(this.key(problemId, lang), code)
    } catch {
      /* 저장 실패는 무시 */
    }
  },
}
