import { useState, useMemo, useCallback } from 'react';
import {
  Card, Col, Row, Statistic, Spin, Alert, Typography, Modal, Table, Drawer, Menu,
  Form, Input, InputNumber, Select, Button, Space, Tag,
} from 'antd';
import {
  NodeIndexOutlined, BranchesOutlined, FileOutlined, CodeOutlined,
  ApiOutlined, SafetyOutlined, AppstoreOutlined, BuildOutlined,
  PlayCircleOutlined, ClockCircleOutlined, SearchOutlined,
  BarChartOutlined, ThunderboltOutlined, SafetyCertificateOutlined,
  HistoryOutlined,
} from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import { useApi } from '@/hooks/useApi';
import { api } from '@/lib/api';
import { useTheme } from '@/context/ThemeContext';
import { TOOLS, CATEGORIES, toolsByCategory, type McpTool } from '@/lib/mcp-tools';
import type { StatsResponse, FileTreeResponse, FileTreeNode } from '@/types/api';

// ── Stats ──

function flattenToRecord(val: unknown): Record<string, number> {
  if (!val || typeof val !== 'object') return {};
  const result: Record<string, number> = {};
  for (const [k, v] of Object.entries(val as Record<string, unknown>)) {
    if (typeof v === 'number') result[k] = v;
    else if (typeof v === 'object' && v !== null && !Array.isArray(v))
      for (const [k2, v2] of Object.entries(v as Record<string, unknown>))
        if (typeof v2 === 'number') result[`${k}/${k2}`] = v2;
  }
  return result;
}

function sumValues(rec: Record<string, number>): number {
  return Object.values(rec).reduce((a, b) => a + b, 0);
}

function StatCard({ title, value, icon, detail, detailTitle }: {
  title: string; value: number | string; icon: React.ReactNode;
  detail?: Record<string, number>; detailTitle?: string;
}) {
  const [open, setOpen] = useState(false);
  const hasDetail = detail && Object.keys(detail).length > 0 && sumValues(detail) > 0;
  const tableData = hasDetail
    ? Object.entries(detail!).sort((a, b) => b[1] - a[1]).map(([name, count]) => ({ key: name, name, count }))
    : [];
  return (
    <>
      <Card hoverable={!!hasDetail} onClick={() => hasDetail && setOpen(true)}
        style={{ cursor: hasDetail ? 'pointer' : 'default', height: '100%' }} size="small">
        <Statistic title={title} value={value} prefix={icon} valueStyle={{ fontSize: 18 }} />
      </Card>
      <Modal title={detailTitle ?? title} open={open} onCancel={() => setOpen(false)} footer={null} width={600}>
        <Table dataSource={tableData} pagination={tableData.length > 15 ? { pageSize: 15 } : false} size="small"
          columns={[
            { title: 'Name', dataIndex: 'name', key: 'name' },
            { title: 'Count', dataIndex: 'count', key: 'count', align: 'right' as const, render: (v: number) => v.toLocaleString() },
          ]} />
      </Modal>
    </>
  );
}

function isComputedStats(s: StatsResponse): s is StatsResponse & {
  graph: { nodes: number; edges: number; files: number };
  languages: Record<string, number>; frameworks: Record<string, number>;
  connections?: unknown; auth?: unknown; architecture?: unknown;
} { return 'graph' in s; }

// ── Treemap ──

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

interface EChartsTreeNode { name: string; value?: number; children?: EChartsTreeNode[]; itemStyle?: { color: string } }

function inferLang(name: string): string {
  return EXT_TO_LANG[name.split('.').pop()?.toLowerCase() ?? ''] ?? 'other';
}

function dominantLang(nodes: FileTreeNode[]): string {
  const counts: Record<string, number> = {};
  (function walk(items: FileTreeNode[]) {
    for (const item of items) {
      if (item.type === 'file') { const l = inferLang(item.name); counts[l] = (counts[l] ?? 0) + (item.nodeCount || 1); }
      if (item.children) walk(item.children);
    }
  })(nodes);
  return Object.entries(counts).sort((a, b) => b[1] - a[1])[0]?.[0] ?? 'other';
}

