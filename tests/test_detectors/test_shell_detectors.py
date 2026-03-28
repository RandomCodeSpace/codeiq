"""Tests for shell detectors."""
from osscodeiq.detectors.base import DetectorContext
from osscodeiq.models.graph import NodeKind


class TestBashDetector:
    def test_detect_functions(self):
        source = b'''#!/bin/bash
function deploy_app {
    docker build -t myapp .
    docker push myapp
}
cleanup() {
    rm -rf /tmp/build
}
export DB_HOST=localhost
'''
        ctx = DetectorContext(file_path="deploy.sh", language="bash", content=source, module_name="scripts")
        from osscodeiq.detectors.shell.bash_detector import BashDetector
        result = BashDetector().detect(ctx)
        methods = [n for n in result.nodes if n.kind == NodeKind.METHOD]
        assert len(methods) >= 2  # deploy_app + cleanup


class TestPowerShellDetector:
    def test_detect_functions(self):
        source = b'''
function Deploy-Application {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$Environment
    )
    Write-Host "Deploying to $Environment"
}
function Get-ServiceHealth {
    Import-Module Az.Monitor
    return $true
}
'''
        ctx = DetectorContext(file_path="deploy.ps1", language="powershell", content=source, module_name="scripts")
        from osscodeiq.detectors.shell.powershell_detector import PowerShellDetector
        result = PowerShellDetector().detect(ctx)
        methods = [n for n in result.nodes if n.kind == NodeKind.METHOD]
        assert len(methods) >= 2
