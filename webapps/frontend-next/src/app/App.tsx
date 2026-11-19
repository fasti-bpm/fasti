/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useEffect, useMemo } from 'react';
import type { ReactNode } from 'react';
import {
  Navigate,
  Outlet,
  RouterProvider,
  Link,
  createHashRouter,
  useLocation,
  useParams,
  useRouteError,
} from 'react-router';
import type { RouteObject } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Tooltip } from 'radix-ui';
import { SearchX } from 'lucide-react';
import { isApiError } from '../api/http';
import { I18nProvider, useI18n, type Locale } from '../i18n';
import { pluginsFor } from '../plugins/registry';
import { ThemeProvider } from '../theme/theme';
import { EmptyState, ErrorState, Loading } from '../ui';
import { ToastProvider } from '../ui/toast';
import { LoginPage } from './LoginPage';
import { prefetchOnIntent } from './prefetch';
import { Shell } from './Shell';
import { SessionContext, useSession, useSessionQuery } from './session';

function RequireSession({ children }: { children: ReactNode }) {
  const { t } = useI18n();
  const session = useSessionQuery();
  const location = useLocation();
  if (session.isPending) {
    return <Loading label={t('state.loading')} />;
  }
  if (session.isError) {
    return <ErrorState error={session.error} onRetry={() => void session.refetch()} />;
  }
  if (!session.data) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  return <SessionContext.Provider value={session.data}>{children}</SessionContext.Provider>;
}

/** Pages of an application are only shown to users who may use it. */
function RequireApp({ app }: { app: string }) {
  const session = useSession();
  return session.authorizedApps.includes(app) ? <Outlet /> : <Navigate to="/" replace />;
}

function NotFound() {
  const { t } = useI18n();
  return (
    <EmptyState
      icon={SearchX}
      title={t('error.pageNotFound')}
      action={
        <Link className="link" to="/">
          {t('nav.home')}
        </Link>
      }
    />
  );
}

function RouteError() {
  const error = useRouteError();
  return <ErrorState error={error} onRetry={() => window.location.reload()} />;
}

