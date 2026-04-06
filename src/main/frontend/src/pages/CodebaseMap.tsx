import { useState, useMemo, useCallback } from 'react';
import { Typography, Spin, Alert, Drawer } from 'antd';
import ReactECharts from 'echarts-for-react';
import { useApi } from '@/hooks/useApi';
import { api } from '@/lib/api';
import { useTheme } from '@/context/ThemeContext';
import type { FileTreeResponse, FileTreeNode } from '@/types/api';

const LANG_COLORS: Record<string, string> = {
  java: '#b07219', python: '#3572A5', typescript: '#3178c6', javascript: '#f1e05a',
  go: '#00ADD8', csharp: '#178600', rust: '#dea584', kotlin: '#A97BFF',
  yaml: '#cb171e', json: '#555', ruby: '#701516', scala: '#c22d40',
  cpp: '#f34b7d', shell: '#89e051', markdown: '#083fa1', html: '#e34c26',
  css: '#563d7c', sql: '#e38c00', proto: '#60a0b0', dockerfile: '#384d54',
  other: '#888',
};

const EXT_TO_LANG: Record<string, string> = {
  java: 'java', py: 'python', ts: 'typescript', tsx: 'typescript',
  js: 'javascript', jsx: 'javascript', go: 'go', cs: 'csharp',
  rs: 'rust', kt: 'kotlin', yml: 'yaml', yaml: 'yaml',
  json: 'json', rb: 'ruby', scala: 'scala', cpp: 'cpp',
  h: 'cpp', sh: 'shell', md: 'markdown', html: 'html',
  css: 'css', sql: 'sql', proto: 'proto',
};

interface EChartsTreeNode {
  name: string;
  value?: number;
  children?: EChartsTreeNode[];
  itemStyle?: { color: string };
}

function inferLang(name: string): string {
  const ext = name.split('.').pop()?.toLowerCase() ?? '';
  return EXT_TO_LANG[ext] ?? 'other';
}

function dominantLang(nodes: FileTreeNode[]): string {
  const counts: Record<string, number> = {};
  function walk(items: FileTreeNode[]) {
    for (const item of items) {
      if (item.type === 'file') {
        const lang = inferLang(item.name);
        counts[lang] = (counts[lang] ?? 0) + (item.nodeCount || 1);
      }
      if (item.children) walk(item.children);
    }
  }
  walk(nodes);
  return Object.entries(counts).sort((a, b) => b[1] - a[1])[0]?.[0] ?? 'other';
}

function collapseTree(nodes: FileTreeNode[]): FileTreeNode[] {
  return nodes.map(n => {
    if (n.type !== 'directory' || !n.children || n.children.length === 0) return n;

    let current = n;
    let collapsedName = n.name;
    while (
      current.type === 'directory' &&
      current.children &&
      current.children.length === 1 &&
      current.children[0].type === 'directory' &&
      current.children[0].children &&
      current.children[0].children.length > 0
    ) {
      current = current.children[0];
      collapsedName += '/' + current.name;
    }

    const collapsedChildren = collapseTree(current.children ?? []);
    return { ...current, name: collapsedName, children: collapsedChildren, nodeCount: n.nodeCount };
  });
}

function toEChartsNodes(nodes: FileTreeNode[]): EChartsTreeNode[] {
  const result: EChartsTreeNode[] = [];
  for (const n of nodes) {
    if (n.nodeCount <= 0 && (!n.children || n.children.length === 0)) continue;

    if (n.type === 'directory' && n.children && n.children.length > 0) {
      const children = toEChartsNodes(n.children);
      if (children.length === 0) continue;
      const lang = dominantLang(n.children);
      result.push({
        name: n.name,
        children,
        itemStyle: { color: LANG_COLORS[lang] ?? '#666' },
      });
    } else {
      const lang = inferLang(n.name);
      result.push({
        name: n.name,
        value: Math.max(n.nodeCount, 1),
        itemStyle: { color: LANG_COLORS[lang] ?? '#666' },
      });
    }
  }
  return result;
}

function fileTreeToECharts(nodes: FileTreeNode[]): EChartsTreeNode[] {
  return toEChartsNodes(collapseTree(nodes));
}

