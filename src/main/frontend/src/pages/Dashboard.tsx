import { useState } from 'react';
import { Card, Col, Row, Statistic, Tag, Spin, Alert, Typography, Modal, Table } from 'antd';
import {
  NodeIndexOutlined,
  BranchesOutlined,
  FileOutlined,
  CodeOutlined,
  ApiOutlined,
  SafetyOutlined,
  AppstoreOutlined,
  BuildOutlined,
} from '@ant-design/icons';
import { useApi } from '@/hooks/useApi';
import { api } from '@/lib/api';
import type { StatsResponse } from '@/types/api';

interface StatCardProps {
  title: string;
  value: number | string;
  icon: React.ReactNode;
  detail?: Record<string, number>;
  detailTitle?: string;
}

function StatCard({ title, value, icon, detail, detailTitle }: StatCardProps) {
  const [open, setOpen] = useState(false);

  const tableData = detail
    ? Object.entries(detail)
        .sort((a, b) => b[1] - a[1])
        .map(([name, count]) => ({ key: name, name, count }))
    : [];

  return (
    <>
      <Card
        hoverable={!!detail}
        onClick={() => detail && setOpen(true)}
        style={{ cursor: detail ? 'pointer' : 'default' }}
      >
        <Statistic title={title} value={value} prefix={icon} />
        {detail && (
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            Click for breakdown
          </Typography.Text>
        )}
      </Card>
      <Modal
        title={detailTitle ?? title}
        open={open}
        onCancel={() => setOpen(false)}
        footer={null}
        width={600}
      >
        <Table
          dataSource={tableData}
          pagination={tableData.length > 15 ? { pageSize: 15 } : false}
          size="small"
          columns={[
            { title: 'Name', dataIndex: 'name', key: 'name' },
            {
              title: 'Count',
              dataIndex: 'count',
              key: 'count',
              align: 'right' as const,
              render: (v: number) => v.toLocaleString(),
            },
          ]}
        />
      </Modal>
    </>
  );
}

function isComputedStats(s: StatsResponse): s is StatsResponse & {
  graph: { nodes: number; edges: number; files: number };
  languages: Record<string, number>;
  frameworks: Record<string, number>;
  connections?: { rest?: Record<string, number> };
  auth?: Record<string, number>;
  architecture?: Record<string, number>;
} {
  return 'graph' in s;
}

export default function Dashboard() {
  const { data: stats, loading, error } = useApi(() => api.getStats(), []);
  const { data: kinds } = useApi(() => api.getKinds(), []);

  const computed = stats && isComputedStats(stats) ? stats : null;
  const queryStats = stats && !isComputedStats(stats)
    ? stats as { node_count: number; edge_count: number; nodes_by_kind: Record<string, number> }
    : null;

  const nodeCount = computed?.graph?.nodes ?? queryStats?.node_count ?? 0;
  const edgeCount = computed?.graph?.edges ?? queryStats?.edge_count ?? 0;
  const fileCount = computed?.graph?.files ?? 0;
  const languages = computed?.languages ?? {};
  const langCount = Object.keys(languages).length;
  const frameworks = computed?.frameworks ?? {};
  const connections = computed?.connections?.rest ?? {};
  const auth = computed?.auth ?? {};
  const architecture = computed?.architecture ?? {};

  // Build node kind breakdown from kinds API
  const nodeKindBreakdown: Record<string, number> = {};
  if (kinds?.kinds) {
    for (const k of kinds.kinds) {
      nodeKindBreakdown[k.kind] = k.count;
    }
  }

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 80 }}><Spin size="large" /></div>;
  }

  if (error) {
    return <Alert type="error" message="Failed to load stats" description={error} showIcon style={{ margin: 24 }} />;
  }

  return (
    <div>
      <Typography.Title level={3} style={{ marginBottom: 24 }}>Dashboard</Typography.Title>

      <Row gutter={[16, 16]}>
        <Col xs={12} sm={8} md={6}>
          <StatCard
            title="Node Kinds"
            value={nodeCount.toLocaleString()}
            icon={<NodeIndexOutlined />}
            detail={nodeKindBreakdown}
            detailTitle="Node Kind Breakdown"
          />
        </Col>
        <Col xs={12} sm={8} md={6}>
          <StatCard
            title="Edges"
            value={edgeCount.toLocaleString()}
            icon={<BranchesOutlined />}
          />
        </Col>
        <Col xs={12} sm={8} md={6}>
          <StatCard
            title="Files"
            value={fileCount.toLocaleString()}
            icon={<FileOutlined />}
          />
        </Col>
        <Col xs={12} sm={8} md={6}>
          <StatCard
            title="Languages"
            value={langCount}
            icon={<CodeOutlined />}
            detail={languages}
            detailTitle="Language Distribution"
          />
        </Col>
        <Col xs={12} sm={8} md={6}>
          <StatCard
            title="Frameworks"
            value={Object.keys(frameworks).length}
            icon={<BuildOutlined />}
            detail={frameworks}
            detailTitle="Detected Frameworks"
          />
        </Col>
        <Col xs={12} sm={8} md={6}>
          <StatCard
            title="REST Endpoints"
            value={Object.values(connections).reduce((a, b) => a + b, 0)}
            icon={<ApiOutlined />}
            detail={connections}
            detailTitle="REST Endpoints by Method"
          />
        </Col>
        <Col xs={12} sm={8} md={6}>
          <StatCard
            title="Auth Guards"
            value={Object.values(auth).reduce((a, b) => a + b, 0)}
            icon={<SafetyOutlined />}
            detail={auth}
            detailTitle="Auth Patterns"
          />
        </Col>
        <Col xs={12} sm={8} md={6}>
          <StatCard
            title="Architecture Layers"
            value={Object.keys(architecture).length}
            icon={<AppstoreOutlined />}
            detail={architecture}
            detailTitle="Architecture Layer Distribution"
          />
        </Col>
      </Row>

      {/* Frameworks as tags for quick glance */}
      {Object.keys(frameworks).length > 0 && (
        <Card title="Detected Frameworks" style={{ marginTop: 16 }} size="small">
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
            {Object.entries(frameworks)
              .sort((a, b) => b[1] - a[1])
              .map(([name, count]) => (
                <Tag key={name} color="blue">{name} ({count})</Tag>
              ))}
          </div>
        </Card>
      )}
    </div>
  );
}
