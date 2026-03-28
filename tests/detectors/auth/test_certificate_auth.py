"""Tests for certificate-based authentication detector."""

from __future__ import annotations

from code_intelligence.detectors.auth.certificate_auth import CertificateAuthDetector
from code_intelligence.detectors.base import DetectorContext
from code_intelligence.models.graph import NodeKind


def _ctx(content: str, language: str, file_path: str = "test_file") -> DetectorContext:
    return DetectorContext(
        file_path=file_path,
        language=language,
        content=content.encode("utf-8"),
        module_name="test-module",
    )


class TestCertificateAuthDetectorMetadata:
    def test_name(self):
        d = CertificateAuthDetector()
        assert d.name == "certificate_auth"

    def test_supported_languages(self):
        d = CertificateAuthDetector()
        assert "java" in d.supported_languages
        assert "python" in d.supported_languages
        assert "typescript" in d.supported_languages
        assert "csharp" in d.supported_languages
        assert "json" in d.supported_languages
        assert "yaml" in d.supported_languages


class TestMtlsPatterns:
    def test_detect_ssl_verify_client(self):
        code = "ssl_verify_client on;"
        d = CertificateAuthDetector()
        result = d.detect(_ctx(code, "yaml", "nginx.conf"))
        assert len(result.nodes) == 1
        assert result.nodes[0].properties["auth_type"] == "mtls"
        assert result.nodes[0].kind == NodeKind.GUARD

    def test_detect_request_cert_true(self):
        code = """\
const options = {
    requestCert: true,
    rejectUnauthorized: true,
};
"""
        d = CertificateAuthDetector()
        result = d.detect(_ctx(code, "typescript", "server.ts"))
        nodes = [n for n in result.nodes if n.properties["auth_type"] == "mtls"]
        assert len(nodes) >= 1

    def test_detect_client_auth_true(self):
        code = '<Connector clientAuth="true" port="8443" />'
        d = CertificateAuthDetector()
        result = d.detect(_ctx(code, "java", "server.xml"))
        assert len(result.nodes) == 1
        assert result.nodes[0].properties["auth_type"] == "mtls"

    def test_detect_x509_authentication_filter_as_mtls(self):
        code = "X509AuthenticationFilter filter = new X509AuthenticationFilter();"
        d = CertificateAuthDetector()
        result = d.detect(_ctx(code, "java", "SecurityConfig.java"))
        # X509AuthenticationFilter matches mTLS (first match wins)
        assert len(result.nodes) == 1
        assert result.nodes[0].properties["auth_type"] == "mtls"

    def test_detect_add_certificate_forwarding(self):
        code = "builder.Services.AddCertificateForwarding(options => { });"
        d = CertificateAuthDetector()
        result = d.detect(_ctx(code, "csharp", "Program.cs"))
        assert len(result.nodes) == 1
        assert result.nodes[0].properties["auth_type"] == "mtls"

    def test_node_id_format(self):
        code = "ssl_verify_client on;"
        d = CertificateAuthDetector()
        result = d.detect(_ctx(code, "yaml", "conf.yml"))
        assert result.nodes[0].id == "auth:conf.yml:cert:1"


class TestX509Patterns:
    def test_detect_certificate_authentication_defaults(self):
        code = """\
services.AddAuthentication(CertificateAuthenticationDefaults.AuthenticationScheme)
    .AddCertificate();
"""
        d = CertificateAuthDetector()
        result = d.detect(_ctx(code, "csharp", "Startup.cs"))
        nodes = [n for n in result.nodes if n.properties["auth_type"] == "x509"]
        assert len(nodes) >= 1

    def test_detect_spring_x509(self):
        code = """\
http
    .x509()
    .subjectPrincipalRegex("CN=(.*?)(?:,|$)");
"""
        d = CertificateAuthDetector()
        result = d.detect(_ctx(code, "java", "SecurityConfig.java"))
        x509_nodes = [n for n in result.nodes if n.properties["auth_type"] == "x509"]
        assert len(x509_nodes) >= 1