function PluginPage() {
  const { pluginId } = useParams();
  const plugin = pluginsFor('shell.navigation').find((candidate) => candidate.id === pluginId);
  if (!plugin) {
    return <NotFound />;
  }
  const Component = plugin.component;
  return <Component />;
}

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    // the first administrator of an engine without users; the server decides when
    path: '/setup',
    lazy: async () => ({ Component: (await import('../admin/SetupPage')).SetupPage }),
  },
  {
    path: '/',
    element: (
      <RequireSession>
        <Shell />
      </RequireSession>
    ),
    errorElement: <RouteError />,
    children: [
      {
        // the landing page: the applications of the user, the own profile, quick links
        index: true,
        lazy: async () => ({ Component: (await import('../welcome/WelcomePage')).WelcomePage }),
      },
      {
        // pages of the Tasklist; the API enforces the permissions on the data
        element: <RequireApp app="tasklist" />,
        children: [
          {
            path: 'tasklist',
            lazy: async () => ({
              Component: (await import('../tasklist/TasklistPage')).TasklistPage,
            }),
          },
          {
            path: 'processes',
            lazy: async () => ({
              Component: (await import('../processes/ProcessesPage')).ProcessesPage,
            }),
          },
        ],
      },
      {
        // pages of the monitoring section; the API enforces the permissions on the data
        element: <RequireApp app="cockpit" />,
        children: [
          {
            path: 'cockpit',
            lazy: async () => ({
              Component: (await import('../cockpit/CockpitLayout')).CockpitLayout,
            }),
            children: [
              {
                index: true,
                lazy: async () => ({
                  Component: (await import('../cockpit/DashboardPage')).DashboardPage,
                }),
              },
              {
                path: 'processes',
                lazy: async () => ({
                  Component: (await import('../cockpit/DefinitionsPage')).DefinitionsPage,
                }),
              },
              {
                path: 'processes/definition/:id',
                lazy: async () => ({
                  Component: (await import('../cockpit/DefinitionPage')).DefinitionPage,
                }),
              },
              {
                path: 'processes/key/:key',
                lazy: async () => ({
                  Component: (await import('../cockpit/DefinitionPage')).DefinitionByKeyPage,
                }),
              },
              {
                path: 'processes/instance/:id',
                lazy: async () => ({
                  Component: (await import('../cockpit/InstancePage')).InstancePage,
                }),
              },
              {
                path: 'incidents',
                lazy: async () => ({
                  Component: (await import('../cockpit/IncidentsPage')).IncidentsPage,
                }),
              },
              {
                path: 'workload',
                lazy: async () => ({
                  Component: (await import('../cockpit/WorkloadPage')).WorkloadPage,
                }),
              },
              {
                path: 'jobs',
                lazy: async () => ({
                  Component: (await import('../cockpit/FailedJobsPage')).FailedJobsPage,
                }),
              },
              {
                path: 'modeler',
                lazy: async () => ({
                  Component: (await import('../modeler/ModelerPage')).ModelerPage,
                }),
              },
              {
                path: 'external-tasks',
                lazy: async () => ({
                  Component: (await import('../cockpit/ExternalTasksPage')).ExternalTasksPage,
                }),
              },
              {
                path: 'deployments',
                lazy: async () => ({
                  Component: (await import('../cockpit/DeploymentsPage')).DeploymentsPage,
                }),
              },
              {
                path: 'deployments/:id',
                lazy: async () => ({
                  Component: (await import('../cockpit/DeploymentsPage')).DeploymentPage,
                }),
              },
              {
                path: 'decisions',
                lazy: async () => ({
                  Component: (await import('../cockpit/DecisionsPage')).DecisionsPage,
                }),
              },
              {
                path: 'decisions/definition/:id',
                lazy: async () => ({
                  Component: (await import('../cockpit/DecisionsPage')).DecisionPage,
                }),
              },
              {
                path: 'history',
                lazy: async () => ({
                  Component: (await import('../cockpit/HistoryPage')).HistoryPage,
                }),
              },
              {
                path: 'history/instance/:id',
                lazy: async () => ({
                  Component: (await import('../cockpit/HistoryPage')).HistoricInstancePage,
                }),
              },
              {
                path: 'migration',
                lazy: async () => ({
                  Component: (await import('../cockpit/MigrationPage')).MigrationPage,
                }),
              },
              {
                path: 'decision-history',
                lazy: async () => ({
                  Component: (await import('../cockpit/DecisionHistoryPage')).DecisionHistoryPage,
                }),
              },
              {
                path: 'decision-history/:id',
                lazy: async () => ({
                  Component: (await import('../cockpit/DecisionHistoryPage')).DecisionInstancePage,
                }),
              },
              {
                path: 'audit',
                lazy: async () => ({
                  Component: (await import('../cockpit/AuditPage')).AuditPage,
                }),
              },
              {
                path: 'cleanup',
                lazy: async () => ({
                  Component: (await import('../cockpit/CleanupPage')).CleanupPage,
                }),
              },
              {
                path: 'batches',
                lazy: async () => ({
                  Component: (await import('../cockpit/BatchesPage')).BatchesPage,
                }),
              },
            ],
          },
        ],
      },
      {
        // pages of the administration; the API enforces the permissions on the data
        element: <RequireApp app="admin" />,
        children: [
          {
            path: 'admin',
            lazy: async () => ({
              Component: (await import('../admin/AdminLayout')).AdminLayout,
            }),
            children: [
              { index: true, element: <Navigate to="users" replace /> },
              {
                path: 'users',
                lazy: async () => ({
                  Component: (await import('../admin/UsersPage')).UsersPage,
                }),
              },
              {
                path: 'users/:id',
                lazy: async () => ({
                  Component: (await import('../admin/UsersPage')).UserPage,
                }),
              },
              {
                path: 'groups',
                lazy: async () => ({
                  Component: (await import('../admin/GroupsPage')).GroupsPage,
                }),
              },
              {
                path: 'groups/:id',
                lazy: async () => ({
                  Component: (await import('../admin/GroupsPage')).GroupPage,
                }),
              },
              {
                path: 'tenants',
                lazy: async () => ({
                  Component: (await import('../admin/TenantsPage')).TenantsPage,
                }),
              },
              {
                path: 'tenants/:id',
                lazy: async () => ({
                  Component: (await import('../admin/TenantsPage')).TenantPage,
                }),
              },
              {
                path: 'authorizations',
                lazy: async () => ({
                  Component: (await import('../admin/AuthorizationsPage')).AuthorizationsPage,
                }),
              },
              {
                path: 'system',
                lazy: async () => ({
                  Component: (await import('../admin/SystemPage')).SystemPage,
                }),
              },
            ],
          },
        ],
      },
      {
        path: 'preferences',
        lazy: async () => ({ Component: (await import('./PreferencesPage')).PreferencesPage }),
      },
      { path: 'plugin/:pluginId', element: <PluginPage /> },
      { path: '*', element: <NotFound /> },
    ],
  },
];

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 15_000,
        refetchOnWindowFocus: true,
        // a missing session or permission does not change by asking again
        retry: (failures, error) =>
          failures < 2 && (isApiError(error, 'network') || isApiError(error, 'server')),
      },
    },
  });
}

export function Providers({
  children,
  queryClient,
  locale,
}: {
  children: ReactNode;
  queryClient: QueryClient;
  locale?: Locale;
}) {
  return (
    <QueryClientProvider client={queryClient}>
      <I18nProvider initial={locale}>
        <ThemeProvider>
          <Tooltip.Provider delayDuration={400}>
            <ToastProvider>{children}</ToastProvider>
          </Tooltip.Provider>
        </ThemeProvider>
      </I18nProvider>
    </QueryClientProvider>
  );
}

export function App() {
  const queryClient = useMemo(createQueryClient, []);
  const router = useMemo(() => createHashRouter(routes), []);
  useEffect(() => prefetchOnIntent(routes), []);
  return (
    <Providers queryClient={queryClient}>
      <RouterProvider router={router} />
    </Providers>
  );
}
