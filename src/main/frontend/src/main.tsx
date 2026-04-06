import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { ConfigProvider, theme, App as AntApp } from 'antd';
import AppRoot from './App';
import { ThemeProvider, useTheme } from './context/ThemeContext';
import './index.css';

function ThemedApp() {
  const { isDark } = useTheme();
  return (
    <ConfigProvider theme={{
      algorithm: isDark
        ? [theme.darkAlgorithm]
        : [theme.defaultAlgorithm],
      token: {
        // Premium deep indigo palette
        colorPrimary: '#4f46e5',
        colorSuccess: '#10b981',
        colorWarning: '#f59e0b',
        colorError: '#ef4444',
        colorInfo: '#6366f1',
        // Typography
        fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
        fontSize: 14,
        // Refined spacing
        borderRadius: 6,
        wireframe: false,
        // Dark mode refinements
        ...(isDark ? {
          colorBgContainer: '#1f1f23',
          colorBgElevated: '#27272b',
          colorBgLayout: '#18181b',
          colorBorder: '#3f3f46',
          colorBorderSecondary: '#34343a',
        } : {
          colorBgContainer: '#ffffff',
          colorBgElevated: '#ffffff',
          colorBgLayout: '#f5f5f9',
        }),
      },
      components: {
        Table: {
          headerBg: isDark ? '#27272b' : '#fafafa',
          rowHoverBg: isDark ? '#2d2d33' : '#f0f0ff',
        },
        Card: {
          paddingLG: 20,
        },
        Menu: {
          itemBg: 'transparent',
          darkItemBg: 'transparent',
        },
      },
    }}>
      <AntApp>
        <BrowserRouter>
          <AppRoot />
        </BrowserRouter>
      </AntApp>
    </ConfigProvider>
  );
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ThemeProvider>
      <ThemedApp />
    </ThemeProvider>
  </React.StrictMode>
);
