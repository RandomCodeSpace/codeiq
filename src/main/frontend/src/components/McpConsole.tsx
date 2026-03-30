import { useState, useCallback } from 'react';
import { Play, Clock, Terminal, Search, GitBranch, Shield, Layers, BarChart3, Code2, Zap, FileText } from 'lucide-react';

interface ToolParam {
  name: string;
  type: 'string' | 'number' | 'boolean';
  description: string;
  required?: boolean;
  default?: string;
  options?: string[];
}

interface McpTool {
  name: string;
  description: string;
  category: string;
  icon: typeof Terminal;
  params: ToolParam[];
  url: string | ((params: Record<string, string>) => string);
  method?: 'GET' | 'POST';
}

const CATEGORIES = [
  { id: 'stats', label: 'Statistics', icon: BarChart3, color: 'emerald' },
  { id: 'query', label: 'Graph Queries', icon: Search, color: 'blue' },
  { id: 'topology', label: 'Service Topology', icon: GitBranch, color: 'purple' },
  { id: 'flow', label: 'Architecture Flow', icon: Layers, color: 'amber' },
  { id: 'analysis', label: 'Analysis', icon: Zap, color: 'rose' },
  { id: 'security', label: 'Security', icon: Shield, color: 'red' },
  { id: 'code', label: 'Code', icon: Code2, color: 'cyan' },
];

