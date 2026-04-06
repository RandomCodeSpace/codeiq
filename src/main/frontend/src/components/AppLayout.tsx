import { Outlet } from 'react-router-dom';
import { Layout, Switch, Typography, Space } from 'antd';
import { SunOutlined, MoonOutlined } from '@ant-design/icons';
import { useTheme } from '@/context/ThemeContext';

const { Header, Content } = Layout;

export default function AppLayout() {
  const { isDark, toggle } = useTheme();

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header
        style={{
          padding: '0 24px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          position: 'sticky',
          top: 0,
          zIndex: 100,
          borderBottom: isDark ? '1px solid #303030' : '1px solid #e8e8e8',
        }}
      >
        <Typography.Title
          level={4}
          style={{ color: '#2563eb', margin: 0, whiteSpace: 'nowrap', lineHeight: '64px' }}
        >
          Code IQ
        </Typography.Title>
        <Space>
          <Switch
            checked={isDark}
            onChange={toggle}
            checkedChildren={<MoonOutlined />}
            unCheckedChildren={<SunOutlined />}
          />
        </Space>
      </Header>
      <Content style={{ padding: '16px 24px', overflow: 'auto' }}>
        <Outlet />
      </Content>
    </Layout>
  );
}