class TestTlsConfigPatterns:
    def test_detect_javax_keystore(self):
        code = 'System.setProperty("javax.net.ssl.keyStore", "/path/to/keystore.jks");'
        d = CertificateAuthDetector()
        result = d.detect(_ctx(code, "java", "TlsConfig.java"))
        assert len(result.nodes) == 1
        assert result.nodes[0].properties["auth_type"] == "tls_config"

    def test_detect_ssl_context(self):
        code = "ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)"
        d = CertificateAuthDetector()
        result = d.detect(_ctx(code, "python", "client.py"))
        assert len(result.nodes) == 1
        assert result.nodes[0].properties["auth_type"] == "tls_config"

    def test_detect_tls_create_server(self):
        code = """\
const server = tls.createServer(options, (socket) => {
    console.log('server connected');
});
"""
        d = CertificateAuthDetector()
        result = d.detect(_ctx(code, "typescript", "server.ts"))
        assert len(result.nodes) >= 1
        assert result.nodes[0].properties["auth_type"] == "tls_config"

    def test_detect_cert_file_path(self):
        code = """cert: fs.readFileSync('/etc/ssl/certs/server.pem')"""
        d = CertificateAuthDetector()
        result = d.detect(_ctx(code, "typescript", "tls.ts"))
        assert len(result.nodes) >= 1
        node = result.nodes[0]
        assert node.properties["auth_type"] == "tls_config"
        assert node.properties["cert_path"] == "/etc/ssl/certs/server.pem"

    def test_detect_truststore(self):
        code = 'trustStore = "/opt/certs/truststore.jks"'
        d = CertificateAuthDetector()
        result = d.detect(_ctx(code, "java", "Config.java"))
        assert len(result.nodes) == 1
        assert result.nodes[0].properties["auth_type"] == "tls_config"


class TestAzureAdPatterns:
    def test_detect_azure_ad_config(self):
        code = """\
{
    "AzureAd": {
        "Instance": "https://login.microsoftonline.com/",
        "TenantId": "your-tenant-id"
    }
}
"""
        d = CertificateAuthDetector()
        result = d.detect(_ctx(code, "json", "appsettings.json"))
        azure_nodes = [n for n in result.nodes if n.properties["auth_type"] == "azure_ad"]
        assert len(azure_nodes) >= 1

    def test_detect_azure_tenant_id(self):
        code = 'AZURE_TENANT_ID = "abc-def-123"'
        d = CertificateAuthDetector()
        result = d.detect(_ctx(code, "python", "settings.py"))
        assert len(result.nodes) == 1
        assert result.nodes[0].properties["auth_type"] == "azure_ad"
        assert result.nodes[0].properties.get("tenant_id") == "abc-def-123"

    def test_detect_msal_browser(self):
        code = """import { PublicClientApplication } from '@azure/msal-browser';"""
        d = CertificateAuthDetector()
        result = d.detect(_ctx(code, "typescript", "auth.ts"))
        assert len(result.nodes) >= 1
        azure_nodes = [n for n in result.nodes if n.properties["auth_type"] == "azure_ad"]
        assert len(azure_nodes) >= 1

    def test_detect_add_microsoft_identity(self):
        code = "builder.Services.AddMicrosoftIdentityWebApi(builder.Configuration);"
        d = CertificateAuthDetector()
        result = d.detect(_ctx(code, "csharp", "Program.cs"))
        assert len(result.nodes) == 1
        assert result.nodes[0].properties["auth_type"] == "azure_ad"

    def test_detect_client_certificate_credential(self):
        code = """\
var credential = new ClientCertificateCredential(tenantId, clientId, certPath);
"""
        d = CertificateAuthDetector()
        result = d.detect(_ctx(code, "csharp", "Auth.cs"))
        assert len(result.nodes) == 1
        assert result.nodes[0].properties["auth_type"] == "azure_ad"
        assert result.nodes[0].properties.get("auth_flow") == "client_certificate"

    def test_detect_msal_auth_flow(self):
        code = "from msal import ConfidentialClientApplication"
        d = CertificateAuthDetector()
        result = d.detect(_ctx(code, "python", "auth.py"))
        assert len(result.nodes) >= 1
        msal_nodes = [n for n in result.nodes if n.properties.get("auth_flow") == "msal"]
        assert len(msal_nodes) >= 1


class TestCertificateAuthStatelessDeterministic:
    def test_deterministic_results(self):
        code = """\
ssl_verify_client on;
trustStore = "/path/to/trust.jks"
"""
        d = CertificateAuthDetector()
        r1 = d.detect(_ctx(code, "yaml", "config.yml"))
        r2 = d.detect(_ctx(code, "yaml", "config.yml"))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]

    def test_no_match_returns_empty(self):
        code = "public class NoCerts { int x = 42; }"
        d = CertificateAuthDetector()
        result = d.detect(_ctx(code, "java", "NoCerts.java"))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_one_node_per_line(self):
        # Even if multiple patterns match the same line, only one node is produced.
        code = "X509AuthenticationFilter filter = new X509AuthenticationFilter();"
        d = CertificateAuthDetector()
        result = d.detect(_ctx(code, "java", "Config.java"))
        assert len(result.nodes) == 1
