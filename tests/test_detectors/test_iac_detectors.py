"""Tests for IaC detectors."""
from osscodeiq.detectors.base import DetectorContext
from osscodeiq.models.graph import NodeKind


def _ctx(content, language="bicep", file_path="main.bicep"):
    return DetectorContext(file_path=file_path, language=language, content=content, module_name="infra")


class TestBicepDetector:
    def test_detect_resources(self):
        source = b"""
        resource cosmosAccount 'Microsoft.DocumentDB/databaseAccounts@2023-04-15' = {
          name: 'mycosmosdb'
          location: 'eastus'
        }
        resource storageAccount 'Microsoft.Storage/storageAccounts@2023-01-01' = {
          name: 'mystorage'
        }
        """
        from osscodeiq.detectors.iac.bicep import BicepDetector
        detector = BicepDetector()
        result = detector.detect(_ctx(source))
        infra_nodes = [n for n in result.nodes if n.kind in (NodeKind.INFRA_RESOURCE, NodeKind.AZURE_RESOURCE)]
        assert len(infra_nodes) >= 2
        types = {n.properties.get("azure_type") for n in infra_nodes}
        assert "Microsoft.DocumentDB/databaseAccounts" in types
        assert "Microsoft.Storage/storageAccounts" in types

    def test_detect_modules(self):
        source = b"""
        module database './core/database/cosmos/cosmos-account.bicep' = {
          name: 'cosmosDb'
        }
        """
        from osscodeiq.detectors.iac.bicep import BicepDetector
        result = BicepDetector().detect(_ctx(source))
        assert len(result.nodes) >= 1 or len(result.edges) >= 1