function collapseTree(nodes: FileTreeNode[]): FileTreeNode[] {
  return nodes.map(n => {
    if (n.type !== 'directory' || !n.children || n.children.length === 0) return n;
    let current = n, collapsedName = n.name;
    while (current.type === 'directory' && current.children && current.children.length === 1
      && current.children[0].type === 'directory' && current.children[0].children && current.children[0].children.length > 0) {
      current = current.children[0]; collapsedName += '/' + current.name;
    }
    return { ...current, name: collapsedName, children: collapseTree(current.children ?? []), nodeCount: n.nodeCount };
  });
}

function toEChartsNodes(nodes: FileTreeNode[]): EChartsTreeNode[] {
  const result: EChartsTreeNode[] = [];
  for (const n of nodes) {
    if (n.nodeCount <= 0 && (!n.children || n.children.length === 0)) continue;
    if (n.type === 'directory' && n.children && n.children.length > 0) {
      const children = toEChartsNodes(n.children);
      if (children.length === 0) continue;
      result.push({ name: n.name, children, itemStyle: { color: LANG_COLORS[dominantLang(n.children)] ?? '#666' } });
    } else {
      result.push({ name: n.name, value: Math.max(n.nodeCount, 1), itemStyle: { color: LANG_COLORS[inferLang(n.name)] ?? '#666' } });
    }
  }
  return result;
}

// ── MCP ──

const CATEGORY_ICONS: Record<string, React.ReactNode> = {
  stats: <BarChartOutlined />, query: <SearchOutlined />, topology: <BranchesOutlined />,
  flow: <AppstoreOutlined />, analysis: <ThunderboltOutlined />, security: <SafetyCertificateOutlined />,
  code: <CodeOutlined />,
};

function resolveUrl(tool: McpTool, params: Record<string, string>): string {
  return typeof tool.url === 'function' ? tool.url(params) : tool.url;
}

function countResults(json: unknown): number | null {
  if (Array.isArray(json)) return json.length;
  if (json && typeof json === 'object') {
    const obj = json as Record<string, unknown>;
    for (const k of ['nodes', 'services', 'kinds']) if (Array.isArray(obj[k])) return (obj[k] as unknown[]).length;
  }
  return null;
}

// ── Main ──

