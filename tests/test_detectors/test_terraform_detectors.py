"""Tests for Terraform detectors."""

from osscodeiq.detectors.base import DetectorContext
from osscodeiq.models.graph import NodeKind, EdgeKind


def _ctx(content, file_path="main.tf"):
    return DetectorContext(
        file_path=file_path,
        language="terraform",
        content=content,
        module_name="infra",
    )


class TestTerraformDetector:
    def test_detect_resources(self):
        source = b'''
resource "azurerm_resource_group" "example" {
  name     = "example-resources"
  location = "East US"
}

resource "azurerm_storage_account" "example" {
  name                     = "examplesa"
  resource_group_name      = azurerm_resource_group.example.name
}

variable "location" {
  type    = string
  default = "East US"
}

output "storage_id" {
  value = azurerm_storage_account.example.id
}
'''
        from osscodeiq.detectors.iac.terraform import TerraformDetector

        result = TerraformDetector().detect(_ctx(source))
        resources = [n for n in result.nodes if n.kind == NodeKind.INFRA_RESOURCE]
        configs = [n for n in result.nodes if n.kind == NodeKind.CONFIG_DEFINITION]
        assert len(resources) >= 2
        assert len(configs) >= 2  # variable + output

    def test_detect_data_sources(self):
        source = b'''
data "azurerm_client_config" "current" {}

data "azurerm_key_vault" "existing" {
  name                = "my-key-vault"
  resource_group_name = "my-rg"
}
'''
        from osscodeiq.detectors.iac.terraform import TerraformDetector

        result = TerraformDetector().detect(_ctx(source))
        resources = [n for n in result.nodes if n.kind == NodeKind.INFRA_RESOURCE]
        assert len(resources) >= 2
        # Verify data source property
        assert all(n.properties.get("data_source") for n in resources)

    def test_detect_modules(self):
        source = b'''
module "networking" {
  source = "./modules/networking"
  vnet_name = "main-vnet"
}

module "database" {
  source = "git::https://example.com/modules/db.git"
}
'''
        from osscodeiq.detectors.iac.terraform import TerraformDetector

        result = TerraformDetector().detect(_ctx(source))
        modules = [n for n in result.nodes if n.kind == NodeKind.MODULE]
        assert len(modules) >= 2
        # Check source extraction
        net_module = [n for n in modules if "networking" in n.label][0]
        assert net_module.properties.get("source") == "./modules/networking"
        # Check DEPENDS_ON edges
        dep_edges = [e for e in result.edges if e.kind == EdgeKind.DEPENDS_ON]
        assert len(dep_edges) >= 2

    def test_detect_providers(self):
        source = b'''
provider "azurerm" {
  features {}
}

provider "aws" {
  region = "us-east-1"
}
'''
        from osscodeiq.detectors.iac.terraform import TerraformDetector

        result = TerraformDetector().detect(_ctx(source))
        providers = [n for n in result.nodes if n.kind == NodeKind.INFRA_RESOURCE
                     and n.properties.get("resource_type") == "provider"]
        assert len(providers) >= 2
        labels = {n.label for n in providers}
        assert "provider.azurerm" in labels
        assert "provider.aws" in labels

    def test_resource_provider_extraction(self):
        source = b'''
resource "aws_s3_bucket" "logs" {
  bucket = "my-logs"
}
'''
        from osscodeiq.detectors.iac.terraform import TerraformDetector

        result = TerraformDetector().detect(_ctx(source))
        resources = [n for n in result.nodes if n.kind == NodeKind.INFRA_RESOURCE]
        assert len(resources) >= 1
        assert resources[0].properties.get("provider") == "aws"
        assert resources[0].properties.get("resource_type") == "aws_s3_bucket"

    def test_node_ids(self):
        source = b'''
resource "azurerm_storage_account" "main" {}
variable "region" {}
output "id" {}
'''
        from osscodeiq.detectors.iac.terraform import TerraformDetector

        result = TerraformDetector().detect(_ctx(source))
        ids = {n.id for n in result.nodes}
        assert "tf:resource:azurerm_storage_account:main" in ids
        assert "tf:var:region" in ids
        assert "tf:output:id" in ids

    def test_supported_languages(self):
        from osscodeiq.detectors.iac.terraform import TerraformDetector

        d = TerraformDetector()
        assert "terraform" in d.supported_languages
