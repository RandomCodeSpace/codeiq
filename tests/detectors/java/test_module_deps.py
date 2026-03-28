"""Tests for Maven/Gradle module dependency detector."""

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.java.module_deps import ModuleDepsDetector
from code_intelligence.models.graph import NodeKind, EdgeKind


def _ctx(content: str, path: str = "pom.xml", language: str = "xml") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestModuleDepsDetector:
    def setup_method(self):
        self.detector = ModuleDepsDetector()

    def test_detects_maven_module(self):
        source = """\
<project>
    <groupId>com.example</groupId>
    <artifactId>my-app</artifactId>
    <version>1.0.0</version>
</project>
"""
        result = self.detector.detect(_ctx(source))
        modules = [n for n in result.nodes if n.kind == NodeKind.MODULE]
        assert len(modules) == 1
        assert modules[0].label == "my-app"
        assert modules[0].properties["group_id"] == "com.example"

    def test_detects_maven_dependencies(self):
        source = """\
<project>
    <groupId>com.example</groupId>
    <artifactId>my-app</artifactId>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>common-lib</artifactId>
        </dependency>
    </dependencies>
</project>
"""
        result = self.detector.detect(_ctx(source))
        dep_edges = [e for e in result.edges if e.kind == EdgeKind.DEPENDS_ON]
        assert len(dep_edges) == 2

    def test_detects_maven_submodules(self):
        source = """\
<project>
    <groupId>com.example</groupId>
    <artifactId>parent</artifactId>
    <modules>
        <module>core</module>
        <module>api</module>
        <module>web</module>
    </modules>
</project>
"""
        result = self.detector.detect(_ctx(source))
        modules = [n for n in result.nodes if n.kind == NodeKind.MODULE]
        assert len(modules) == 4  # parent + 3 submodules
        contains_edges = [e for e in result.edges if e.kind == EdgeKind.CONTAINS]
        assert len(contains_edges) == 3

    def test_detects_gradle_dependencies(self):
        source = """\
plugins {
    id 'java'
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter:3.0.0'
    implementation project(':common')
    testImplementation 'junit:junit:4.13.2'
}
"""
        result = self.detector.detect(_ctx(source, path="build.gradle", language="gradle"))
        dep_edges = [e for e in result.edges if e.kind == EdgeKind.DEPENDS_ON]
        assert len(dep_edges) >= 3

    def test_gradle_settings_file_still_creates_module(self):
        # Note: settings.gradle matches .endswith(".gradle") in the detector,
        # so it goes through _detect_gradle rather than _detect_gradle_settings.
        source = """\
rootProject.name = 'my-project'
include ':core'
include ':api'
include ':web'
"""
        result = self.detector.detect(_ctx(source, path="settings.gradle", language="gradle"))
        modules = [n for n in result.nodes if n.kind == NodeKind.MODULE]
        assert len(modules) >= 1

    def test_empty_returns_nothing(self):
        result = self.detector.detect(_ctx("no xml here", path="readme.txt", language="text"))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_invalid_xml_returns_nothing(self):
        result = self.detector.detect(_ctx("<broken>xml<", path="pom.xml"))
        assert len(result.nodes) == 0

    def test_determinism(self):
        source = """\
<project>
    <groupId>com.example</groupId>
    <artifactId>my-app</artifactId>
    <dependencies>
        <dependency>
            <groupId>org.spring</groupId>
            <artifactId>spring-core</artifactId>
        </dependency>
    </dependencies>
</project>
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
        assert len(r1.edges) == len(r2.edges)
