"""Tests for PowerShell detector."""

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.shell.powershell_detector import PowerShellDetector
from osscodeiq.models.graph import NodeKind, EdgeKind


def _ctx(content: str, path: str = "script.ps1", language: str = "powershell") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestPowerShellDetector:
    def setup_method(self):
        self.detector = PowerShellDetector()

    def test_detects_functions(self):
        source = """\
function Deploy-Application {
    Write-Host "Deploying..."
}

function Get-ServiceHealth {
    return $true
}
"""
        result = self.detector.detect(_ctx(source))
        methods = [n for n in result.nodes if n.kind == NodeKind.METHOD]
        assert len(methods) == 2
        names = {n.label for n in methods}
        assert "Deploy-Application" in names
        assert "Get-ServiceHealth" in names

    def test_detects_advanced_function(self):
        source = """\
function Set-Configuration {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$Environment
    )
    Write-Host "Setting config for $Environment"
}
"""
        result = self.detector.detect(_ctx(source))
        methods = [n for n in result.nodes if n.kind == NodeKind.METHOD]
        assert len(methods) == 1
        assert methods[0].properties.get("advanced_function") is True

    def test_detects_import_module(self):
        source = """\
Import-Module Az.Monitor
Import-Module ActiveDirectory

function Check-Status {
    Get-ADUser -Filter *
}
"""
        result = self.detector.detect(_ctx(source))
        import_edges = [e for e in result.edges if e.kind == EdgeKind.IMPORTS]
        assert len(import_edges) >= 2
        targets = {e.target for e in import_edges}
        assert "Az.Monitor" in targets
        assert "ActiveDirectory" in targets

    def test_detects_dot_sourcing(self):
        source = """\
. ./helpers.ps1
. "C:\\scripts\\utils.ps1"
"""
        result = self.detector.detect(_ctx(source))
        import_edges = [e for e in result.edges if e.kind == EdgeKind.IMPORTS]
        assert len(import_edges) >= 1

    def test_detects_typed_parameters(self):
        source = """\
function New-Deployment {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$AppName,
        [Parameter()] [int]$Replicas
    )
    kubectl apply -f deployment.yaml
}
"""
        result = self.detector.detect(_ctx(source))
        config_nodes = [n for n in result.nodes if n.kind == NodeKind.CONFIG_DEFINITION]
        assert len(config_nodes) >= 2
        param_names = {n.fqn for n in config_nodes}
        assert "AppName" in param_names
        assert "Replicas" in param_names

    def test_empty_returns_nothing(self):
        result = self.detector.detect(_ctx("# just a comment\n"))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_no_functions(self):
        source = """\
$x = 1
Write-Host $x
"""
        result = self.detector.detect(_ctx(source))
        methods = [n for n in result.nodes if n.kind == NodeKind.METHOD]
        assert len(methods) == 0

    def test_determinism(self):
        source = """\
Import-Module PSReadLine

function Start-Service {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$Name
    )
    sc.exe start $Name
}

function Stop-Service {
    sc.exe stop $args[0]
}
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
        assert len(r1.edges) == len(r2.edges)
