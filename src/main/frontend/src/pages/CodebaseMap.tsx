import { useState, useMemo } from 'react';
import { Select, Typography, Space, Spin, Alert } from 'antd';
import ReactECharts from 'echarts-for-react';
import { useApi } from '@/hooks/useApi';
import { api } from '@/lib/api';
import { useTheme } from '@/context/ThemeContext';
import type { NodesListResponse } from '@/types/api';

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

interface TreeNode {
  name: string;
  path: string;
  value: number;
  children?: TreeNode[];
  itemStyle?: { color: string };
}

function buildTreemap(
  nodes: Array<{ file_path?: string; label: string; properties?: Record<string, unknown> }>,
): TreeNode[] {
  // Build a nested directory tree
  const root: Record<string, { count: number; langs: Record<string, number>; children: Record<string, unknown> }> = {};

  for (const node of nodes) {
    const fp = node.file_path;
    if (!fp) continue;

    const parts = fp.split('/');
    const ext = fp.split('.').pop()?.toLowerCase() ?? '';
    const lang = EXT_TO_LANG[ext] ?? 'other';

    // Walk/create directory path
    let current = root;
    for (let i = 0; i < parts.length - 1; i++) {
      const dir = parts[i];
      if (!current[dir]) {
        current[dir] = { count: 0, langs: {}, children: {} };
      }
      current[dir].count++;
      current[dir].langs[lang] = (current[dir].langs[lang] ?? 0) + 1;
      current = current[dir].children as typeof root;
    }
  }

  function toTreeNodes(map: typeof root, parentPath: string): TreeNode[] {
    return Object.entries(map)
      .sort((a, b) => b[1].count - a[1].count)
      .map(([name, data]) => {
        const path = parentPath ? `${parentPath}/${name}` : name;
        const dominantLang = Object.entries(data.langs).sort((a, b) => b[1] - a[1])[0]?.[0] ?? 'other';
        const children = toTreeNodes(data.children as typeof root, path);

        if (children.length > 0) {
          return {
            name,
            path,
            value: data.count,
            children,
            itemStyle: { color: LANG_COLORS[dominantLang] ?? '#888' },
          };
        }
        return {
          name,
          path,
          value: data.count,
          itemStyle: { color: LANG_COLORS[dominantLang] ?? '#888' },
        };
      });
  }

  return toTreeNodes(root, '');
}

export default function CodebaseMap() {
  const { isDark } = useTheme();
  const [langFilter, setLangFilter] = useState<string | undefined>(undefined);
  const { data: allNodesData, loading, error } = useApi<NodesListResponse>(
    () => api.getNodes(undefined, undefined, 10000, 0), []
  );

  const nodes = useMemo(() => {
    const all = allNodesData?.nodes ?? [];
    return all.filter(n => n.file_path);
  }, [allNodesData]);

  const uniqueLangs = useMemo(() => {
    const langs = new Set<string>();
    for (const n of nodes) {
      const ext = n.file_path?.split('.').pop()?.toLowerCase() ?? '';
      const lang = EXT_TO_LANG[ext];
      if (lang) langs.add(lang);
    }
    return Array.from(langs).sort();
  }, [nodes]);

  const filteredNodes = useMemo(() => {
    if (!langFilter) return nodes;
    const matchExts = Object.entries(EXT_TO_LANG)
      .filter(([, lang]) => lang === langFilter)
      .map(([ext]) => ext);
    return nodes.filter(n => {
      const ext = n.file_path?.split('.').pop()?.toLowerCase() ?? '';
      return matchExts.includes(ext);
    });
  }, [nodes, langFilter]);

  const treemapData = useMemo(() => buildTreemap(filteredNodes), [filteredNodes]);

  const chartOption = useMemo(() => ({
    tooltip: {
      formatter: (info: { name: string; value: number; treePathInfo?: Array<{ name: string }> }) => {
        const path = info.treePathInfo?.map(p => p.name).filter(Boolean).join('/') ?? info.name;
        return `<b>${path}</b><br/>Nodes: ${info.value?.toLocaleString()}`;
      },
    },
    series: [{
      type: 'treemap',
      data: treemapData,
      roam: 'move',
      leafDepth: 2,
      drillDownIcon: '▶',
      breadcrumb: {
        show: true,
        top: 0,
        left: 0,
        itemStyle: {
          color: isDark ? '#1f1f38' : '#f5f5f5',
          borderColor: isDark ? '#2d2d4a' : '#d9d9d9',
          textStyle: { color: isDark ? '#e0e0e0' : '#333' },
        },
      },
      levels: [
        {
          itemStyle: {
            borderColor: isDark ? '#444' : '#ccc',
            borderWidth: 2,
            gapWidth: 2,
          },
          upperLabel: {
            show: true,
            height: 28,
            color: isDark ? '#e0e0e0' : '#333',
            fontSize: 13,
            fontWeight: 'bold' as const,
          },
        },
        {
          itemStyle: {
            borderColor: isDark ? '#555' : '#ddd',
            borderWidth: 1,
            gapWidth: 1,
          },
          upperLabel: {
            show: true,
            height: 22,
            fontSize: 12,
          },
        },
        {
          itemStyle: {
            borderColor: isDark ? '#666' : '#eee',
            borderWidth: 0.5,
            gapWidth: 0.5,
          },
          label: {
            show: true,
            fontSize: 11,
          },
        },
      ],
      label: {
        show: true,
        formatter: '{b}',
        fontSize: 12,
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
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 96px)', margin: '-16px -24px', padding: '8px 16px 0' }}>
      {/* Top bar: title + filter */}
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: 8,
        flexShrink: 0,
      }}>
        <Space>
          <Typography.Title level={4} style={{ margin: 0 }}>Codebase Map</Typography.Title>
          <Typography.Text type="secondary">
            {filteredNodes.length.toLocaleString()} nodes · {uniqueLangs.length} languages
          </Typography.Text>
        </Space>
        <Select
          allowClear
          placeholder="Filter by language"
          style={{ width: 180 }}
          value={langFilter}
          onChange={setLangFilter}
          options={uniqueLangs.map(l => ({ label: l.charAt(0).toUpperCase() + l.slice(1), value: l }))}
        />
      </div>

      {/* Treemap fills remaining space */}
      <div style={{ flex: 1, minHeight: 0 }}>
        {treemapData.length > 0 ? (
          <ReactECharts
            option={chartOption}
            style={{ height: '100%', width: '100%' }}
            theme={isDark ? 'dark' : undefined}
            opts={{ renderer: 'canvas' }}
          />
        ) : (
          <div style={{ textAlign: 'center', padding: 60 }}>
            <Typography.Text type="secondary">No file data available. Run index + enrich first.</Typography.Text>
          </div>
        )}
      </div>
    </div>
  );
}