const TOOLS: McpTool[] = [
  // Stats
  { name: 'get_stats', description: 'Graph statistics — node counts, edge counts, breakdown by kind and layer', category: 'stats', icon: BarChart3, params: [], url: '/api/stats' },
  { name: 'get_detailed_stats', description: 'Rich categorized statistics: frameworks, infra, connections, auth, architecture', category: 'stats', icon: BarChart3,
    params: [{ name: 'category', type: 'string', description: 'Category filter', options: ['all', 'graph', 'languages', 'frameworks', 'infra', 'connections', 'auth', 'architecture'], default: 'all' }],
    url: (p) => `/api/stats/detailed?category=${p.category || 'all'}` },

  // Graph Queries
  { name: 'query_nodes', description: 'Query nodes with optional kind filter', category: 'query', icon: Search,
    params: [
      { name: 'kind', type: 'string', description: 'Node kind filter (e.g., endpoint, entity, class)', options: ['endpoint', 'entity', 'class', 'method', 'module', 'guard', 'config_key', 'infra_resource', 'component', 'service'] },
      { name: 'limit', type: 'number', description: 'Max results', default: '20' },
    ],
    url: (p) => `/api/nodes?kind=${p.kind || ''}&limit=${p.limit || '20'}` },
  { name: 'query_edges', description: 'Query edges with optional kind filter', category: 'query', icon: Search,
    params: [
      { name: 'kind', type: 'string', description: 'Edge kind filter', options: ['calls', 'depends_on', 'imports', 'extends', 'implements', 'exposes', 'produces', 'consumes', 'protects'] },
      { name: 'limit', type: 'number', description: 'Max results', default: '20' },
    ],
    url: (p) => `/api/edges?kind=${p.kind || ''}&limit=${p.limit || '20'}` },
  { name: 'list_kinds', description: 'List all node kinds with counts', category: 'query', icon: Search, params: [], url: '/api/kinds' },
  { name: 'get_node_detail', description: 'Full detail for a specific node including properties and edges', category: 'query', icon: Search,
    params: [{ name: 'nodeId', type: 'string', description: 'Node ID', required: true }],
    url: (p) => `/api/nodes/${encodeURIComponent(p.nodeId || '')}/detail` },
  { name: 'get_neighbors', description: 'Get neighbor nodes', category: 'query', icon: Search,
    params: [
      { name: 'nodeId', type: 'string', description: 'Node ID', required: true },
      { name: 'direction', type: 'string', description: 'Direction', options: ['both', 'in', 'out'], default: 'both' },
    ],
    url: (p) => `/api/nodes/${encodeURIComponent(p.nodeId || '')}/neighbors?direction=${p.direction || 'both'}` },
  { name: 'search_graph', description: 'Free-text search across node labels, IDs, and properties', category: 'query', icon: Search,
    params: [
      { name: 'q', type: 'string', description: 'Search query', required: true },
      { name: 'limit', type: 'number', description: 'Max results', default: '20' },
    ],
    url: (p) => `/api/search?q=${encodeURIComponent(p.q || '')}&limit=${p.limit || '20'}` },
  { name: 'find_cycles', description: 'Find circular dependency cycles in the graph', category: 'query', icon: Search,
    params: [{ name: 'limit', type: 'number', description: 'Max cycles', default: '10' }],
    url: (p) => `/api/query/cycles?limit=${p.limit || '10'}` },
  { name: 'find_shortest_path', description: 'Shortest path between two nodes', category: 'query', icon: Search,
    params: [
      { name: 'source', type: 'string', description: 'Source node ID', required: true },
      { name: 'target', type: 'string', description: 'Target node ID', required: true },
    ],
    url: (p) => `/api/query/shortest-path?source=${encodeURIComponent(p.source || '')}&target=${encodeURIComponent(p.target || '')}` },
  { name: 'find_dead_code', description: 'Find potentially dead code — classes/methods with no incoming references', category: 'query', icon: Search,
    params: [
      { name: 'kind', type: 'string', description: 'Filter by kind', options: ['class', 'method', 'interface'] },
      { name: 'limit', type: 'number', description: 'Max results', default: '20' },
    ],
    url: (p) => `/api/query/dead-code?kind=${p.kind || ''}&limit=${p.limit || '20'}` },

  // Topology
  { name: 'get_topology', description: 'Full service topology map — services and their connections', category: 'topology', icon: GitBranch, params: [], url: '/api/topology' },
  { name: 'service_detail', description: 'Detailed view of a specific service', category: 'topology', icon: GitBranch,
    params: [{ name: 'name', type: 'string', description: 'Service name', required: true }],
    url: (p) => `/api/topology/services/${encodeURIComponent(p.name || '')}` },
  { name: 'service_dependencies', description: 'What a service depends on (DBs, queues, other services)', category: 'topology', icon: GitBranch,
    params: [{ name: 'name', type: 'string', description: 'Service name', required: true }],
    url: (p) => `/api/topology/services/${encodeURIComponent(p.name || '')}/deps` },
  { name: 'blast_radius', description: 'BFS blast radius — all services affected if this node fails', category: 'topology', icon: GitBranch,
    params: [{ name: 'nodeId', type: 'string', description: 'Node ID', required: true }],
    url: (p) => `/api/topology/blast-radius/${encodeURIComponent(p.nodeId || '')}` },
  { name: 'find_bottlenecks', description: 'Services with the most connections (potential bottlenecks)', category: 'topology', icon: GitBranch, params: [], url: '/api/topology/bottlenecks' },
  { name: 'find_circular_deps', description: 'Circular service-to-service dependency cycles', category: 'topology', icon: GitBranch, params: [], url: '/api/topology/circular' },
  { name: 'find_dead_services', description: 'Services with no incoming connections (potentially unused)', category: 'topology', icon: GitBranch, params: [], url: '/api/topology/dead' },

  // Flow
  { name: 'generate_flow', description: 'Generate architecture flow diagram', category: 'flow', icon: Layers,
    params: [
      { name: 'view', type: 'string', description: 'View type', options: ['overview', 'ci', 'deploy', 'runtime', 'auth'], default: 'overview' },
    ],
    url: (p) => `/api/flow/${p.view || 'overview'}?format=json` },

  // Analysis
  { name: 'find_component_by_file', description: 'Find which component/layer a file belongs to', category: 'analysis', icon: Zap,
    params: [{ name: 'file', type: 'string', description: 'File path', required: true }],
    url: (p) => `/api/triage/component?file=${encodeURIComponent(p.file || '')}` },
  { name: 'trace_impact', description: 'Trace downstream impact from a node', category: 'analysis', icon: Zap,
    params: [
      { name: 'nodeId', type: 'string', description: 'Node ID', required: true },
      { name: 'depth', type: 'number', description: 'Max depth', default: '3' },
    ],
    url: (p) => `/api/triage/impact/${encodeURIComponent(p.nodeId || '')}?depth=${p.depth || '3'}` },
  { name: 'find_consumers', description: 'Find nodes that consume/listen to a target', category: 'analysis', icon: Zap,
    params: [{ name: 'targetId', type: 'string', description: 'Target node ID', required: true }],
    url: (p) => `/api/query/consumers/${encodeURIComponent(p.targetId || '')}` },
  { name: 'find_producers', description: 'Find nodes that produce/publish to a target', category: 'analysis', icon: Zap,
    params: [{ name: 'targetId', type: 'string', description: 'Target node ID', required: true }],
    url: (p) => `/api/query/producers/${encodeURIComponent(p.targetId || '')}` },

  // Security
  { name: 'find_unprotected', description: 'Find endpoints without auth guards', category: 'security', icon: Shield,
    params: [], url: '/api/flow/auth?format=json' },

  // Code
  { name: 'read_file', description: 'Read source file content with optional line range', category: 'code', icon: FileText,
    params: [
      { name: 'path', type: 'string', description: 'File path (relative to codebase root)', required: true },
      { name: 'startLine', type: 'number', description: 'Start line' },
      { name: 'endLine', type: 'number', description: 'End line' },
    ],
    url: (p) => `/api/file?path=${encodeURIComponent(p.path || '')}${p.startLine ? `&startLine=${p.startLine}` : ''}${p.endLine ? `&endLine=${p.endLine}` : ''}` },
  { name: 'analyze_codebase', description: 'Trigger a full codebase re-analysis', category: 'analysis', icon: Zap,
    params: [{ name: 'incremental', type: 'boolean', description: 'Incremental mode', default: 'true' }],
    url: (p) => `/api/analyze?incremental=${p.incremental ?? 'true'}`, method: 'POST' },
];

