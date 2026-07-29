import { lazy, Suspense } from "react";
import { Navigate, createBrowserRouter } from "react-router-dom";

import { useAuthStore } from "@/stores/authStore";

const LoginPage = lazy(() =>
  import("@/pages/LoginPage").then((module) => ({ default: module.LoginPage }))
);
const ChatPage = lazy(() =>
  import("@/pages/ChatPage").then((module) => ({ default: module.ChatPage }))
);
const NotFoundPage = lazy(() =>
  import("@/pages/NotFoundPage").then((module) => ({ default: module.NotFoundPage }))
);
const AdminLayout = lazy(() =>
  import("@/pages/admin/AdminLayout").then((module) => ({ default: module.AdminLayout }))
);
const DashboardPage = lazy(() =>
  import("@/pages/admin/dashboard/DashboardPage").then((module) => ({
    default: module.DashboardPage
  }))
);
const KnowledgeListPage = lazy(() =>
  import("@/pages/admin/knowledge/KnowledgeListPage").then((module) => ({
    default: module.KnowledgeListPage
  }))
);
const KnowledgeDocumentsPage = lazy(() =>
  import("@/pages/admin/knowledge/KnowledgeDocumentsPage").then((module) => ({
    default: module.KnowledgeDocumentsPage
  }))
);
const KnowledgeChunksPage = lazy(() =>
  import("@/pages/admin/knowledge/KnowledgeChunksPage").then((module) => ({
    default: module.KnowledgeChunksPage
  }))
);
const IntentTreePage = lazy(() =>
  import("@/pages/admin/intent-tree/IntentTreePage").then((module) => ({
    default: module.IntentTreePage
  }))
);
const IntentListPage = lazy(() =>
  import("@/pages/admin/intent-tree/IntentListPage").then((module) => ({
    default: module.IntentListPage
  }))
);
const IntentEditPage = lazy(() =>
  import("@/pages/admin/intent-tree/IntentEditPage").then((module) => ({
    default: module.IntentEditPage
  }))
);
const IngestionPage = lazy(() =>
  import("@/pages/admin/ingestion/IngestionPage").then((module) => ({
    default: module.IngestionPage
  }))
);
const RagTracePage = lazy(() =>
  import("@/pages/admin/traces/RagTracePage").then((module) => ({
    default: module.RagTracePage
  }))
);
const RagTraceDetailPage = lazy(() =>
  import("@/pages/admin/traces/RagTraceDetailPage").then((module) => ({
    default: module.RagTraceDetailPage
  }))
);
const SystemSettingsPage = lazy(() =>
  import("@/pages/admin/settings/SystemSettingsPage").then((module) => ({
    default: module.SystemSettingsPage
  }))
);
const SampleQuestionPage = lazy(() =>
  import("@/pages/admin/sample-questions/SampleQuestionPage").then((module) => ({
    default: module.SampleQuestionPage
  }))
);
const QueryTermMappingPage = lazy(() =>
  import("@/pages/admin/query-term-mapping/QueryTermMappingPage").then((module) => ({
    default: module.QueryTermMappingPage
  }))
);
const UserListPage = lazy(() =>
  import("@/pages/admin/users/UserListPage").then((module) => ({
    default: module.UserListPage
  }))
);

function routePage(element: JSX.Element) {
  return (
    <Suspense
      fallback={
        <div className="grid min-h-[16rem] place-items-center text-sm text-slate-500">
          正在加载页面…
        </div>
      }
    >
      {element}
    </Suspense>
  );
}

function RequireAuth({ children }: { children: JSX.Element }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  return children;
}

function RequireAdmin({ children }: { children: JSX.Element }) {
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (user?.role !== "admin") {
    return <Navigate to="/chat" replace />;
  }

  return children;
}

function RedirectIfAuth({ children }: { children: JSX.Element }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  if (isAuthenticated) {
    return <Navigate to="/chat" replace />;
  }
  return children;
}

function HomeRedirect() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  return <Navigate to={isAuthenticated ? "/chat" : "/login"} replace />;
}

export const router = createBrowserRouter([
  {
    path: "/",
    element: <HomeRedirect />
  },
  {
    path: "/login",
    element: (
      <RedirectIfAuth>{routePage(<LoginPage />)}</RedirectIfAuth>
    )
  },
  {
    path: "/chat",
    element: (
      <RequireAuth>{routePage(<ChatPage />)}</RequireAuth>
    )
  },
  {
    path: "/chat/:sessionId",
    element: (
      <RequireAuth>{routePage(<ChatPage />)}</RequireAuth>
    )
  },
  {
    path: "/admin",
    element: (
      <RequireAdmin>{routePage(<AdminLayout />)}</RequireAdmin>
    ),
    children: [
      {
        index: true,
        element: <Navigate to="/admin/dashboard" replace />
      },
      {
        path: "dashboard",
        element: routePage(<DashboardPage />)
      },
      {
        path: "knowledge",
        element: routePage(<KnowledgeListPage />)
      },
      {
        path: "knowledge/:kbId",
        element: routePage(<KnowledgeDocumentsPage />)
      },
      {
        path: "knowledge/:kbId/docs/:docId",
        element: routePage(<KnowledgeChunksPage />)
      },
      {
        path: "intent-tree",
        element: routePage(<IntentTreePage />)
      },
      {
        path: "intent-list",
        element: routePage(<IntentListPage />)
      },
      {
        path: "intent-list/:id/edit",
        element: routePage(<IntentEditPage />)
      },
      {
        path: "ingestion",
        element: routePage(<IngestionPage />)
      },
      {
        path: "traces",
        element: routePage(<RagTracePage />)
      },
      {
        path: "traces/:traceId",
        element: routePage(<RagTraceDetailPage />)
      },
      {
        path: "settings",
        element: routePage(<SystemSettingsPage />)
      },
      {
        path: "sample-questions",
        element: routePage(<SampleQuestionPage />)
      },
      {
        path: "mappings",
        element: routePage(<QueryTermMappingPage />)
      },
      {
        path: "users",
        element: routePage(<UserListPage />)
      }
    ]
  },
  {
    path: "*",
    element: routePage(<NotFoundPage />)
  }
]);
