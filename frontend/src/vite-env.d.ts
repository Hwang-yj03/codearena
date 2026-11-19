/// <reference types="vite/client" />

// Monaco는 워커 진입 파일의 타입 선언을 제공하지 않는다 (부수 효과만 있는 모듈)
declare module 'monaco-editor/editor/editor.worker.js'
