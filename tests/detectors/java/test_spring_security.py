"""Tests for Spring Security auth detector."""

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.java.spring_security import SpringSecurityDetector
from code_intelligence.models.graph import NodeKind


def _ctx(content: str, file_path: str = "SecurityConfig.java") -> DetectorContext:
    return DetectorContext(
        file_path=file_path,
        language="java",
        content=content.encode("utf-8"),
        module_name="test-module",
    )


class TestSpringSecurityDetector:
    def setup_method(self):
        self.detector = SpringSecurityDetector()

    def test_supported_languages(self):
        assert self.detector.supported_languages == ("java",)
        assert self.detector.name == "spring_security"

    def test_empty_input(self):
        result = self.detector.detect(_ctx(""))
        assert isinstance(result, DetectorResult)
        assert result.nodes == []
        assert result.edges == []

    def test_no_match(self):
        source = """\
package com.example;

public class UserService {
    public User getUser(Long id) {
        return repo.findById(id);
    }
}
"""
        result = self.detector.detect(_ctx(source))
        assert result.nodes == []

    def test_secured_single_role(self):
        source = """\
package com.example;

@Secured("ROLE_ADMIN")
public void deleteUser(Long id) {
    repo.deleteById(id);
}
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        node = guards[0]
        assert node.properties["auth_type"] == "spring_security"
        assert node.properties["roles"] == ["ROLE_ADMIN"]
        assert node.properties["auth_required"] is True
        assert node.id == "auth:SecurityConfig.java:Secured:3"
        assert "@Secured" in node.annotations

    def test_secured_multiple_roles(self):
        source = """\
@Secured({"ROLE_ADMIN", "ROLE_MANAGER"})
public void updateUser(Long id) {}
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        assert guards[0].properties["roles"] == ["ROLE_ADMIN", "ROLE_MANAGER"]

    def test_preauthorize_has_role(self):
        source = """\
@PreAuthorize("hasRole('ADMIN')")
public void adminOnly() {}
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        node = guards[0]
        assert node.properties["auth_type"] == "spring_security"
        assert node.properties["roles"] == ["ADMIN"]
        assert node.properties["expression"] == "hasRole('ADMIN')"
        assert node.id == "auth:SecurityConfig.java:PreAuthorize:1"

    def test_preauthorize_has_any_role(self):
        source = """\
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public void restricted() {}
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        assert set(guards[0].properties["roles"]) == {"ADMIN", "MANAGER"}

    def test_roles_allowed(self):
        source = """\
@RolesAllowed({"ROLE_USER", "ROLE_ADMIN"})
public void someEndpoint() {}
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        assert guards[0].properties["roles"] == ["ROLE_USER", "ROLE_ADMIN"]
        assert guards[0].id == "auth:SecurityConfig.java:RolesAllowed:1"

    def test_roles_allowed_single(self):
        source = """\
@RolesAllowed("ROLE_ADMIN")
public void adminOnly() {}
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        assert guards[0].properties["roles"] == ["ROLE_ADMIN"]

    def test_enable_web_security(self):
        source = """\
@Configuration
@EnableWebSecurity
public class SecurityConfig {
}
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        assert guards[0].label == "@EnableWebSecurity"
        assert guards[0].properties["auth_type"] == "spring_security"
        assert guards[0].properties["auth_required"] is True

    def test_enable_method_security(self):
        source = """\
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
}
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        assert guards[0].label == "@EnableMethodSecurity"

    def test_security_filter_chain(self):
        source = """\
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http.build();
}
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        assert guards[0].properties["method_name"] == "filterChain"
        assert "SecurityFilterChain" in guards[0].label

    def test_authorize_http_requests(self):
        source = """\
http
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/admin/**").hasRole("ADMIN")
        .anyRequest().authenticated()
    );
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        assert ".authorizeHttpRequests()" in guards[0].label

    def test_multiple_patterns_in_one_file(self):
        source = """\
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
        return http.build();
    }

    @Secured("ROLE_ADMIN")
    public void adminMethod() {}

    @PreAuthorize("hasRole('USER')")
    public void userMethod() {}
}
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        # EnableWebSecurity, EnableMethodSecurity, SecurityFilterChain,
        # authorizeHttpRequests, Secured, PreAuthorize = 6
        assert len(guards) == 6

    def test_determinism(self):
        source = """\
@Secured("ROLE_ADMIN")
public void deleteUser(Long id) {}

@PreAuthorize("hasRole('USER')")
public void getProfile() {}
"""
        result1 = self.detector.detect(_ctx(source))
        result2 = self.detector.detect(_ctx(source))
        assert len(result1.nodes) == len(result2.nodes)
        for n1, n2 in zip(result1.nodes, result2.nodes):
            assert n1.id == n2.id
            assert n1.kind == n2.kind
            assert n1.properties == n2.properties
            assert n1.location == n2.location

    def test_line_numbers_are_correct(self):
        source = "line1\nline2\n@Secured(\"ROLE_X\")\npublic void m() {}\n"
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        assert guards[0].location.line_start == 3