const colorMap: Record<string, string> = {
  emerald: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
  blue: 'bg-blue-500/10 text-blue-400 border-blue-500/20',
  purple: 'bg-purple-500/10 text-purple-400 border-purple-500/20',
  amber: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
  rose: 'bg-rose-500/10 text-rose-400 border-rose-500/20',
  red: 'bg-red-500/10 text-red-400 border-red-500/20',
  cyan: 'bg-cyan-500/10 text-cyan-400 border-cyan-500/20',
};

export default function McpConsole() {
  const [selectedTool, setSelectedTool] = useState<McpTool>(TOOLS[0]);
  const [params, setParams] = useState<Record<string, string>>({});
  const [response, setResponse] = useState<string>('');
  const [status, setStatus] = useState<number | null>(null);
  const [duration, setDuration] = useState<number | null>(null);
  const [executing, setExecuting] = useState(false);
  const [activeCategory, setActiveCategory] = useState<string | null>(null);
  const [resultCount, setResultCount] = useState<number | null>(null);

  const selectTool = (tool: McpTool) => {
    setSelectedTool(tool);
    const defaults: Record<string, string> = {};
    tool.params.forEach(p => { if (p.default) defaults[p.name] = p.default; });
    setParams(defaults);
    setResponse('');
    setStatus(null);
    setDuration(null);
    setResultCount(null);
  };

  const execute = useCallback(async () => {
    setExecuting(true);
    const start = performance.now();
    try {
      const resolvedUrl = typeof selectedTool.url === 'function' ? selectedTool.url(params) : selectedTool.url;
      const method = selectedTool.method || 'GET';
      const opts: RequestInit = { method };
      if (method === 'POST') {
        opts.headers = { 'Content-Type': 'application/json' };
        opts.body = JSON.stringify(params);
      }

      const res = await fetch(resolvedUrl, opts);
      const elapsed = Math.round(performance.now() - start);
      setStatus(res.status);
      setDuration(elapsed);

      const contentType = res.headers.get('content-type') || '';
      let text: string;
      if (contentType.includes('json')) {
        const json = await res.json();
        text = JSON.stringify(json, null, 2);
        // Count results
        if (Array.isArray(json)) setResultCount(json.length);
        else if (json.nodes) setResultCount(json.nodes.length);
        else if (json.services) setResultCount(json.services.length);
        else if (json.kinds) setResultCount(json.kinds.length);
        else setResultCount(null);
      } else {
        text = await res.text();
        setResultCount(null);
      }
      setResponse(text);
    } catch (err) {
      const elapsed = Math.round(performance.now() - start);
      setStatus(0);
      setDuration(elapsed);
      setResponse(JSON.stringify({ error: err instanceof Error ? err.message : String(err) }, null, 2));
      setResultCount(null);
    } finally {
      setExecuting(false);
    }
  }, [selectedTool, params]);

  const filteredTools = activeCategory ? TOOLS.filter(t => t.category === activeCategory) : TOOLS;

  return (
    <div className="h-full max-w-[1600px] mx-auto">
      <div className="mb-4">
        <div className="flex items-center gap-3">
          <Terminal className="w-6 h-6 text-brand-400" />
          <div>
            <h1 className="text-2xl font-bold gradient-text">MCP Inspector</h1>
            <p className="text-xs text-surface-400">31 tools across 7 categories</p>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-12 gap-3" style={{ height: 'calc(100vh - 180px)' }}>
        {/* Left: Categories + Tools */}
        <div className="col-span-3 flex flex-col gap-2 overflow-hidden">
          {/* Category pills */}
          <div className="glass-card p-2">
            <div className="flex flex-wrap gap-1">
              <button
                onClick={() => setActiveCategory(null)}
                className={`px-2 py-1 rounded text-[10px] font-medium transition-colors ${
                  !activeCategory ? 'bg-brand-500/20 text-brand-300 border border-brand-500/30' : 'text-surface-400 hover:text-surface-200 hover:bg-surface-800/50'
                }`}
              >
                All ({TOOLS.length})
              </button>
              {CATEGORIES.map(cat => {
                const count = TOOLS.filter(t => t.category === cat.id).length;
                return (
                  <button
                    key={cat.id}
                    onClick={() => setActiveCategory(activeCategory === cat.id ? null : cat.id)}
                    className={`px-2 py-1 rounded text-[10px] font-medium transition-colors flex items-center gap-1 ${
                      activeCategory === cat.id
                        ? `${colorMap[cat.color]} border`
                        : 'text-surface-400 hover:text-surface-200 hover:bg-surface-800/50'
                    }`}
                  >
                    <cat.icon className="w-3 h-3" />
                    {cat.label} ({count})
                  </button>
                );
              })}
            </div>
          </div>

          {/* Tool list */}
          <div className="glass-card flex-1 overflow-y-auto">
            {filteredTools.map((tool, i) => {
              const cat = CATEGORIES.find(c => c.id === tool.category);
              return (
                <button
                  key={i}
                  onClick={() => selectTool(tool)}
                  className={`w-full text-left px-3 py-2 border-b border-surface-800/20 hover:bg-surface-800/50 transition-colors ${
                    selectedTool.name === tool.name ? 'bg-brand-500/10 border-l-2 border-l-brand-500' : ''
                  }`}
                >
                  <div className="flex items-center gap-2">
                    <span className={`w-1.5 h-1.5 rounded-full ${cat ? colorMap[cat.color].split(' ')[0] : ''}`} />
                    <span className="text-xs font-mono text-surface-200">{tool.name}</span>
                  </div>
                  <p className="text-[10px] text-surface-500 mt-0.5 pl-3.5 line-clamp-1">{tool.description}</p>
                </button>
              );
            })}
          </div>
        </div>

        {/* Middle: Parameters */}
        <div className="col-span-3 flex flex-col gap-2">
          {/* Tool info */}
          <div className="glass-card p-4">
            <div className="flex items-center gap-2 mb-2">
              <selectedTool.icon className="w-4 h-4 text-brand-400" />
              <h3 className="text-sm font-bold text-surface-100 font-mono">{selectedTool.name}</h3>
            </div>
            <p className="text-xs text-surface-400 leading-relaxed">{selectedTool.description}</p>
            <div className="mt-2 flex items-center gap-2">
              <span className={`text-[10px] font-mono font-bold px-1.5 py-0.5 rounded ${
                (selectedTool.method || 'GET') === 'GET' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-amber-500/10 text-amber-400'
              }`}>
                {selectedTool.method || 'GET'}
              </span>
              <span className="text-[10px] font-mono text-surface-500">
                {typeof selectedTool.url === 'function' ? selectedTool.url(params) : selectedTool.url}
              </span>
            </div>
          </div>

          {/* Parameters form */}
          <div className="glass-card flex-1 p-4 overflow-y-auto">
            <h4 className="text-xs font-medium text-surface-400 uppercase tracking-wider mb-3">Parameters</h4>
            {selectedTool.params.length === 0 ? (
              <p className="text-xs text-surface-500 italic">No parameters required</p>
            ) : (
              <div className="space-y-3">
                {selectedTool.params.map(param => (
                  <div key={param.name}>
                    <label className="flex items-center gap-1.5 mb-1">
                      <span className="text-xs font-mono text-surface-300">{param.name}</span>
                      {param.required && <span className="text-[9px] text-red-400">required</span>}
                      {param.type === 'number' && <span className="text-[9px] text-surface-600">number</span>}
                    </label>
                    <p className="text-[10px] text-surface-500 mb-1">{param.description}</p>
                    {param.options ? (
                      <select
                        value={params[param.name] || param.default || ''}
                        onChange={e => setParams({ ...params, [param.name]: e.target.value })}
                        className="w-full px-3 py-1.5 rounded bg-surface-800 border border-surface-700/50 text-xs font-mono text-surface-200 focus:outline-none focus:border-brand-500/50"
                      >
                        <option value="">-- select --</option>
                        {param.options.map(opt => <option key={opt} value={opt}>{opt}</option>)}
                      </select>
                    ) : param.type === 'boolean' ? (
                      <select
                        value={params[param.name] || param.default || 'true'}
                        onChange={e => setParams({ ...params, [param.name]: e.target.value })}
                        className="w-full px-3 py-1.5 rounded bg-surface-800 border border-surface-700/50 text-xs font-mono text-surface-200 focus:outline-none focus:border-brand-500/50"
                      >
                        <option value="true">true</option>
                        <option value="false">false</option>
                      </select>
                    ) : (
                      <input
                        type={param.type === 'number' ? 'number' : 'text'}
                        value={params[param.name] || ''}
                        onChange={e => setParams({ ...params, [param.name]: e.target.value })}
                        onKeyDown={e => e.key === 'Enter' && execute()}
                        placeholder={param.default || ''}
                        className="w-full px-3 py-1.5 rounded bg-surface-800 border border-surface-700/50 text-xs font-mono text-surface-200 placeholder:text-surface-600 focus:outline-none focus:border-brand-500/50"
                      />
                    )}
                  </div>
                ))}
              </div>
            )}

            <button
              onClick={execute}
              disabled={executing}
              className="w-full mt-4 px-4 py-2.5 rounded-lg bg-brand-600 hover:bg-brand-500 text-white text-sm font-medium flex items-center justify-center gap-2 transition-colors disabled:opacity-50"
            >
              {executing ? (
                <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
              ) : (
                <Play className="w-4 h-4" />
              )}
              Execute
            </button>
          </div>
        </div>

        {/* Right: Response */}
        <div className="col-span-6 glass-card overflow-hidden flex flex-col">
          <div className="px-4 py-2 border-b border-surface-800/50 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <span className="text-xs font-medium text-surface-400">Response</span>
              {status !== null && (
                <span className={`text-xs font-mono px-2 py-0.5 rounded ${
                  status >= 200 && status < 300
                    ? 'bg-emerald-500/10 text-emerald-400'
                    : status >= 400
                      ? 'bg-red-500/10 text-red-400'
                      : 'bg-amber-500/10 text-amber-400'
                }`}>
                  {status} {status >= 200 && status < 300 ? 'OK' : status >= 400 ? 'Error' : ''}
                </span>
              )}
              {duration !== null && (
                <span className="text-[10px] text-surface-500 font-mono flex items-center gap-1">
                  <Clock className="w-3 h-3" /> {duration}ms
                </span>
              )}
              {resultCount !== null && (
                <span className="text-[10px] text-surface-500 font-mono">
                  {resultCount} results
                </span>
              )}
            </div>
          </div>
          <div className="flex-1 overflow-auto p-4">
            {!response ? (
              <div className="h-full flex items-center justify-center text-surface-600">
                <div className="text-center">
                  <Terminal className="w-10 h-10 mx-auto mb-3 opacity-30" />
                  <p className="text-sm">Select a tool and click Execute</p>
                  <p className="text-xs mt-1 text-surface-700">Results will appear here</p>
                </div>
              </div>
            ) : (
              <pre className="text-xs font-mono text-surface-300 whitespace-pre-wrap leading-relaxed">{response}</pre>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
