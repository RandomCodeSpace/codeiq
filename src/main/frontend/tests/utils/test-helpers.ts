import { type Page, expect } from '@playwright/test';

// ── Route helpers ────────────────────────────────────────────────────────────

export const ROUTES = {
  dashboard: '/',
  graph: '/graph',
  explorer: '/explorer',
  console: '/console',
  apiDocs: '/api-docs',
} as const;

export type AppRoute = (typeof ROUTES)[keyof typeof ROUTES];

/** Navigate to a route and wait for the main content area to be visible. */
export async function gotoRoute(page: Page, route: AppRoute) {
  await page.goto(route);
  await page.waitForSelector('main', { state: 'visible' });
}

// ── Theme helpers ────────────────────────────────────────────────────────────

/** Returns the current theme: 'dark' | 'light'. */
export async function getTheme(page: Page): Promise<'dark' | 'light'> {
  const cls = await page.locator('html').getAttribute('class') ?? '';
  return cls.includes('dark') ? 'dark' : 'light';
}

/** Click the theme toggle and wait for the class to flip. */
export async function toggleTheme(page: Page) {
  const before = await getTheme(page);
  // Theme toggle button — uses aria-label or data-testid set by the component
  await page.getByRole('button', { name: /toggle theme|switch theme|dark mode|light mode/i }).click();
  await expect(page.locator('html')).toHaveClass(before === 'dark' ? /light/ : /dark/, { timeout: 2000 });
}

// ── API mock helpers ─────────────────────────────────────────────────────────

/** Seed the `/api/stats` mock for deterministic dashboard tests. */
export async function mockStats(page: Page, nodeCount = 1234, edgeCount = 5678) {
  await page.route('**/api/stats', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        totalNodes: nodeCount,
        totalEdges: edgeCount,
        nodesByKind: { endpoint: 10, class: 20, method: 30 },
        edgesByKind: { calls: 100, depends_on: 50 },
        languages: { java: 500, typescript: 200 },
        frameworks: { spring_boot: 300 },
        layers: { backend: 600, frontend: 200, infra: 100, shared: 50, unknown: 284 },
      }),
    })
  );
}

/**
 * Generate a synthetic node list for performance/stress tests.
 * Returns a NodesListResponse-shaped object.
 */
export function generateNodeList(count: number) {
  const nodes = Array.from({ length: count }, (_, i) => ({
    id: `node:file${i % 100}.ts:class:Class${i}`,
    kind: ['class', 'method', 'endpoint', 'entity', 'function'][i % 5],
    name: `Symbol${i}`,
    qualifiedName: `com.example.Symbol${i}`,
    filePath: `src/file${i % 100}.ts`,
    layer: 'backend',
    framework: null,
    properties: {},
  }));
  return { nodes, total: count, offset: 0, limit: count };
}

/** Seed the `/api/kinds` + `/api/nodes` endpoints with synthetic data. */
export async function mockGraphData(page: Page, nodeCount: number) {
  const data = generateNodeList(nodeCount);

  await page.route('**/api/kinds', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        kinds: [
          { kind: 'class', count: Math.floor(nodeCount * 0.3) },
          { kind: 'method', count: Math.floor(nodeCount * 0.3) },
          { kind: 'endpoint', count: Math.floor(nodeCount * 0.15) },
          { kind: 'entity', count: Math.floor(nodeCount * 0.15) },
          { kind: 'function', count: Math.floor(nodeCount * 0.1) },
        ],
      }),
    })
  );

  await page.route('**/api/nodes**', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(data),
    })
  );

  await page.route('**/api/topology', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        services: [
          { name: 'api-service', nodeCount: Math.floor(nodeCount / 3), dependencies: ['db-service'] },
          { name: 'db-service', nodeCount: Math.floor(nodeCount / 3), dependencies: [] },
          { name: 'frontend-service', nodeCount: Math.floor(nodeCount / 3), dependencies: ['api-service'] },
        ],
      }),
    })
  );
}

// ── Graph interaction helpers ────────────────────────────────────────────────

/** Wait for the G6 graph canvas to finish rendering (listens for afterrender event). */
export async function waitForGraphRender(page: Page, timeoutMs = 5000) {
  await page.waitForFunction(
    () => {
      // G6 v5 sets a data attribute on the container when rendering is complete
      const container = document.querySelector('[data-testid="graph-container"]');
      return container?.getAttribute('data-render-state') === 'ready';
    },
    { timeout: timeoutMs }
  );
}

/** Returns the elapsed ms from when the route handler returns data to graph-ready state. */
export async function measureGraphRenderTime(page: Page): Promise<number> {
  return page.evaluate(() => {
    return (window as Window & { __graphRenderMs?: number }).__graphRenderMs ?? -1;
  });
}

// ── Keyboard navigation helpers ──────────────────────────────────────────────

/** Tab through all interactive elements and collect focused element labels. */
export async function collectTabOrder(page: Page, maxTabs = 50): Promise<string[]> {
  const labels: string[] = [];
  for (let i = 0; i < maxTabs; i++) {
    await page.keyboard.press('Tab');
    const focused = await page.evaluate(() => {
      const el = document.activeElement;
      if (!el) return null;
      return (
        el.getAttribute('aria-label') ??
        el.getAttribute('title') ??
        (el as HTMLElement).innerText?.slice(0, 40) ??
        el.tagName
      );
    });
    if (!focused || labels.includes(focused)) break;
    labels.push(focused);
  }
  return labels;
}