export default function Dashboard() {
  const { isDark } = useTheme();

  // Stats
  const { data: stats, loading: statsLoading, error: statsError } = useApi(() => api.getStats(), []);
  const { data: kinds } = useApi(() => api.getKinds(), []);
  const { data: detailed } = useApi(() => api.getDetailedStats('all'), []);
  const computed = stats && isComputedStats(stats) ? stats : null;
  const qs = stats && !isComputedStats(stats) ? stats as { node_count: number; edge_count: number } : null;
  const nodeCount = computed?.graph?.nodes ?? qs?.node_count ?? 0;
  const edgeCount = computed?.graph?.edges ?? qs?.edge_count ?? 0;
  const fileCount = computed?.graph?.files ?? 0;
  const languages = flattenToRecord(computed?.languages);
  const frameworks = flattenToRecord(computed?.frameworks);
  const connections = flattenToRecord(computed?.connections);
  const auth = flattenToRecord(computed?.auth);
  const architecture = flattenToRecord(computed?.architecture);
  const nodeKindBreakdown: Record<string, number> = {};
  if (kinds?.kinds) for (const k of kinds.kinds) nodeKindBreakdown[k.kind] = k.count;
  const edgeKindBreakdown: Record<string, number> = {};
  if (computed) {
    const g = (computed as unknown as Record<string, unknown>).graph as Record<string, unknown> | undefined;
    if (g?.edges_by_kind && typeof g.edges_by_kind === 'object') Object.assign(edgeKindBreakdown, flattenToRecord(g.edges_by_kind));
  }
  if (detailed && typeof detailed === 'object' && Object.keys(edgeKindBreakdown).length === 0) {
    const g = (detailed as Record<string, unknown>).graph as Record<string, unknown> | undefined;
    if (g?.edges_by_kind && typeof g.edges_by_kind === 'object') Object.assign(edgeKindBreakdown, flattenToRecord(g.edges_by_kind));
  }

  // Treemap
  const { data: treeData, loading: treeLoading } = useApi<FileTreeResponse>(() => api.getFileTree(), []);
  const treemapData = useMemo(() => toEChartsNodes(collapseTree(treeData?.tree ?? [])), [treeData]);

  // File viewer — dblclick only (single click = treemap navigate)
  const [fileDrawer, setFileDrawer] = useState<{ path: string; content: string } | null>(null);
  const [fileLoading, setFileLoading] = useState(false);
  const onDblClickNode = useCallback(async (params: { data?: { children?: unknown[] }; treePathInfo?: Array<{ name: string }> }) => {
    if (params.data?.children && (params.data.children as unknown[]).length > 0) return;
    const pathParts = params.treePathInfo?.map(p => p.name).filter(Boolean) ?? [];
    if (pathParts.length === 0) return;
    const filePath = pathParts.join('/');
    setFileLoading(true);
    try { setFileDrawer({ path: filePath, content: await api.readFile(filePath) }); }
    catch { setFileDrawer({ path: filePath, content: '// Could not load file' }); }
    finally { setFileLoading(false); }
  }, []);
  const treemapEvents = useMemo(() => ({ dblclick: onDblClickNode }), [onDblClickNode]);

  const chartOption = useMemo(() => ({
    tooltip: {
      formatter: (info: { name: string; value: number; treePathInfo?: Array<{ name: string }> }) => {
        const path = info.treePathInfo?.map(p => p.name).filter(Boolean).join('/') ?? info.name;
        return `<b>${path}</b><br/>Nodes: ${(info.value ?? 0).toLocaleString()}<br/><i style="color:#888">Double-click to view source</i>`;
      },
    },
    series: [{
      type: 'treemap', data: treemapData, top: 0, left: 0, right: 0, bottom: 0, width: '100%', height: '100%',
      leafDepth: 2, drillDownIcon: '▶ ', roam: false, nodeClick: 'zoomToNode',
      breadcrumb: {
        show: true, bottom: 8, left: 'center', height: 28,
        itemStyle: {
          color: isDark ? '#000' : '#1a1a1a',
          borderColor: isDark ? '#555' : '#333',
          borderWidth: 1,
          shadowBlur: 6,
          shadowColor: 'rgba(0,0,0,0.4)',
          borderRadius: 4,
        },
        textStyle: { color: '#fff', fontSize: 13, fontWeight: 'bold' as const },
        emphasis: {
          itemStyle: { color: isDark ? '#1a1a1a' : '#333' },
          textStyle: { color: '#fff' },
        },
      },
      levels: [
        { itemStyle: { borderColor: isDark ? '#303030' : '#bbb', borderWidth: 3, gapWidth: 3 },
          upperLabel: { show: true, height: 28, color: isDark ? '#e0e0e0' : '#333', fontSize: 13, fontWeight: 'bold' as const } },
        { itemStyle: { borderColor: isDark ? '#404040' : '#ccc', borderWidth: 2, gapWidth: 2 },
          upperLabel: { show: true, height: 22, fontSize: 11, color: isDark ? '#ccc' : '#555' } },
        { itemStyle: { borderColor: isDark ? '#4a4a4a' : '#ddd', borderWidth: 1, gapWidth: 1 }, label: { show: true, fontSize: 10 } },
      ],
      label: { show: true, formatter: '{b}', fontSize: 11, color: isDark ? '#ddd' : '#333' },
    }],
  }), [treemapData, isDark]);

  // MCP Console
  const [selectedTool, setSelectedTool] = useState<McpTool | null>(TOOLS[0] ?? null);
  const [toolModalOpen, setToolModalOpen] = useState(false);
  const [mcpResponse, setMcpResponse] = useState('');
  const [mcpStatus, setMcpStatus] = useState<number | null>(null);
  const [mcpDuration, setMcpDuration] = useState<number | null>(null);
  const [executing, setExecuting] = useState(false);
  const [mcpResultCount, setMcpResultCount] = useState<number | null>(null);
  const [responseModalOpen, setResponseModalOpen] = useState(false);
  const [history, setHistory] = useState<Array<{ toolName: string; status: number; duration: number; response: string }>>([]);
  const [paletteQuery, setPaletteQuery] = useState('');
  const [form] = Form.useForm();
  const grouped = toolsByCategory();


  const selectTool = useCallback((tool: McpTool) => {
    setSelectedTool(tool);
    const defaults: Record<string, string> = {};
    tool.params.forEach(p => { if (p.default !== undefined) defaults[p.name] = p.default; });
    form.setFieldsValue(defaults);
  }, [form]);

  const execute = useCallback(async () => {
    if (!selectedTool) return;
    const values = form.getFieldsValue();
    const params: Record<string, string> = {};
    for (const [k, v] of Object.entries(values)) { if (v !== undefined && v !== null && v !== '') params[k] = String(v); }
    if (selectedTool.params.filter(p => p.required && !params[p.name]?.trim()).length) { form.validateFields(); return; }
    setExecuting(true);
    const start = performance.now();
    try {
      const res = await fetch(resolveUrl(selectedTool, params), {
        method: selectedTool.method ?? 'GET',
        ...(selectedTool.method === 'POST' ? { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(params) } : {}),
      });
      const elapsed = Math.round(performance.now() - start);
      setMcpStatus(res.status); setMcpDuration(elapsed);
      const ct = res.headers.get('content-type') ?? '';
      let text: string;
      if (ct.includes('json')) { const json = await res.json(); text = JSON.stringify(json, null, 2); setMcpResultCount(countResults(json)); }
      else { text = await res.text(); setMcpResultCount(null); }
      setMcpResponse(text); setResponseModalOpen(true);
      setHistory(prev => [{ toolName: selectedTool.name, status: res.status, duration: elapsed, response: text }, ...prev.slice(0, 19)]);
    } catch (err) {
      const elapsed = Math.round(performance.now() - start);
      setMcpStatus(0); setMcpDuration(elapsed);
      const text = JSON.stringify({ error: err instanceof Error ? err.message : String(err) }, null, 2);
      setMcpResponse(text); setMcpResultCount(null); setResponseModalOpen(true);
      setHistory(prev => [{ toolName: selectedTool.name, status: 0, duration: elapsed, response: text }, ...prev.slice(0, 19)]);
    } finally { setExecuting(false); }
  }, [selectedTool, form]);

  const q = paletteQuery.toLowerCase().trim();
  const filteredMenuItems = CATEGORIES.map(cat => {
    const tools = (grouped[cat.id] ?? []).filter(t => !q || t.name.includes(q) || t.description.toLowerCase().includes(q));
    return {
      key: cat.id, icon: CATEGORY_ICONS[cat.id] ?? <ApiOutlined />, label: cat.label,
      children: tools.map(tool => ({
        key: tool.name,
        label: (
          <div style={{ lineHeight: 1.4, padding: '6px 0' }}>
            <div style={{ fontSize: 12, fontWeight: 500 }}>{tool.name}</div>
            <div style={{ fontSize: 11, color: '#888', whiteSpace: 'normal', marginTop: 2 }}>{tool.description}</div>
          </div>
        ),
      })),
    };
  }).filter(cat => cat.children.length > 0);

  if (statsLoading || treeLoading) return <div style={{ textAlign: 'center', padding: 80 }}><Spin size="large" /></div>;
  if (statsError) return <Alert type="error" message="Failed to load stats" description={statsError} showIcon style={{ margin: 24 }} />;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 64px)', margin: '-16px -24px', padding: '8px 10px 0' }}>
      {/* Stats row */}
      <Row gutter={[8, 8]} style={{ flexShrink: 0, marginBottom: 8 }}>
        {[
          { t: 'Nodes', v: nodeCount.toLocaleString(), i: <NodeIndexOutlined />, d: nodeKindBreakdown, dt: 'Nodes by Kind' },
          { t: 'Edges', v: edgeCount.toLocaleString(), i: <BranchesOutlined />, d: edgeKindBreakdown, dt: 'Edges by Kind' },
          { t: 'Files', v: fileCount.toLocaleString(), i: <FileOutlined /> },
          { t: 'Languages', v: Object.keys(languages).length, i: <CodeOutlined />, d: languages, dt: 'Languages' },
          { t: 'Frameworks', v: Object.keys(frameworks).length, i: <BuildOutlined />, d: frameworks, dt: 'Frameworks' },
          { t: 'Connections', v: sumValues(connections), i: <ApiOutlined />, d: connections, dt: 'Connections' },
          { t: 'Security', v: sumValues(auth), i: <SafetyOutlined />, d: auth, dt: 'Auth Patterns' },
          { t: 'Code Structure', v: sumValues(architecture), i: <AppstoreOutlined />, d: architecture, dt: 'Code Structure' },
        ].map(s => (
          <Col key={s.t} xs={12} sm={8} md={6} lg={3}>
            <StatCard title={s.t} value={s.v} icon={s.i} detail={s.d} detailTitle={s.dt} />
          </Col>
        ))}
      </Row>

      {/* Main area: Treemap (left) + MCP Console (right) */}
      <div style={{ flex: 1, minHeight: 0, display: 'flex', gap: 8 }}>
        {/* Treemap */}
        <div style={{ flex: 1, minWidth: 0, position: 'relative' }}>
          {treemapData.length > 0 ? (
            <ReactECharts option={chartOption} style={{ height: '100%', width: '100%' }}
              theme={isDark ? 'dark' : undefined} opts={{ renderer: 'canvas' }} onEvents={treemapEvents} />
          ) : (
            <div style={{ textAlign: 'center', padding: 60 }}>
              <Typography.Text type="secondary">No file data. Run index + enrich first.</Typography.Text>
            </div>
          )}
        </div>

        {/* MCP Tools — 20% */}
        <div style={{ width: '20%', flexShrink: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden',
          border: isDark ? '1px solid #303030' : '1px solid #e8e8e8', borderRadius: 8, background: isDark ? '#141414' : '#fff' }}>
          <div style={{ padding: '10px 12px 6px', flexShrink: 0 }}>
            <Typography.Text strong style={{ fontSize: 14 }}>MCP Tools</Typography.Text>
            <Input size="small" placeholder="Search tools..." prefix={<SearchOutlined style={{ color: '#888' }} />}
              allowClear value={paletteQuery} onChange={e => setPaletteQuery(e.target.value)} style={{ marginTop: 6 }} />
          </div>
          <div style={{ flex: 1, overflow: 'auto' }}>
            <Menu mode="inline" className="mcp-tool-menu" selectedKeys={[]}
              defaultOpenKeys={paletteQuery ? CATEGORIES.map(c => c.id) : ['stats']}
              items={filteredMenuItems}
              onClick={({ key }: { key: string }) => {
                const t = TOOLS.find(t => t.name === key);
                if (t) { selectTool(t); setToolModalOpen(true); }
              }}
              style={{ borderRight: 'none', fontSize: 11 }} />
          </div>
        </div>
      </div>

      {/* MCP Tool Form Modal */}
      <Modal
        title={selectedTool ? (
          <Space size={4}>
            <span>{selectedTool.name}</span>
            <Tag color={selectedTool.method === 'POST' ? 'orange' : 'green'}>{selectedTool.method ?? 'GET'}</Tag>
          </Space>
        ) : 'Tool'}
        open={toolModalOpen}
        onCancel={() => setToolModalOpen(false)}
        footer={null}
        width={500}
      >
        {selectedTool && (
          <>
            <Typography.Paragraph type="secondary" style={{ fontSize: 12 }}>{selectedTool.description}</Typography.Paragraph>
            {selectedTool.params.length > 0 ? (
              <Form form={form} layout="vertical" onFinish={() => { execute(); setToolModalOpen(false); }} size="small">
                {selectedTool.params.map(param => (
                  <Form.Item key={param.name} name={param.name} style={{ marginBottom: 8 }}
                    label={<Space><span>{param.name}</span>{param.required && <Tag color="red" style={{ fontSize: 10 }}>required</Tag>}</Space>}
                    rules={param.required ? [{ required: true, message: `${param.name} is required` }] : []}
                    tooltip={param.description}>
                    {param.options ? (
                      <Select allowClear placeholder={param.description} options={param.options.filter(Boolean).map(o => ({ label: o, value: o }))} />
                    ) : param.type === 'number' ? (
                      <InputNumber placeholder={param.default ?? ''} style={{ width: '100%' }} />
                    ) : param.type === 'boolean' ? (
                      <Select options={[{ label: 'true', value: 'true' }, { label: 'false', value: 'false' }]} />
                    ) : (
                      <Input placeholder={param.default ?? ''} onPressEnter={() => { execute(); setToolModalOpen(false); }} />
                    )}
                  </Form.Item>
                ))}
                <Button type="primary" icon={<PlayCircleOutlined />} loading={executing} onClick={() => { execute(); setToolModalOpen(false); }} block>
                  Run
                </Button>
              </Form>
            ) : (
              <Button type="primary" icon={<PlayCircleOutlined />} loading={executing} block
                onClick={() => { execute(); setToolModalOpen(false); }}>
                Run
              </Button>
            )}

            {history.length > 0 && (
              <div style={{ marginTop: 12, borderTop: '1px solid rgba(128,128,128,0.15)', paddingTop: 8 }}>
                <Typography.Text type="secondary" style={{ fontSize: 11 }}><HistoryOutlined /> Recent</Typography.Text>
                {history.slice(0, 5).map((entry, i) => (
                  <div key={i} onClick={() => { setMcpResponse(entry.response); setMcpStatus(entry.status); setMcpDuration(entry.duration); setResponseModalOpen(true); }}
                    style={{ padding: '3px 0', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 6, fontSize: 11 }}>
                    <Tag color={entry.status >= 200 && entry.status < 300 ? 'green' : 'red'} style={{ fontSize: 10 }}>{entry.status}</Tag>
                    <span style={{ flex: 1 }}>{entry.toolName}</span>
                    <span style={{ color: '#888' }}>{entry.duration}ms</span>
                  </div>
                ))}
              </div>
            )}
          </>
        )}
      </Modal>

      {/* MCP Response Modal */}
      <Modal title={
        <Space>
          <span>Response</span>
          {mcpStatus !== null && <Tag color={mcpStatus >= 200 && mcpStatus < 300 ? 'green' : mcpStatus >= 400 ? 'red' : 'orange'}>{mcpStatus}</Tag>}
          {mcpDuration !== null && <Typography.Text type="secondary" style={{ fontSize: 12 }}><ClockCircleOutlined /> {mcpDuration}ms</Typography.Text>}
          {mcpResultCount !== null && <Typography.Text type="secondary" style={{ fontSize: 12 }}>{mcpResultCount} results</Typography.Text>}
        </Space>
      } open={responseModalOpen} onCancel={() => setResponseModalOpen(false)} footer={null} width={700}>
        <pre style={{ margin: 0, fontSize: 12, maxHeight: 500, overflow: 'auto', whiteSpace: 'pre-wrap', wordBreak: 'break-word',
          background: isDark ? '#0a0a0a' : '#fafafa', padding: 12, borderRadius: 4 }}>{mcpResponse}</pre>
      </Modal>

      {/* File viewer Drawer (double-click on leaf file) */}
      <Drawer title={fileDrawer?.path} placement="right" width="60%" open={!!fileDrawer} onClose={() => setFileDrawer(null)} styles={{ body: { padding: 0 } }}>
        {fileLoading ? <div style={{ textAlign: 'center', padding: 40 }}><Spin /></div> : (
          <pre style={{ margin: 0, padding: 16, fontSize: 13, lineHeight: 1.5, overflow: 'auto', height: '100%',
            background: isDark ? '#0a0a0a' : '#fafafa', color: isDark ? '#d4d4d4' : '#1f1f1f' }}>{fileDrawer?.content}</pre>
        )}
      </Drawer>

    </div>
  );
}
