"""Tests for LDAP authentication detector."""

from __future__ import annotations

from code_intelligence.detectors.auth.ldap_auth import LdapAuthDetector
from code_intelligence.detectors.base import DetectorContext
from code_intelligence.models.graph import NodeKind


def _ctx(content: str, language: str, file_path: str = "test_file") -> DetectorContext:
    return DetectorContext(
        file_path=file_path,
        language=language,
        content=content.encode("utf-8"),
        module_name="test-module",
    )


class TestLdapAuthDetectorMetadata:
    def test_name(self):
        d = LdapAuthDetector()
        assert d.name == "ldap_auth"

    def test_supported_languages(self):
        d = LdapAuthDetector()
        assert set(d.supported_languages) == {"java", "python", "typescript", "csharp"}

    def test_unsupported_language_returns_empty(self):
        d = LdapAuthDetector()
        result = d.detect(_ctx("LdapContextSource source = new LdapContextSource();", "go", "test.go"))
        assert len(result.nodes) == 0


class TestLdapAuthJava:
    def test_detect_ldap_context_source(self):
        code = """\
import org.springframework.ldap.core.LdapTemplate;

@Configuration
public class LdapConfig {
    @Bean
    public LdapContextSource contextSource() {
        LdapContextSource source = new LdapContextSource();
        source.setUrl("ldap://localhost:389");
        return source;
    }
}
"""
        d = LdapAuthDetector()
        result = d.detect(_ctx(code, "java", "LdapConfig.java"))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) >= 2
        assert all(n.properties["auth_type"] == "ldap" for n in guards)
        assert all(n.properties["language"] == "java" for n in guards)

    def test_detect_ldap_template(self):
        code = "LdapTemplate template = new LdapTemplate(contextSource);"
        d = LdapAuthDetector()
        result = d.detect(_ctx(code, "java", "Service.java"))
        assert len(result.nodes) == 1
        assert result.nodes[0].properties["auth_type"] == "ldap"

    def test_detect_active_directory_provider(self):
        code = """\
ActiveDirectoryLdapAuthenticationProvider provider =
    new ActiveDirectoryLdapAuthenticationProvider("corp.example.com", "ldap://ad.example.com");
"""
        d = LdapAuthDetector()
        result = d.detect(_ctx(code, "java", "SecurityConfig.java"))
        assert len(result.nodes) >= 1
        assert any("ActiveDirectory" in n.properties.get("pattern", "") for n in result.nodes)

    def test_detect_enable_ldap_repositories(self):
        code = """\
@EnableLdapRepositories
public class LdapRepoConfig {
}
"""
        d = LdapAuthDetector()
        result = d.detect(_ctx(code, "java", "LdapRepoConfig.java"))
        assert len(result.nodes) == 1

    def test_node_id_format(self):
        code = "LdapTemplate template = new LdapTemplate(ctx);"
        d = LdapAuthDetector()
        result = d.detect(_ctx(code, "java", "Svc.java"))
        assert result.nodes[0].id == "auth:Svc.java:ldap:1"


class TestLdapAuthPython:
    def test_detect_ldap3_connection(self):
        code = """\
from ldap3 import Server, Connection
server = ldap3.Server('ldap://ldap.example.com')
conn = ldap3.Connection(server, user='cn=admin', password='secret')
"""
        d = LdapAuthDetector()
        result = d.detect(_ctx(code, "python", "auth.py"))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) >= 2

    def test_detect_django_ldap_settings(self):
        code = """\
AUTH_LDAP_SERVER_URI = "ldap://ldap.example.com"
AUTH_LDAP_BIND_DN = "cn=admin,dc=example,dc=com"
AUTH_LDAP_BIND_PASSWORD = "secret"
"""
        d = LdapAuthDetector()
        result = d.detect(_ctx(code, "python", "settings.py"))
        assert len(result.nodes) >= 2
        types = {n.properties["auth_type"] for n in result.nodes}
        assert types == {"ldap"}

    def test_node_location(self):
        code = """\
# comment
AUTH_LDAP_SERVER_URI = "ldap://example.com"
"""
        d = LdapAuthDetector()
        result = d.detect(_ctx(code, "python", "settings.py"))
        assert result.nodes[0].location is not None
        assert result.nodes[0].location.line_start == 2


class TestLdapAuthTypeScript:
    def test_detect_require_ldapjs(self):
        code = """\
const ldap = require('ldapjs');
const client = ldap.createClient({ url: 'ldap://localhost:389' });
"""
        d = LdapAuthDetector()
        result = d.detect(_ctx(code, "typescript", "auth.ts"))
        assert len(result.nodes) >= 1

    def test_detect_import_ldapjs(self):
        code = """\
import ldapjs from 'ldapjs';
"""
        d = LdapAuthDetector()
        result = d.detect(_ctx(code, "typescript", "ldap.ts"))
        assert len(result.nodes) == 1

    def test_detect_passport_ldapauth(self):
        code = """\
import LdapStrategy from 'passport-ldapauth';
const strategy = new LdapStrategy({ server: { url: 'ldap://localhost' } });
"""
        d = LdapAuthDetector()
        result = d.detect(_ctx(code, "typescript", "passport.ts"))
        assert len(result.nodes) >= 1
        assert any("passport-ldapauth" in n.properties.get("pattern", "") for n in result.nodes)


class TestLdapAuthCSharp:
    def test_detect_directory_services(self):
        code = """\
using System.DirectoryServices;

public class LdapHelper {
    public void Connect() {
        DirectoryEntry entry = new DirectoryEntry("LDAP://dc=example,dc=com");
    }
}
"""
        d = LdapAuthDetector()
        result = d.detect(_ctx(code, "csharp", "LdapHelper.cs"))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) >= 2

    def test_detect_ldap_connection(self):
        code = """\
var connection = new LdapConnection(new LdapDirectoryIdentifier("ldap.example.com"));
"""
        d = LdapAuthDetector()
        result = d.detect(_ctx(code, "csharp", "Auth.cs"))
        assert len(result.nodes) == 1
        assert result.nodes[0].properties["auth_type"] == "ldap"

    def test_detect_directory_entry(self):
        code = "DirectoryEntry entry = new DirectoryEntry(path);"
        d = LdapAuthDetector()
        result = d.detect(_ctx(code, "csharp", "Ldap.cs"))
        assert len(result.nodes) == 1


class TestLdapAuthStatelessDeterministic:
    def test_deterministic_results(self):
        code = """\
LdapTemplate template = new LdapTemplate(ctx);
LdapContextSource source = new LdapContextSource();
"""
        d = LdapAuthDetector()
        r1 = d.detect(_ctx(code, "java", "Config.java"))
        r2 = d.detect(_ctx(code, "java", "Config.java"))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]

    def test_no_match_returns_empty(self):
        code = "public class NoLdap { int x = 42; }"
        d = LdapAuthDetector()
        result = d.detect(_ctx(code, "java", "NoLdap.java"))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0
