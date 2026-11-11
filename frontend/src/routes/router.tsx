import { createBrowserRouter, Navigate } from 'react-router-dom';

import { AuthPage } from '@/features/auth/AuthPage';
import { WorkspacePage } from '@/features/workspace/WorkspacePage';
import { NotFoundPage } from '@/routes/NotFoundPage';

export const router = createBrowserRouter([
  { path: '/', element: <Navigate to="/login" replace /> },
  { path: '/login', element: <AuthPage mode="login" /> },
  { path: '/register', element: <AuthPage mode="register" /> },
  { path: '/app', element: <WorkspacePage /> },
  { path: '*', element: <NotFoundPage /> },
]);