export default function CodebaseMap() {
  const { isDark } = useTheme();
  const [fileDrawer, setFileDrawer] = useState<{ path: string; content: string } | null>(null);
  const [fileLoading, setFileLoading] = useState(false);

  const { data: treeData, loading, error } = useApi<FileTreeResponse>(
    () => api.getFileTree(), []
  );

  const tree = treeData?.tree ?? [];
  const totalFiles = treeData?.total_files ?? 0;
  const treemapData = useMemo(() => fileTreeToECharts(tree), [tree]);

  // On click: if leaf node (no children), open file in drawer
  const onClickNode = useCallback(async (params: {
    data?: { children?: unknown[] };
    treePathInfo?: Array<{ name: string }>;
  }) => {
    if (params.data?.children && (params.data.children as unknown[]).length > 0) return;
    const pathParts = params.treePathInfo?.map(p => p.name).filter(Boolean) ?? [];
    if (pathParts.length === 0) return;
    const filePath = pathParts.join('/');
    setFileLoading(true);
    try {
      const content = await api.readFile(filePath);
      setFileDrawer({ path: filePath, content });
    } catch {
      setFileDrawer({ path: filePath, content: '// Could not load file' });
    } finally {
      setFileLoading(false);
    }
  }, []);

  const onEvents = useMemo(() => ({
    click: onClickNode,
  }), [onClickNode]);

  const chartOption = useMemo(() => ({
    tooltip: {
      formatter: (info: { name: string; value: number; treePathInfo?: Array<{ name: string }> }) => {
        const path = info.treePathInfo?.map(p => p.name).filter(Boolean).join('/') ?? info.name;
        return `<b>${path}</b><br/>Nodes: ${(info.value ?? 0).toLocaleString()}`;
      },
    },
    series: [{
      type: 'treemap',
      data: treemapData,
      top: 0,
      left: 0,
      right: 0,
      bottom: 0,
      width: '100%',
      height: '100%',
      leafDepth: 2,
      drillDownIcon: '▶ ',
      roam: false,
      nodeClick: 'zoomToNode',
      breadcrumb: {
        show: true,
        bottom: 8,
        left: 'center',
        height: 28,
        itemStyle: {
          color: isDark ? '#1f1f1f' : '#fff',
          borderColor: isDark ? '#444' : '#bbb',
          borderWidth: 1,
          shadowBlur: 3,
          shadowColor: isDark ? 'rgba(0,0,0,0.5)' : 'rgba(0,0,0,0.15)',
        },
        textStyle: {
          color: isDark ? '#e0e0e0' : '#333',
          fontSize: 14,
          fontWeight: 'bold' as const,
        },
      },
      levels: [
        {
          itemStyle: {
            borderColor: isDark ? '#303030' : '#bbb',
            borderWidth: 3,
            gapWidth: 3,
          },
          upperLabel: {
            show: true,
            height: 30,
            color: isDark ? '#e0e0e0' : '#333',
            fontSize: 14,
            fontWeight: 'bold' as const,
          },
        },
        {
          itemStyle: {
            borderColor: isDark ? '#404040' : '#ccc',
            borderWidth: 2,
            gapWidth: 2,
          },
          upperLabel: {
            show: true,
            height: 24,
            fontSize: 12,
            color: isDark ? '#ccc' : '#555',
          },
        },
        {
          itemStyle: {
            borderColor: isDark ? '#4a4a4a' : '#ddd',
            borderWidth: 1,
            gapWidth: 1,
          },
          label: { show: true, fontSize: 11 },
        },
      ],
      label: {
        show: true,
        formatter: '{b}',
        fontSize: 12,
        color: isDark ? '#ddd' : '#333',
      },
    }],
  }), [treemapData, isDark]);

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 80 }}><Spin size="large" /></div>;
  }

  if (error) {
    return <Alert type="error" message="Failed to load codebase data" description={error} showIcon style={{ margin: 24 }} />;
  }

  return (
    <div style={{ position: 'relative', height: 'calc(100vh - 64px)', margin: '-16px -24px' }}>
      {treemapData.length > 0 ? (
        <>
          <div style={{
            position: 'absolute',
            top: 6,
            right: 10,
            zIndex: 10,
            background: isDark ? 'rgba(10,10,10,0.8)' : 'rgba(255,255,255,0.85)',
            borderRadius: 4,
            padding: '2px 10px',
            fontSize: 12,
            color: isDark ? '#888' : '#999',
          }}>
            {totalFiles.toLocaleString()} files
          </div>
          <ReactECharts
            option={chartOption}
            style={{ height: '100%', width: '100%' }}
            theme={isDark ? 'dark' : undefined}
            opts={{ renderer: 'canvas' }}
            onEvents={onEvents}
          />
        </>
      ) : (
        <div style={{ textAlign: 'center', padding: 60 }}>
          <Typography.Text type="secondary">No file data available. Run index + enrich first.</Typography.Text>
        </div>
      )}

      <Drawer
        title={fileDrawer?.path}
        placement="right"
        width="60%"
        open={!!fileDrawer}
        onClose={() => setFileDrawer(null)}
        styles={{ body: { padding: 0 } }}
      >
        {fileLoading ? (
          <div style={{ textAlign: 'center', padding: 40 }}><Spin /></div>
        ) : (
          <pre style={{
            margin: 0,
            padding: 16,
            fontSize: 13,
            lineHeight: 1.5,
            overflow: 'auto',
            height: '100%',
            background: isDark ? '#0a0a0a' : '#fafafa',
            color: isDark ? '#d4d4d4' : '#1f1f1f',
          }}>
            {fileDrawer?.content}
          </pre>
        )}
      </Drawer>
    </div>
  );
}
