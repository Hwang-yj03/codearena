import Editor, { loader } from '@monaco-editor/react'
import { useEffect, useRef } from 'react'
import * as monaco from 'monaco-editor'
import EditorWorker from './editor.worker?worker'

// @monaco-editor/react는 기본으로 Monaco를 CDN에서 받아온다.
// 오프라인·사내망에서도 동작하도록 npm 패키지를 번들에 포함시킨다.
// C++/Python/Java는 문법 강조만 쓰므로 기본 에디터 워커 하나면 충분하다.
self.MonacoEnvironment = { getWorker: () => new EditorWorker() }
loader.config({ monaco })

type Props = {
  language: string
  value: string
  onChange: (value: string) => void
  onSubmitShortcut?: () => void
}

export default function CodeEditor({ language, value, onChange, onSubmitShortcut }: Props) {
  // 단축키는 에디터 생성 시 한 번만 등록된다. 등록 시점의 함수를 그대로 쓰면
  // 그때의 코드·상태로 제출되므로(오래된 클로저), 항상 최신 함수를 ref로 가리킨다.
  const submitRef = useRef(onSubmitShortcut)
  useEffect(() => {
    submitRef.current = onSubmitShortcut
  }, [onSubmitShortcut])

  return (
    <Editor
      height="100%"
      language={language}
      value={value}
      theme="vs"
      onChange={(v) => onChange(v ?? '')}
      onMount={(editor) => {
        editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => submitRef.current?.())
      }}
      options={{
        fontFamily: "'JetBrains Mono', ui-monospace, monospace",
        fontSize: 14,
        lineHeight: 22,
        minimap: { enabled: false },
        scrollBeyondLastLine: false,
        tabSize: 4,
        automaticLayout: true,
        padding: { top: 12 },
        renderLineHighlight: 'line',
        // 언어 서버가 없어 추천이 단어 나열뿐이고, Enter가 추천어를 입력해 코드가 깨지기 쉽다
        quickSuggestions: false,
        suggestOnTriggerCharacters: false,
        wordBasedSuggestions: 'off',
        acceptSuggestionOnEnter: 'off',
        ariaLabel: '코드 입력',
      }}
      loading={<div className="editor-loading">에디터를 불러오는 중</div>}
    />
  )
}
