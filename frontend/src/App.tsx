import { Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { RequireAuth } from './components/RequireAuth'
import { LoginPage, SignupPage } from './pages/AuthPages'
import { MySubmissionsPage } from './pages/MySubmissionsPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { ProblemListPage } from './pages/ProblemListPage'
import { ProblemPage } from './pages/ProblemPage'

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<ProblemListPage />} />
        <Route path="problems/:id" element={<ProblemPage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="signup" element={<SignupPage />} />
        <Route
          path="me/submissions"
          element={
            <RequireAuth>
              <MySubmissionsPage />
            </RequireAuth>
          }
        />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
